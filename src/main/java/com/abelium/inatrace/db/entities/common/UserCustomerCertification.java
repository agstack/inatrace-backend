package com.abelium.inatrace.db.entities.common;

import com.abelium.inatrace.db.base.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;

/**
 * Entity holding relationship between certificate and user customer (farmer or collector).
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Entity
public class UserCustomerCertification extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	private UserCustomer userCustomer;

	/**
	 * Certificate type
	 */
	@Column
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

	public UserCustomer getUserCustomer() {
		return userCustomer;
	}

	public void setUserCustomer(UserCustomer userCustomer) {
		this.userCustomer = userCustomer;
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
