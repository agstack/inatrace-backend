package com.abelium.inatrace.db.entities.product;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.types.KnowledgeBlogType;

import jakarta.persistence.*;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(indexes = { @Index(columnList = "type") })
public class KnowledgeBlog extends TimestampEntity {
	
	/**
	 * Product this part belongs to 
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private Product product;
	
	/**
	 * product status
	 */
	@Enumerated(EnumType.STRING)
    @Column(nullable = false, length = Lengths.ENUM)
    private KnowledgeBlogType type;	
	
	/**
	 * Title
	 */
	@Column(length = Lengths.DEFAULT)
	private String title;
	
	/**
	 * Abstract
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String summary;
	
	/**
	 * Content
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String content;

	/**
	 * Date
	 */
	@Column
	private LocalDate date; 

	/**
	 * Youtube url
	 */
	@Column(length = Lengths.DEFAULT)
	private String youtubeUrl;
	
	/**
	 * Documents
	 */
	@ManyToMany(cascade = CascadeType.ALL)
	private List<Document> documents = new ArrayList<>();

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}
	
	public KnowledgeBlogType getType() {
		return type;
	}

	public void setType(KnowledgeBlogType type) {
		this.type = type;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public String getYoutubeUrl() {
		return youtubeUrl;
	}

	public void setYoutubeUrl(String youtubeUrl) {
		this.youtubeUrl = youtubeUrl;
	}

	public List<Document> getDocuments() {
		return documents;
	}

	public void setDocuments(List<Document> documents) {
		this.documents = documents;
	}
}
