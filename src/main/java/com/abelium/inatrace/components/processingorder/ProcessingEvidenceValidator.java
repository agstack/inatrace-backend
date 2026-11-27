package com.abelium.inatrace.components.processingorder;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrderEvidenceFieldValue;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrderEvidenceTypeValue;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceField;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceType;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionPEF;
import com.abelium.inatrace.db.entities.processingaction.ProcessingActionPET;
import com.abelium.inatrace.types.ProcessingActionType;
import com.abelium.inatrace.types.ProcessingEvidenceFieldType;
import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates the evidence configured as mandatory on a processing action.
 *
 * <p>Every target output must carry every mandatory document and field. Evidence
 * that is not configured on the action is ignored, so existing clients may keep
 * sending optional or extra evidence without it satisfying the mandatory rules.</p>
 */
public final class ProcessingEvidenceValidator {

    private ProcessingEvidenceValidator() {
    }

    public static boolean isProcessingEvidenceAction(ProcessingAction processingAction) {
        return processingAction != null
                && (processingAction.getType() == ProcessingActionType.PROCESSING
                || processingAction.getType() == ProcessingActionType.FINAL_PROCESSING
                || processingAction.getType() == ProcessingActionType.GENERATE_QR_CODE);
    }

    public static void validateProcessingOrder(EntityManager em,
                                               ProcessingAction processingAction,
                                               List<ApiStockOrder> targetStockOrders) throws ApiException {

        List<String> missingEvidence = new ArrayList<>();

        for (ApiStockOrder output : targetStockOrders) {
            rejectNullEntries(output.getRequiredEvidenceTypeValues(),
                    "Processing evidence type entry must not be null");
            rejectNullEntries(output.getRequiredEvidenceFieldValues(),
                    "Processing evidence field entry must not be null");
            rejectNullEntries(output.getOtherEvidenceDocuments(),
                    "Processing evidence type entry must not be null");
        }

        for (int i = 0; i < targetStockOrders.size(); i++) {
            ApiStockOrder output = targetStockOrders.get(i);

            for (ProcessingActionPET requiredDocument : processingAction.getRequiredDocumentTypes()) {
                if (!Boolean.TRUE.equals(requiredDocument.getMandatory())) {
                    continue;
                }

                Long evidenceTypeId = requiredDocument.getProcessingEvidenceType().getId();
                boolean present = output.getRequiredEvidenceTypeValues().stream()
                        .filter(value -> value != null)
                        .filter(value -> evidenceTypeId.equals(value.getEvidenceTypeId()))
                        .anyMatch(value -> documentExists(em, value));

                if (!present) {
                    missingEvidence.add("document '"
                            + evidenceTypeLabel(requiredDocument.getProcessingEvidenceType())
                            + "' for output '" + outputLabel(output, i) + "'");
                }
            }

            for (ProcessingActionPEF requiredField : processingAction.getProcessingEvidenceFields()) {
                if (!Boolean.TRUE.equals(requiredField.getMandatory())) {
                    continue;
                }

                ProcessingEvidenceField evidenceField = requiredField.getProcessingEvidenceField();
                Long evidenceFieldId = evidenceField.getId();
                boolean present = output.getRequiredEvidenceFieldValues().stream()
                        .filter(value -> value != null)
                        .filter(value -> evidenceFieldId.equals(value.getEvidenceFieldId()))
                        .anyMatch(value -> hasEvidenceFieldValue(value, evidenceField.getType()));

                if (!present) {
                    missingEvidence.add("field '" + evidenceFieldLabel(evidenceField)
                            + "' for output '" + outputLabel(output, i) + "'");
                }
            }
        }

        if (!missingEvidence.isEmpty()) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR,
                    "Missing required processing evidence: " + String.join("; ", missingEvidence));
        }
    }

    private static void rejectNullEntries(List<?> values, String message) throws ApiException {
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR, message);
        }
    }

    private static boolean documentExists(EntityManager em, ApiStockOrderEvidenceTypeValue value) {
        return value.getDocument() != null
                && value.getDocument().getId() != null
                && em.find(Document.class, value.getDocument().getId()) != null;
    }

    private static boolean hasEvidenceFieldValue(ApiStockOrderEvidenceFieldValue value,
                                                 ProcessingEvidenceFieldType fieldType) {

        if (fieldType == null) {
            return value.getStringValue() != null
                    || value.getNumericValue() != null
                    || value.getBooleanValue() != null
                    || value.getDateValue() != null;
        }

        return switch (fieldType) {
            case STRING, TEXT, OBJECT -> StringUtils.isNotBlank(value.getStringValue());
            case NUMBER, INTEGER, PRICE, EXCHANGE_RATE -> value.getNumericValue() != null;
            case DATE, TIMESTAMP -> value.getDateValue() != null;
        };
    }

    private static String evidenceTypeLabel(ProcessingEvidenceType evidenceType) {
        return StringUtils.defaultIfBlank(evidenceType.getCode(), String.valueOf(evidenceType.getId()));
    }

    private static String evidenceFieldLabel(ProcessingEvidenceField evidenceField) {
        return StringUtils.defaultIfBlank(evidenceField.getFieldName(), String.valueOf(evidenceField.getId()));
    }

    private static String outputLabel(ApiStockOrder output, int index) {

        if (StringUtils.isNotBlank(output.getInternalLotNumber())) {
            return output.getInternalLotNumber();
        }
        if (StringUtils.isNotBlank(output.getIdentifier())) {
            return output.getIdentifier();
        }
        return "output " + (index + 1);
    }
}
