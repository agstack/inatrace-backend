package com.abelium.inatrace.components.groupstockorder;

import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.facility.FacilityService;
import com.abelium.inatrace.components.groupstockorder.api.ApiGroupStockOrder;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.types.Language;
import jakarta.persistence.TypedQuery;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Lazy
@Service
public class GroupStockOrderService extends BaseService {

    private final FacilityService facilityService;

    @Autowired
    public GroupStockOrderService(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    public ApiPaginatedList<ApiGroupStockOrder> getGroupedStockOrderList(
            ApiPaginatedRequest request,
            GroupStockOrderQueryRequest queryRequest,
            Language language,
            CustomUserDetails user
    ) throws ApiException {

        PermissionsUtil.checkUserIfCompanyEnrolled(
                facilityService.fetchFacility(queryRequest.facilityId).getCompany().getUsers().stream().toList(), user);

        // SELECT query string for which columns to get from database and put them into DTO ApiGroupStockOrder
        StringBuilder queryString = new StringBuilder(
            "SELECT new com.abelium.inatrace.components.groupstockorder.api.ApiGroupStockOrder(" +
            "GROUP_CONCAT(SO.id), " +
            "SO.productionDate AS date, SO.internalLotNumber AS id, COUNT(SO.sacNumber) as noOfSacs, " +
            "SO.orderType, SPT.name, CONCAT(FP.name, ' (', P.name, ')'), " +
            "SUM(SO.totalQuantity), SUM(SO.fulfilledQuantity), SUM(SO.availableQuantity), " +
            "MUT.label, SO.deliveryTime AS deliveryTime, PO.updateTimestamp AS updateTimestamp, " +
            "SO.isAvailable " +
            ") FROM StockOrder SO " +
            "LEFT JOIN SO.processingOrder PO " +
            "LEFT JOIN SO.measurementUnitType MUT " +
            "LEFT JOIN SO.semiProduct SP " +
            "LEFT JOIN SO.finalProduct FP " +
            "LEFT JOIN FP.product P " +
            "LEFT JOIN SP.semiProductTranslations SPT "
        );

        // Build WHERE query string for filtering purposes
        StringBuilder whereClause = new StringBuilder();
        whereClause.append(" WHERE (SPT.language IS NULL OR SPT.language = :language)");
        if(queryRequest.facilityId != null) {
            whereClause.append(" AND SO.facility.id = :facilityId");
        }
        if(queryRequest.availableOnly != null && queryRequest.availableOnly) {
            whereClause.append(" AND SO.isAvailable = true");
        }
        if(queryRequest.isPurchaseOrderOnly != null && queryRequest.isPurchaseOrderOnly) {
            whereClause.append(" AND SO.isPurchaseOrder = true");
        }
        if(queryRequest.semiProducId != null) {
            whereClause.append(" AND SP.id = :semiProductId");
        }
        queryString.append(whereClause);

        // Add GROUP BY query string for grouping common columns
        queryString.append(
                " GROUP BY SO.productionDate, SO.internalLotNumber, SO.orderType, SPT.name, MUT.label, " +
                "SO.deliveryTime, PO.updateTimestamp, SO.isAvailable, FP.name, P.name "
        );

        // Add ORDER BY query string to sort on requested field and direction
        queryString.append("ORDER BY ");
        queryString.append(request.sortBy);
        queryString.append(" ");
        queryString.append(request.sort.toString());

        TypedQuery<ApiGroupStockOrder> query = em.createQuery(queryString.toString(), ApiGroupStockOrder.class);
        query.setParameter("language", language);
        if(queryRequest.facilityId != null) {
            query.setParameter("facilityId", queryRequest.facilityId);
        }
        if(queryRequest.semiProducId != null) {
            query.setParameter("semiProductId", queryRequest.semiProducId);
        }

        long count = query.getResultList().size();  // Get count of all entities for pagination
        return PaginationTools.createPaginatedResponse(
                null,
                () -> query.setFirstResult(request.offset).setMaxResults(request.limit).getResultList(),
                () -> count
        );
    }

}
