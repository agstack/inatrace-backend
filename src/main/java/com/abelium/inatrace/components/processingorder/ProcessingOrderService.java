package com.abelium.inatrace.components.processingorder;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.company.CompanyQueries;
import com.abelium.inatrace.components.processingorder.api.ApiProcessingOrder;
import com.abelium.inatrace.components.processingorder.mappers.ProcessingOrderMapper;
import com.abelium.inatrace.components.stockorder.StockOrderService;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.components.transaction.TransactionService;
import com.abelium.inatrace.components.transaction.api.ApiTransaction;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.payment.Payment;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProcessingActionType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Lazy
@Service
public class ProcessingOrderService extends BaseService {

    private final StockOrderService stockOrderService;

    private final TransactionService transactionService;

    private final CompanyQueries companyQueries;

    @Autowired
    public ProcessingOrderService(StockOrderService stockOrderService,
                                  TransactionService transactionService,
                                  CompanyQueries companyQueries) {
        this.stockOrderService = stockOrderService;
        this.transactionService = transactionService;
        this.companyQueries = companyQueries;
    }

    public ApiProcessingOrder getProcessingOrder(Long id, CustomUserDetails authUser, Language language) throws ApiException {

        ProcessingOrder processingOrder = fetchEntity(id, ProcessingOrder.class);

        // Check if request user is enrolled in one of the connected companies with the processing order owner company
        PermissionsUtil.checkUserIfConnectedWithProducts(
                companyQueries.fetchCompanyProducts(processingOrder.getProcessingAction().getCompany().getId()),
                authUser);

        return ProcessingOrderMapper.toApiProcessingOrder(processingOrder, language);
    }

