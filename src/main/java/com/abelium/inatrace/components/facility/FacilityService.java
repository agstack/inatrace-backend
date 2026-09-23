package com.abelium.inatrace.components.facility;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.semiproduct.SemiProductService;
import com.abelium.inatrace.components.codebook.semiproduct.api.ApiSemiProduct;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.company.CompanyQueries;
import com.abelium.inatrace.components.facility.api.ApiFacility;
import com.abelium.inatrace.components.product.FinalProductService;
import com.abelium.inatrace.components.product.api.ApiFinalProduct;
import com.abelium.inatrace.components.value_chain.ValueChainService;
import com.abelium.inatrace.components.value_chain.api.ApiValueChain;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.facility.*;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.value_chain.FacilityValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProductCompanyType;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for facility entity.
 *
 * @author Rene Flores, Sunesis d.o.o.
 */
@Lazy
@Service
public class FacilityService extends BaseService {

	private final SemiProductService semiProductService;

	private final CompanyQueries companyQueries;

	private final FinalProductService finalProductService;

	private final ValueChainService valueChainService;

	@Autowired
	public FacilityService(SemiProductService semiProductService, CompanyQueries companyQueries, FinalProductService finalProductService, ValueChainService valueChainService) {
		this.semiProductService = semiProductService;
		this.companyQueries = companyQueries;
		this.finalProductService = finalProductService;
		this.valueChainService = valueChainService;
	}

	public ApiFacility getFacility(Long id, CustomUserDetails user, Language language) throws ApiException {

		Facility facility = fetchFacility(id);

		// If facility is public (facility that sells semi-products or final products) check that user is enrolled in one of the connected companies
		if (facility.getIsPublic()) {

			PermissionsUtil.checkUserIfConnectedWithProducts(companyQueries.fetchCompanyProducts(facility.getCompany().getId()), user);

		} else {

			// Check if req. user is enrolled in facility's company
			PermissionsUtil.checkUserIfCompanyEnrolled(facility.getCompany().getUsers().stream().toList(), user);
		}

		return FacilityMapper.toApiFacility(facility, language);
	}

	public ApiFacility getFacilityDetail(Long id, CustomUserDetails user, Language language) throws ApiException {

		Facility facility = fetchFacility(id);

		// Check if req. user is enrolled in facility's company
		PermissionsUtil.checkUserIfCompanyEnrolled(facility.getCompany().getUsers().stream().toList(), user);

		return FacilityMapper.toApiFacilityDetail(facility, language);
	}

