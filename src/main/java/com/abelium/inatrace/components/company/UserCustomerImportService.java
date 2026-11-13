package com.abelium.inatrace.components.company;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.common.CommonApiTools;
import com.abelium.inatrace.components.common.DocumentData;
import com.abelium.inatrace.components.common.StorageService;
import com.abelium.inatrace.components.company.api.*;
import com.abelium.inatrace.components.company.types.UserCustomerImportCellErrorType;
import com.abelium.inatrace.components.geoid.FaoGeoIdClientService;
import com.abelium.inatrace.components.product.ProductTypeMapper;
import com.abelium.inatrace.components.product.api.ApiBankInformation;
import com.abelium.inatrace.components.product.api.ApiFarmInformation;
import com.abelium.inatrace.components.product.api.ApiFarmPlantInformation;
import com.abelium.inatrace.components.product.api.ApiProductType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.types.Gender;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserCustomerType;
import com.abelium.inatrace.types.UserRole;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfMeasurement;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.EmptyFileException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.xmlbeans.XmlException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Lazy
@Service
public class UserCustomerImportService extends BaseService {

    @Autowired
    private CompanyQueries companyQueries;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private StorageService storageService;

    @Autowired(required = false)
    private FaoGeoIdClientService faoGeoIdClientService;

    /**
     * Column index of the "Geo Data" cell, which accepts any of the formats
     * {@link GeoDataParser} recognises.
     */
    static final int GEO_DATA_COLUMN = 33;

    /** Column index of the "GeoID (FAO)" cell, appended after {@link #GEO_DATA_COLUMN}. */
    static final int GEO_ID_COLUMN = 34;

    /** Highest column index the importer reads. */
    private static final int LAST_COLUMN = GEO_ID_COLUMN;

    private static final String PLOT_NAME_PREFIX = "Plot ";

    @Transactional
    public ApiUserCustomerImportResponse importFarmersSpreadsheet(Long companyId, Long documentId, CustomUserDetails authUser, Language language) throws ApiException {
        return importFarmersSpreadsheet(companyId, documentId, authUser, language, List.of());
    }

    @Transactional
    public ApiUserCustomerImportResponse importFarmersSpreadsheet(Long companyId, Long documentId, CustomUserDetails authUser, Language language, List<String> koboSelections) throws ApiException {
        return importFarmersSpreadsheet(companyId, documentId, authUser, language, false, null, koboSelections);
    }

