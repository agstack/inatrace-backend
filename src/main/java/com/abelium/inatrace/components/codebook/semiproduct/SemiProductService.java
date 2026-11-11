package com.abelium.inatrace.components.codebook.semiproduct;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.measure_unit_type.MeasureUnitTypeService;
import com.abelium.inatrace.components.codebook.semiproduct.api.ApiSemiProduct;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.codebook.SemiProductTranslation;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for SemiProduct entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Lazy
@Service
public class SemiProductService extends BaseService {

	/**
	 * Every entity attribute that references this entry (the foreign keys to its table, except its
	 * translations). A delete is refused while any of them does; CodebookDeletionTest checks this
	 * list against the database schema.
	 */
	public static final List<String> IN_USE_REFERENCES = List.of(
			"ValueChainSemiProduct.semiProduct",
			"FacilitySemiProduct.semiProduct",
			"ProcessingAction.inputSemiProduct",
			"ProcessingActionOutputSemiProduct.outputSemiProduct",
			"StockOrder.semiProduct",
			"Transaction.semiProduct");

	private final MeasureUnitTypeService measureUnitTypeService;

	@Autowired
	public SemiProductService(MeasureUnitTypeService measureUnitTypeService) {
		this.measureUnitTypeService = measureUnitTypeService;
	}

	public ApiPaginatedList<ApiSemiProduct> getSemiProductList(ApiPaginatedRequest request, Language language) {

		return PaginationTools.createPaginatedResponse(em, request, () -> semiProductQueryObject(request),
				semiProduct -> SemiProductMapper.toApiSemiProductDetail(semiProduct, ApiSemiProduct.class, language));
	}

	private SemiProduct semiProductQueryObject(ApiPaginatedRequest request) {

		SemiProduct semiProductProxy = Torpedo.from(SemiProduct.class);

		switch (request.sortBy) {
			case "name":
				QueryTools.orderBy(request.sort, semiProductProxy.getName());
				break;
			case "description":
				QueryTools.orderBy(request.sort, semiProductProxy.getDescription());
				break;
			case "measurementUnitType":
				QueryTools.orderBy(request.sort, semiProductProxy.getMeasurementUnitType().getLabel());
				break;
			default:
				QueryTools.orderBy(request.sort, semiProductProxy.getId());
		}

		return semiProductProxy;
	}

	public ApiSemiProduct getSemiProduct(Long id, Language language) throws ApiException {

		return SemiProductMapper.toApiSemiProduct(fetchSemiProduct(id), ApiSemiProduct.class, language);
	}

	public ApiSemiProduct getSemiProductDetails(Long id, Language language) throws ApiException {

		return SemiProductMapper.toApiSemiProductDetail(fetchSemiProduct(id), ApiSemiProduct.class, language);
	}

	@Transactional
	public ApiBaseEntity createOrUpdateSemiProduct(CustomUserDetails authUser, ApiSemiProduct apiSemiProduct) throws ApiException {

		SemiProduct semiProduct;

		if (apiSemiProduct.getId() != null) {

			// Editing is not permitted for Regional admin
			if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
				throw new ApiException(ApiStatus.UNAUTHORIZED, "Regional admin not authorized!");
			}

			semiProduct = fetchSemiProduct(apiSemiProduct.getId());
		} else {
			semiProduct = new SemiProduct();
		}

		semiProduct.setName(apiSemiProduct.getName());
		semiProduct.setDescription(apiSemiProduct.getDescription());
		semiProduct.setSKU(apiSemiProduct.getSKU());
		semiProduct.setBuyable(apiSemiProduct.getBuyable());
		semiProduct.setSKUEndCustomer(apiSemiProduct.getSKUEndCustomer());

