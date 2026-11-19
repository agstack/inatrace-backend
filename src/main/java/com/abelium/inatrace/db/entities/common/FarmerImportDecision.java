package com.abelium.inatrace.db.entities.common;

import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Records a completed create-separate decision without storing farmer names. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "UK_farmer_import_decision",
        columnNames = {"companyId", "documentId", "selectionKey"}))
public class FarmerImportDecision extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "companyId", nullable = false, foreignKey = @ForeignKey(name = "FK_farmer_import_company"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documentId", nullable = false, foreignKey = @ForeignKey(name = "FK_farmer_import_document"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Document document;
    @Column(nullable = false, length = 64)
    private String selectionKey;

    protected FarmerImportDecision() {}

    public FarmerImportDecision(Company company, Document document, String selectionKey) {
        this.company = company;
        this.document = document;
        this.selectionKey = selectionKey;
    }
}
