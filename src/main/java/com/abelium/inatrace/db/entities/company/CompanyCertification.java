package com.abelium.inatrace.db.entities.company;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.common.Document;
import jakarta.persistence.*;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;

@Entity
@NamedQueries({
		@NamedQuery(name = "CompanyCertification.getCertificatesForCompanyIDs",
		            query = "SELECT cc FROM CompanyCertification cc WHERE cc.company.id IN :companyIDs")
})
public class CompanyCertification extends BaseEntity {

	/**
	 * Company 
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private Company company;
	
	/**
	 * Company translation this document applies to
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private CompanyTranslation companyTranslation;
	
    /**
     * Document type (link, file)
     */
	@Column(length = Lengths.DEFAULT)
    private String type;
	
	/**
	 * Certificate document
	 */
	@ManyToOne
	private Document certificate;
	
	/**
	 * Description of this certification
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String description;

	/**
	 * Validity
	 */
	@Column
	private LocalDate validity;

	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public CompanyTranslation getCompanyTranslation() {
		return companyTranslation;
	}

	public void setCompanyTranslation(CompanyTranslation companyTranslation) {
		this.companyTranslation = companyTranslation;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Document getCertificate() {
		return certificate;
	}

	public void setCertificate(Document certificate) {
		this.certificate = certificate;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getValidity() {
		return validity;
	}

	public void setValidity(LocalDate validity) {
		this.validity = validity;
	}	
}
