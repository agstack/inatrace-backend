package com.abelium.inatrace.db.entities.payment;

import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.common.ActivityProof;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class BulkPaymentActivityProof extends BaseEntity{

    @ManyToOne
    private BulkPayment bulkPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activityProof_id", nullable = true)
    private ActivityProof activityProof;

    public BulkPayment getBulkPayment() {
        return bulkPayment;
    }

    public void setBulkPayment(BulkPayment bulkPayment) {
        this.bulkPayment = bulkPayment;
    }

    public ActivityProof getActivityProof() {
        return activityProof;
    }

    public void setActivityProof(ActivityProof activityProof) {
        this.activityProof = activityProof;
    }
}
