package com.abelium.inatrace.db.entities.product;

import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.abelium.inatrace.db.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

@Entity
public class Responsibility extends BaseEntity {

	/**
	 * labor policies - Briefly describe labor policies you have in place in your company and 
	 * activities you pursue to make them happen (example: women inclusion, no child labor, etc.)
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String laborPolicies;
	
	public String getLaborPolicies() {
		return laborPolicies;
	}

	public void setLaborPolicies(String laborPolicies) {
		this.laborPolicies = laborPolicies;
	}

	public Responsibility copy() {
		Responsibility r = new Responsibility();
		r.setLaborPolicies(getLaborPolicies());
		return r;
	}

}
