package com.abelium.inatrace.components.processingorder.mappers;

import com.abelium.inatrace.components.processingaction.ProcessingActionMapper;
import com.abelium.inatrace.components.processingorder.api.ApiProcessingOrder;
import com.abelium.inatrace.components.stockorder.mappers.StockOrderMapper;
import com.abelium.inatrace.components.transaction.mappers.TransactionMapper;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.types.Language;

import java.util.stream.Collectors;

public class ProcessingOrderMapper {

    public static ApiProcessingOrder toApiProcessingOrderBase(ProcessingOrder entity) {

        if (entity == null) {
            return null;
        }

        ApiProcessingOrder apiProcessingOrder = new ApiProcessingOrder();
        apiProcessingOrder.setId(entity.getId());
        apiProcessingOrder.setInitiatorUserId(entity.getInitiatorUserId());

        return apiProcessingOrder;
    }

    public static ApiProcessingOrder toApiProcessingOrder(ProcessingOrder entity, Long userId, Language language) {

        ApiProcessingOrder apiProcessingOrder = toApiProcessingOrderBase(entity);

        if (apiProcessingOrder == null) {
            return null;
        }

        apiProcessingOrder.setCreationTimestamp(entity.getCreationTimestamp());
        apiProcessingOrder.setProcessingDate(entity.getProcessingDate());
        apiProcessingOrder.setProcessingAction(ProcessingActionMapper.toApiProcessingAction(entity.getProcessingAction(), language));
        apiProcessingOrder.setInputTransactions(entity.getInputTransactions().stream().map(transaction -> TransactionMapper.toApiTransactionBase(transaction, language)).collect(Collectors.toList()));
        apiProcessingOrder.setTargetStockOrders(entity.getTargetStockOrders().stream().map(so -> StockOrderMapper.toApiStockOrder(so, userId, language)).collect(Collectors.toList()));

        return apiProcessingOrder;
    }

    public static ApiProcessingOrder toApiProcessingOrderHistory(ProcessingOrder entity, Language language) {

        ApiProcessingOrder apiProcessingOrder = toApiProcessingOrderBase(entity);

        if (apiProcessingOrder == null) {
            return null;
        }

        apiProcessingOrder.setCreationTimestamp(entity.getCreationTimestamp());
        apiProcessingOrder.setProcessingDate(entity.getProcessingDate());
        apiProcessingOrder.setProcessingAction(ProcessingActionMapper.toApiProcessingActionHistory(entity.getProcessingAction(), language));

        return apiProcessingOrder;
    }

}
