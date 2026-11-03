package com.abelium.inatrace.components.company;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.currencies.CurrencyTypeService;
import com.abelium.inatrace.components.common.CommonApiTools;
import com.abelium.inatrace.components.common.CommonService;
import com.abelium.inatrace.components.common.api.ApiCertification;
import com.abelium.inatrace.components.common.mappers.CountryMapper;
import com.abelium.inatrace.components.company.api.*;
import com.abelium.inatrace.components.company.mappers.PlotMapper;
import com.abelium.inatrace.components.company.types.CompanyAction;
import com.abelium.inatrace.components.company.types.CompanyTranslatables;
import com.abelium.inatrace.components.product.ProductTypeMapper;
import com.abelium.inatrace.components.product.api.ApiBankInformation;
import com.abelium.inatrace.components.product.api.ApiFarmInformation;
import com.abelium.inatrace.components.product.api.ApiFarmPlantInformation;
import com.abelium.inatrace.components.user.UserApiTools;
import com.abelium.inatrace.components.user.UserQueries;
import com.abelium.inatrace.components.value_chain.api.ApiValueChain;
import com.abelium.inatrace.db.entities.common.*;
import com.abelium.inatrace.db.entities.company.*;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.value_chain.CompanyValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.tools.ListTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserCustomerType;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;


@Lazy
@Component
public class CompanyApiTools {

	@PersistenceContext
	private EntityManager em;
	
	@Autowired
	private CommonService commonEngine;

	@Autowired
	private CommonApiTools commonApiTools;
	
	@Autowired
	private UserQueries userQueries;

	@Autowired
	private CompanyQueries companyQueries;

	@Autowired
	private CurrencyTypeService currencyTypeService;
	
	public static ApiCompanyListResponse toApiCompanyListResponse(Company company) {
		if (company == null) return null;
		
		ApiCompanyListResponse apiCompany = new ApiCompanyListResponse();
		CommonApiTools.updateApiBaseEntity(apiCompany, company);
		apiCompany.name = company.getName();
		apiCompany.status = company.getStatus();
		return apiCompany;
	}

	public void updateApiCompany(Long userId, ApiCompany ac, Company c, Language language) {
		CommonApiTools.updateApiBaseEntity(ac, c);
		if (language == null || Language.EN == language) updateApiCompanyTranslatables(userId, ac, c);
		else {
			CompanyTranslation ct = companyQueries.fetchCompanyTranslation(c, language);
			if (ct != null) updateApiCompanyTranslatables(userId, ac, ct);
		}
		ac.logo = CommonApiTools.toApiDocument(c.getLogo(), userId);
		ac.headquarters = CommonApiTools.toApiAddress(c.getHeadquarters());
		ac.manager = c.getManager();
		ac.email = c.getEmail();
		ac.phone = c.getPhone();
		ac.currency = CommonApiTools.toApiCurrencyType(c.getCurrency());
		ac.displayPrefferedWayOfPayment = c.getDisplayPrefferedWayOfPayment();
		ac.purchaseProofDocumentMultipleFarmers = c.getPurchaseProofDocumentMultipleFarmers();
		ac.allowBeycoIntegration = c.getAllowBeycoIntegration();
	}
	
	public ApiCompanyGet toApiCompanyGet(Long userId,
										 Company c,
										 Language language,
										 List<CompanyAction> actions,
										 List<ApiCompanyUser> users,
										 List<ApiValueChain> valueChains) {

		if (c == null) {
			return null;
		}
		
		ApiCompanyGet ac = new ApiCompanyGet();
		updateApiCompany(userId, ac, c, language);
		ac.actions = actions;
		ac.users = users;
		ac.valueChains = valueChains;

		// Map the company roles
		ac.setCompanyRoles(c.getCompanyRoles().stream().map(ProductCompany::getType).distinct().collect(Collectors.toList()));

		// Set the flag if this company supports collectors for deliveries
		ac.setSupportsCollectors(c.getFacilities().stream().anyMatch(f -> BooleanUtils.isTrue(f.getDisplayMayInvolveCollectors())));

		return ac;
	}