    /**
     * This is temporary method so that everything else stays backward compatible.
     * @param apiProcessingOrder - Processing order request
     * @param user - ID of the user that has requested this action
     * @return Status
     * @throws ApiException - You know... when something goes wrong it's nice to have a feedback
     */
    @Transactional
    public ApiBaseEntity createOrUpdateProcessingOrder(ApiProcessingOrder apiProcessingOrder, CustomUserDetails user, Language language) throws ApiException {

        ProcessingOrder entity = fetchEntityOrElse(apiProcessingOrder.getId(), ProcessingOrder.class, new ProcessingOrder());

        // Custom exceptions for required fields
        if (apiProcessingOrder.getProcessingAction() == null ||
                apiProcessingOrder.getProcessingAction().getId() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Processing action ID must be provided!");
        }

        if (apiProcessingOrder.getTargetStockOrders() == null || apiProcessingOrder.getTargetStockOrders().isEmpty()) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "At least one target StockOrder has to be provided!");
        }

        ProcessingAction processingAction = fetchEntity(apiProcessingOrder.getProcessingAction().getId(), ProcessingAction.class);

        entity.setProcessingAction(processingAction);
        entity.setInitiatorUserId(apiProcessingOrder.getInitiatorUserId());
        entity.setProcessingDate(apiProcessingOrder.getProcessingDate() != null
                ? apiProcessingOrder.getProcessingDate()
                : LocalDate.now());

        /*
         * --- VALIDATION ---
         */

        // Various validations based on ProcessingActionType
        switch (processingAction.getType()) {

            // SHIPMENT is actually QUOTE
            case SHIPMENT:
                return createOrUpdateQuoteOrder(entity, apiProcessingOrder, user, language);

            case PROCESSING:
            case FINAL_PROCESSING:
            case GENERATE_QR_CODE:

                if (apiProcessingOrder.getInputTransactions() == null || apiProcessingOrder.getInputTransactions().isEmpty()) {
                    throw new ApiException(ApiStatus.INVALID_REQUEST, "At least one input Transaction has to be provided!");
                }

                // Validate that there is no transaction that is referencing current ProcessingOrder (via targetStockOrder)
                // That kind of transaction cannot be part of the same processing
                if (entity.getId() != null) {
                    for (ApiTransaction apiTransaction : apiProcessingOrder.getInputTransactions()) {

                        if (apiTransaction.getId() != null
                                && apiTransaction.getTargetStockOrder() != null
                                && entity.getId().equals(apiTransaction.getTargetStockOrder().getId()))

                            throw new ApiException(ApiStatus.VALIDATION_ERROR, "Transaction with ID '"
                                    + apiTransaction.getId()
                                    + "' cannot be used, because it has been already used in this processing step");
                    }
                }

                // In case we have 'GENERATE_QR_CODE', only one target stock order is permitted
                if (processingAction.getType() == ProcessingActionType.GENERATE_QR_CODE) {
                    if (apiProcessingOrder.getTargetStockOrders().size() > 1) {
                        throw new ApiException(ApiStatus.VALIDATION_ERROR,
                                "Only one target Stock order is permitted when ProcessingActionType is 'Generate QR code'");
                    }
                }

                break;

            case TRANSFER:

                if (apiProcessingOrder.getInputTransactions() == null || apiProcessingOrder.getInputTransactions().isEmpty()) {
                    throw new ApiException(ApiStatus.INVALID_REQUEST, "At least one input Transaction has to be provided!");
                }

                // When processing action is of type TRANSFER, source StockOrders (contained within each input Transaction)
                // will be mapped to target StockOrders, so their size should be the same.
                if (apiProcessingOrder.getInputTransactions().size() != apiProcessingOrder.getTargetStockOrders().size())
                    throw new ApiException(ApiStatus.INVALID_REQUEST, "When ProcessingActionType is 'TRANSFER', " +
                            "size of 'inputTransactions' has to match the size of 'targetStockOrders'!");

                // Source StockOrders from inputTransactions must match with targetStockOrders.
                // Target StockOrders represent new/modified source StockOrder.
                for (int i = 0; i < apiProcessingOrder.getInputTransactions().size(); i++) {

                    ApiStockOrder targetStockOrder = apiProcessingOrder.getTargetStockOrders().get(i);
                    ApiStockOrder sourceStockOrder = apiProcessingOrder.getInputTransactions().get(i).getSourceStockOrder();

                    // Each input Transaction must contain a source StockOrder
                    if (sourceStockOrder == null || sourceStockOrder.getId() == null)
                        throw new ApiException(ApiStatus.VALIDATION_ERROR,
                                "Each input Transaction must contain a valid source StockOrder.");

                    // If target StockOrder contains ID, then there must be matching source StockOrder
                    if (targetStockOrder.getId() != null && !targetStockOrder.getId().equals(sourceStockOrder.getId()))
                        throw new ApiException(ApiStatus.VALIDATION_ERROR,
                                "For each existing target StockOrder there has to be a source StockOrder with the same ID.");
                }
                break;

            default:
                throw new ApiException(ApiStatus.ERROR, "Selected processing action is missing a ProcessingActionType!");
        }

        // Validate transactions. They should all contain:
        // - Company ID
        // - Source StockOrder ID
        // - TransactionStatus
        boolean areTransactionsValid = apiProcessingOrder.getInputTransactions()
                .stream()
                .allMatch(it -> it.getCompany() != null
                        && it.getCompany().getId() != null
                        && it.getSourceStockOrder() != null
                        && it.getSourceStockOrder().getId() != null
                        && it.getStatus() != null
                );

        if (!areTransactionsValid) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR, "At least one of the provided transactions is not valid!");
        }

        // Delete target StockOrder and input Transactions that are not present in the request
        // (applies only for existing ProcessingOrders)
        if (entity.getId() != null) {

            // Remove transactions that are not present in request
            List<Transaction> transactionsToBeDeleted = entity.getInputTransactions()
                    .stream()
                    .filter(transaction -> apiProcessingOrder.getInputTransactions()
                            .stream()
                            .noneMatch(apiTransaction -> transaction.getId().equals(apiTransaction.getId())))
                    .collect(Collectors.toList());

            for (Transaction t : transactionsToBeDeleted) {
                transactionService.deleteTransaction(t.getId(), user, language);
            }

            // Find target StockOrders that are not present in request
            List<StockOrder> targetStockOrdersToBeDeleted = entity.getTargetStockOrders()
                    .stream()
                    .filter(targetStockOrder -> apiProcessingOrder.getTargetStockOrders()
                            .stream()
                            .noneMatch(apiTargetStockOrder -> targetStockOrder.getId().equals(apiTargetStockOrder.getId())))
                    .collect(Collectors.toList());

            // Before deleting, verify that deletion is possible
            for (StockOrder targetStockOrder : targetStockOrdersToBeDeleted) {

                // Used quantity cannot be negative: "fulfilledQuantity - availableQuantity >= 0"
                if (targetStockOrder.getTotalQuantity().subtract(targetStockOrder.getAvailableQuantity())
                        .compareTo(BigDecimal.ZERO) < 0)
                    throw new ApiException(ApiStatus.VALIDATION_ERROR, "Target StockOrder with ID '"
                            + targetStockOrder.getId() + "' cannot be deleted!");
            }

            entity.getTargetStockOrders().removeAll(targetStockOrdersToBeDeleted);
        }

        // Verify that existing target StockOrders (in other words those with ID) can be updated
        for (StockOrder targetStockOrder : entity.getTargetStockOrders()) {
            ApiStockOrder apiTargetStockOrder = apiProcessingOrder.getTargetStockOrders()
                    .stream()
                    .filter(apiTSO -> targetStockOrder.getId().equals(apiTSO.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ApiStatus.ERROR,
                            "Inappropriate deletion of target StockOrders that are not present in request!"));

            // Total quantity - used quantity >= 0
            if (apiTargetStockOrder.getTotalQuantity().subtract(targetStockOrder.getTotalQuantity())
                    .compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal usedQuantity = apiTargetStockOrder.getFulfilledQuantity()
                        .subtract(apiTargetStockOrder.getAvailableQuantity());
                if (targetStockOrder.getTotalQuantity().compareTo(usedQuantity) < 0)
                    throw new ApiException(ApiStatus.VALIDATION_ERROR, "Cannot reduce total quantity below already used quantity.");
            }
        }

        Boolean isProcessing = processingAction.getType() != ProcessingActionType.TRANSFER;

        // Get the first input Transaction with generated QR code tag (if present)
        String presentQrCodeTag = findFirstQRCodeTagTransaction(apiProcessingOrder.getInputTransactions());
        FinalProduct qrCodeFinalProduct = null;

        // Create new or update existing input Transactions and validate conditions if there is an input Transaction with generated QR code tag
        for (int i = 0; i < apiProcessingOrder.getInputTransactions().size(); i++) {

            ApiTransaction apiTransaction = apiProcessingOrder.getInputTransactions().get(i);

            ApiBaseEntity insertedApiTransaction = transactionService.createOrUpdateTransaction(apiTransaction, isProcessing);
            Transaction insertedTransaction = fetchEntity(insertedApiTransaction.getId(), Transaction.class);
            insertedTransaction.setTargetProcessingOrder(entity);

            // If we have 'GENERATE_QR_CODE' verify that there no input transaction with generated
            // QR code tag (only transactions without QR code tag are permitted)
            if (processingAction.getType() == ProcessingActionType.GENERATE_QR_CODE &&
                    insertedTransaction.getSourceStockOrder().getQrCodeTag() != null) {
                throw new ApiException(ApiStatus.VALIDATION_ERROR,
                        "Generate QR code action cannot contain input transactions that have already generated QR code tag.");
            }

            // If we have input Transaction which has QR code tag generated, check that we have consistency
            // between all the input Transactions (all shall have the same QR code tag)
            qrCodeFinalProduct = validateQRCodeTag(presentQrCodeTag, insertedTransaction);

            entity.getInputTransactions().remove(insertedTransaction);
            entity.getInputTransactions().add(insertedTransaction);

            // Update source StockOrder
            if (apiTransaction.getId() == null) {
                stockOrderService.calculateQuantities(
                        apiTransaction.getSourceStockOrder(),
                        fetchEntity(apiTransaction.getSourceStockOrder().getId(), StockOrder.class),
                        entity,
                        insertedTransaction.getId()
                );
            }

            // Set targetStockOrders for TRANSFER
            if (processingAction.getType() == ProcessingActionType.TRANSFER) {

                Long targetStockOrderId = stockOrderService.createOrUpdateStockOrder(apiProcessingOrder.getTargetStockOrders().get(i), user, entity).getId();
                StockOrder targetStockOrder = fetchEntity(targetStockOrderId, StockOrder.class);
                targetStockOrder.setProcessingOrder(entity);

                // Transfer the QR code tag (if present) to the target Stock order
                targetStockOrder.setQrCodeTag(presentQrCodeTag);
                targetStockOrder.setQrCodeTagFinalProduct(qrCodeFinalProduct);

                entity.getTargetStockOrders().add(targetStockOrder);
            }
        }

        // Create new or update existing target stock order for PROCESSING
        if (processingAction.getType() != ProcessingActionType.TRANSFER) {

            for (ApiStockOrder apiTargetStockOrder: apiProcessingOrder.getTargetStockOrders()) {

                Long insertedTargetStockOrderId = stockOrderService.createOrUpdateStockOrder(apiTargetStockOrder, user, entity).getId();
                StockOrder targetStockOrder = fetchEntity(insertedTargetStockOrderId, StockOrder.class);
                targetStockOrder.setProcessingOrder(entity);

                // Transfer the QR code tag (if present) to the target Stock order
                if (presentQrCodeTag != null) {
                    targetStockOrder.setQrCodeTag(presentQrCodeTag);
                    targetStockOrder.setQrCodeTagFinalProduct(qrCodeFinalProduct);
                }

                entity.getTargetStockOrders().add(targetStockOrder);
            }
        }

        if (entity.getId() == null) {
            em.persist(entity);
        }

        return new ApiBaseEntity(entity);
    }

    private ApiBaseEntity createOrUpdateQuoteOrder(ProcessingOrder entity, ApiProcessingOrder apiProcessingOrder, CustomUserDetails user, Language language) throws ApiException {

        // Validate that there is target Stock order provided
        if (apiProcessingOrder.getTargetStockOrders().size() != 1) {
            throw new ApiException(ApiStatus.INVALID_REQUEST,
                    "One target StockOrder should be present in the request.");
        }

        // Get the target Quote order
        ApiStockOrder apiQuoteStockOrder = apiProcessingOrder.getTargetStockOrders().get(0);

        // Validate that order ID is not provided if we have Product order present
        if (apiQuoteStockOrder.getProductOrder() != null && StringUtils.isNotBlank(apiQuoteStockOrder.getOrderId())) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Order ID cannot be provided when there is a Product ID present");
        }

        // Remove transactions that are not present in request
        if (entity.getId() != null) {
            List<Transaction> transactionsToBeDeleted = entity.getInputTransactions()
                    .stream()
                    .filter(transaction -> apiProcessingOrder.getInputTransactions()
                            .stream()
                            .noneMatch(apiTransaction -> transaction.getId().equals(apiTransaction.getId())))
                    .collect(Collectors.toList());

            entity.getInputTransactions().removeAll(transactionsToBeDeleted);
            for (Transaction t: transactionsToBeDeleted) {
                transactionService.deleteTransaction(t.getId(), user, language);
            }
        }

        // Get the first input Transaction with generated QR code tag (if present)
        String presentQrCodeTag = findFirstQRCodeTagTransaction(apiProcessingOrder.getInputTransactions());
        FinalProduct qrCodeFinalProduct = null;

        // Create new or update existing input Transactions
        for (int i = 0; i < apiProcessingOrder.getInputTransactions().size(); i++) {

            ApiTransaction apiTransaction = apiProcessingOrder.getInputTransactions().get(i);
            Long insertedTransactionId = transactionService.createOrUpdateTransaction(apiTransaction, false).getId();
            Transaction insertedTransaction = fetchEntity(insertedTransactionId, Transaction.class);

            // If we have input Transaction which has QR code tag generated, check that we have consistency
            // between all the input Transactions (all shall have the same QR code tag)
            qrCodeFinalProduct = validateQRCodeTag(presentQrCodeTag, insertedTransaction);

            entity.getInputTransactions().remove(insertedTransaction);
            entity.getInputTransactions().add(insertedTransaction);
            insertedTransaction.setTargetProcessingOrder(entity);

            // Update source StockOrder
            if (apiTransaction.getId() == null) {
                stockOrderService.calculateQuantities(
                        apiTransaction.getSourceStockOrder(),
                        fetchEntity(apiTransaction.getSourceStockOrder().getId(), StockOrder.class),
                        entity,
                        insertedTransaction.getId()
                );
            }
        }

        if (apiQuoteStockOrder.getOrderType() != OrderType.GENERAL_ORDER) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Order must be of orderType " +  OrderType.GENERAL_ORDER
                    + " to allow input transactions");
        }

        if (apiQuoteStockOrder.getId() == null) {
            if (apiQuoteStockOrder.getFulfilledQuantity() != null && apiQuoteStockOrder.getFulfilledQuantity().compareTo(BigDecimal.ZERO) != 0) {
                throw new ApiException(ApiStatus.INVALID_REQUEST, "Fulfilled quantity must be 0");
            }
        }

        Long insertedStockOrderId = stockOrderService.createOrUpdateQuoteStockOrder(apiQuoteStockOrder, user, entity).getId();
        StockOrder quoteStockOrder = fetchEntity(insertedStockOrderId, StockOrder.class);

        // Set the back reference for the Processing order
        quoteStockOrder.setProcessingOrder(entity);

        // Set the QR code tag data
        quoteStockOrder.setQrCodeTag(presentQrCodeTag);
        quoteStockOrder.setQrCodeTagFinalProduct(qrCodeFinalProduct);

        // Set the user entered Order ID
        quoteStockOrder.setOrderId(apiQuoteStockOrder.getOrderId());

        entity.setTargetStockOrders(Set.of(quoteStockOrder));

        if (entity.getId() == null) {
            em.persist(entity);
        }

        return new ApiBaseEntity(entity);
    }

    @Transactional
    public void deleteProcessingOrder(Long id, CustomUserDetails user) throws ApiException {

        ProcessingOrder entity = fetchEntity(id, ProcessingOrder.class);

        // Check if req. user is enrolled in owner company for this processing order
        PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(entity.getProcessingAction().getCompany().getUsers().stream().toList(), user);

        assertProcessingOutputsCanBeDeleted(entity);

        // Removing a processing order also removes its generated orders. Restore the
        // quantities reserved by each input transaction before deleting that transaction.
        for (Transaction t: Set.copyOf(entity.getInputTransactions())) {
            transactionService.deleteTransactionForProcessingOrder(t, user);
        }

        for (StockOrder so: Set.copyOf(entity.getTargetStockOrders())) {
            stockOrderService.deleteProcessingOutputStockOrder(so);
        }

        em.remove(entity);
    }

    private void assertProcessingOutputsCanBeDeleted(ProcessingOrder processingOrder) throws ApiException {
        for (StockOrder output : processingOrder.getTargetStockOrders()) {
            Long otherTransactions = em.createQuery(
                            "SELECT COUNT(t) FROM Transaction t WHERE t.sourceStockOrder.id = :stockOrderId " +
                                    "AND (t.targetProcessingOrder IS NULL OR t.targetProcessingOrder.id <> :processingOrderId)",
                            Long.class)
                    .setParameter("stockOrderId", output.getId())
                    .setParameter("processingOrderId", processingOrder.getId())
                    .getSingleResult();
            if (otherTransactions > 0) {
                throw new ApiException(ApiStatus.VALIDATION_ERROR,
                        "Cannot delete a processing order whose output has been used by another transaction");
            }

            Long payments = em.createQuery("SELECT COUNT(p) FROM Payment p WHERE p.stockOrder.id = :stockOrderId", Long.class)
                    .setParameter("stockOrderId", output.getId())
                    .getSingleResult();
            if (payments > 0) {
                throw new ApiException(ApiStatus.VALIDATION_ERROR,
                        "Cannot delete a processing order whose output has payments");
            }
        }
    }

    /**
     * Finds the first input Transaction which has QR code tag generated.
     *
     * @param inputTransactions List of input Transactions from which to find the first one with QR code tag.
     *
     * @return The QR code tag or null if there is no input Transaction with generated one.
     */
    private String findFirstQRCodeTagTransaction(List<ApiTransaction> inputTransactions) {

        return inputTransactions
                .stream()
                .filter(apiTransaction -> apiTransaction.getSourceStockOrder().getQrCodeTag() != null)
                .map(apiTransaction -> apiTransaction.getSourceStockOrder().getQrCodeTag())
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if the provided QR code tag is the same in the provided input Transaction.
     * When we have multiple input transactions and present QR code tag, all the present transaction should have the same QR code tag.
     *
     * @param qrCodeTag The present QR code tag.
     * @param transaction The input transaction to validate.
     *
     * @return The final product for which the QR code tag is generated.
     *
     * @throws ApiException If the input Transaction validation fails.
     */
    private FinalProduct validateQRCodeTag(String qrCodeTag, Transaction transaction) throws ApiException {

        if (qrCodeTag != null) {
            if (!qrCodeTag.equals(transaction.getSourceStockOrder().getQrCodeTag())) {
                throw new ApiException(ApiStatus.VALIDATION_ERROR, "There are input transactions present with different QR code tags.");
            } else {
                return transaction.getSourceStockOrder().getQrCodeTagFinalProduct();
            }
        }

        return null;
    }

    private <E> E fetchEntity(Long id, Class<E> entityClass) throws ApiException {

        E entity = Queries.get(em, entityClass, id);
        if (entity == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST,
                    entityClass.getSimpleName() + " entity with ID '" + id  + "' not found.");
        }
        return entity;
    }

    private <E> E fetchEntityOrElse(Long id, Class<E> entityClass, E defaultValue) {
        E entity = Queries.get(em, entityClass, id);
        return entity == null ? defaultValue : entity;
    }

}
