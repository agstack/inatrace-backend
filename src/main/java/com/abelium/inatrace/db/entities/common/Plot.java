package com.abelium.inatrace.db.entities.common;

import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import jakarta.persistence.*;

import java.util.*;

/**
 * Entity representing farmer plot. Used only for user customer of type FARMER.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Entity
public class Plot extends BaseEntity {

	@Column
	private String plotName;

	@ManyToOne
	private ProductType crop;

	@Column
	private Integer numberOfPlants;

	@Column
	private String unit;

	@Column
	private Double size;

	@Column
	private String geoId;

	@Column
	private Date organicStartOfTransition;

	@Column
	private Date lastUpdated;

	@OneToMany(mappedBy = "plot",
	           cascade = CascadeType.ALL,
	           orphanRemoval = true,
	           fetch = FetchType.LAZY)
	@OrderBy("id ASC")
	private List<PlotCoordinate> coordinates;

	@ManyToOne
	private UserCustomer farmer;

	public String getPlotName() {
		return plotName;
	}

	public void setPlotName(String plotName) {
		this.plotName = plotName;
	}

	public ProductType getCrop() {
		return crop;
	}

	public void setCrop(ProductType crop) {
		this.crop = crop;
	}

	public Integer getNumberOfPlants() {
		return numberOfPlants;
	}

	public void setNumberOfPlants(Integer numberOfPlants) {
		this.numberOfPlants = numberOfPlants;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public Double getSize() {
		return size;
	}

	public void setSize(Double size) {
		this.size = size;
	}

	public String getGeoId() {
		return geoId;
	}

	public void setGeoId(String geoId) {
		this.geoId = geoId;
	}

	public Date getOrganicStartOfTransition() {
		return organicStartOfTransition;
	}

	public void setOrganicStartOfTransition(Date organicStartOfTransition) {
		this.organicStartOfTransition = organicStartOfTransition;
	}

	public Date getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(Date lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	public List<PlotCoordinate> getCoordinates() {
		if (coordinates == null) {
			coordinates = new ArrayList<>();
		}
		return coordinates;
	}

	public void setCoordinates(List<PlotCoordinate> coordinates) {
		this.coordinates = coordinates;
	}

	public UserCustomer getFarmer() {
		return farmer;
	}

	public void setFarmer(UserCustomer farmer) {
		this.farmer = farmer;
	}

}