	public ApiCompanyName toApiCompanyName(Company c) {

		ApiCompanyName apiCompanyName = new ApiCompanyName();
		apiCompanyName.setId(c.getId());
		apiCompanyName.setName(c.getName());
		apiCompanyName.setAbbreviation(c.getAbbreviation());
		return apiCompanyName;
	}
	
	public ApiCompany toApiCompany(Long userId, Company c, Language language) {
		if (c == null) return null;
		
		ApiCompany ac = new ApiCompany();
		updateApiCompany(userId, ac, c, language);
		return ac;
	}

	public ApiCompany toApiCompany(Company company) {
		if (company == null) return null;

		ApiCompany apiCompany = new ApiCompany();
		apiCompany.setId(company.getId());
		apiCompany.setName(company.getName());

		return apiCompany;
	}

	public void updateLocation(GeoAddress geoAddress, ApiGeoAddress apiGeoAddress) {
		geoAddress.setAddress(apiGeoAddress.getAddress());
		geoAddress.setCell(apiGeoAddress.getCell());
		geoAddress.setCity(apiGeoAddress.getCity());
		geoAddress.setCountry(em.find(Country.class, apiGeoAddress.getCountry().getId()));
		geoAddress.setHondurasDepartment(apiGeoAddress.getHondurasDepartment());
		geoAddress.setHondurasFarm(apiGeoAddress.getHondurasFarm());
		geoAddress.setHondurasMunicipality(apiGeoAddress.getHondurasMunicipality());
		geoAddress.setHondurasVillage(apiGeoAddress.getHondurasVillage());
		geoAddress.setLatitude(apiGeoAddress.getLatitude());
		geoAddress.setLongitude(apiGeoAddress.getLongitude());
		geoAddress.setSector(apiGeoAddress.getSector());
		geoAddress.setState(apiGeoAddress.getState());
		geoAddress.setVillage(apiGeoAddress.getVillage());
		geoAddress.setOtherAddress(apiGeoAddress.getOtherAddress());
		geoAddress.setZip(apiGeoAddress.getZip());
	}

	public void updateCompany(Long userId, Company c, ApiCompany ac, Language language) throws ApiException {
		if (Language.EN == language || language == null) {
			updateCompanyTranslatables(userId, c, ac);
		} else {
			CompanyTranslation ct = companyQueries.fetchCompanyTranslation(c, language);
			if (ct == null) ct = companyQueries.createAndPersistCompanyTranslation(c, language);
			updateCompanyTranslatables(userId, ct, ac);
		}
		c.setLogo(commonEngine.fetchDocument(userId, ac.logo));
		c.setHeadquarters(commonApiTools.toAddress(ac.headquarters));
		c.setManager(ac.manager);
		c.setEmail(ac.email);
		c.setPhone(ac.phone);
		c.setCurrency(ac.getCurrency() == null ? null : currencyTypeService.getCurrencyType(ac.getCurrency().getId()));
		c.setDisplayPrefferedWayOfPayment(ac.displayPrefferedWayOfPayment);
		c.setPurchaseProofDocumentMultipleFarmers(ac.purchaseProofDocumentMultipleFarmers);
		c.setAllowBeycoIntegration(ac.allowBeycoIntegration);
	}

