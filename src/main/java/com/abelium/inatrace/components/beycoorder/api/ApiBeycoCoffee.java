package com.abelium.inatrace.components.beycoorder.api;

import com.abelium.inatrace.tools.converters.InstantConverter;
import com.abelium.inatrace.types.*;
import tools.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class ApiBeycoCoffee {

    @Schema(description = "Internal LOT number or name of coffee")
    @Size(min = 1)
    private String name;

    @Schema(description = "Region of coffee beans")
    private String region;

    @Schema(description = "Country of coffee beans")
    @Size(min = 1)
    private String country;

    @Schema(description = "Harvest date")
    @JsonSerialize(using = InstantConverter.Serializer.class)
    private Instant harvestAt;

    @Schema(description = "Coffee species")
    private BeycoSpeciesType species;

    @Schema(description = "Coffee process")
    private BeycoProcessType process;

    @Schema(description = "Minimal screen size")
    @Min(8)
    @Max(20)
    private Integer minScreenSize;

    @Schema(description = "Maximum screen size")
    @Min(8)
    @Max(20)
    private Integer maxScreenSize;

    @Schema(description = "Cupping score")
    @Min(8)
    @Max(20)
    private Integer cuppingScore;

    @Schema(description = "Varieties of coffee beans")
    private List<ApiBeycoCoffeeVariety> varieties;

    @Schema(description = "Quality of coffee beans")
    private List<ApiBeycoCoffeeQuality> qualitySegments;

    @Schema(description = "Grades of coffee beans")
    private List<ApiBeycoCoffeeGrade> grades;

    @Schema(description = "Additional grade, if selected 'Other' in grades")
    private String additionalQualityDescriptors;

    @Schema(description = "Is bulk")
    private Boolean isBulk;

    @Schema(description = "Certificates of coffee beans")
    private List<ApiBeycoCoffeeCertificate> certificates;

    @Min(0)
    @Max(999999999999999999L)
    @Schema(description = "Quantity of beans")
    private BigDecimal quantity;

    @Schema(description = "Unit of order")
    private BeycoCoffeeUnit unit;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Instant getHarvestAt() {
        return harvestAt;
    }

    public void setHarvestAt(Instant harvestAt) {
        this.harvestAt = harvestAt;
    }

    public BeycoSpeciesType getSpecies() {
        return species;
    }

    public void setSpecies(BeycoSpeciesType species) {
        this.species = species;
    }

    public BeycoProcessType getProcess() {
        return process;
    }

    public void setProcess(BeycoProcessType process) {
        this.process = process;
    }

    public Integer getMinScreenSize() {
        return minScreenSize;
    }

    public void setMinScreenSize(Integer minScreenSize) {
        this.minScreenSize = minScreenSize;
    }

    public Integer getMaxScreenSize() {
        return maxScreenSize;
    }

    public void setMaxScreenSize(Integer maxScreenSize) {
        this.maxScreenSize = maxScreenSize;
    }

    public Integer getCuppingScore() {
        return cuppingScore;
    }

    public void setCuppingScore(Integer cuppingScore) {
        this.cuppingScore = cuppingScore;
    }

    public List<ApiBeycoCoffeeVariety> getVarieties() {
        return varieties;
    }

    public void setVarieties(List<ApiBeycoCoffeeVariety> varieties) {
        this.varieties = varieties;
    }

    public List<ApiBeycoCoffeeQuality> getQualitySegments() {
        return qualitySegments;
    }

    public void setQualitySegments(List<ApiBeycoCoffeeQuality> qualitySegments) {
        this.qualitySegments = qualitySegments;
    }

    public List<ApiBeycoCoffeeGrade> getGrades() {
        return grades;
    }

    public void setGrades(List<ApiBeycoCoffeeGrade> grades) {
        this.grades = grades;
    }

    public List<ApiBeycoCoffeeCertificate> getCertificates() {
        return certificates;
    }

    public void setCertificates(List<ApiBeycoCoffeeCertificate> certificates) {
        this.certificates = certificates;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Boolean getIsBulk() {
        return isBulk;
    }

    public void setIsBulk(Boolean isBulk) {
        this.isBulk = isBulk;
    }

    public BeycoCoffeeUnit getUnit() {
        return unit;
    }

    public void setUnit(BeycoCoffeeUnit unit) {
        this.unit = unit;
    }

    public String getAdditionalQualityDescriptors() {
        return additionalQualityDescriptors;
    }

    public void setAdditionalQualityDescriptors(String additionalQualityDescriptors) {
        this.additionalQualityDescriptors = additionalQualityDescriptors;
    }
}
