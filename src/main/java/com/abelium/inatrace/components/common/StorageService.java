package com.abelium.inatrace.components.common;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.configuration.SafeExceptionLog;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.tools.ImageTools;
import com.abelium.inatrace.tools.ImageTools.ImageSizeData;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.types.DocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.UUID;

@Lazy
@Service
public class StorageService extends BaseService
{
    @Value("${INATrace.fileStorage.root}")
    private String root;

    @Transactional
    public Document uploadDocument(byte[] file, String name, String contentType, Long size, DocumentType type) throws ApiException {

        if (file == null) {
        	throw new ApiException(ApiStatus.ERROR, "NULL file cannot be saved");
        }
    	String storageKey = UUID.randomUUID().toString();
    	Path path = Paths.get(root, type.toString());

        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                SafeExceptionLog.error(logger, "Error creating type directory", e);
                throw new ApiException(ApiStatus.ERROR, "Error saving file");
            }
        }
        
        path = Paths.get(path.toString(), storageKey);
        try {
            Files.write(path, file);
        } catch (IOException e) {
            SafeExceptionLog.error(logger, "Error writing file", e);
            throw new ApiException(ApiStatus.ERROR, "Error saving file");
        }

        Document document = new Document();
        document.setName(name);
        document.setType(type);
        document.setStorageKey(storageKey);
        document.setContentType(contentType);
        document.setSize(size);
        em.persist(document);

        return document;
    }
    
    public byte[] downloadFileFromUrl(String url) {
    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    	try {
    		byte[] chunk = new byte[4096];
    		int bytesRead;
    		InputStream stream = new URL(url).openStream();
    		while ((bytesRead = stream.read(chunk)) > 0) {
    			outputStream.write(chunk, 0, bytesRead);
    		}
    	} catch(MalformedURLException e) {
			SafeExceptionLog.error(logger, "Malformed file download URL", e);
    		return null;
    	} catch (Exception e) {
			SafeExceptionLog.error(logger, "Cannot download file", e);
    		return null;
    	}
    	return outputStream.toByteArray();
    }

	public byte[] downloadLocalFile(String absolutePath) {
		if(absolutePath == null) {
			return null;
		}
		try {
			return Files.readAllBytes(Paths.get(absolutePath));
		} catch (IOException e) {
			SafeExceptionLog.error(logger, "Cannot read local file", e);
			return null;
		}
	}
    
    public DocumentData uploadDocumentFromUrl(String url, String name, DocumentType type) throws ApiException {
    	byte[] doc = downloadFileFromUrl(url);
    	return new DocumentData(doc, uploadDocument(doc, name, null, null, type));
    }
    
    public Path getPath(Document document) {
    	return Paths.get(root, document.getType().toString(), document.getStorageKey());
    }
    
    public Path getImagePath(Document document, String size) {
    	return Paths.get(root, document.getType().toString(), size, document.getStorageKey());
    }
    

    @Transactional
    public DocumentData downloadDocument(String storageKey) throws ApiException {
        return downloadDocument(storageKey, null);
    }
    
    @Transactional
    public DocumentData downloadDocument(String storageKey, EnumSet<DocumentType> allowedTypes) throws ApiException {
        Document document = Queries.getFirstBy(em, Document.class, Document::getStorageKey, storageKey);
        
        if (document == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid storage key");
        }
        if (allowedTypes != null && !allowedTypes.contains(document.getType())) {
            throw new ApiException(ApiStatus.UNAUTHORIZED, "Invalid storage key");
        }
        try {
            byte[] bytes = Files.readAllBytes(getPath(document));
            return new DocumentData(bytes, document);
        } catch (IOException e) {
            SafeExceptionLog.error(logger, "Error reading file", e);
            throw new ApiException(ApiStatus.ERROR, "Error reading file");
        }
    }

	public String saveFileLocally(byte[] file, String folder, String name) throws ApiException {
    	Path path = Paths.get(root, folder);
        if (Files.notExists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                SafeExceptionLog.error(logger, "Error creating type directory", e);
                throw new ApiException(ApiStatus.ERROR, "Error saving file");
            }
        }
        
        path = Paths.get(path.toString(), name);
        try {
            Files.write(path, file);
        } catch (IOException e) {
            SafeExceptionLog.error(logger, "Error writing file", e);
            throw new ApiException(ApiStatus.ERROR, "Error saving file");
        }
        return path.toString();
	}

	@Transactional
	public Document uploadImageVariants(byte[] file, ImageSizeData[] imageSizes, String fileType, String name) throws ApiException {
		// Upload original image
		Document mainDoc = uploadDocument(file, name, fileType, (long) file.length, DocumentType.IMAGE);
		
		// Upload all variants
		for (ImageSizeData is : imageSizes) {
	    	Path path = Paths.get(root, DocumentType.IMAGE.toString() + "/" + is.name);
	        if (Files.notExists(path)) {
	            try {
	                Files.createDirectories(path);
	            } catch (IOException e) {
	                SafeExceptionLog.error(logger, "Error creating type directory", e);
	                throw new ApiException(ApiStatus.ERROR, "Error saving file");
	            }
	        }
	        path = Paths.get(path.toString(), mainDoc.getStorageKey());
	        try {
	        	if (MediaType.IMAGE_PNG_VALUE.equals(fileType)) {
	        		file = ImageTools.convertToJpg(file);
	        	}
	            Files.write(path, ImageTools.resize(file, is));
	        } catch (IOException e) {
	            SafeExceptionLog.error(logger, "Error writing file", e);
	            throw new ApiException(ApiStatus.ERROR, "Error saving file");
	        }
		}
		return mainDoc;
	}

    @Transactional
    public DocumentData downloadImage(String storageKey, String size) throws ApiException {
        Document document = Queries.getFirstBy(em, Document.class, Document::getStorageKey, storageKey);
        
        if (document == null || document.getType() != DocumentType.IMAGE) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid storage key or file type");
        }
        try {
        	Path path = (size != null) ? getImagePath(document, size.toUpperCase()) : getPath(document); 
            byte[] bytes = Files.readAllBytes(path);
            return new DocumentData(bytes, document);
        } catch (IOException e) {
            SafeExceptionLog.error(logger, "Error reading file", e);
            throw new ApiException(ApiStatus.ERROR, "Error reading file");
        }
    }
	
}