	@Transactional
	public ApiBaseEntity createOrUpdateFacility(ApiFacility apiFacility, CustomUserDetails user) throws ApiException {

		Facility entity;
		FacilityLocation facilityLocation;
		Address address;
		Company company;

		if (apiFacility.getId() != null) {
			entity = fetchFacility(apiFacility.getId());
			facilityLocation = entity.getFacilityLocation();
			address = facilityLocation == null ? null : facilityLocation.getAddress();
			company = entity.getCompany();
		} else {
			entity = new Facility();
			facilityLocation = new FacilityLocation();
			address = new Address();
			company = companyQueries.fetchCompany(apiFacility.getCompany().getId());
		}

		// Create or update can be done only by company admin or system admin
		PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(company.getUsers().stream().toList(), user);
		if (facilityLocation == null) {
			facilityLocation = new FacilityLocation();
		}
		if (address == null) {
			address = new Address();
		}

		Long previousLocationId = facilityLocation.getId();
		if (entity.getId() != null && previousLocationId != null) {
			Long references = em.createQuery(
						"SELECT COUNT(f) FROM Facility f WHERE f.facilityLocation.id = :locationId", Long.class)
					.setParameter("locationId", previousLocationId)
					.getSingleResult();
			if (references > 1) {
				// Editing one facility must not change the other facilities using this location.
				facilityLocation = new FacilityLocation();
				address = new Address();
			}
		}

		entity.setIsCollectionFacility(apiFacility.getIsCollectionFacility());
		entity.setIsPublic(apiFacility.getIsPublic());
		entity.setDisplayMayInvolveCollectors(apiFacility.getDisplayMayInvolveCollectors() != null ? apiFacility.getDisplayMayInvolveCollectors() : Boolean.FALSE);
		entity.setDisplayOrganic(apiFacility.getDisplayOrganic() != null ? apiFacility.getDisplayOrganic() : Boolean.FALSE);
		entity.setDisplayPriceDeductionDamage(apiFacility.getDisplayPriceDeductionDamage() != null ? apiFacility.getDisplayPriceDeductionDamage() : Boolean.FALSE);
		entity.setDisplayWeightDeductionDamage(apiFacility.getDisplayWeightDeductionDamage() != null ? apiFacility.getDisplayWeightDeductionDamage() : Boolean.FALSE);
		entity.setDisplayTare(apiFacility.getDisplayTare() != null ? apiFacility.getDisplayTare() : Boolean.FALSE);
		entity.setDisplayWomenOnly(apiFacility.getDisplayWomenOnly() != null ? apiFacility.getDisplayWomenOnly() : Boolean.FALSE);
		entity.setIsDeactivated(BooleanUtils.isTrue(apiFacility.getDeactivated()));
		entity.setDisplayPriceDeterminedLater(BooleanUtils.isTrue(apiFacility.getDisplayPriceDeterminedLater()));

		facilityLocation.setLatitude(apiFacility.getFacilityLocation().getLatitude());
		facilityLocation.setLongitude(apiFacility.getFacilityLocation().getLongitude());
		facilityLocation.setNumberOfFarmers(apiFacility.getFacilityLocation().getNumberOfFarmers());
		facilityLocation.setPinName(apiFacility.getFacilityLocation().getPinName());
		facilityLocation.setPubliclyVisible(apiFacility.getFacilityLocation().getPubliclyVisible());

		address.setAddress(apiFacility.getFacilityLocation().getAddress().getAddress());
		address.setCity(apiFacility.getFacilityLocation().getAddress().getCity());
		address.setState(apiFacility.getFacilityLocation().getAddress().getState());
		address.setZip(apiFacility.getFacilityLocation().getAddress().getZip());
		address.setCell(apiFacility.getFacilityLocation().getAddress().getCell());
		address.setSector(apiFacility.getFacilityLocation().getAddress().getSector());
		address.setVillage(apiFacility.getFacilityLocation().getAddress().getVillage());

		Country country = Queries.get(em, Country.class, apiFacility.getFacilityLocation().getAddress().getCountry().getId());
		address.setCountry(country);
		facilityLocation.setAddress(address);

		FacilityType facilityType = Queries.get(em, FacilityType.class, apiFacility.getFacilityType().getId());
		entity.setFacilityType(facilityType);
		
		entity.setFacilityLocation(facilityLocation);

		entity.setCompany(company);

		if (facilityLocation.getId() == null) {
			em.persist(facilityLocation);
		}
		if (entity.getId() == null) {
			em.persist(entity);
		}

		// Update the Facility semi-products
		updateFacilitySemiProducts(apiFacility, entity);

		// Update the Facility final products
		updateFacilityFinalProducts(apiFacility, entity);

		// Update the Facility value chains
		updateFacilityValueChains(apiFacility, entity);

		// Remove translations not in request
		entity.getFacilityTranslations().removeIf(facilityTranslation -> apiFacility
				.getTranslations()
				.stream()
				.noneMatch(apiFacilityTranslation -> facilityTranslation
						.getLanguage()
						.equals(apiFacilityTranslation.getLanguage())));

		// Add or update existing
		apiFacility.getTranslations().forEach(apiFacilityTranslation -> {
			FacilityTranslation translation = entity.getFacilityTranslations().stream().filter(facilityTranslation -> facilityTranslation.getLanguage().equals(apiFacilityTranslation.getLanguage())).findFirst().orElse(new FacilityTranslation());
			translation.setName(apiFacilityTranslation.getName());
			translation.setLanguage(apiFacilityTranslation.getLanguage());
			translation.setFacility(entity);
			entity.getFacilityTranslations().add(translation);
		});

		if (previousLocationId != null && !Objects.equals(previousLocationId, facilityLocation.getId())) {
			em.flush();
			removeUnreferencedFacilityLocation(previousLocationId);
		}

		return new ApiBaseEntity(entity);
	}

