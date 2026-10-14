package com.abelium.inatrace.components.common;

import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedQueryStringRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.api.ApiCountry;
import com.abelium.inatrace.components.common.api.ApiDocument;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.tools.*;
import com.abelium.inatrace.types.DocumentType;
import com.abelium.inatrace.types.MediaObject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.util.EnumSet;

@Lazy
@Service
public class CommonService extends BaseService {
	
	@Autowired
	private StorageService storageEngine;

    private Country countryListQueryObject(ApiPaginatedQueryStringRequest request) {
        Country cProxy = Torpedo.from(Country.class);

        if (StringUtils.isNotBlank(request.queryString)) {
            Torpedo.where(cProxy.getName()).like().startsWith(request.queryString);
        }
        if (request.sortBy.equals("name")) {
            QueryTools.orderBy(request.sort, cProxy.getName());
        } else {
            QueryTools.orderBy(request.sort, cProxy.getId());
        }
        return cProxy;
    }
	
    @Transactional
    public ApiPaginatedList<ApiCountry> fetchCountryList(ApiPaginatedQueryStringRequest filterRequest) {
        return PaginationTools.createPaginatedResponse(em, filterRequest, () -> countryListQueryObject(filterRequest),
        		CommonApiTools::toApiCountry);
    }

    public ApiDocument uploadDocument(Long userId, byte[] file, String name, String contentType, Long size, DocumentType type) throws ApiException {
        if (file == null) {
            throw new ApiException(ApiStatus.REQUEST_BODY_ERROR, "Uploading file failed. File is null.");
        }
        Document document = storageEngine.uploadDocument(file, name, contentType, size, type);
        return CommonApiTools.toApiDocument(document, userId);
    }
    
	public ApiDocument uploadImage(Long userId, byte[] file, String name, String contentType, long size, boolean resize) throws ApiException {
        if (file == null) {
            throw new ApiException(ApiStatus.REQUEST_BODY_ERROR, "Uploading image failed. File is null.");
        }
        
        // First filter; we'll check type later with Tika library
        if (!MediaType.IMAGE_JPEG_VALUE.equals(contentType) && !MediaType.IMAGE_PNG_VALUE.equals(contentType)) {
        	throw new ApiException(ApiStatus.INVALID_REQUEST, "Unsupported file type.");
        }
        
        String fileType = ResourceTools.mediaTypeDetector().detect(file);
        
        if (!MediaType.IMAGE_JPEG_VALUE.equals(fileType) && !MediaType.IMAGE_PNG_VALUE.equals(fileType)) {
        	throw new ApiException(ApiStatus.INVALID_REQUEST, "Unsupported file type.");
        }
        
        Document document;
        
        if (resize) {
        	document = storageEngine.uploadImageVariants(file, ImageTools.STANDARD_IMAGE_SIZES, fileType, name);
        } else {
            document = storageEngine.uploadDocument(file, name, fileType, size, DocumentType.IMAGE);
        }
        return CommonApiTools.toApiDocument(document, userId);        
	}

    public MediaObject getDocument(Long userId, String tempKey, EnumSet<DocumentType> allowedTypes) throws ApiException {
		String storageKey = StorageKeyCache.getPrivate(tempKey, userId);
        if (storageKey == null) {
            throw new ApiException(ApiStatus.INVALID_OR_EXPIRED_STORAGE_KEY, "Invalid storage key or storage key has expired");
        }
        DocumentData document = storageEngine.downloadDocument(storageKey, allowedTypes);
        MediaType mediaType = document.data.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.valueOf(document.data.getContentType());
        return new MediaObject(mediaType, document.file);
    }

    public MediaObject getPublicDocument(String tempKey, EnumSet<DocumentType> allowedTypes) throws ApiException {
        String storageKey = StorageKeyCache.getPublic(tempKey);
        if (storageKey == null) {
            throw new ApiException(ApiStatus.INVALID_OR_EXPIRED_STORAGE_KEY, "Invalid storage key or storage key has expired");
        }
        DocumentData document = storageEngine.downloadDocument(storageKey, allowedTypes);
        MediaType mediaType = document.data.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.valueOf(document.data.getContentType());
        return new MediaObject(mediaType, document.file);
    }
    
	public MediaObject getImage(Long userId, String tempKey, String size) throws ApiException {
		String storageKey = StorageKeyCache.getPrivate(tempKey, userId);
        if (storageKey == null) {
            throw new ApiException(ApiStatus.INVALID_OR_EXPIRED_STORAGE_KEY, "Invalid storage key or storage key has expired");
        }
        DocumentData document = storageEngine.downloadImage(storageKey, size);
        MediaType mediaType = document.data.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.valueOf(document.data.getContentType());
        return new MediaObject(mediaType, document.file);
	}

    public MediaObject getPublicImage(String tempKey, String size) throws ApiException {
        String storageKey = StorageKeyCache.getPublic(tempKey);
        if (storageKey == null) {
            throw new ApiException(ApiStatus.INVALID_OR_EXPIRED_STORAGE_KEY, "Invalid storage key or storage key has expired");
        }
        DocumentData document = storageEngine.downloadImage(storageKey, size);
        MediaType mediaType = document.data.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.valueOf(document.data.getContentType());
        return new MediaObject(mediaType, document.file);
    }
    
    @Transactional 
    public Document fetchDocument(Long userId, ApiDocument ad) throws ApiException {
    	if (ad == null) {
    		return null;
		} else {
			String storageKey = StorageKeyCache.getPrivate(ad.storageKey, userId);
        	
        	if (storageKey == null) {
        		throw new ApiException(ApiStatus.INVALID_OR_EXPIRED_STORAGE_KEY, "Invalid storage key or storage key has expired");
        	}
    		return Queries.getUniqueBy(em, Document.class, Document::getStorageKey, storageKey);
    	}
    	
    }
    
    @Transactional
    public Country fetchCountry(ApiCountry ac) throws ApiException {
    	if (ac == null) return null;
    	
    	Country c = Queries.get(em, Country.class, ac.getId());
    	if (c == null) {
    		throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid country id");
    	}
    	return c;
    }

}