    private ApiUserCustomerImportResponse importFarmersSpreadsheet(Long companyId, Long documentId, CustomUserDetails authUser, Language language, boolean preview, ApiUserCustomer selection, List<String> koboSelections) throws ApiException {

        // If importing as a Regional admin, check that it is enrolled in the company
        if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
            Company company = companyQueries.fetchCompany(companyId);
            PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), authUser);
        }

        Document document = em.find(Document.class, documentId);
        if (document == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid import document.");
        }
        if (document.getUploadedBy() == null || !document.getUploadedBy().getId().equals(authUser.getUserId())) {
            throw new ApiException(ApiStatus.UNAUTHORIZED, "You are not authorized to import this document.");
        }

        DocumentData documentData = storageService.downloadDocument(document.getStorageKey());
        InputStream inputStream;
        XSSFWorkbook mainWorkbook;

        try {
            inputStream = new ByteArrayInputStream(documentData.file);
            mainWorkbook = new XSSFWorkbook(inputStream);
            if (mainWorkbook.getNumberOfSheets() == 0) {
                mainWorkbook.close();
                throw new ApiException(ApiStatus.INVALID_REQUEST, "The file is not a valid XLSX spreadsheet. Please check the file and try again.");
            }
        } catch (NotOfficeXmlFileException | EmptyFileException e) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "The file is not a valid XLSX spreadsheet. Please check the file and try again.");
        } catch (POIXMLException e) {
            // POI 5.3.0 reports unsupported Strict OOXML without a typed cause.
            if (e.getCause() instanceof InvalidFormatException || e.getCause() instanceof XmlException
                    || "Strict OOXML isn't currently supported, please see bug #57699".equals(e.getMessage())) {
                throw new ApiException(ApiStatus.INVALID_REQUEST, "The file is not a valid XLSX spreadsheet. Please check the file and try again.");
            }
            throw e;
        } catch (IOException e) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "The file is not a valid XLSX spreadsheet. Please check the file and try again.");
        }

        XSSFSheet mainSheet = mainWorkbook.getSheetAt(0);

        // A KoboToolbox / ODK export is read directly rather than making the user re-key it into
        // the template - re-keying is where plots get attached to the wrong farmer or dropped.
        if (KoboExportReader.isKoboExport(mainWorkbook)) {
            return importKoboExport(mainWorkbook, companyId, authUser, language, preview, selection, koboSelections);
        }

        // boolean means that the second product is also present in the Excel
        boolean hasSecondProductType = checkSecondProductType(mainSheet);

        int rowIndex = 5;

        // Rows are grouped into one farmer only when they share a non-blank company-internal ID.
        // The ID column is optional and is empty in most real files, so rows without one are each
        // their own farmer - keying the map on null would merge the whole file into a single farmer.
        Map<String, ApiUserCustomer> farmersMap = new HashMap<>();
        List<ApiUserCustomer> farmers = new ArrayList<>();

        // A GeoID always resolves to the same boundary, so resolve each distinct one only once per
        // file rather than once per row that references it.
        Map<String, List<ApiPlot>> geoIdCache = new HashMap<>();

        // company product types (first two)
        List<ApiProductType> companyProductTypes = readCompanyProductTypes(companyId, language);

        // in case the list is empty, which means company missconfiguration, return an error.
        if (companyProductTypes.isEmpty()) {
                 throw new ApiException(ApiStatus.INVALID_REQUEST,
                     "Company has no product types configured. "
                     + "Please associate the company with a value chain before importing farmers.");
        }

        if (hasSecondProductType && companyProductTypes.size() < 2) {
            throw new ApiException(ApiStatus.INVALID_REQUEST,
                    "The spreadsheet requires two product types, but the company has fewer than two configured.");
        }
        
        // if only first product type is given in Excel,
        // then take only the first element from company product types list
        if (!hasSecondProductType) {
            companyProductTypes = companyProductTypes.subList(0, 1);
        }

        ApiUserCustomerImportResponse response = new ApiUserCustomerImportResponse();

        // The farmer a geo-only continuation row belongs to: the one the row above referred to,
        // which is not necessarily the one created most recently.
        ApiUserCustomer lastFarmer = null;

        // Go through every row and validate data
        while (true) {

            Row row = mainSheet.getRow(rowIndex);

            if (emptyRow(row)) {
                break;
            }

            // A row with geo data and nothing else continues the farmer above rather than starting
            // a new one, so the farmer columns are not required on it.
            if (geoOnlyRow(row)) {

                if (preview && (lastFarmer == null || !matchesImportSelection(lastFarmer, selection))) {
                    rowIndex++;
                    continue;
                }

                ApiUserCustomerImportRowValidationError geoValidation =
                        new ApiUserCustomerImportRowValidationError(row.getRowNum());
                validateGeoCells(row, geoValidation, companyProductTypes.get(0).getId(), geoIdCache);

                if (!geoValidation.getColumnValidationErrors().isEmpty()) {
                    response.getValidationErrors().add(geoValidation);
                } else if (lastFarmer == null) {
                    // No farmer above to attach these plots to.
                    geoValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(
                            getCellAddress(row.getCell(GEO_DATA_COLUMN) != null
                                    ? row.getCell(GEO_DATA_COLUMN) : row.getCell(GEO_ID_COLUMN)),
                            UserCustomerImportCellErrorType.INVALID_GEODATA));
                    response.getValidationErrors().add(geoValidation);
                } else {
                    addPlotsToFarmer(lastFarmer,
                            createUserGeoData(row, companyProductTypes.get(0).getId(), geoIdCache));
                }

                rowIndex++;
                continue;
            }

            boolean selectedRow = !preview || (sameFarmerField(getStringOrNumeric(row.getCell(0)), selection.getFarmerCompanyInternalId())
                    && sameFarmerField(getString(row.getCell(1)), selection.getSurname())
                    && sameFarmerField(getString(row.getCell(2)), selection.getName())
                    && sameFarmerField(getString(row.getCell(11)), selection.getLocation().getAddress().getCity()));
            ApiUserCustomerImportRowValidationError rowValidation = validateRow(row, companyProductTypes.get(0).getId(), geoIdCache, selectedRow);

            // If there are no column validation errors, the row is valid
            if (rowValidation.getColumnValidationErrors().isEmpty()) {
                String internalId = getStringOrNumeric(row.getCell(0));
                boolean groupable = internalId != null && !internalId.isBlank();

                // A farmer already seen in this file gains the plot of this row instead of a twin
                if (groupable && farmersMap.containsKey(internalId)) {
                    ApiUserCustomer existingFarmer = farmersMap.get(internalId);
                    if (!sameFarmerField(existingFarmer.getSurname(), getString(row.getCell(1)))
                            || !sameFarmerField(existingFarmer.getName(), getString(row.getCell(2)))
                            || !sameFarmerField(existingFarmer.getLocation().getAddress().getCity(), getString(row.getCell(11)))
                            || !sameFarmerField(existingFarmer.getLocation().getAddress().getCountry().getCode(), getString(row.getCell(15)))) {
                        rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(
                                getCellAddress(row.getCell(0)), UserCustomerImportCellErrorType.INVALID_VALUE));
                        response.getValidationErrors().add(rowValidation);
                        lastFarmer = null;
                    } else {
                        if (selectedRow) {
                            addPlotsToFarmer(existingFarmer,
                                    createUserGeoData(row, companyProductTypes.get(0).getId(), geoIdCache));
                        }
                        lastFarmer = existingFarmer;
                    }
                } else {

                    ApiUserCustomer apiUserCustomer = new ApiUserCustomer();
                    apiUserCustomer.setCompanyId(companyId);
                    apiUserCustomer.setType(UserCustomerType.FARMER);
                    apiUserCustomer.setProductTypes(companyProductTypes);

                    // ID (company-internal)
                    apiUserCustomer.setFarmerCompanyInternalId(getStringOrNumeric(row.getCell(0)));

                    // Personal data (name, surname, email, phone, etc.)
                    apiUserCustomer.setSurname(getString(row.getCell(1)));
                    apiUserCustomer.setName(getString(row.getCell(2)));
                    apiUserCustomer.setGender(getGender(row.getCell(16)));
                    apiUserCustomer.setPhone(getStringOrNumeric(row.getCell(17)));
                    apiUserCustomer.setEmail(getStringOrNumeric(row.getCell(18)));
                    apiUserCustomer.setHasSmartphone(getBoolean(row.getCell(19)));

                    // Address data - location info
                    apiUserCustomer.setLocation(new ApiUserCustomerLocation());
                    apiUserCustomer.getLocation().setAddress(new ApiAddress());
                    apiUserCustomer.getLocation().getAddress().setVillage(getString(row.getCell(3)));
                    apiUserCustomer.getLocation().getAddress().setCell(getString(row.getCell(4)));
                    apiUserCustomer.getLocation().getAddress().setSector(getString(row.getCell(5)));
                    apiUserCustomer.getLocation().getAddress().setHondurasFarm(getString(row.getCell(6)));
                    apiUserCustomer.getLocation().getAddress().setHondurasVillage(getString(row.getCell(7)));
                    apiUserCustomer.getLocation().getAddress().setHondurasMunicipality(getString(row.getCell(8)));
                    apiUserCustomer.getLocation().getAddress().setHondurasDepartment(getString(row.getCell(9)));
                    apiUserCustomer.getLocation().getAddress().setAddress(getString(row.getCell(10)));
                    apiUserCustomer.getLocation().getAddress().setCity(getString(row.getCell(11)));
                    apiUserCustomer.getLocation().getAddress().setState(getString(row.getCell(12)));
                    apiUserCustomer.getLocation().getAddress().setZip(getStringOrNumeric(row.getCell(13)));
                    apiUserCustomer.getLocation().getAddress().setOtherAddress(getString(row.getCell(14)));
                    apiUserCustomer.getLocation().getAddress().setCountry(CommonApiTools.toApiCountry(getCountryByCode(getString(row.getCell(15)))));
                    apiUserCustomer.getLocation().getAddress().getCountry().setCode(getString(row.getCell(15)));

                    // Farm info
                    apiUserCustomer.setFarm(new ApiFarmInformation());
                    apiUserCustomer.getFarm().setFarmPlantInformationList(new ArrayList<>());
                    apiUserCustomer.getFarm().setAreaUnit(getString(row.getCell(20)));
                    apiUserCustomer.getFarm().setTotalCultivatedArea(getNumericBigDecimal(row.getCell(21)));

                    ApiFarmPlantInformation apiPlant1Information = new ApiFarmPlantInformation();
                    apiPlant1Information.setProductType(companyProductTypes.get(0));
                    apiPlant1Information.setPlantCultivatedArea(getNumericBigDecimal(row.getCell(22)));
                    apiPlant1Information.setNumberOfPlants(getNumericInteger(row.getCell(23)));
                    apiUserCustomer.getFarm().getFarmPlantInformationList().add(apiPlant1Information);

                    if (hasSecondProductType && companyProductTypes.get(1) != null) {
                        ApiFarmPlantInformation apiPlant2Information = new ApiFarmPlantInformation();
                        apiPlant2Information.setProductType(companyProductTypes.get(1));
                        apiPlant2Information.setPlantCultivatedArea(getNumericBigDecimal(row.getCell(24)));
                        apiPlant2Information.setNumberOfPlants(getNumericInteger(row.getCell(25)));
                        apiUserCustomer.getFarm().getFarmPlantInformationList().add(apiPlant2Information);
                    }

                    apiUserCustomer.getFarm().setOrganic(getBoolean(row.getCell(26)));
                    if (apiUserCustomer.getFarm().getOrganic() == null) {
                        apiUserCustomer.getFarm().setOrganic(false);
                    }

                    apiUserCustomer.getFarm().setAreaOrganicCertified(getNumericBigDecimal(row.getCell(27)));
                    apiUserCustomer.getFarm().setStartTransitionToOrganic(getDate(row.getCell(28)));

                    // Bank info
                    apiUserCustomer.setBank(new ApiBankInformation());
                    apiUserCustomer.getBank().setAccountNumber(getStringOrNumeric(row.getCell(29)));
                    apiUserCustomer.getBank().setAccountHolderName(getStringOrNumeric(row.getCell(30)));
                    apiUserCustomer.getBank().setBankName(getStringOrNumeric(row.getCell(31)));
                    apiUserCustomer.getBank().setAdditionalInformation(getStringOrNumeric(row.getCell(32)));

                    // GeoData - from the Geo Data cell, the GeoID cell, or both
                    apiUserCustomer.setPlots(selectedRow
                            ? createUserGeoData(row, companyProductTypes.get(0).getId(), geoIdCache) : new ArrayList<>());
                    nameFarmerPlots(apiUserCustomer);

                    if (groupable) {
                        farmersMap.put(internalId, apiUserCustomer);
                    }
                    farmers.add(apiUserCustomer);
                    lastFarmer = apiUserCustomer;
                }

            } else {

                response.getValidationErrors().add(rowValidation);
            }

            rowIndex++;
        }

        persistFarmers(farmers, companyId, authUser, language, response, preview, selection);

        return response;
    }

    private boolean sameFarmerField(String first, String second) {
        return first == null ? second == null : second != null && first.trim().equalsIgnoreCase(second.trim());
    }

    private boolean matchesImportSelection(ApiUserCustomer farmer, ApiUserCustomer selection) {
        return sameFarmerField(farmer.getFarmerCompanyInternalId(), selection.getFarmerCompanyInternalId())
                && sameFarmerField(farmer.getName(), selection.getName())
                && sameFarmerField(farmer.getSurname(), selection.getSurname())
                && sameFarmerField(farmer.getLocation().getAddress().getCity(), selection.getLocation().getAddress().getCity());
    }

    @Transactional
    public void confirmImportedFarmerPlots(Long companyId, Long documentId, ApiUserCustomer selection,
                                           CustomUserDetails authUser, Language language) throws ApiException {
        confirmImportedFarmerPlots(companyId, documentId, selection, authUser, language, List.of());
    }

    @Transactional
    public void confirmImportedFarmerPlots(Long companyId, Long documentId, ApiUserCustomer selection,
                                           CustomUserDetails authUser, Language language, List<String> koboSelections) throws ApiException {
        ApiUserCustomer source = findConfirmedImport(companyId, documentId, selection, authUser, language, koboSelections);
        companyService.applyConfirmedFarmerImport(companyId, documentId, source, authUser, language, false);
    }

    @Transactional
    public void createSeparateImportedFarmer(Long companyId, Long documentId, ApiUserCustomer selection,
                                             CustomUserDetails authUser, Language language) throws ApiException {
        createSeparateImportedFarmer(companyId, documentId, selection, authUser, language, List.of());
    }

    @Transactional
    public void createSeparateImportedFarmer(Long companyId, Long documentId, ApiUserCustomer selection,
                                             CustomUserDetails authUser, Language language, List<String> koboSelections) throws ApiException {
        ApiUserCustomer source = findConfirmedImport(companyId, documentId, selection, authUser, language, koboSelections);
        companyService.applyConfirmedFarmerImport(companyId, documentId, source, authUser, language, true);
    }

    private ApiUserCustomer findConfirmedImport(Long companyId, Long documentId, ApiUserCustomer selection,
                                                 CustomUserDetails authUser, Language language, List<String> koboSelections) throws ApiException {
        if (selection == null || selection.getLocation() == null || selection.getLocation().getAddress() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid farmer selection.");
        }
        ApiUserCustomerImportResponse preview = importFarmersSpreadsheet(companyId, documentId, authUser, language, true, selection, koboSelections);
        if (!preview.getValidationErrors().isEmpty() || preview.getDuplicates() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "The import must be valid before confirming plots.");
        }
        List<ApiUserCustomer> matches = preview.getDuplicates();
        if (matches.size() != 1) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "The selected import row could not be identified unambiguously.");
        }
        return matches.get(0);
    }

    /**
     * Persists the farmers an import produced, unless the file had validation errors - in which
     * case nothing at all is persisted, so a partly-rejected file never leaves half its farmers
     * behind. Existing farmers are returned for an explicit decision before plots are attached.
     */
    private void persistFarmers(List<ApiUserCustomer> farmers,
                                Long companyId,
                                CustomUserDetails authUser,
                                Language language,
                                ApiUserCustomerImportResponse response, boolean preview, ApiUserCustomer selection) throws ApiException {

        List<ApiUserCustomer> duplicates = new ArrayList<>();
        List<ApiUserCustomer> toAdd = new ArrayList<>();

        for (ApiUserCustomer farmer : farmers) {
            if (preview && !matchesImportSelection(farmer, selection)) {
                continue;
            }
            if (companyService.existsUserCustomer(farmer)) {
                duplicates.add(farmer);
            } else {
                toAdd.add(farmer);
            }
        }

        int successful = 0;

        if (response.getValidationErrors().isEmpty()) {
            // Farmers that are new to the company
            if (!preview) {
                for (ApiUserCustomer apiUserCustomer : toAdd) {
                    // Re-check against the database after each insert: rows created earlier in
                    // this same import must be matched exactly like pre-existing farmers.
                    if (companyService.existsUserCustomer(apiUserCustomer)) {
                        if (!duplicates.contains(apiUserCustomer)) {
                            duplicates.add(apiUserCustomer);
                        }
                    } else {
                        companyService.addImportedUserCustomer(companyId, apiUserCustomer, authUser, language);
                        successful++;
                    }
                }
            }
            response.setDuplicates(duplicates);
        }

        response.setSuccessful(successful);
    }

    /**
     * Imports a KoboToolbox / ODK export directly. The export already links every plot of a repeat
     * group to its submission, so multi-plot farmers come through intact - which is exactly what
     * gets lost when the same data is copied into the template by hand.
     */
    private ApiUserCustomerImportResponse importKoboExport(XSSFWorkbook workbook,
                                                           Long companyId,
                                                           CustomUserDetails authUser,
                                                           Language language,
                                                           boolean preview, ApiUserCustomer selection, List<String> koboSelections) throws ApiException {

        List<ApiProductType> companyProductTypes = readCompanyProductTypes(companyId, language);
        if (companyProductTypes.isEmpty()) {
            throw new ApiException(ApiStatus.ERROR,
                    "Company has no product types configured. Please add a value chain to the company first.");
        }
        ApiProductType firstProductType = companyProductTypes.get(0);

        KoboExportReader.KoboExport export;
        try {
            Map<String, Integer> choices = new LinkedHashMap<>();
            for (String choice : koboSelections) {
                String[] parts = choice.split(":", -1);
                if (parts.length != 2) throw new IllegalArgumentException("Invalid repeat sheet selection");
                int sheetIndex = Integer.parseInt(parts[0]);
                int column = Integer.parseInt(parts[1]);
                if (sheetIndex < 1 || sheetIndex >= workbook.getNumberOfSheets()
                        || choices.putIfAbsent(workbook.getSheetName(sheetIndex), column) != null) {
                    throw new IllegalArgumentException("Invalid repeat sheet selection");
                }
            }
            export = KoboExportReader.read(workbook, choices);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid Kobo export: " + e.getMessage());
        }

        if (!export.getMissingConcepts().isEmpty()) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, String.format(
                    "This looks like a KoboToolbox export, but no column could be matched to: %s. "
                            + "Rename the matching question(s) in the export, or add the wording to "
                            + "geo/kobo-header-synonyms.csv. Columns found: %s",
                    export.getMissingConcepts(), export.getHeaders()));
        }

        if (!export.getUnresolvedSheets().isEmpty()) {
            if (preview) throw new ApiException(ApiStatus.INVALID_REQUEST, "Repeat sheet selections are incomplete.");
            ApiUserCustomerImportResponse pending = new ApiUserCustomerImportResponse();
            Map<String, List<String>> sheets = new LinkedHashMap<>();
            export.getUnresolvedSheets().forEach((name, headers) ->
                    sheets.put(workbook.getSheetIndex(name) + ":" + name, headers));
            pending.setUnresolvedKoboSheets(sheets);
            return pending;
        }

        ApiUserCustomerImportResponse response = new ApiUserCustomerImportResponse();
        List<ApiUserCustomer> farmers = new ArrayList<>();

        // Cell-level problems found while reading repeat groups (missing geometry, orphan or
        // conflicting references) are reported like template errors instead of aborting the file.
        for (KoboExportReader.KoboCellError cellError : export.getErrors()) {
            ApiUserCustomerImportRowValidationError rowValidation =
                    new ApiUserCustomerImportRowValidationError(cellError.getRowNum());
            String cell = "'" + cellError.getSheetName().replace("'", "''") + "'!"
                    + new CellReference(cellError.getRowNum(), cellError.getColumnNum()).formatAsString();
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(
                    cell, cellError.getErrorType()));
            response.getValidationErrors().add(rowValidation);
        }

        for (KoboExportReader.KoboFarmer koboFarmer : export.getFarmers()) {

            if (koboFarmer.get(KoboExportReader.Concept.LAST_NAME) == null) {
                response.getValidationErrors().add(rowError(
                        koboFarmer, KoboExportReader.Concept.LAST_NAME, UserCustomerImportCellErrorType.REQUIRED));
                continue;
            }

            Country country = getCountryByNameOrCode(koboFarmer.get(KoboExportReader.Concept.COUNTRY));
            Gender gender = toGender(koboFarmer.get(KoboExportReader.Concept.GENDER));

            if (country == null || gender == null) {
                if (country == null) {
                    response.getValidationErrors().add(rowError(
                            koboFarmer, KoboExportReader.Concept.COUNTRY, UserCustomerImportCellErrorType.INVALID_VALUE));
                }
                if (gender == null) {
                    response.getValidationErrors().add(rowError(
                            koboFarmer, KoboExportReader.Concept.GENDER, UserCustomerImportCellErrorType.INVALID_VALUE));
                }
                continue;
            }

            int previousErrors = response.getValidationErrors().size();
            List<ApiPlot> plots = koboPlots(koboFarmer, firstProductType.getId(), response);
            if (response.getValidationErrors().size() > previousErrors) {
                continue;
            }

            ApiUserCustomer apiUserCustomer = new ApiUserCustomer();
            apiUserCustomer.setCompanyId(companyId);
            apiUserCustomer.setType(UserCustomerType.FARMER);
            apiUserCustomer.setProductTypes(List.of(firstProductType));

            apiUserCustomer.setFarmerCompanyInternalId(koboFarmer.get(KoboExportReader.Concept.INTERNAL_ID));
            apiUserCustomer.setSurname(koboFarmer.get(KoboExportReader.Concept.LAST_NAME));
            apiUserCustomer.setName(koboFarmer.get(KoboExportReader.Concept.FIRST_NAME));
            apiUserCustomer.setGender(gender);
            apiUserCustomer.setPhone(koboFarmer.get(KoboExportReader.Concept.PHONE));
            apiUserCustomer.setEmail(koboFarmer.get(KoboExportReader.Concept.EMAIL));
            apiUserCustomer.setHasSmartphone(toBoolean(koboFarmer.get(KoboExportReader.Concept.SMARTPHONE)));

            apiUserCustomer.setLocation(new ApiUserCustomerLocation());
            apiUserCustomer.getLocation().setAddress(new ApiAddress());
            apiUserCustomer.getLocation().getAddress().setCity(koboFarmer.get(KoboExportReader.Concept.CITY));
            apiUserCustomer.getLocation().getAddress().setState(koboFarmer.get(KoboExportReader.Concept.STATE));
            apiUserCustomer.getLocation().getAddress().setCountry(CommonApiTools.toApiCountry(country));

            apiUserCustomer.setFarm(new ApiFarmInformation());
            apiUserCustomer.getFarm().setFarmPlantInformationList(new ArrayList<>());
            apiUserCustomer.getFarm().setOrganic(false);

            apiUserCustomer.setPlots(plots);
            nameFarmerPlots(apiUserCustomer);

            farmers.add(apiUserCustomer);
        }

        persistFarmers(farmers, companyId, authUser, language, response, preview, selection);

        return response;
    }

    /** A validation error against a whole row, for sheets that have no per-cell addresses to point at. */
    /**
     * Row-level error addressed to the cell that holds the offending concept, so the client can show
     * a spreadsheet address instead of a pseudo-address such as "row 5".
     */
    private ApiUserCustomerImportRowValidationError rowError(KoboExportReader.KoboFarmer koboFarmer,
                                                             KoboExportReader.Concept concept,
                                                             UserCustomerImportCellErrorType errorType) {

        ApiUserCustomerImportRowValidationError rowValidation =
                new ApiUserCustomerImportRowValidationError(koboFarmer.getRowNum());
        int column = koboFarmer.getColumn(concept);
        String cellAddress = column < 0
                ? "row " + (koboFarmer.getRowNum() + 1)
                : new CellReference(koboFarmer.getRowNum(), column).formatAsString();
        rowValidation.getColumnValidationErrors().add(
                new ApiUserCustomerImportColumnValidationError(cellAddress, errorType));
        return rowValidation;
    }

    /** Converts the plots of one Kobo submission, preferring the surveyed size over the computed area. */
    private List<ApiPlot> koboPlots(KoboExportReader.KoboFarmer koboFarmer, Long productTypeId,
                                    ApiUserCustomerImportResponse response) {

        List<ApiPlot> plots = new ArrayList<>();

        for (KoboExportReader.KoboPlot koboPlot : koboFarmer.getPlots()) {
            List<ApiPlot> built;
            try {
                built = buildPlots(koboPlot.getGeoData(), null, productTypeId);
            } catch (ApiException e) {
                ApiUserCustomerImportRowValidationError error = new ApiUserCustomerImportRowValidationError(koboPlot.getRowNum());
                String cell = "'" + koboPlot.getSheetName().replace("'", "''") + "'!"
                        + new CellReference(koboPlot.getRowNum(), koboPlot.getColumnNum()).formatAsString();
                error.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(
                        cell, UserCustomerImportCellErrorType.INVALID_GEODATA));
                response.getValidationErrors().add(error);
                continue;
            }
            for (ApiPlot plot : built) {
                if (koboPlot.getSize() != null && koboPlot.getSize() > 0) {
                    plot.setSize(koboPlot.getSize());
                    plot.setUnit("ha");
                }
                plot.setNumberOfPlants(koboPlot.getNumberOfPlants());
            }
            plots.addAll(built);
        }

        return plots;
    }

    private Country getCountryByNameOrCode(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();

        Country byCode = getCountryByCode(trimmed);
        if (byCode != null) {
            return byCode;
        }

        // Exports written with "labels" rather than "values" carry the country name, not its code.
        Country country = Torpedo.from(Country.class);
        Torpedo.where(country.getName()).eq(trimmed);
        List<Country> countries = Torpedo.select(country).list(em);
        return countries.size() == 1 ? countries.get(0) : null;
    }

    /** Accepts the gender wordings the supported form languages produce. */
    static Gender toGender(String value) {

        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "m", "male", "masculin", "homme", "hombre", "masculino" -> Gender.MALE;
            case "f", "female", "feminin", "féminin", "femme", "mujer", "femenino" -> Gender.FEMALE;
            case "n/a", "na" -> Gender.N_A;
            case "diverse", "divers", "diverso" -> Gender.DIVERSE;
            default -> null;
        };
    }

    /** Accepts the yes/no wordings the supported form languages produce. */
    static Boolean toBoolean(String value) {

        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "y", "yes", "oui", "si", "sí", "true", "1" -> Boolean.TRUE;
            case "n", "no", "non", "false", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * Checks if second product type exist. Checks if title of the second product type is present in the Excel.
     *
     * @param xssfSheet - xsssheet
     * @return if second product header cell is set
     */
    private boolean checkSecondProductType(XSSFSheet xssfSheet) {

        // Column headers are present in the row with index 4
        int rowIndex = 4;

        Row row = xssfSheet.getRow(rowIndex);

        // check for 25th cell if second product type is specified with title
        int colIndex = 24;

        return !emptyCell(row.getCell(colIndex));
    }

    /**
     * List of company product types.
     *
     * @param companyId - used for retrieving company product types
     * @param language - request language
     * @return - list of product types.
     */
    private List<ApiProductType> readCompanyProductTypes(Long companyId, Language language) {

        ProductType productTypeProxy = companyService.getCompanyProductTypes(companyId, null);

        // currently max of 2 product types are supported
        int numberOfMaxSupportedProductTypes = 2;

        return Torpedo.select(productTypeProxy).list(em)
                .stream()
                .limit(numberOfMaxSupportedProductTypes)
                .map(pt -> ProductTypeMapper.toApiProductTypeDetailed(pt, language))
                .collect(Collectors.toList());
    }

    private boolean emptyRow(Row row) {
        if (row == null) {
            return true;
        }
        // Every column counts, geo data included: a row that carries only a further plot for the
        // farmer above is real data, and stopping at it would silently discard the rest of the file.
        for (int i = 0; i <= LAST_COLUMN; i++) {
            if (!emptyCell(row.getCell(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean emptyCell(Cell cell) {
        return cell == null || CellType._NONE.equals(cell.getCellType()) || CellType.BLANK.equals(cell.getCellType());
    }

    private String getCellAddress(Cell cell) {
        return cell.getAddress().formatAsString();
    }

    private ApiUserCustomerImportRowValidationError validateRow(Row row, Long productTypeId, Map<String, List<ApiPlot>> geoIdCache, boolean validateGeometry) {

        ApiUserCustomerImportRowValidationError rowValidation = new ApiUserCustomerImportRowValidationError(row.getRowNum());

        // Check every cell for error, if present add the validation error in the row validation object

        // Company internal ID
        if (invalidCell(row.getCell(0), List.of(CellType.STRING, CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(0)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Last name
        if (emptyCell(row.getCell(1))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(
                    new CellReference(row.getRowNum(), 1).formatAsString(), UserCustomerImportCellErrorType.REQUIRED));
        } else if (invalidCell(row.getCell(1), CellType.STRING)) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(1)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        } else if (exceedsCharacterLimit(row.getCell(1), Lengths.SURNAME)) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(1)), UserCustomerImportCellErrorType.INVALID_VALUE));
        }

        // First name
        if (invalidCell(row.getCell(2), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(2)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        } else if (exceedsCharacterLimit(row.getCell(2), Lengths.NAME)) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(2)), UserCustomerImportCellErrorType.INVALID_VALUE));
        }

        // Village
        if (invalidCell(row.getCell(3), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(3)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Cell
        if (invalidCell(row.getCell(4), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(4)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Sector
        if (invalidCell(row.getCell(5), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(5)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Caserio
        if (invalidCell(row.getCell(6), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(6)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Aldea
        if (invalidCell(row.getCell(7), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(7)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Municipio
        if (invalidCell(row.getCell(8), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(8)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Departamento
        if (invalidCell(row.getCell(9), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(9)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Street Address
        if (invalidCell(row.getCell(10), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(10)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // City / Town / Village
        if (invalidCell(row.getCell(11), List.of(CellType.STRING))) {
            if (emptyCell(row.getCell(11))) {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(11)), UserCustomerImportCellErrorType.REQUIRED));
            } else {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(11)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
            }
        }

        // State / Province / Region
        if (invalidCell(row.getCell(12), List.of(CellType.STRING))) {
            if (emptyCell(row.getCell(12))) {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(12)), UserCustomerImportCellErrorType.REQUIRED));
            } else {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(12)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
            }
        }

        // Zip / Postal Code / P.O. Box
        if (invalidCell(row.getCell(13), List.of(CellType.STRING, CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(13)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Additional / Other address
        if (invalidCell(row.getCell(14), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(14)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Country
        if (emptyCell(row.getCell(15))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(
                    new CellReference(row.getRowNum(), 15).formatAsString(), UserCustomerImportCellErrorType.REQUIRED));
        } else if (invalidCell(row.getCell(15), CellType.STRING)) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(15)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        } else if (getCountryByCode(getString(row.getCell(15))) == null) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(15)), UserCustomerImportCellErrorType.INVALID_VALUE));
        }

        // Gender
        if (emptyCell(row.getCell(16))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(
                    new CellReference(row.getRowNum(), 16).formatAsString(), UserCustomerImportCellErrorType.REQUIRED));
        } else if (invalidCell(row.getCell(16), CellType.STRING)) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(16)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        } else if (getGender(row.getCell(16)) == null) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(16)), UserCustomerImportCellErrorType.INVALID_VALUE));
        }

        // Phone number
        if (invalidCell(row.getCell(17), List.of(CellType.STRING, CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(17)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // E-mail
        if (invalidCell(row.getCell(18), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(18)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Smartphone
        if (invalidCell(row.getCell(19), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(19)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Area unit
        if (invalidCell(row.getCell(20), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(20)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Total cultivated area
        if (invalidCell(row.getCell(21), List.of(CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(21)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Area cultivated with first plant
        if (invalidCell(row.getCell(22), List.of(CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(22)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Number of (first plant) trees
        if (invalidCell(row.getCell(23), List.of(CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(23)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Area cultivated with second plant
        if (invalidCell(row.getCell(24), List.of(CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(24)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Number of (second plant) trees
        if (invalidCell(row.getCell(25), List.of(CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(25)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Organic production (EU)
        if (invalidCell(row.getCell(26), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(26)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Area organic certified
        if (invalidCell(row.getCell(27), List.of(CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(27)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Start date of transitioning to organic
        if (invalidCell(row.getCell(28), List.of(CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(28)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Account number
        if (invalidCell(row.getCell(29), List.of(CellType.STRING, CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(29)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Account holder's name
        if (invalidCell(row.getCell(30), List.of(CellType.STRING, CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(30)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Bank name
        if (invalidCell(row.getCell(31), List.of(CellType.STRING, CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(31)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Additional information
        if (invalidCell(row.getCell(32), List.of(CellType.STRING, CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(32)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        if (validateGeometry) validateGeoCells(row, rowValidation, productTypeId, geoIdCache);

        return rowValidation;
    }

    /**
     * Validates the Geo Data and GeoID cells. Both are optional; when present, Geo Data must parse
     * as one of the formats {@link GeoDataParser} supports and the GeoID must look like a GeoID.
     */
    private void validateGeoCells(Row row, ApiUserCustomerImportRowValidationError rowValidation,
                                  Long productTypeId, Map<String, List<ApiPlot>> geoIdCache) {

        Cell geoCell = row.getCell(GEO_DATA_COLUMN);
        if (!emptyCell(geoCell)) {
            if (invalidCell(geoCell, List.of(CellType.STRING))) {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(geoCell), UserCustomerImportCellErrorType.INCORRECT_TYPE));
            } else {
                try {
                    GeoDataParser.parse(geoCell.getStringCellValue().trim(), getString(row.getCell(15)));
                } catch (IllegalArgumentException e) {
                    rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(geoCell), UserCustomerImportCellErrorType.INVALID_GEODATA));
                }
            }
        }

        Cell geoIdCell = row.getCell(GEO_ID_COLUMN);
        if (!emptyCell(geoIdCell)) {
            if (invalidCell(geoIdCell, List.of(CellType.STRING))) {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(geoIdCell), UserCustomerImportCellErrorType.INCORRECT_TYPE));
            } else if (!FaoGeoIdClientService.isGeoIdFormat(geoIdCell.getStringCellValue().trim())) {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(geoIdCell), UserCustomerImportCellErrorType.INVALID_GEODATA));
            } else if ((emptyCell(geoCell) || (geoCell.getCellType() == CellType.STRING && geoCell.getStringCellValue().isBlank()))
                    && resolveGeoId(geoIdCell.getStringCellValue().trim(), productTypeId, geoIdCache).isEmpty()) {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(geoIdCell), UserCustomerImportCellErrorType.INVALID_GEODATA));
            }
        }
    }

    /**
     * A row carrying nothing but geo data: an extra plot for the farmer on the row above. Files
     * exported from a form with a repeat group flatten multi-plot farmers exactly this way, leaving
     * the farmer columns blank on the continuation rows.
     */
    private boolean geoOnlyRow(Row row) {

        if (emptyCell(row.getCell(GEO_DATA_COLUMN)) && emptyCell(row.getCell(GEO_ID_COLUMN))) {
            return false;
        }
        for (int i = 0; i < GEO_DATA_COLUMN; i++) {
            if (!emptyCell(row.getCell(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean exceedsCharacterLimit(Cell cell, int limit) {
        if (emptyCell(cell) || cell.getCellType() != CellType.STRING) return false;
        String value = cell.getStringCellValue();
        return value.codePointCount(0, value.length()) > limit;
    }

    private boolean invalidCell(Cell cell, List<CellType> cellTypeList) {
        return invalidCell(cell, CellType._NONE) && invalidCell(cell, CellType.BLANK) && cellTypeList.stream().allMatch(cellType -> invalidCell(cell, cellType));
    }

    private boolean invalidCell(Cell cell, CellType cellType) {
        return cell != null && !cellType.equals(cell.getCellType());
    }

    private String getString(Cell cell) {
        return emptyCell(cell) ? null : cell.getStringCellValue();
    }

    private Integer getNumericInteger(Cell cell) {
        return emptyCell(cell) ? null : (int) cell.getNumericCellValue();
    }

    private BigDecimal getNumericBigDecimal(Cell cell) {
        return emptyCell(cell) ? null : BigDecimal.valueOf(cell.getNumericCellValue());
    }

    private Boolean getBoolean(Cell cell) {
        if (emptyCell(cell)) {
            return null;
        }
        switch (cell.getStringCellValue()) {
            case "Y":
                return Boolean.TRUE;
            case "N":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private Gender getGender(Cell cell) {
        if (emptyCell(cell)) {
            return null;
        }
        switch (cell.getStringCellValue()) {
            case "M":
                return Gender.MALE;
            case "F":
                return Gender.FEMALE;
            case "N/A":
                return Gender.N_A;
            case "DIVERSE":
                return Gender.DIVERSE;
            default:
                return null;
        }
    }

    private Date getDate(Cell cell) {
        return emptyCell(cell) ? null : cell.getDateCellValue();
    }

    private String getStringOrNumeric(Cell cell) {
        if (emptyCell(cell)) {
            return null;
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(Double.valueOf(cell.getNumericCellValue()).longValue());
            default:
                return null;
        }
    }

    private Country getCountryByCode(String code) {
        Country country = Torpedo.from(Country.class);
        Torpedo.where(country.getCode()).eq(code);
        List<Country> countries = Torpedo.select(country).list(em);
        return countries.size() == 1 ? countries.get(0) : null;
    }


    /**
     * Builds the plots for one spreadsheet row from its Geo Data cell, its GeoID cell, or both.
     *
     * <p>When a GeoID is present it wins: it is the registry's immutable handle for the boundary,
     * so its geometry is authoritative and interoperable. If it cannot be resolved - unknown id,
     * registry unreachable, or resolution disabled - the row falls back to the Geo Data cell.
     * The unresolved identifier is not assigned to unverified fallback geometry. If there is no
     * fallback geometry, the import is rejected instead of silently losing the plot.</p>
     */
    private List<ApiPlot> createUserGeoData(Row row, Long productTypeId, Map<String, List<ApiPlot>> geoIdCache)
            throws ApiException {

        String countryCode = getString(row.getCell(15));
        String geoId = emptyCell(row.getCell(GEO_ID_COLUMN)) ? null : row.getCell(GEO_ID_COLUMN).getStringCellValue().trim();
        String geoData = emptyCell(row.getCell(GEO_DATA_COLUMN)) ? null : row.getCell(GEO_DATA_COLUMN).getStringCellValue().trim();

        List<ApiPlot> plots = new ArrayList<>();

        if (geoId != null && !geoId.isBlank()) {
            for (ApiPlot resolved : resolveGeoId(geoId, productTypeId, geoIdCache)) {
                plots.add(copyPlot(resolved));
            }
            if (!plots.isEmpty()) {
                return plots;
            }
            logger.warn("GeoID {} could not be resolved; falling back to the Geo Data cell", geoId);
			if (geoData == null || geoData.isBlank()) {
				throw new ApiException(ApiStatus.INVALID_REQUEST,
						"Unable to resolve GeoID in cell " + getCellAddress(row.getCell(GEO_ID_COLUMN))
								+ "; provide valid Geo Data or configure the registry.");
			}
        }

        plots.addAll(buildPlots(geoData, countryCode, productTypeId));

        return plots;
    }

    /**
     * Resolves a GeoID to plots, at most once per distinct identifier per imported file. Returns an
     * empty list when the registry is not configured, does not know the identifier, or cannot be
     * reached - the caller then falls back to the row's own Geo Data cell.
     */
    private List<ApiPlot> resolveGeoId(String geoId, Long productTypeId, Map<String, List<ApiPlot>> geoIdCache) {

        List<ApiPlot> cached = geoIdCache.get(geoId);
        if (cached != null) {
            return cached;
        }

        List<ApiPlot> resolved = List.of();
        String geoJson = faoGeoIdClientService == null ? null : faoGeoIdClientService.resolveGeoJson(geoId);

        if (geoJson != null && !geoJson.isBlank()) {
            try {
                resolved = buildPlots(geoJson, null, productTypeId);
                if (resolved.size() != 1) {
                    logger.warn("GeoID {} resolved to multiple plots and cannot be assigned to one boundary", geoId);
                    resolved = List.of();
                } else {
                    resolved.get(0).setGeoId(geoId);
                }
            } catch (ApiException e) {
                // The registry, not the user, produced this geometry, so the row falls back to its
                // own Geo Data cell rather than being rejected over something it cannot fix.
                logger.warn("GeoID {} resolved to geometry that could not be read: {}", geoId, e.getMessage());
                resolved = List.of();
            }
        }

        geoIdCache.put(geoId, resolved);
        return resolved;
    }

    /**
     * Parses a geo data value in any supported format into plots.
     *
     * @throws ApiException when the value is not geo data the importer can read; the row is then
     *                      rejected rather than imported without its plots
     */
    private List<ApiPlot> buildPlots(String geoData, String countryCode, Long productTypeId) throws ApiException {

        List<GeoDataParser.ParsedPlot> parsedPlots;
        try {
            parsedPlots = GeoDataParser.parse(geoData, countryCode);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Could not read the geo data: " + e.getMessage());
        }

        ApiProductType productType = new ApiProductType();
        productType.setId(productTypeId);

        List<ApiPlot> plots = new ArrayList<>();

        for (GeoDataParser.ParsedPlot parsed : parsedPlots) {

            ApiPlot plot = new ApiPlot();
            plot.setPlotName(parsed.getLabel());
            plot.setCrop(productType);

            plot.setCoordinates(parsed.getPoints().stream().map(point -> {
                ApiPlotCoordinate coordinate = new ApiPlotCoordinate();
                coordinate.setLatitude(point[0]);
                coordinate.setLongitude(point[1]);
                return coordinate;
            }).collect(Collectors.toList()));

            if (parsed.getType() == GeoDataParser.GeoDataType.POLYGON) {
                plot.setSize(areaInHectares(parsed.getPoints()));
                plot.setUnit("ha");
            }

            plots.add(plot);
        }

        return plots;
    }

    /** Area of a plot boundary in hectares, rounded down to two decimals. */
    private double areaInHectares(List<double[]> latLonPoints) {

        List<Point> ring = latLonPoints.stream()
                .map(point -> Point.fromLngLat(point[1], point[0]))
                .collect(Collectors.toCollection(ArrayList::new));

        // Turf measures a closed ring; a hand-written boundary is often left open.
        Point first = ring.get(0);
        Point last = ring.get(ring.size() - 1);
        if (first.latitude() != last.latitude() || first.longitude() != last.longitude()) {
            ring.add(first);
        }

        Polygon polygon = Polygon.fromLngLats(Collections.singletonList(ring));
        double areaM2 = TurfMeasurement.area(Feature.fromGeometry(polygon));

        // One hectare is 10 000 m².
        return Math.floor((areaM2 / 10000) * 100) / 100.0;
    }

    /**
     * Gives every plot of a farmer a distinct name, keeping any label the source provided (for
     * example the {@code P1} / {@code P3} labels of a multi-plot cell) and numbering the rest.
     */
    private void nameFarmerPlots(ApiUserCustomer farmer) {

        if (CollectionUtils.isEmpty(farmer.getPlots())) {
            return;
        }

        int index = 0;
        for (ApiPlot plot : farmer.getPlots()) {
            index++;
            String label = plot.getPlotName();
            // Plots named by an earlier pass keep their name; the counter still advances so the
            // names stay distinct as more plots are appended to this farmer.
            if (label != null && label.startsWith(PLOT_NAME_PREFIX)) {
                continue;
            }
            plot.setPlotName(PLOT_NAME_PREFIX + (label == null || label.isBlank() ? String.valueOf(index) : label.trim()));
        }
    }

    /** Appends plots to a farmer, keeping every plot of that farmer distinctly named. */
    private void addPlotsToFarmer(ApiUserCustomer farmer, List<ApiPlot> newPlots) {

        if (newPlots.isEmpty()) {
            return;
        }
        if (farmer.getPlots() == null) {
            farmer.setPlots(new ArrayList<>());
        }
        farmer.getPlots().addAll(newPlots);
        nameFarmerPlots(farmer);
    }

    /** Shallow copy of a cached plot, so rows sharing a GeoID do not share mutable plot objects. */
    private ApiPlot copyPlot(ApiPlot source) {

        ApiPlot copy = new ApiPlot();
        copy.setPlotName(source.getPlotName());
        copy.setGeoId(source.getGeoId());
        copy.setSize(source.getSize());
        copy.setUnit(source.getUnit());
        copy.setCrop(source.getCrop());

        if (source.getCoordinates() != null) {
            copy.setCoordinates(source.getCoordinates().stream().map(coordinate -> {
                ApiPlotCoordinate copiedCoordinate = new ApiPlotCoordinate();
                copiedCoordinate.setLatitude(coordinate.getLatitude());
                copiedCoordinate.setLongitude(coordinate.getLongitude());
                return copiedCoordinate;
            }).collect(Collectors.toList()));
        }

        return copy;
    }

}