	@Transactional
	public void setFacilityDeactivatedStatus(Long id, Boolean deactivated, CustomUserDetails user) throws ApiException {

		Facility facility = em.find(Facility.class, id);

		// Deactivate/Activate can be done only by company admin or system admin
		PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(facility.getCompany().getUsers().stream().toList(), user);

		facility.setIsDeactivated(deactivated);
	}

	@Transactional
	public void deleteFacility(Long id, CustomUserDetails user) throws ApiException {

		Facility facility = fetchFacility(id);

		// Remove can be done only by company admin or system admin
		PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(facility.getCompany().getUsers().stream().toList(), user);

		Long locationId = facility.getFacilityLocation() == null
				? null : facility.getFacilityLocation().getId();
		em.remove(facility);
		em.flush();
		if (locationId != null) {
			removeUnreferencedFacilityLocation(locationId);
		}
	}

	private void removeUnreferencedFacilityLocation(Long locationId) {
		Long references = em.createQuery(
					"SELECT COUNT(f) FROM Facility f WHERE f.facilityLocation.id = :locationId", Long.class)
				.setParameter("locationId", locationId)
				.getSingleResult();
		if (references == 0) {
			FacilityLocation location = em.find(FacilityLocation.class, locationId);
			if (location != null) {
				em.remove(location);
			}
		}
	}

	public Facility fetchFacility(Long id) throws ApiException {

		Facility facility = Queries.get(em, Facility.class, id);
		if (facility == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid facility ID");
		}

		return facility;
	}

	/**
	 * List all (active and deactivated) facilities by provided company ID.
	 */
	public ApiPaginatedList<ApiFacility> listAllFacilitiesByCompany(Long companyId,
	                                                                ApiPaginatedRequest request,
	                                                                Language language,
																	CustomUserDetails user) throws ApiException {

		Company company = companyQueries.fetchCompany(companyId);
		PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);

