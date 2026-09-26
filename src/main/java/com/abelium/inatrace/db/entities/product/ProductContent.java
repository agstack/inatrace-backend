package com.abelium.inatrace.db.entities.product;

import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.process.Process;
import jakarta.persistence.*;

@MappedSuperclass
public class ProductContent extends BaseEntity {
	
	/**
	 * product name
	 */
	@Column
	private String name;

	/**
	 * high-resolution photo of the product (jpeg, jpg, png), 72dpi and 1200x2000px recommended 
	 */
	@ManyToOne
	private Document photo;
	
	/**
	 * product description - briefly describe the product AND reasons why your product is worth buying 
	 * (example: special properties, health benefits, great value, organic production....)
	 */
	@Lob
	@Column(columnDefinition = "LONGTEXT")
	private String description;
	
	/**
	 * origin - text and quantity input - Briefly describe where the product or its 
	 * ingredients are produced. 
	 */
	@Lob
	@Column(columnDefinition = "LONGTEXT")
	private String originText;

	/**
	 * process
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "process_id", nullable = true)
	private Process process = new Process();
	
	/**
	 * social responsibility
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responsibility_id", nullable = true)
	private Responsibility responsibility = new Responsibility();
	
	/**
	 * environmental sustainability
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sustainability_id", nullable = true)
	private Sustainability sustainability = new Sustainability();

    /**
     * journey coordinates
     */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "journey_id", nullable = true)
    private ProductJourney journey = new ProductJourney();
	
	/**
	 * product settings
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "settings_id", nullable = true)
	private ProductSettings settings = new ProductSettings();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "businessToCustomerSettings_id", nullable = true)
	private BusinessToCustomerSettings businessToCustomerSettings = new BusinessToCustomerSettings();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Document getPhoto() {
		return photo;
	}

	public void setPhoto(Document photo) {
		this.photo = photo;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getOriginText() {
		return originText;
	}

	public void setOriginText(String originText) {
		this.originText = originText;
	}

	public Process getProcess() {
		return process;
	}

	public void setProcess(Process process) {
		this.process = process;
	}

	public Responsibility getResponsibility() {
		return responsibility;
	}

	public void setResponsibility(Responsibility responsibility) {
		this.responsibility = responsibility;
	}

	public Sustainability getSustainability() {
		return sustainability;
	}

	public void setSustainability(Sustainability sustainability) {
		this.sustainability = sustainability;
	}

	public ProductSettings getSettings() {
		return settings;
	}

	public void setSettings(ProductSettings settings) {
		this.settings = settings;
	}

    public ProductJourney getJourney() {
        return journey;
    }

    public void setJourney(ProductJourney journey) {
        this.journey = journey;
    }

	public BusinessToCustomerSettings getBusinessToCustomerSettings() {
		return businessToCustomerSettings;
	}

	public void setBusinessToCustomerSettings(BusinessToCustomerSettings businessToCustomerSettings) {
		this.businessToCustomerSettings = businessToCustomerSettings;
	}
}
