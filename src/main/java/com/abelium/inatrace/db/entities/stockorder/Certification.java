package com.abelium.inatrace.db.entities.stockorder;

import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.common.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
public class Certification extends TimestampEntity {
	
	@Version
	private Long entityVersion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "certificate_id", nullable = true)
	private Document certificate;
	
	@Column
	private String description;
	
	@Column
	private String type;
	
	@Column
	private Instant validity;

	@ManyToOne
	private StockOrder stockOrder;

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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Instant getValidity() {
		return validity;
	}

	public void setValidity(Instant validity) {
		this.validity = validity;
	}

	public StockOrder getStockOrder() {
		return stockOrder;
	}

	public void setStockOrder(StockOrder stockOrder) {
		this.stockOrder = stockOrder;
	}

	public Certification(Document certificate, String description, String type, Instant validity) {
		super();
		this.certificate = certificate;
		this.description = description;
		this.type = type;
		this.validity = validity;
	}

	public Certification() {
		super();
	}
	
}