	public void updateCompanyValueChains(ApiCompany apiCompany, Company company) throws ApiException {
		if (apiCompany == null || apiCompany.valueChains == null) {
			return;
		}

		Set<Long> newValueChainsList = apiCompany.getValueChains().stream().map(ApiValueChain::getId).collect(Collectors.toSet());
		Set<Long> remainingOldValueChains = new HashSet<>();
		// updates company value-chains
		// first remove old, that were deleted
		company.getValueChains().forEach(valueChainCompany -> {
			if (!newValueChainsList.contains(valueChainCompany.getValueChain().getId())) {
				em.remove(valueChainCompany);
			} else {
				remainingOldValueChains.add(valueChainCompany.getValueChain().getId());
			}
		});

		// then add all new
		for (ApiValueChain newApiValueChain: apiCompany.getValueChains()) {
			if (!remainingOldValueChains.contains(newApiValueChain.getId())) {
				// add new if not added
				CompanyValueChain companyValueChain = new CompanyValueChain();
				ValueChain valueChain = fetchValueChain(newApiValueChain.getId());

				companyValueChain.setCompany(company);
				companyValueChain.setValueChain(valueChain);

				em.persist(companyValueChain);
			}
		}
	}

	private ValueChain fetchValueChain(Long id) throws ApiException {
		// find value chain
		ValueChain valueChain = Queries.get(em, ValueChain.class, id);
		if (valueChain == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid value chain ID");
		}

		return valueChain;
	}
	
	private void updateCompanyTranslatables(Long userId, CompanyTranslatables c, ApiCompany ac) throws ApiException {
		c.setName(ac.name);
		c.setAbbreviation(ac.abbreviation);
		c.setAbout(ac.about);
		c.setInterview(ac.interview);
		c.setWebPage(ac.webPage);
		if (ac.mediaLinks != null) {
			c.setMediaLinks(ac.mediaLinks);
		}
		
		Company cc = (c instanceof Company) ? (Company) c : null;
		CompanyTranslation cct = (c instanceof CompanyTranslation) ? (CompanyTranslation) c : null;

		if (ac.documents != null) {
			Set<Long> toRemove = new HashSet<>();

			for (CompanyDocument cd : c.getDocuments()) {
				Optional<ApiCompanyDocument> optionalApiCompanyDocument = ac.getDocuments().stream().filter(apiCompanyDocument -> cd.getId().equals(apiCompanyDocument.id)).findFirst();

				// Update if document already exists
				if (optionalApiCompanyDocument.isPresent()) {
					ApiCompanyDocument acd = optionalApiCompanyDocument.get();

					cd.setCompany(cc);
					cd.setCompanyTranslation(cct);
					cd.setType(acd.type);
					cd.setCategory(acd.category);
					cd.setName(acd.name);
					cd.setQuote(acd.quote);
					cd.setDescription(acd.description);
					cd.setLink(acd.link);
					cd.setDocument(commonEngine.fetchDocument(userId, acd.document));
				} else {
					// Mark for removal otherwise
					toRemove.add(cd.getId());
				}
			}

			// Remove marked
			c.getDocuments().removeIf(companyDocument -> toRemove.contains(companyDocument.getId()));

			// Add new
			for (ApiCompanyDocument acd : ac.documents) {
				if (acd.getId() == null) {
					c.getDocuments().add(toCompanyDocument(userId, cc, cct, acd));
				}
			}
		}

		if (ac.certifications != null) {
			c.getCertifications().clear();
			c.getCertifications().addAll(ListTools.mapThrowable(ac.certifications, acc -> toCompanyCertification(userId, cc, cct, acc)));
		}
	}

	private static void updateApiCompanyTranslatables(Long userId, ApiCompany ac, CompanyTranslatables c) {
		ac.name = c.getName();
		ac.abbreviation = c.getAbbreviation();
		ac.about = c.getAbout();
		ac.interview = c.getInterview();
		ac.webPage = c.getWebPage();
		ac.mediaLinks = c.getMediaLinks();
		ac.documents = c.getDocuments().stream().map(cd -> toApiCompanyDocument(userId, cd)).collect(Collectors.toList());
		ac.certifications = c.getCertifications().stream().map(cd -> toApiCertification(userId, cd)).collect(Collectors.toList());
	}

