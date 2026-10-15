
package com.abelium.inatrace.db.entities.common;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.CreationTimestampEntity;
import com.abelium.inatrace.types.DocumentType;
import jakarta.persistence.*;

@Entity
@Table(indexes = { @Index(columnList = "storageKey") })
public class Document extends CreationTimestampEntity {

    /**
     * Authenticated user that originally uploaded this document.  It is nullable because
     * documents created before upload provenance was introduced (and system-generated
     * documents) have no user-owned source.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploadedBy_id")
    private User uploadedBy;

    /**
     * Storage key on storage engine (e.g. file name on system)
     */
    @Column(unique = true, length = Lengths.DEFAULT)
    private String storageKey;
    
    /**
     * Document type (to place files on storage into different folders) 
     */
    @Enumerated(EnumType.STRING)
    @Column(length = Lengths.ENUM)
    private DocumentType type = DocumentType.GENERAL;
    
    /**
     * Document name (file name)
     */
    @Column(length = Lengths.DEFAULT)
    private String name;

    /**
     * Content type
     */
    @Column(length = Lengths.CONTENT_TYPE)
    private String contentType;

    /**
     * File size
     */
    @Column
    private Long size;
    
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

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
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

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

}
