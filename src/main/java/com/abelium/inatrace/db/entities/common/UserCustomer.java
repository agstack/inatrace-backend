package com.abelium.inatrace.db.entities.common;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.types.Gender;
import com.abelium.inatrace.types.UserCustomerType;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@NamedQueries({
	@NamedQuery(name = "UserCustomer.getUserCustomerByCompanyIdAndType",
			    query = "SELECT u FROM UserCustomer u WHERE u.company.id = :companyId AND u.type = :type"),
	@NamedQuery(name = "UserCustomer.getUserCustomerByNameSurnameAndCity",
			    query = "SELECT u FROM UserCustomer u WHERE u.company.id = :companyId AND u.name = :name AND u.surname = :surname AND u.userCustomerLocation.address.city = :city"),
	@NamedQuery(name = "UserCustomer.getUserCustomerByCompanyAndInternalId",
			    query = "SELECT u FROM UserCustomer u WHERE u.company.id = :companyId AND u.farmerCompanyInternalId = :internalId"),
	@NamedQuery(name = "UserCustomer.countCompanyFarmers",
			    query = "SELECT COUNT(u) FROM UserCustomer u WHERE u.company.id = :companyId AND u.type = com.abelium.inatrace.types.UserCustomerType.FARMER")
})
public class UserCustomer extends BaseEntity {

	/**
	 * product this collector belongs to
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private Product product;

	/**
	 * company reference
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private Company company;

	/**
	 * company farmer internal id
	 */
	@Column(length = Lengths.DEFAULT)
	private String farmerCompanyInternalId;
	
	/**
	 * type (collector, farmer)
	 */
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private UserCustomerType type;
	
	/**
	 * name
	 */
	@Column(length = Lengths.NAME)
	private String name;

	/**
	 * surname
	 */
	@Column(length = Lengths.SURNAME)
	private String surname;
	
	/**
	 * phone number
	 */
	@Column(length = Lengths.PHONE_NUMBER)
	private String phone;
	
	/**
	 * email
	 */
	@Column(length = Lengths.EMAIL)
	private String email;

	@Column
	private Boolean hasSmartphone;

	/**
	 * location (text description)
	 */
	@Column(length = Lengths.DEFAULT)
	private String location;

	@OneToOne
	private UserCustomerLocation userCustomerLocation;
	
	/**
	 * gender
	 */
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private Gender gender;

	@Column
	private BankInformation bank;

	@Column
	private FarmInformation farm;

	@OneToMany(mappedBy = "userCustomer", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<UserCustomerAssociation> associations;

	@OneToMany(mappedBy = "userCustomer", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<UserCustomerCooperative> cooperatives;

	@OneToMany(mappedBy = "userCustomer", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<UserCustomerCertification> certifications;

	@OneToMany(mappedBy = "userCustomer", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<UserCustomerProductType> productTypes;

	@OneToMany(mappedBy = "userCustomer", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<FarmPlantInformation> farmPlantInformationList;

	@OneToMany(mappedBy = "farmer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private Set<Plot> plots;
	
	public UserCustomerType getType() {
		return type;
	}

	public void setType(UserCustomerType type) {
		this.type = type;
	}

	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public String getFarmerCompanyInternalId() {
		return farmerCompanyInternalId;
	}

	public void setFarmerCompanyInternalId(String farmerCompanyInternalId) {
		this.farmerCompanyInternalId = farmerCompanyInternalId;
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSurname() {
		return surname;
	}

	public void setSurname(String surname) {
		this.surname = surname;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}
	
	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Boolean getHasSmartphone() {
		return hasSmartphone;
	}

	public void setHasSmartphone(Boolean hasSmartphone) {
		this.hasSmartphone = hasSmartphone;
	}

	public UserCustomerLocation getUserCustomerLocation() {
		return userCustomerLocation;
	}

	public void setUserCustomerLocation(UserCustomerLocation userCustomerLocation) {
		this.userCustomerLocation = userCustomerLocation;
	}

	public Gender getGender() {
		return gender;
	}

	public void setGender(Gender gender) {
		this.gender = gender;
	}

	public BankInformation getBank() {
		return bank;
	}

	public void setBank(BankInformation bank) {
		this.bank = bank;
	}

	public FarmInformation getFarm() {
		return farm;
	}

	public void setFarm(FarmInformation farm) {
		this.farm = farm;
	}

	public Set<UserCustomerAssociation> getAssociations() {
		return associations;
	}

	public void setAssociations(Set<UserCustomerAssociation> associations) {
		this.associations = associations;
	}

	public Set<UserCustomerCooperative> getCooperatives() {
		return cooperatives;
	}

	public void setCooperatives(Set<UserCustomerCooperative> cooperatives) {
		this.cooperatives = cooperatives;
	}

	public Set<UserCustomerCertification> getCertifications() {
		if (certifications == null) {
			certifications = new HashSet<>();
		}
		return certifications;
	}

	public void setCertifications(Set<UserCustomerCertification> certifications) {
		this.certifications = certifications;
	}

	public Set<UserCustomerProductType> getProductTypes() {
		if (productTypes == null) {
			productTypes = new HashSet<>();
		}
		return productTypes;
	}

	public void setProductTypes(Set<UserCustomerProductType> productTypes) {
		this.productTypes = productTypes;
	}

	public Set<FarmPlantInformation> getFarmPlantInformationList() {
		if (farmPlantInformationList == null) {
			farmPlantInformationList = new HashSet<>();
		}
		return farmPlantInformationList;
	}

	public void setFarmPlantInformationList(Set<FarmPlantInformation> farmPlantInformationList) {
		this.farmPlantInformationList = farmPlantInformationList;
	}

	public Set<Plot> getPlots() {
		if (plots == null) {
			plots = new HashSet<>();
		}
		return plots;
	}

	public void setPlots(Set<Plot> plots) {
		this.plots = plots;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}
}