	public void updateCompanyWithUsers(Long userId, Company c, ApiCompanyUpdate ac) throws ApiException {
		updateCompany(userId, c, ac, ac.language);
		if (ac.users != null) {
			Set<Long> apiUserIds = ac.users.stream().map(ApiBaseEntity::getId).collect(Collectors.toSet()); 
			Set<Long> dbUserIds = c.getUsers().stream().map(cu -> cu.getUser().getId()).collect(Collectors.toSet());
			
	        c.getUsers().removeIf(dbItem -> !apiUserIds.contains(dbItem.getUser().getId()));
	        for (ApiBaseEntity be : ac.users) {
	        	if (!dbUserIds.contains(be.getId())) {
	        		CompanyUser cu = new CompanyUser();
	        		cu.setCompany(c);
	        		cu.setUser(userQueries.fetchUser(be.getId()));
	        		c.getUsers().add(cu);
	        	}
	        }
		}
	}
	
	public static ApiCompanyDocument toApiCompanyDocument(Long userId, CompanyDocument cd) {
		if (cd == null) return null;
		
		ApiCompanyDocument acd = new ApiCompanyDocument();
		acd.id = cd.getId();
		acd.type = cd.getType();
		acd.category = cd.getCategory();
		acd.name = cd.getName();
		acd.quote = cd.getQuote();
		acd.description = cd.getDescription();
		acd.link = cd.getLink();
		acd.document = CommonApiTools.toApiDocument(cd.getDocument(), userId);
		return acd;
	}

	public static ApiCertification toApiCertification(Long userId, CompanyCertification cc) {
		if (cc == null) return null;
		
		ApiCertification acc = new ApiCertification();
		acc.type = cc.getType();
		acc.description = cc.getDescription();
		acc.validity = cc.getValidity();
		acc.certificate = CommonApiTools.toApiDocument(cc.getCertificate(), userId);
		return acc;
	}
	
	private CompanyDocument toCompanyDocument(Long userId, Company c, CompanyTranslation ct, ApiCompanyDocument ad) throws ApiException {
		CompanyDocument pd = new CompanyDocument();
		pd.setCompany(c);
		pd.setCompanyTranslation(ct);
		pd.setType(ad.type);
		pd.setCategory(ad.category);
		pd.setName(ad.name);
		pd.setQuote(ad.quote);
		pd.setDescription(ad.description);
		pd.setLink(ad.link);
		pd.setDocument(commonEngine.fetchDocument(userId, ad.document));
		return pd;
	}
	
	private CompanyCertification toCompanyCertification(Long userId, Company c, CompanyTranslation ct, ApiCertification ac) throws ApiException {
		CompanyCertification pd = new CompanyCertification();
		pd.setCompany(c);
		pd.setCompanyTranslation(ct);
		pd.setType(ac.type);
		pd.setDescription(ac.description);
		pd.setValidity(ac.validity);
		pd.setCertificate(commonEngine.fetchDocument(userId, ac.certificate));
		return pd;
	}
	
	public static ApiCompanyUser toApiCompanyUser(CompanyUser cu) {
		ApiCompanyUser acu = new ApiCompanyUser();
		UserApiTools.updateApiUserBase(acu, cu.getUser());
		acu.companyRole = cu.getRole();
		return acu;
	}

