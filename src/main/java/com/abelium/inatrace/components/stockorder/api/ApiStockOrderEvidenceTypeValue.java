package com.abelium.inatrace.components.stockorder.api;

import com.abelium.inatrace.components.common.api.ApiDocument;
import com.abelium.inatrace.tools.converters.SimpleDateConverter;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;

@Validated
public class ApiStockOrderEvidenceTypeValue {

	@Schema(description = "The id of the Processing evidence type from the codebook")
	private Long evidenceTypeId;

	@Schema(description = "The field code of the Processing evidence type from the codebook")
	private String evidenceTypeCode;

	@Schema(description = "The field label of the Processing evidence type from the codebook")
	private String evidenceTypeLabel;

	@Schema(description = "The date of the document upload")
	@JsonSerialize(converter = SimpleDateConverter.Serialize.class)
	@JsonDeserialize(using = SimpleDateConverter.Deserialize.class)
	private Instant date;

	@Schema(description = "The attached document reference of the processing evidence")
	private ApiDocument document;

	public Long getEvidenceTypeId() {
		return evidenceTypeId;
	}

	public void setEvidenceTypeId(Long evidenceTypeId) {
		this.evidenceTypeId = evidenceTypeId;
	}

	public String getEvidenceTypeCode() {
		return evidenceTypeCode;
	}

	public void setEvidenceTypeCode(String evidenceTypeCode) {
		this.evidenceTypeCode = evidenceTypeCode;
	}

	public String getEvidenceTypeLabel() {
		return evidenceTypeLabel;
	}

	public void setEvidenceTypeLabel(String evidenceTypeLabel) {
		this.evidenceTypeLabel = evidenceTypeLabel;
	}

	public Instant getDate() {
		return date;
	}

	public void setDate(Instant date) {
		this.date = date;
	}

	public ApiDocument getDocument() {
		return document;
	}

	public void setDocument(ApiDocument document) {
		this.document = document;
	}

}