		if (apiSemiProduct.getMeasurementUnitType() != null &&
				apiSemiProduct.getMeasurementUnitType().getId() != null &&
				(semiProduct.getMeasurementUnitType() == null ||
						!Objects.equals(semiProduct.getMeasurementUnitType().getId(),
								apiSemiProduct.getMeasurementUnitType().getId()))) {
			MeasureUnitType measureUnitType = measureUnitTypeService.fetchMeasureUnitType(
					apiSemiProduct.getMeasurementUnitType().getId());
			semiProduct.setMeasurementUnitType(measureUnitType);
		} else if (apiSemiProduct.getMeasurementUnitType() == null || apiSemiProduct.getMeasurementUnitType().getId() == null) {
			semiProduct.setMeasurementUnitType(null);
		}

		if (semiProduct.getId() == null) {
			em.persist(semiProduct);
		}

		apiSemiProduct.getTranslations().stream().filter(semiProductTranslation -> semiProductTranslation != null &&
					Language.EN.equals(semiProductTranslation.getLanguage()) &&
					semiProductTranslation.getName() != null &&
					semiProductTranslation.getDescription() != null)
				.findFirst()
				.orElseThrow(() -> new ApiException(ApiStatus.INVALID_REQUEST, "English translation is required!"));

		semiProduct.getSemiProductTranslations().removeIf(semiProductTranslation -> apiSemiProduct.getTranslations().stream().noneMatch(apiSemiProductTranslation -> semiProductTranslation.getLanguage().equals(apiSemiProductTranslation.getLanguage())));

		apiSemiProduct.getTranslations().forEach(apiSemiProductTranslation -> {
			SemiProductTranslation translation = semiProduct.getSemiProductTranslations().stream().filter(semiProductTranslation -> semiProductTranslation.getLanguage().equals(apiSemiProductTranslation.getLanguage())).findFirst().orElse(new SemiProductTranslation());
			translation.setName(apiSemiProductTranslation.getName());
			translation.setDescription(apiSemiProductTranslation.getDescription());
			translation.setLanguage(apiSemiProductTranslation.getLanguage());
			translation.setSemiProduct(semiProduct);
			semiProduct.getSemiProductTranslations().add(translation);
		});

		return new ApiBaseEntity(semiProduct);
	}

	// The in-use answer may come after the flush (Queries.removeUnreferenced): roll back the whole delete then.
	@Transactional(rollbackOn = ApiException.class)
	public void deleteSemiProduct(Long id) throws ApiException {

		SemiProduct semiProduct = fetchSemiProduct(id);
		if (Queries.isReferenced(em, id, IN_USE_REFERENCES)) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "Cannot delete a semi-product that is in use");
		}
		Queries.removeUnreferenced(em, semiProduct, "Cannot delete a semi-product that is in use");
	}

	public SemiProduct fetchSemiProduct(Long id) throws ApiException {

		SemiProduct semiProduct = Queries.get(em, SemiProduct.class, id);
		if (semiProduct == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid semi-product ID");
		}

		return semiProduct;
	}

	public ApiPaginatedList<ApiSemiProduct> getSemiProductListByValueChains(ApiPaginatedRequest request,
	                                                                        List<Long> valueChainIds,
	                                                                        Language language) {

		TypedQuery<SemiProduct> semiProductsQuery = em.createNamedQuery(
						"SemiProduct.getSemiProductsForValueChainIds", SemiProduct.class)
				.setParameter("valueChainIds", valueChainIds)
				.setFirstResult(request.getOffset())
				.setMaxResults(request.getLimit());

		List<SemiProduct> semiProducts = semiProductsQuery.getResultList();

		Long count = em.createNamedQuery("SemiProduct.countSemiProductsForValueChainIds", Long.class)
				.setParameter("valueChainIds", valueChainIds)
				.getSingleResult();

		return new ApiPaginatedList<>(
				semiProducts
						.stream()
						.map(processingEvidenceField -> SemiProductMapper.toApiSemiProduct(processingEvidenceField, ApiSemiProduct.class, language))
						.collect(Collectors.toList()), count);

	}
}
