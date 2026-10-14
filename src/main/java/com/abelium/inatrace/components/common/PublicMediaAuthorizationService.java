package com.abelium.inatrace.components.common;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.stockorder.StockOrderService;
import com.abelium.inatrace.db.entities.product.ProductLabel;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.types.ProductLabelStatus;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Validates that a public media capability still has a current public owner. */
@Service
public class PublicMediaAuthorizationService extends BaseService {

    private final StockOrderService stockOrderService;

    @Autowired
    public PublicMediaAuthorizationService(StockOrderService stockOrderService) {
        this.stockOrderService = stockOrderService;
    }

    @Transactional
    public void validate(String tempKey) throws ApiException {
        StorageKeyCache.CacheValue value = StorageKeyCache.getPublicValue(tempKey);
        if (value == null || value.getPublicScopeType() == null) {
            throw invalidStorageKey();
        }

        switch (value.getPublicScopeType()) {
            case LABEL:
                validateLabel(value.getPublicLabelUid());
                break;
            case PRODUCT:
                validateProduct(value.getPublicProductId());
                break;
            case QR_CERTIFICATE:
                stockOrderService.validatePublicQrCertificate(
                        value.getPublicQrTag(),
                        value.getPublicCertificationId(),
                        value.getDbStorageKey());
                break;
            default:
                throw invalidStorageKey();
        }
    }

    private void validateLabel(String labelUid) throws ApiException {
        ProductLabel label = labelUid == null ? null
                : Queries.getUniqueBy(em, ProductLabel.class, ProductLabel::getUuid, labelUid);
        if (label == null || label.getStatus() == ProductLabelStatus.UNPUBLISHED) {
            throw invalidStorageKey();
        }
    }

    private void validateProduct(Long productId) throws ApiException {
        if (productId == null) {
            throw invalidStorageKey();
        }

        Long publishedLabelCount = em.createQuery(
                        "SELECT COUNT(label) FROM ProductLabel label WHERE label.product.id = :productId "
                                + "AND label.status = :status", Long.class)
                .setParameter("productId", productId)
                .setParameter("status", ProductLabelStatus.PUBLISHED)
                .getSingleResult();
        if (publishedLabelCount == 0) {
            throw invalidStorageKey();
        }
    }

    private ApiException invalidStorageKey() {
        return new ApiException(ApiStatus.INVALID_OR_EXPIRED_STORAGE_KEY,
                "Invalid storage key or storage key has expired");
    }
}
