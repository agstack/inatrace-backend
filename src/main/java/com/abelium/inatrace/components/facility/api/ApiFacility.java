package com.abelium.inatrace.components.facility.api;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.components.codebook.facility_type.api.ApiFacilityType;
import com.abelium.inatrace.components.codebook.semiproduct.api.ApiSemiProduct;
import com.abelium.inatrace.components.company.api.ApiCompanyBase;

import com.abelium.inatrace.components.product.api.ApiFinalProduct;
import com.abelium.inatrace.components.value_chain.api.ApiValueChain;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public class ApiFacility extends ApiBaseEntity {

	@Schema(description = "Facility name")
	private String name;

	@Schema(description = "Is collection facility")
	private Boolean isCollectionFacility;

	@Schema(description = "Is public facility")
	private Boolean isPublic;

	@Schema(description = "Enable form control 'May involve collectors'")
	private Boolean displayMayInvolveCollectors;

	@Schema(description = "Enable form control 'Organic'")
	private Boolean displayOrganic;

	@Schema(description = "Enable form control 'Price deduction damage'")
	private Boolean displayPriceDeductionDamage;

	@Schema(description = "Enable form control 'Weight deduction damage'")
	private Boolean displayWeightDeductionDamage;

	@Schema(description = "Enable form control 'Tare'")
	private Boolean displayTare;

	@Schema(description = "Enable form control 'Women only'")
	private Boolean displayWomenOnly;

	@Schema(description = "Facility is activated")
	private Boolean isDeactivated;

	@Schema(description = "Enable form control 'Price determined later'")
	private Boolean displayPriceDeterminedLater;

	@Schema(description = "Facility location")
	private ApiFacilityLocation facilityLocation;

	@Schema(description = "Facility company")
	private ApiCompanyBase company;

	@Schema(description = "Facility type")
	private ApiFacilityType facilityType;

	@Schema(description = "List of semi product IDs for this facility. Omit on update to preserve associations; send an empty list to clear them.")
	private List<ApiSemiProduct> facilitySemiProductList;

	@Schema(description = "List of final product IDs for this facility. Omit on update to preserve associations; send an empty list to clear them.")
	private List<ApiFinalProduct> facilityFinalProducts;

	@Schema(description = "List of value chains for this facility. Omit on update to preserve associations; send an empty list to clear them.")
	private List<ApiValueChain> facilityValueChains;

	@Schema(description = "List of facility translations")
	private List<ApiFacilityTranslation> translations;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Boolean getIsCollectionFacility() {
		return isCollectionFacility;
	}

	public void setIsCollectionFacility(Boolean isCollectionFacility) {
		this.isCollectionFacility = isCollectionFacility;
	}

	public Boolean getIsPublic() {
		return isPublic;
	}

	public void setIsPublic(Boolean isPublic) {
		this.isPublic = isPublic;
	}

	public Boolean getDisplayMayInvolveCollectors() {
		return displayMayInvolveCollectors;
	}

	public void setDisplayMayInvolveCollectors(Boolean displayMayInvolveCollectors) {
		this.displayMayInvolveCollectors = displayMayInvolveCollectors;
	}

	public Boolean getDisplayOrganic() {
		return displayOrganic;
	}

	public void setDisplayOrganic(Boolean displayOrganic) {
		this.displayOrganic = displayOrganic;
	}

	public Boolean getDisplayPriceDeductionDamage() {
		return displayPriceDeductionDamage;
	}

	public void setDisplayPriceDeductionDamage(Boolean displayPriceDeductionDamage) {
		this.displayPriceDeductionDamage = displayPriceDeductionDamage;
	}

	public Boolean getDisplayWeightDeductionDamage() {
		return displayWeightDeductionDamage;
	}

	public void setDisplayWeightDeductionDamage(Boolean displayWeightDeductionDamage) {
		this.displayWeightDeductionDamage = displayWeightDeductionDamage;
	}

	public Boolean getDisplayTare() {
		return displayTare;
	}

	public void setDisplayTare(Boolean displayTare) {
		this.displayTare = displayTare;
	}

	public Boolean getDisplayWomenOnly() {
		return displayWomenOnly;
	}

	public void setDisplayWomenOnly(Boolean displayWomenOnly) {
		this.displayWomenOnly = displayWomenOnly;
	}

	public Boolean getDeactivated() {
		return isDeactivated;
	}

	public void setDeactivated(Boolean deactivated) {
		isDeactivated = deactivated;
	}

	public Boolean getDisplayPriceDeterminedLater() {
		return displayPriceDeterminedLater;
	}

	public void setDisplayPriceDeterminedLater(Boolean displayPriceDeterminedLater) {
		this.displayPriceDeterminedLater = displayPriceDeterminedLater;
	}

	public ApiFacilityLocation getFacilityLocation() {
		return facilityLocation;
	}

	public void setFacilityLocation(ApiFacilityLocation facilityLocation) {
		this.facilityLocation = facilityLocation;
	}

	public ApiCompanyBase getCompany() {
		return company;
	}

	public void setCompany(ApiCompanyBase company) {
		this.company = company;
	}

	public ApiFacilityType getFacilityType() {
		return facilityType;
	}

	public void setFacilityType(ApiFacilityType facilityType) {
		this.facilityType = facilityType;
	}

	public List<ApiSemiProduct> getFacilitySemiProductList() {
		return facilitySemiProductList;
	}

	public void setFacilitySemiProductList(List<ApiSemiProduct> facilitySemiProductList) {
		this.facilitySemiProductList = facilitySemiProductList;
	}

	public List<ApiFinalProduct> getFacilityFinalProducts() {
		return facilityFinalProducts;
	}

	public void setFacilityFinalProducts(List<ApiFinalProduct> facilityFinalProducts) {
		this.facilityFinalProducts = facilityFinalProducts;
	}

	public List<ApiValueChain> getFacilityValueChains() {
		return facilityValueChains;
	}

	public void setFacilityValueChains(List<ApiValueChain> facilityValueChains) {
		this.facilityValueChains = facilityValueChains;
	}

	public List<ApiFacilityTranslation> getTranslations() {
		return translations;
	}

	public void setTranslations(List<ApiFacilityTranslation> translations) {
		this.translations = translations;
	}

	public ApiFacility() {
		super();
	}

	public ApiFacility(String name, Boolean isCollectionFacility, Boolean isPublic, Boolean isDeactivated,
	                   Boolean displayPriceDeterminedLater, ApiFacilityLocation facilityLocation,
	                   ApiCompanyBase company, ApiFacilityType facilityType,
	                   List<ApiSemiProduct> facilitySemiProductList, List<ApiValueChain> facilityValueChains,
	                   List<ApiFacilityTranslation> facilityTranslationList) {
		super();
		this.name = name;
		this.isCollectionFacility = isCollectionFacility;
		this.isPublic = isPublic;
		this.isDeactivated = isDeactivated;
		this.displayPriceDeterminedLater = displayPriceDeterminedLater;
		this.facilityLocation = facilityLocation;
		this.company = company;
		this.facilityType = facilityType;
		this.facilitySemiProductList = facilitySemiProductList;
		this.facilityValueChains = facilityValueChains;
		this.translations = facilityTranslationList;
	}

}