	public ApiUserCustomer toApiUserCustomer(UserCustomer userCustomer, Long userId, Language language) {

		if (userCustomer == null) {
			return null;
		}

		ApiUserCustomer apiUserCustomer = new ApiUserCustomer();
		apiUserCustomer.setId(userCustomer.getId());
		apiUserCustomer.setFarmerCompanyInternalId(userCustomer.getFarmerCompanyInternalId());
		apiUserCustomer.setName(userCustomer.getName());
		apiUserCustomer.setSurname(userCustomer.getSurname());
		apiUserCustomer.setType(userCustomer.getType());
		apiUserCustomer.setPhone(userCustomer.getPhone());
		apiUserCustomer.setEmail(userCustomer.getEmail());
		apiUserCustomer.setGender(userCustomer.getGender());
		apiUserCustomer.setHasSmartphone(userCustomer.getHasSmartphone());

		// Company
		apiUserCustomer.setCompanyId(userCustomer.getCompany().getId());

		// Location
		apiUserCustomer.setLocation(toApiUserCustomerLocation(userCustomer.getUserCustomerLocation()));

		// Bank
		apiUserCustomer.setBank(toApiBankInformation(userCustomer.getBank()));

		// Farm
		apiUserCustomer.setFarm(toApiFarmInformation(userCustomer, language));

		// Associations
		apiUserCustomer.setAssociations(toApiUserCustomerAssociationList(userCustomer.getAssociations().stream().toList()));

		// Cooperatives
		apiUserCustomer.setCooperatives(toApiUserCustomerCooperativesList(userCustomer.getCooperatives().stream().toList()));

		// Certifications
		apiUserCustomer.setCertifications(userCustomer.getCertifications().stream().map(ucc -> {

			ApiCertification apiCertification = new ApiCertification();
			apiCertification.setId(ucc.getId());
			apiCertification.setCertificate(CommonApiTools.toApiDocument(ucc.getCertificate(), userId));
			apiCertification.setType(ucc.getType());
			apiCertification.setDescription(ucc.getDescription());
			apiCertification.setValidity(ucc.getValidity());

			return apiCertification;

		}).collect(Collectors.toList()));

		// If user customer is of type FARMER, map Product types and Plots
		if (UserCustomerType.FARMER.equals(apiUserCustomer.getType())) {

			apiUserCustomer.setProductTypes(userCustomer.getProductTypes().stream()
					.map(ucpt -> ProductTypeMapper.toApiProductType(ucpt.getProductType(), language))
					.collect(Collectors.toList()));

			apiUserCustomer.setPlots(
					userCustomer.getPlots().stream().map(plot -> PlotMapper.toApiPlot(plot, language)).collect(Collectors.toList()));
		}

		return apiUserCustomer;
	}

	public ApiBankInformation toApiBankInformation(BankInformation bankInformation) {
		if (bankInformation == null) return null;
		ApiBankInformation apiBankInformation = new ApiBankInformation();
		apiBankInformation.setAccountHolderName(bankInformation.getAccountHolderName());
		apiBankInformation.setAccountNumber(bankInformation.getAccountNumber());
		apiBankInformation.setAdditionalInformation(bankInformation.getAdditionalInformation());
		apiBankInformation.setBankName(bankInformation.getBankName());

		return apiBankInformation;
	}

	public ApiFarmInformation toApiFarmInformation(UserCustomer userCustomer, Language language) {
		if (userCustomer.getFarm() == null) return null;
		ApiFarmInformation apiFarmInformation = new ApiFarmInformation();
		apiFarmInformation.setAreaUnit(userCustomer.getFarm().getAreaUnit());
		apiFarmInformation.setAreaOrganicCertified(userCustomer.getFarm().getAreaOrganicCertified());
		apiFarmInformation.setOrganic(userCustomer.getFarm().getOrganic());
		apiFarmInformation.setStartTransitionToOrganic(userCustomer.getFarm().getStartTransitionToOrganic());
		apiFarmInformation.setTotalCultivatedArea(userCustomer.getFarm().getTotalCultivatedArea());

		if (!userCustomer.getFarmPlantInformationList().isEmpty()) {
			apiFarmInformation.setFarmPlantInformationList(new ArrayList<>());

			userCustomer.getFarmPlantInformationList().forEach(plantInformation -> {
				if (plantInformation != null) {
					ApiFarmPlantInformation apiFarmPlantInformation = new ApiFarmPlantInformation();
					apiFarmPlantInformation.setNumberOfPlants(
							plantInformation.getNumberOfPlants());
					apiFarmPlantInformation.setPlantCultivatedArea(
							plantInformation.getPlantCultivatedArea());
					apiFarmPlantInformation.setProductType(ProductTypeMapper.toApiProductType(
							plantInformation.getProductType(), language));
					apiFarmInformation.getFarmPlantInformationList().add(apiFarmPlantInformation);
				}
			});
		}

		return apiFarmInformation;
	}

