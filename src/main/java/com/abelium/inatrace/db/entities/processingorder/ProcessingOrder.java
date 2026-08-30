package com.abelium.inatrace.db.entities.processingorder;

import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
public class ProcessingOrder extends TimestampEntity {

    @Version
    private Long entityVersion;

    @Column
    private Long initiatorUserId;

    @Column
    private LocalDate processingDate;

    @ManyToOne
    private ProcessingAction processingAction;

    @OneToMany(mappedBy = "targetProcessingOrder", cascade = CascadeType.ALL)
    private Set<Transaction> inputTransactions;

    @OneToMany(mappedBy = "processingOrder",cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<StockOrder> targetStockOrders;

    public Long getInitiatorUserId() {
        return initiatorUserId;
    }

    public void setInitiatorUserId(Long initiatorUserId) {
        this.initiatorUserId = initiatorUserId;
    }

    public LocalDate getProcessingDate() {
        return processingDate;
    }

    public void setProcessingDate(LocalDate processingDate) {
        this.processingDate = processingDate;
    }

    public ProcessingAction getProcessingAction() {
        return processingAction;
    }

    public void setProcessingAction(ProcessingAction processingAction) {
        this.processingAction = processingAction;
    }

    public Set<Transaction> getInputTransactions() {
        if (inputTransactions == null)
            inputTransactions = new HashSet<>();
        return inputTransactions;
    }

    public void setInputTransactions(Set<Transaction> inputTransactions) {
        this.inputTransactions = inputTransactions;
    }

    public Set<StockOrder> getTargetStockOrders() {
        if (targetStockOrders == null)
            targetStockOrders = new HashSet<>();
        return targetStockOrders;
    }

    public void setTargetStockOrders(Set<StockOrder> targetStockOrders) {
        this.targetStockOrders = targetStockOrders;
    }
}