		return PaginationTools.createPaginatedResponse(em, request, () ->
						listFacilitiesByCompanyQuery(companyId, null, null, language, null),
				facility -> FacilityMapper.toApiFacility(facility, language));
	}

	/**
	 * List only currently active facilities by provided company ID.
	 */
	public ApiPaginatedList<ApiFacility> listFacilitiesByCompany(Long companyId,
	                                                             Long semiProductId,
	                                                             Long finalProductId,
	                                                             ApiPaginatedRequest request,
																 CustomUserDetails user,
	                                                             Language language) throws ApiException {

		Company company = companyQueries.fetchCompany(companyId);
		PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);

		return PaginationTools.createPaginatedResponse(em, request, () ->
						listFacilitiesByCompanyQuery(companyId, semiProductId, finalProductId, language, true),
				facility -> FacilityMapper.toApiFacility(facility, language));
	}

	private Facility listFacilitiesByCompanyQuery(Long companyId, Long semiProductId, Long finalProductId, Language language, Boolean activated) {

		Facility facilityProxy = Torpedo.from(Facility.class);
		FacilityTranslation ft = Torpedo.innerJoin(facilityProxy.getFacilityTranslations());

		OnGoingLogicalCondition condition = Torpedo.condition();

		condition = condition.and(ft.getLanguage()).eq(language);
		condition = condition.and(facilityProxy.getCompany().getId()).eq(companyId);

		if (BooleanUtils.isTrue(activated)) {
			condition = condition.and(facilityProxy.getIsDeactivated()).neq(Boolean.TRUE);
		}

		if (semiProductId != null) {
			FacilitySemiProduct fsp = Torpedo.leftJoin(facilityProxy.getFacilitySemiProducts());
			condition = condition.and(fsp.getSemiProduct().getId()).eq(semiProductId);
		} else if (finalProductId != null) {
			FacilityFinalProduct ffp = Torpedo.leftJoin(facilityProxy.getFacilityFinalProducts());
			condition = condition.and(ffp.getFinalProduct().getId()).eq(finalProductId);
		}

		Torpedo.where(condition);

		return facilityProxy;
	}
	
	public ApiPaginatedList<ApiFacility> listCollectingFacilitiesByCompany(Long companyId, ApiPaginatedRequest request, CustomUserDetails user, Language language) throws ApiException {

		// Check request user if enrolled in company
		Company company = companyQueries.fetchCompany(companyId);
		PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);

		TypedQuery<Facility> collectingFacilitiesQuery = em.createNamedQuery("Facility.listCollectingFacilitiesByCompany",
						Facility.class)
				.setParameter("companyId", companyId)
				.setParameter("language", language)
				.setFirstResult(request.getOffset())
				.setMaxResults(request.getLimit());

		List<Facility> facilities = collectingFacilitiesQuery.getResultList();

		Long count = em.createNamedQuery("Facility.countCollectingFacilitiesByCompany", Long.class)
				.setParameter("companyId", companyId).getSingleResult();

		return new ApiPaginatedList<>(
				facilities.stream().map(facility -> FacilityMapper.toApiFacility(facility, language)).collect(Collectors.toList()), count);
	}

	// Get the list of selling (public) facilities that the company with the provided ID can see.
	// The candidates of companies' facilities are retrieved through the connected products (value chains).
	public ApiPaginatedList<ApiFacility> listAvailableSellingFacilitiesForCompany(Long companyId,
	                                                                              Long semiProductId,
	                                                                              Long finalProductId,
	                                                                              ApiPaginatedRequest request,
																				  CustomUserDetails user,
	                                                                              Language language) throws ApiException {

		// Check that the request user is enrolled in the company for which we are fetching connected selling facilities
		Company company = companyQueries.fetchCompany(companyId);
		PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);

		// First get all the products where the company is participating in role BUYER OR EXPORTER
		TypedQuery<ProductCompany> productsAssociationsQuery = em.createNamedQuery(
				"ProductCompany.getCompanyProductsAsBuyerOrExporter", ProductCompany.class)
				.setParameter("companyId", companyId);
		List<ProductCompany> productsAssociations = productsAssociationsQuery.getResultList();

		if (productsAssociations.isEmpty()) {
			return new ApiPaginatedList<>(Collections.emptyList(), 0);
		}

		// First handle the products where the company is BUYER (get the exporters below)
		List<Long> productsIdsAsBuyer = productsAssociations.stream()
				.filter(pa -> pa.getType() == ProductCompanyType.BUYER).map(pa -> pa.getProduct().getId())
				.collect(Collectors.toList());
		List<Company> exporterCompaniesIds = em.createNamedQuery("ProductCompany.getProductCompaniesByAssociationType", Company.class)
				.setParameter("companyId", companyId)
				.setParameter("productIds", productsIdsAsBuyer)
				.setParameter("associationType", ProductCompanyType.EXPORTER)
				.getResultList();

		// Handle the products where the company is EXPORTER (get the producers below)
		List<Long> productsIdsAsExporter = productsAssociations.stream()
				.filter(pa -> pa.getType() == ProductCompanyType.EXPORTER).map(pa -> pa.getProduct().getId())
				.collect(Collectors.toList());
		List<Company> producerCompaniesIds = em.createNamedQuery("ProductCompany.getProductCompaniesByAssociationType", Company.class)
				.setParameter("companyId", companyId)
				.setParameter("productIds", productsIdsAsExporter)
				.setParameter("associationType", ProductCompanyType.PRODUCER)
				.getResultList();

		// Initialize the set which will hold the IDs of candidate companies to be used in facilities query
		Set<Company> candidateCompaniesIds = new HashSet<>(exporterCompaniesIds);
		candidateCompaniesIds.addAll(producerCompaniesIds);

		if (candidateCompaniesIds.isEmpty()) {
			return new ApiPaginatedList<>(Collections.emptyList(), 0);
		}

		return PaginationTools.createPaginatedResponse(em, request, () ->
				availableSellingFacilitiesQuery(candidateCompaniesIds, semiProductId, finalProductId, language),
				facility -> FacilityMapper.toApiFacility(facility, language));
	}

	private Facility availableSellingFacilitiesQuery(Set<Company> companiesIds, Long semiProductId, Long finalProductId, Language language) {

		Facility facilityProxy = Torpedo.from(Facility.class);
		FacilityTranslation ft = Torpedo.innerJoin(facilityProxy.getFacilityTranslations());

		OnGoingLogicalCondition condition = Torpedo.condition();

		condition = condition.and(ft.getLanguage()).eq(language);
		condition = condition.and(facilityProxy.getCompany()).in(companiesIds);
		condition = condition.and(facilityProxy.getIsPublic()).eq(true);
		condition = condition.and(facilityProxy.getIsDeactivated()).neq(Boolean.TRUE);

		if (semiProductId != null) {
			FacilitySemiProduct fsp = Torpedo.leftJoin(facilityProxy.getFacilitySemiProducts());
			condition = condition.and(fsp.getSemiProduct().getId()).eq(semiProductId);
		} else if (finalProductId != null) {
			FacilityFinalProduct ffp = Torpedo.leftJoin(facilityProxy.getFacilityFinalProducts());
			condition = condition.and(ffp.getFinalProduct().getId()).eq(finalProductId);
		}

		Torpedo.where(condition);

		return facilityProxy;
	}

	private void updateFacilitySemiProducts(ApiFacility apiFacility, Facility entity) throws ApiException {

		entity.getFacilitySemiProducts().clear();

		for (ApiSemiProduct apiSemiProduct : apiFacility.getFacilitySemiProductList()) {
			FacilitySemiProduct facilitySemiProduct = new FacilitySemiProduct();
			SemiProduct semiProduct = semiProductService.fetchSemiProduct(apiSemiProduct.getId());
			facilitySemiProduct.setFacility(entity);
			facilitySemiProduct.setSemiProduct(semiProduct);
			entity.getFacilitySemiProducts().add(facilitySemiProduct);
		}
	}

	private void updateFacilityFinalProducts(ApiFacility apiFacility, Facility entity) throws ApiException {

		entity.getFacilityFinalProducts().clear();

		for (ApiFinalProduct apiFinalProduct : apiFacility.getFacilityFinalProducts()) {
			FacilityFinalProduct facilityFinalProduct = new FacilityFinalProduct();
			FinalProduct finalProduct = finalProductService.fetchFinalProduct(apiFinalProduct.getId());
			facilityFinalProduct.setFacility(entity);
			facilityFinalProduct.setFinalProduct(finalProduct);
			entity.getFacilityFinalProducts().add(facilityFinalProduct);
		}
	}

	private void updateFacilityValueChains(ApiFacility apiFacility, Facility entity) throws ApiException {

		entity.getFacilityValueChains().clear();

		for (ApiValueChain apiValueChain : apiFacility.getFacilityValueChains()) {
			FacilityValueChain facilityValueChain = new FacilityValueChain();
			ValueChain valueChain = valueChainService.fetchValueChain(apiValueChain.getId());
			facilityValueChain.setFacility(entity);
			facilityValueChain.setValueChain(valueChain);
			entity.getFacilityValueChains().add(facilityValueChain);
		}
	}

}
