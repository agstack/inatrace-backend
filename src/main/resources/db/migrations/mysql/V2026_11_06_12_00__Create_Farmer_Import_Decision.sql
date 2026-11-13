CREATE TABLE IF NOT EXISTS FarmerImportDecision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    companyId BIGINT NOT NULL,
    documentId BIGINT NOT NULL,
    selectionKey VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_farmer_import_decision UNIQUE (companyId, documentId, selectionKey),
    CONSTRAINT FK_farmer_import_company FOREIGN KEY (companyId) REFERENCES Company (id) ON DELETE CASCADE,
    CONSTRAINT FK_farmer_import_document FOREIGN KEY (documentId) REFERENCES Document (id) ON DELETE CASCADE
);