	public ApiUserCustomerAssociation toApiUserCustomerAssociation(UserCustomerAssociation userCustomerAssociation) {
		if (userCustomerAssociation == null) return null;
		ApiUserCustomerAssociation apiUserCustomerAssociation = new ApiUserCustomerAssociation();
		apiUserCustomerAssociation.setId(userCustomerAssociation.getId());
		apiUserCustomerAssociation.setCompany(toApiCompany(userCustomerAssociation.getCompany()));
		apiUserCustomerAssociation.setUserCustomer(new ApiUserCustomer());
		apiUserCustomerAssociation.getUserCustomer().setId(userCustomerAssociation.getUserCustomer().getId());

		return apiUserCustomerAssociation;
	}

	public ApiUserCustomerCooperative toApiUserCustomerCooperative(UserCustomerCooperative userCustomerCooperative) {
		if (userCustomerCooperative == null) return null;

		ApiUserCustomerCooperative apiUserCustomerCooperative = new ApiUserCustomerCooperative();
		apiUserCustomerCooperative.setId(userCustomerCooperative.getId());
		apiUserCustomerCooperative.setCompany(toApiCompany(userCustomerCooperative.getCompany()));
		apiUserCustomerCooperative.setUserCustomer(new ApiUserCustomer());
		apiUserCustomerCooperative.getUserCustomer().setId(userCustomerCooperative.getUserCustomer().getId());
		apiUserCustomerCooperative.setUserCustomerType(userCustomerCooperative.getRole());

		return apiUserCustomerCooperative;
	}

	public ApiUserCustomerLocation toApiUserCustomerLocation(UserCustomerLocation userCustomerLocation) {
		if (userCustomerLocation == null) return null;
		ApiUserCustomerLocation apiUserCustomerLocation = new ApiUserCustomerLocation();
		apiUserCustomerLocation.setAddress(toApiAddress(userCustomerLocation.getAddress()));
		apiUserCustomerLocation.setLatitude(userCustomerLocation.getLatitude());
		apiUserCustomerLocation.setLongitude(userCustomerLocation.getLongitude());
		apiUserCustomerLocation.setPubliclyVisible(userCustomerLocation.getPubliclyVisible());

		return apiUserCustomerLocation;
	}

	public ApiAddress toApiAddress(Address address) {
		if(address == null) return null;
		ApiAddress apiAddress = new ApiAddress();
		apiAddress.setAddress(address.getAddress());
		apiAddress.setCell(address.getCell());
		apiAddress.setCity(address.getCity());
		apiAddress.setCountry(CountryMapper.toApiCountry(address.getCountry()));
		apiAddress.setHondurasDepartment(address.getHondurasDepartment());
		apiAddress.setHondurasFarm(address.getHondurasFarm());
		apiAddress.setHondurasMunicipality(address.getHondurasMunicipality());
		apiAddress.setHondurasVillage(address.getHondurasVillage());
		apiAddress.setSector(address.getSector());
		apiAddress.setState(address.getState());
		apiAddress.setVillage(address.getVillage());
		apiAddress.setOtherAddress(address.getOtherAddress());
		apiAddress.setZip(address.getZip());

		return apiAddress;
	}

	public List<ApiUserCustomerAssociation> toApiUserCustomerAssociationList(List<UserCustomerAssociation> userCustomerAssociationList) {
		if (userCustomerAssociationList == null) {
			return null;
		}
		return userCustomerAssociationList.stream().map(this::toApiUserCustomerAssociation).collect(Collectors.toList());
	}

	public List<ApiUserCustomerCooperative> toApiUserCustomerCooperativesList(List<UserCustomerCooperative> userCustomerCooperativeList) {
		if (userCustomerCooperativeList == null) {
			return null;
		}
		return userCustomerCooperativeList.stream().map(this::toApiUserCustomerCooperative).collect(Collectors.toList());
	}
				
}
