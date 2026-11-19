package com.abelium.inatrace.db.entities.process;

import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.abelium.inatrace.db.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

@Entity
public class Process extends BaseEntity {

	/**
	 * production description - Briefly describe your production process. 
	 * If the ingredients are organic, describe how you achieve that 
	 * (e.g. organic seedlings, no chemical fertilizers, etc.)
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String production;

	public String getProduction() {
		return production;
	}

	public void setProduction(String production) {
		this.production = production;
	}
	
	public Process copy() {
		Process p = new Process();
		p.setProduction(getProduction());
		return p;
	}
}
