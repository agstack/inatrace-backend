package com.abelium.inatrace.components.transaction;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.stockorder.StockOrderService;
import com.abelium.inatrace.components.stockorder.mappers.StockOrderMapper;
import com.abelium.inatrace.components.transaction.api.ApiTransaction;
import com.abelium.inatrace.components.transaction.mappers.TransactionMapper;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.TransactionStatus;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProcessingActionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.stream.Collectors;

@Lazy
@Service
public class TransactionService extends BaseService {

    private final StockOrderService stockOrderService;

    @Autowired
    public TransactionService(StockOrderService stockOrderService) {
        this.stockOrderService = stockOrderService;
    }

    /**
     * Return a ist of input transactions for provided stock order ID. Used in Quote orders.
     * @param stockOrderId Stock order ID.
     * @return List of transactions.
     */
    public ApiPaginatedList<ApiTransaction> getStockOrderInputTransactions(Long stockOrderId, CustomUserDetails user, Language language) throws ApiException {

        // Fetch the stock order
        StockOrder stockOrder = stockOrderService.fetchEntity(stockOrderId, StockOrder.class);

        // Validate that stock order has processing order (if not processing order, there are no transactions)
        ProcessingOrder processingOrder = stockOrder.getProcessingOrder();
        if (processingOrder == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "The Stock order with the provided ID has no Processing order");
        }

        // Check that the request user is enrolled in the stock order owner company
        PermissionsUtil.checkUserIfCompanyEnrolled(stockOrder.getCompany().getUsers().stream().toList(), user);

