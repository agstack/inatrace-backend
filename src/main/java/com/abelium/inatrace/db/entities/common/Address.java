package com.abelium.inatrace.db.entities.common;

import com.abelium.inatrace.api.types.Lengths;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.ManyToOne;

@Embeddable
public class Address {
	
	/**
	 * address
	 */
	@Column(length = Lengths.ADDRESS)
	private String address;
	
	/**
	 * city
	 */
	@Column(length = Lengths.CITY)
	private String city;

	/**
	 * state / province / region
	 */
	@Column(length = Lengths.STATE)
	private String state;

	/**
	 * ZIP / postal code / p.p. box
	 */
	@Column(length = Lengths.ZIPCODE)
	private String zip;

	/**
	 * Village cell
	 */
	@Column(length = Lengths.CELL)
	private String cell;

	/**
	 * Village sector
	 */
	@Column(length = Lengths.SECTOR)
	private String sector;

	/**
	 * Village name
	 */
	@Column(length = Lengths.VILLAGE)
	private String village;

	/**
	 * Additional/other address field; If provided, other address fields are not mandatory.
	 */
	@Column(length = Lengths.OTHER_ADDRESS)
	private String otherAddress;

	/**
	 * Honduras farm name
	 */
	@Column(length = Lengths.DEFAULT)
	private String hondurasFarm;

	/**
	 * Honduras village name
	 */
	@Column(length = Lengths.DEFAULT)
	private String hondurasVillage;

	/**
	 * Honduras municipality name
	 */
	@Column(length = Lengths.DEFAULT)
	private String hondurasMunicipality;

	/**
	 * Honduras department name
	 */
	@Column(length = Lengths.DEFAULT)
	private String hondurasDepartment;
	
	/**
	 * country
	 */
	@ManyToOne
	private Country country;
	
	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getZip() {
		return zip;
	}

	public void setZip(String zip) {
		this.zip = zip;
	}

	public String getCell() {
		return cell;
	}

	public void setCell(String cell) {
		this.cell = cell;
	}

	public String getSector() {
		return sector;
	}

	public void setSector(String sector) {
		this.sector = sector;
	}

	public String getVillage() {
		return village;
	}

	public void setVillage(String village) {
		this.village = village;
	}

	public String getOtherAddress() {
		return otherAddress;
	}

	public void setOtherAddress(String otherAddress) {
		this.otherAddress = otherAddress;
	}

	public Country getCountry() {
		return country;
	}

	public void setCountry(Country country) {
		this.country = country;
	}

	public String getHondurasFarm() {
		return hondurasFarm;
	}

	public void setHondurasFarm(String hondurasFarm) {
		this.hondurasFarm = hondurasFarm;
	}

	public String getHondurasVillage() {
		return hondurasVillage;
	}

	public void setHondurasVillage(String hondurasVillage) {
		this.hondurasVillage = hondurasVillage;
	}

	public String getHondurasMunicipality() {
		return hondurasMunicipality;
	}

	public void setHondurasMunicipality(String hondurasMunicipality) {
		this.hondurasMunicipality = hondurasMunicipality;
	}

	public String getHondurasDepartment() {
		return hondurasDepartment;
	}

	public void setHondurasDepartment(String hondurasDepartment) {
		this.hondurasDepartment = hondurasDepartment;
	}
}
