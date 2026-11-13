package com.abelium.inatrace.components.codebook.semiproduct.api;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.components.codebook.measure_unit_type.api.ApiMeasureUnitType;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * SemiProduct API model object.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Validated
public class ApiSemiProduct extends ApiBaseEntity {

	private String name;

	private String description;

	private ApiMeasureUnitType measurementUnitType;

	private Boolean isSKU;

	private Boolean isBuyable;

	private Boolean isSKUEndCustomer;

	private List<ApiSemiProductTranslation> translations;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public ApiMeasureUnitType getMeasurementUnitType() {
		return measurementUnitType;
	}

	public void setMeasurementUnitType(ApiMeasureUnitType measurementUnitType) {
		this.measurementUnitType = measurementUnitType;
	}

	@JsonProperty("sku")
	public Boolean getSKU() {
		return isSKU;
	}

	@JsonProperty("sku")
	public void setSKU(Boolean SKU) {
		isSKU = SKU;
	}

	public Boolean getBuyable() {
		return isBuyable;
	}

	public void setBuyable(Boolean buyable) {
		isBuyable = buyable;
	}

	@JsonProperty("skuendCustomer")
	public Boolean getSKUEndCustomer() {
		return isSKUEndCustomer;
	}

	@JsonProperty("skuendCustomer")
	public void setSKUEndCustomer(Boolean SKUEndCustomer) {
		isSKUEndCustomer = SKUEndCustomer;
	}

	public List<ApiSemiProductTranslation> getTranslations() {
		return translations;
	}

	public void setTranslations(List<ApiSemiProductTranslation> translations) {
		this.translations = translations;
	}
}
