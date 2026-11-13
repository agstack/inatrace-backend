package com.abelium.inatrace.components.common.api;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.tools.converters.SimpleDateConverter;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;

/**
 * ActivityProof API model object.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Validated
public class ApiActivityProof extends ApiBaseEntity {

	@Schema(description = "The formal creation date of the document")
	@JsonSerialize(converter = SimpleDateConverter.Serialize.class)
	@JsonDeserialize(using = SimpleDateConverter.Deserialize.class)
	private Instant formalCreationDate;

	@Schema(description = "Date until the document is valid")
	@JsonSerialize(converter = SimpleDateConverter.Serialize.class)
	@JsonDeserialize(using = SimpleDateConverter.Deserialize.class)
	private Instant validUntil;

	@Schema(description = "The type of the activity proof")
	private String type;

	@Schema(description = "The attached document reference of the activity proof")
	private ApiDocument document;

	public Instant getFormalCreationDate() {
		return formalCreationDate;
	}

	public void setFormalCreationDate(Instant formalCreationDate) {
		this.formalCreationDate = formalCreationDate;
	}

	public Instant getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(Instant validUntil) {
		this.validUntil = validUntil;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public ApiDocument getDocument() {
		return document;
	}

	public void setDocument(ApiDocument document) {
		this.document = document;
	}

}
