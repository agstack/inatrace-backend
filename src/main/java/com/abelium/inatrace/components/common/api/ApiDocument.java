package com.abelium.inatrace.components.common.api;

import com.abelium.inatrace.api.ApiBaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@Validated
public class ApiDocument extends ApiBaseEntity {

    @NotNull
    @Schema(description = "storage key (file on system, s3, ...).", requiredMode = Schema.RequiredMode.REQUIRED)
    public String storageKey;
    
    @NotNull
    @Schema(description = "document (file) name", requiredMode = Schema.RequiredMode.REQUIRED)
    public String name;    

    @Schema(description = "content type")
    public String contentType;    
    
    @Schema(description = "size")
    public Long size;
    
    public ApiDocument() {}

    public ApiDocument(Long id, String storageKey) {
        this.id = id;
        this.storageKey = storageKey;
    }

	public String getStorageKey() {
		return storageKey;
	}

	public void setStorageKey(String storageKey) {
		this.storageKey = storageKey;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}
}
