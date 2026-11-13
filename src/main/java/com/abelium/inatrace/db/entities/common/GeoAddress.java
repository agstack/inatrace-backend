package com.abelium.inatrace.db.entities.common;

import com.abelium.inatrace.api.types.Lengths;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.ManyToOne;

/**
 * Embeddable address with coordinates.
 *
 * <p>Hibernate 6 no longer supports the same Java class being mapped with
 * different component property combinations. {@code GeoAddress} therefore
 * keeps its own copy of the address attributes instead of extending
 * {@link Address}, which remains the plain address embeddable used by
 * companies and locations.</p>
 */
@Embeddable
public class GeoAddress {

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(length = Lengths.ADDRESS)
    private String address;

    @Column(length = Lengths.CITY)
    private String city;

    @Column(length = Lengths.STATE)
    private String state;

    @Column(length = Lengths.ZIPCODE)
    private String zip;

    @Column(length = Lengths.CELL)
    private String cell;

    @Column(length = Lengths.SECTOR)
    private String sector;

    @Column(length = Lengths.VILLAGE)
    private String village;

    @Column(length = Lengths.OTHER_ADDRESS)
    private String otherAddress;

    @Column(length = Lengths.DEFAULT)
    private String hondurasFarm;

    @Column(length = Lengths.DEFAULT)
    private String hondurasVillage;

    @Column(length = Lengths.DEFAULT)
    private String hondurasMunicipality;

    @Column(length = Lengths.DEFAULT)
    private String hondurasDepartment;

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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