        return new ApiPaginatedList<>(
                processingOrder.getInputTransactions().stream().map(t -> TransactionMapper.toApiTransactionBase(t, language))
                        .collect(Collectors.toList()), processingOrder.getInputTransactions().size());
    }

    /**
     * Creates new or updates existing Transaction.
     *
     * @param apiTransaction - API transaction request
     * @param isProcessing - Is transaction part of ProcessingAction with type PROCESSING?
     * @return Inserted transactions
     * @throws ApiException - When something goes wrong..
     */
    @Transactional
    public ApiBaseEntity createOrUpdateTransaction(ApiTransaction apiTransaction,
                                                   Boolean isProcessing) throws ApiException {

        Transaction transaction = apiTransaction.getId() != null
                ? fetchEntity(apiTransaction.getId(), Transaction.class)
                : new Transaction();

        transaction.setCompany(fetchEntity(apiTransaction.getCompany().getId(), Company.class));
        transaction.setInitiationUserId(apiTransaction.getInitiationUserId());
        transaction.setStatus(apiTransaction.getStatus());
        transaction.setIsProcessing(isProcessing);
        transaction.setInputQuantity(apiTransaction.getInputQuantity());
        transaction.setOutputQuantity(apiTransaction.getOutputQuantity());
        transaction.setSourceStockOrder(fetchEntity(apiTransaction.getSourceStockOrder().getId(), StockOrder.class));
        transaction.setSourceFacility(transaction.getSourceStockOrder().getFacility());
        transaction.setInputMeasureUnitType(transaction.getSourceStockOrder().getMeasurementUnitType());
        transaction.setSemiProduct(transaction.getSourceStockOrder().getSemiProduct());
        transaction.setFinalProduct(transaction.getSourceStockOrder().getFinalProduct());

        if (transaction.getSemiProduct() == null && transaction.getFinalProduct() == null) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR, "Semi-product or Final prodcut is required.");
        }

        if (transaction.getId() == null)
            em.persist(transaction);

        return new ApiBaseEntity(transaction);
    }

    @Transactional
    public void deleteTransaction(Long id, CustomUserDetails user, Language language) throws ApiException {

        Transaction transaction = fetchEntity(id, Transaction.class);

        PermissionsUtil.checkUserIfCompanyEnrolled(transaction.getCompany().getUsers().stream().toList(), user);

        // Only PENDING transactions can be deleted within QUOTE order
        if (transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getTargetProcessingOrder() != null
                && transaction.getTargetProcessingOrder().getProcessingAction().getType() == ProcessingActionType.SHIPMENT) {

            throw new ApiException(ApiStatus.VALIDATION_ERROR, "Only PENDING transactions can be deleted.");
        }

        // rejectTransaction already restored a canceled transaction. Deleting it must
        // not return the source quantity a second time.
        if (!TransactionStatus.CANCELED.equals(transaction.getStatus())) {
            revertQuantities(transaction, user, language);
        }

        em.remove(transaction);
    }

    /**
     * Processing-order deletion is authorised by ProcessingOrderService. Revert the
     * reserved quantity without requiring the caller to be enrolled in every source
     * company connected to that processing order.
     */
    @Transactional
    public void deleteTransactionForProcessingOrder(Transaction transaction, CustomUserDetails user) throws ApiException {
        // rejectTransaction already restored a canceled transaction. Restoring it again
        // here would increase the source order's available quantity twice.
        if (!TransactionStatus.CANCELED.equals(transaction.getStatus())) {
            revertQuantities(transaction, user, Language.EN, false);
        }
        em.remove(transaction);
    }

    @Transactional
    public void approveTransaction(Long id, CustomUserDetails user, Language language) throws ApiException {

        Transaction transaction = fetchEntity(id, Transaction.class);

        if (transaction.getTargetProcessingOrder() == null || transaction.getTargetProcessingOrder().getTargetStockOrders().isEmpty()) {
            return;
        }

        // Approve transaction can ben called by quote order owner company (check that request user is enrolled in quote order owner company)
        PermissionsUtil.checkUserIfCompanyEnrolled(
                transaction.getTargetProcessingOrder().getTargetStockOrders().stream().toList().get(0).getCompany().getUsers().stream().toList(), user);

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR, "Only PENDING transactions can be approved.");
        }

        transaction.setStatus(TransactionStatus.EXECUTED);

        // Update quote order (to refresh quantities)
        ProcessingOrder processingOrder = transaction.getTargetProcessingOrder();
        StockOrder quoteStockOrder = processingOrder.getTargetStockOrders().stream().findFirst().orElse(null);
        if (quoteStockOrder != null) {
            quoteStockOrder.setAvailableQuantity(
                    quoteStockOrder.getAvailableQuantity().add(transaction.getInputQuantity()));
            stockOrderService.createOrUpdateStockOrder(
                    StockOrderMapper.toApiStockOrder(quoteStockOrder, user.getUserId(), language), user, processingOrder);
        }
    }

    @Transactional
    public void rejectTransaction(ApiTransaction apiTransaction, CustomUserDetails user, Language language) throws ApiException {

        Transaction transaction = fetchEntity(apiTransaction.getId(), Transaction.class);

        if (transaction.getTargetProcessingOrder() == null || transaction.getTargetProcessingOrder().getTargetStockOrders().isEmpty()) {
            return;
        }

        // Reject transaction can ben called by quote order owner company (check that request user is enrolled in quote order owner company)
        PermissionsUtil.checkUserIfCompanyEnrolled(
                transaction.getTargetProcessingOrder().getTargetStockOrders().stream().toList().get(0).getCompany().getUsers().stream().toList(), user);

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR, "Only PENDING transactions can be rejected.");
        }
        if (apiTransaction.getRejectComment() == null || apiTransaction.getRejectComment().isEmpty()) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Reject comment cannot be null.");
        }

        transaction.setStatus(TransactionStatus.CANCELED);
        transaction.setRejectComment(apiTransaction.getRejectComment());

        // Revert quantities but do NOT delete the transaction!
        revertQuantities(transaction, user, language, false);

        // Balance fulfilled quantity in quote stock order
        ProcessingOrder processingOrder = transaction.getTargetProcessingOrder();
        StockOrder quoteStockOrder = processingOrder.getTargetStockOrders().stream().findFirst().orElse(null);
        if (quoteStockOrder != null) {
            stockOrderService.createOrUpdateStockOrder(
                    StockOrderMapper.toApiStockOrder(quoteStockOrder, user.getUserId(), language), user, processingOrder);
        }
    }

    private void revertQuantities(Transaction transaction, CustomUserDetails user, Language language) throws ApiException {
        revertQuantities(transaction, user, language, true);
    }

    private void revertQuantities(Transaction transaction, CustomUserDetails user, Language language, boolean checkCompanyEnrolment) throws ApiException {

        StockOrder sourceStockOrder = transaction.getSourceStockOrder();

        // Reservation subtracts outputQuantity, so the reversal must use the same unit.
        if (sourceStockOrder != null) {
            sourceStockOrder.setAvailableQuantity(sourceStockOrder.getAvailableQuantity().add(transaction.getOutputQuantity()));
            stockOrderService.createOrUpdateStockOrder(
                    StockOrderMapper.toApiStockOrder(sourceStockOrder, user.getUserId(), language),
                    user,
                    null,
                    checkCompanyEnrolment
            );
        }
    }

    private <E> E fetchEntity(Long id, Class<E> entityClass) throws ApiException {

        E entity = Queries.get(em, entityClass, id);
        if (entity == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST,
                    entityClass.getSimpleName() + " entity with ID '" + id  + "' not found.");
        }
        return entity;
    }

}
