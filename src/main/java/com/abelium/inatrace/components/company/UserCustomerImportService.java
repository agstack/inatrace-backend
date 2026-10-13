package com.abelium.inatrace.components.company;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.common.CommonApiTools;
import com.abelium.inatrace.components.common.DocumentData;
import com.abelium.inatrace.components.common.StorageService;
import com.abelium.inatrace.components.company.api.*;
import com.abelium.inatrace.components.company.types.UserCustomerImportCellErrorType;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.Torpedo;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    @Transactional
    public ApiUserCustomerImportResponse importFarmersSpreadsheet(Long companyId, Long documentId, CustomUserDetails authUser, Language language) throws ApiException {

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
        } catch (IOException e) {
            throw new ApiException(ApiStatus.ERROR, "Could not read file");
        }

        XSSFSheet mainSheet = mainWorkbook.getSheetAt(0);

        // boolean means that the second product is also present in the Excel
        boolean hasSecondProductType = checkSecondProductType(mainSheet);

        int rowIndex = 5;
        int successful = 0;
        List<ApiUserCustomer> duplicates = new ArrayList<>();
        List<ApiUserCustomer> toAdd = new ArrayList<>();

        // company product types (first two)
        List<ApiProductType> companyProductTypes = readCompanyProductTypes(companyId, language);

        // in case the list is empty, which means company missconfiguration, return an error.
        if (companyProductTypes.isEmpty()) {
                 throw new ApiException(ApiStatus.INVALID_REQUEST,
                     "Company has no product types configured. "
                     + "Please associate the company with a value chain before importing farmers.");
        }
        
        // if only first product type is given in Excel,
        // then take only the first element from company product types list
        if (!hasSecondProductType) {
            companyProductTypes = companyProductTypes.subList(0, 1);
        }

        ApiUserCustomerImportResponse response = new ApiUserCustomerImportResponse();

        // Go through every row and validate data
        while (true) {

            Row row = mainSheet.getRow(rowIndex);

            if (emptyRow(row)) {
                break;
            }

            ApiUserCustomerImportRowValidationError rowValidation = validateRow(row);

            // If there are no column validation errors, the row is valid
            if (rowValidation.getColumnValidationErrors().isEmpty()) {

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

                if (companyService.existsUserCustomer(apiUserCustomer)) {
                    duplicates.add(apiUserCustomer);
                } else {
                    toAdd.add(apiUserCustomer);
                }
            } else {

                response.getValidationErrors().add(rowValidation);
            }

            rowIndex++;
        }

        // If no validation errors are present, proceed and add the user customers; If validation errors are present,
        // no user customer should be persisted
        if (response.getValidationErrors().isEmpty()) {
            for (ApiUserCustomer apiUserCustomer : toAdd) {
                companyService.addUserCustomer(companyId, apiUserCustomer, authUser, language);
                successful++;
            }

            response.setDuplicates(duplicates);
        }

        response.setSuccessful(successful);

        return response;
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
        for (int i = 0; i < 33; i++) {
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

    private ApiUserCustomerImportRowValidationError validateRow(Row row) {

        ApiUserCustomerImportRowValidationError rowValidation = new ApiUserCustomerImportRowValidationError(row.getRowNum());

        // Check every cell for error, if present add the validation error in the row validation object

        // Company internal ID
        if (invalidCell(row.getCell(0), List.of(CellType.STRING, CellType.NUMERIC))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(0)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        }

        // Last name
        if (invalidCell(row.getCell(1), CellType.STRING)) {
            if (emptyCell(row.getCell(1))) {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(1)), UserCustomerImportCellErrorType.REQUIRED));
            } else {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(1)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
            }
        }

        // First name
        if (invalidCell(row.getCell(2), List.of(CellType.STRING))) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(2)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
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
        if (invalidCell(row.getCell(15), CellType.STRING)) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(15)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
        } else if (getCountryByCode(getString(row.getCell(15))) == null) {
            rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(15)), UserCustomerImportCellErrorType.INVALID_VALUE));
        }

        // Gender
        if (invalidCell(row.getCell(16), CellType.STRING)) {
            if (emptyCell(row.getCell(16))) {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(16)), UserCustomerImportCellErrorType.REQUIRED));
            } else {
                rowValidation.getColumnValidationErrors().add(new ApiUserCustomerImportColumnValidationError(getCellAddress(row.getCell(16)), UserCustomerImportCellErrorType.INCORRECT_TYPE));
            }
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

        return rowValidation;
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
}
