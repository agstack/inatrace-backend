package com.abelium.inatrace.db.entities.stockorder;

import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.common.ActivityProof;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Connecting entity that connects stock order with particular activity proof.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Entity
public class StockOrderActivityProof extends BaseEntity {

	@ManyToOne
	private StockOrder stockOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "activityProof_id", nullable = true)
	private ActivityProof activityProof;

	public StockOrder getStockOrder() {
		return stockOrder;
	}

	public void setStockOrder(StockOrder stockOrder) {
		this.stockOrder = stockOrder;
	}

	public ActivityProof getActivityProof() {
		return activityProof;
	}

	public void setActivityProof(ActivityProof activityProof) {
		this.activityProof = activityProof;
	}

}
