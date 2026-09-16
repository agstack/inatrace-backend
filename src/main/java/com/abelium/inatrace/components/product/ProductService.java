package com.abelium.inatrace.components.product;

import com.abelium.inatrace.api.*;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.analytics.AnalyticsEngine;
import com.abelium.inatrace.components.analytics.RequestLogService;
import com.abelium.inatrace.components.codebook.measure_unit_type.MeasureUnitTypeService;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.common.StorageKeyCache;
import com.abelium.inatrace.components.company.CompanyApiTools;
import com.abelium.inatrace.components.product.api.*;
import com.abelium.inatrace.components.product.types.ProductLabelAction;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyDocument;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.product.*;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.tools.TorpedoProjector;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.ProductLabelStatus;
import com.abelium.inatrace.types.RequestLogType;
import com.abelium.inatrace.types.UserRole;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Lazy
@Service
public class ProductService extends BaseService {
	
	@Autowired
	private ProductApiTools productApiTools;

	@Autowired
	private ProductQueries productQueries;
	
	@Autowired
	private RequestLogService requestLogEngine;
	
	@Autowired
	private AnalyticsEngine analyticsEngine;

	@Autowired
	private MeasureUnitTypeService measureUnitTypeService;
	
    private Product productListQueryObject(ApiListProductsRequest request) {
        Product pProxy = Torpedo.from(Product.class);
        
        OnGoingLogicalCondition condition = Torpedo.condition();
        if (StringUtils.isNotBlank(request.name)) {
            condition = condition.and(pProxy.getName()).like().any(request.name);
        }
        Torpedo.where(condition);
        switch (request.sortBy) {
	        case "name": QueryTools.orderBy(request.sort, pProxy.getName()); break;
	        default: QueryTools.orderBy(request.sort, pProxy.getId());
        }
        return pProxy;
    }	

    @Transactional
	public ApiPaginatedList<ApiProductListResponse> listProducts(Long userId, ApiListProductsRequest request) {
    	return PaginationTools.createPaginatedResponse(em, request, () -> productListQueryObject(request), 
    			p -> ProductApiTools.toApiProductListResponse(userId, p)); 
	}
    
    private TorpedoProjector<Product, ApiProductListResponse> userProductListQueryObject1(Long userId, ApiListProductsRequest request) {

        CompanyUser cuProxy = Torpedo.from(CompanyUser.class);
        Torpedo.where(cuProxy.getUser().getId()).eq(userId);
        List<Long> companyIds = Torpedo.select(cuProxy.getCompany().getId()).list(em);
        
        ProductCompany pcProxy = Torpedo.from(ProductCompany.class);
        Torpedo.where(pcProxy.getCompany().getId()).in(companyIds);
        List<Long> productIds = Torpedo.select(pcProxy.getProduct().getId()).list(em);
        
        Product pProxy = Torpedo.from(Product.class);
        OnGoingLogicalCondition condition = Torpedo.condition(pProxy.getCompany().getId()).in(companyIds).or(pProxy.getId()).in(productIds);
        if (StringUtils.isNotBlank(request.name)) {
            condition = condition.and(pProxy.getName()).like().any(request.name);
        }
        Document dProxy = Torpedo.leftJoin(pProxy.getPhoto());        
        Torpedo.where(condition);
        switch (request.sortBy) {
	        case "name": QueryTools.orderBy(request.sort, pProxy.getName()); break;
	        default: QueryTools.orderBy(request.sort, pProxy.getId());
        }
        return new TorpedoProjector<>(pProxy, ApiProductListResponse.class).
			add(pProxy.getId(), ApiProductListResponse::setId).
			add(pProxy.getName(), ApiProductListResponse::setName).
			add(dProxy.getStorageKey(), (r, s) -> r.setPhotoStorageId(StorageKeyCache.put(s, userId)));
    }
    
    
    @Transactional
	public ApiPaginatedList<ApiProductListResponse> listUserProducts1(Long userId, ApiListProductsRequest request) {
    	return PaginationTools.createPaginatedResponse(em, request, () -> userProductListQueryObject1(userId, request));
	}

    @Transactional
	public ApiBaseEntity createProduct(CustomUserDetails authUser, ApiProduct request) throws ApiException {

		Company company = em.find(Company.class, request.getCompany().getId());
		PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), authUser);

		Product product = new Product();
		
		productApiTools.updateProduct(authUser, product, request);

		if (product.getAssociatedCompanies().stream().noneMatch(productCompany -> productCompany.getType() == ProductCompanyType.OWNER)) {
			if (request.getCompany() == null || request.getCompany().getId() == null) {
				throw new ApiException(ApiStatus.INVALID_REQUEST, "Company ID is required");
			}
			ProductCompany productCompany = new ProductCompany();
			productCompany.setCompany(company);
			productCompany.setProduct(product);
			productCompany.setType(ProductCompanyType.OWNER);
			product.getAssociatedCompanies().add(productCompany);
		}

		em.persist(product.getProcess());
		em.persist(product.getResponsibility());
		em.persist(product.getSustainability());
		em.persist(product.getSettings());
		em.persist(product.getJourney());
		em.persist(product.getBusinessToCustomerSettings());
		em.persist(product);
		return new ApiBaseEntity(product);
	}

    @Transactional
	public ApiProduct getProduct(CustomUserDetails authUser, long id, boolean includeLabels) throws ApiException {
		Product p = fetchProductAssoc(authUser, id);
		ApiProduct ap = productApiTools.toApiProduct(authUser.getUserId(), p);
		if (includeLabels) productApiTools.updateProductWithLabels(authUser.getUserId(), ap, p);
		return ap;
	}

    @Transactional
	public void updateProduct(CustomUserDetails authUser, ApiProduct ap) throws ApiException {

		if (ap.getAssociatedCompanies() != null && ap.getAssociatedCompanies().stream().noneMatch(apiProductCompany -> apiProductCompany.getType() == ProductCompanyType.OWNER)) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "The product must have at least one owner.");
		}

		Product p = fetchProductAssoc(authUser, ap.id);

		if (p.getSettings() == null) {
			p.setSettings(new ProductSettings());
			em.persist(p.getSettings());
		}

        if (p.getJourney() == null) {
            p.setJourney(new ProductJourney());
            em.persist(p.getJourney());
        }

		productApiTools.updateProduct(authUser, p, ap);

		removeProductLabelCompanyDocumentsForRemovedCompanyAssociations(p);
	}

	private void removeProductLabelCompanyDocumentsForRemovedCompanyAssociations(Product p) {
		em.createQuery("DELETE FROM ProductLabelCompanyDocument plcd WHERE plcd.productLabelId IN :plIds AND plcd.companyDocumentId NOT IN :cdIds")
				.setParameter("plIds", p.getLabels().stream().map(ProductLabel::getId).collect(Collectors.toList()))
				.setParameter("cdIds", p.getAssociatedCompanies().stream().flatMap(productCompany -> productCompany.getCompany().getDocuments().stream().map(CompanyDocument::getId)).collect(Collectors.toList())).executeUpdate();
	}

    @Transactional
	public ApiProductLabel getProductLabel(CustomUserDetails authUser, Long id) throws ApiException {
		ProductLabel pl = productQueries.fetchProductLabelAssoc(authUser, id);
		return ProductApiTools.toApiProductLabel(pl);
	}

    @Transactional
	public ApiProductLabelContent getProductLabelContent(CustomUserDetails authUser, Long id) throws ApiException {
		ProductLabel pl = productQueries.fetchProductLabelAssoc(authUser, id);
		return productApiTools.toApiProductLabelContent(authUser.getUserId(), pl.getContent());
	}

	public List<ApiProductLabelCompanyDocument> getCompanyDocumentsForProductLabel(CustomUserDetails authUser, Long id) throws ApiException {

		productQueries.checkProductLabelPermissionAssoc(authUser, id);

		List<CompanyDocument> availableDocuments = availableCompanyDocumentsForProductLabel(id);
		List<ProductLabelCompanyDocument> selectedDocuments = selectedCompanyDocumentsForProductLabel(id);

		return availableDocuments.stream().map(companyDocument -> {

			ApiProductLabelCompanyDocument apiProductLabelCompanyDocument = ProductApiTools.toApiProductLabelCompanyDocument(
					CompanyApiTools.toApiCompanyDocument(authUser.getUserId(), companyDocument));

			apiProductLabelCompanyDocument.setActive(selectedDocuments.stream().anyMatch(
					productLabelCompanyDocument -> productLabelCompanyDocument.getCompanyDocumentId()
							.equals(companyDocument.getId())));

			return apiProductLabelCompanyDocument;
		}).collect(Collectors.toList());
	}

	private List<CompanyDocument> availableCompanyDocumentsForProductLabel(Long id) {
		return em.createQuery("SELECT DISTINCT cd FROM ProductLabel pl JOIN pl.product p JOIN p.associatedCompanies ac JOIN ac.company c JOIN c.documents cd WHERE pl.id = :id", CompanyDocument.class)
				.setParameter("id", id)
				.getResultList();
	}

	private List<ProductLabelCompanyDocument> selectedCompanyDocumentsForProductLabel(Long id) {
		return em.createQuery("SELECT plcd FROM ProductLabelCompanyDocument plcd WHERE plcd.productLabelId = :productLabelId", ProductLabelCompanyDocument.class)
				.setParameter("productLabelId", id)
				.getResultList();
	}

	@Transactional
	public void updateCompanyDocumentsForProductLabel(CustomUserDetails authUser, Long id, List<ApiProductLabelCompanyDocument> documentList) throws ApiException {

		productQueries.checkProductLabelPermissionAssoc(authUser, id);

		// Get existing state
		List<ProductLabelCompanyDocument> existing = selectedCompanyDocumentsForProductLabel(id);

		// Remove deactivated entries
		existing.forEach(existingDocument -> {

			if (documentList.stream().filter(ApiProductLabelCompanyDocument::getActive).noneMatch(
					apiProductLabelCompanyDocument -> existingDocument.getCompanyDocumentId()
							.equals(apiProductLabelCompanyDocument.getId()))) {
				em.remove(existingDocument);
			}
		});

		List<CompanyDocument> availableDocuments = availableCompanyDocumentsForProductLabel(id);

		// Add activated entries
		documentList
				.stream()
				.filter(apiProductLabelCompanyDocument -> apiProductLabelCompanyDocument.getActive() && availableDocuments
						.stream()
						.anyMatch(companyDocument -> companyDocument.getId().equals(apiProductLabelCompanyDocument.getId())))
				.forEach(apiProductLabelCompanyDocument -> {

					if (existing.stream().noneMatch(existingDocument -> existingDocument.getCompanyDocumentId()
							.equals(apiProductLabelCompanyDocument.getId()))) {
						ProductLabelCompanyDocument productLabelCompanyDocument = new ProductLabelCompanyDocument();

						productLabelCompanyDocument.setProductLabelId(id);
						productLabelCompanyDocument.setCompanyDocumentId(apiProductLabelCompanyDocument.getId());

						em.persist(productLabelCompanyDocument);
					}
				});
	}

    @Transactional
	public ApiProductLabelValuesExtended getProductLabelValuesPublic(String uid) throws ApiException {
		ProductLabel pl = fetchProductLabelPublic(uid);
		ApiProductLabelValuesExtended aplx = new ApiProductLabelValuesExtended();
		
		productApiTools.updateApiProductLabelValues(null, pl, aplx);

		productApiTools.loadBusinessToCustomerSettings(pl, aplx);

		productApiTools.loadBusinessToCustomerMedia(aplx, getCompanyDocuments(pl));

		aplx.numberOfBatches = Queries.getCountBy(em, ProductLabelBatch.class, ProductLabelBatch::getLabel, pl);
		aplx.checkAuthenticityCount = countBatchFields(pl, ProductLabelBatch::getCheckAuthenticity, true);
		aplx.traceOriginCount = countBatchFields(pl, ProductLabelBatch::getTraceOrigin, true);
		return aplx;
	}

	private List<CompanyDocument> getCompanyDocuments(ProductLabel pl) {
		return em.createQuery("SELECT cd FROM CompanyDocument cd JOIN ProductLabelCompanyDocument plcd ON cd.id = plcd.companyDocumentId AND plcd.productLabelId = :plId", CompanyDocument.class)
				.setParameter("plId", pl.getId())
				.getResultList();
	}

	private <P> int countBatchFields(ProductLabel pl, Function<ProductLabelBatch, P> property, P value) {
		ProductLabelBatch plbProxy = Torpedo.from(ProductLabelBatch.class);
        Torpedo.where(plbProxy.getLabel()).eq(pl).and(property.apply(plbProxy)).eq(value);
        Optional<Long> result = Torpedo.select(Torpedo.count(plbProxy)).get(em);
        return result.isPresent() ? result.get().intValue() : 0;
    }
    
	public ApiProductLabelAnalytics getProductLabelAnalytics(CustomUserDetails authUser, String uid) throws ApiException {
		productQueries.checkProductLabelPermissionAssoc(authUser, uid);
		return analyticsEngine.createAnalyticsForLabel(uid);
	}
    
    @Transactional
	public ApiBaseEntity createProductLabel(CustomUserDetails authUser, ApiProductLabel request) throws ApiException {

		if (request.getLanguage() == null) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "Label language is required");
		}

		Product p = fetchProductAssoc(authUser, request.productId);
    	
    	ProductLabelContent plc = ProductLabelContent.fromProduct(p);
    	em.persist(plc.getProcess());
    	em.persist(plc.getResponsibility());
    	em.persist(plc.getSustainability());
    	em.persist(plc.getSettings());
        em.persist(plc.getJourney());
		em.persist(plc.getBusinessToCustomerSettings());
    	em.persist(plc);
    	
		ProductLabel pl = new ProductLabel();
		pl.setContent(plc);
		pl.setProduct(p);
		pl.setLanguage(request.getLanguage());
		ProductApiTools.updateProductLabel(pl, request);

		em.persist(pl);

		return new ApiBaseEntity(pl.getId());
	}
    
    @Transactional
	public ApiDefaultResponse updateProductLabel(CustomUserDetails authUser, ApiProductLabel request) throws ApiException {
		ProductLabel pl = productQueries.fetchProductLabelAssoc(authUser, request.id);
		ProductApiTools.updateProductLabel(pl, request);
		return new ApiDefaultResponse();
	}    

    @Transactional
	public ApiDefaultResponse updateProductLabelContent(CustomUserDetails authUser, ApiProductLabelContent request) throws ApiException {
		ProductLabel pl = productQueries.fetchProductLabelAssoc(authUser, request.labelId);
		ProductLabelContent plc = pl.getContent();

        if (plc.getJourney() == null) {
            ProductJourney journey = new ProductJourney();
            em.persist(journey);
            plc.setJourney(journey);
        }

		productApiTools.updateProductLabelContent(authUser.getUserId(), plc, request);
		return new ApiDefaultResponse();
	}    

    @Transactional
	public List<ApiProductLabelBase> getProductLabels(CustomUserDetails authUser, Long id) throws ApiException {
    	checkProductPermissionAssoc(authUser, id);
    	
		ProductLabel plProxy = Torpedo.from(ProductLabel.class);
		Torpedo.where(plProxy.getProduct().getId()).eq(id);
		List<ProductLabel> labels = Torpedo.select(plProxy).list(em);
		List<ApiProductLabelBase> result = new ArrayList<>(labels.size());
		for (ProductLabel pl : labels) {
			result.add(ProductApiTools.toApiProductLabelBase(pl));
		}
		return result;
	}
    
    @Transactional
	public void deleteProductLabel(CustomUserDetails authUser, Long id) throws ApiException {
		ProductLabel pl = productQueries.fetchProductLabelAssoc(authUser, id);

		removeProductLabelBatches(List.of(pl.getId()));
    	em.remove(pl.getContent().getProcess());
    	em.remove(pl.getContent().getResponsibility());
    	em.remove(pl.getContent().getSustainability());

		if (pl.getContent().getJourney() != null) {
			em.remove(pl.getContent().getJourney());
		}

    	em.remove(pl.getContent().getSettings());
		em.remove(pl.getContent());
		em.remove(pl);
	}
    
    @Transactional
	public void deleteProductLabelFeedback(CustomUserDetails authUser, Long id) throws ApiException {
		ProductLabelFeedback fb = Queries.get(em, ProductLabelFeedback.class, id);
		if (fb == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid feedback id");
		}
		productQueries.checkProductLabelPermissionAssoc(authUser, fb.getLabel().getId());
		em.remove(fb);
	}
    
    @Transactional
	public void deleteProduct(CustomUserDetails authUser, Long id) throws ApiException {
		Product p = fetchProduct(authUser, id);
		
		ProductLabel plProxy = Torpedo.from(ProductLabel.class);
		Torpedo.where(plProxy.getProduct()).eq(p);
		List<ProductLabel> labels = Torpedo.select(plProxy).list(em);

		removeProductLabelBatches(labels.stream().map(ProductLabel::getId).collect(Collectors.toList()));
		labels.forEach(pl -> em.remove(pl));
		em.remove(p);
	}

    private void removeProductLabelBatches(List<Long> labelIds) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaDelete<ProductLabelBatch> cd = cb.createCriteriaDelete(ProductLabelBatch.class);
		Root<ProductLabelBatch> root = cd.from(ProductLabelBatch.class);
		cd.where(root.get("label").get("id").in(labelIds));
		em.createQuery(cd).executeUpdate();
    }

    @Transactional
	public void executeAction(CustomUserDetails authUser, ApiBaseEntity request, ProductLabelAction action) throws ApiException {
    	ProductLabel pl = productQueries.fetchProductLabelAssoc(authUser, request.id);
    	switch (action) {
    		case PUBLISH_LABEL: pl.setStatus(ProductLabelStatus.PUBLISHED); break;
    		case UNPUBLISH_LABEL: pl.setStatus(ProductLabelStatus.UNPUBLISHED); break;
    		default: throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid action");
    	}
	}

    @Transactional
	public ApiProductLabelBatch getProductLabelBatch(CustomUserDetails authUser, Long id) throws ApiException {
		ProductLabelBatch plb = fetchProductLabelBatch(authUser, id);
		return ProductApiTools.toApiProductLabelBatch(authUser.getUserId(), plb);
	}
    	
    @Transactional
	public ApiBaseEntity createProductLabelBatch(CustomUserDetails authUser, ApiProductLabelBatch request) throws ApiException {
    	ProductLabel pl = productQueries.fetchProductLabelAssoc(authUser, request.labelId);
		ProductLabelBatch plb = new ProductLabelBatch();
		plb.setLabel(pl);
		productApiTools.updateProductLabelBatch(authUser.getUserId(), plb, request);
		em.persist(plb);
		return new ApiBaseEntity(plb);
	}
    
    @Transactional
	public ApiDefaultResponse updateProductLabelBatch(CustomUserDetails authUser, ApiProductLabelBatch request) throws ApiException {
		ProductLabelBatch plb = fetchProductLabelBatch(authUser, request.id);
		productApiTools.updateProductLabelBatch(authUser.getUserId(), plb, request);
		return new ApiDefaultResponse();
	}    

    private ProductLabelBatch productLabelBatchListQueryObject(Long labelId, ApiListProductLabelBatchesRequest request) {
		ProductLabelBatch plbProxy = Torpedo.from(ProductLabelBatch.class);
        OnGoingLogicalCondition condition = Torpedo.condition();

        condition = condition.and(plbProxy.getLabel().getId()).eq(labelId);
        if (StringUtils.isNotBlank(request.number)) {
            condition = condition.and(plbProxy.getNumber()).like().startsWith(request.number);
        }
        Torpedo.where(condition);
        switch (request.sortBy) {
	        case "number": QueryTools.orderBy(request.sort, plbProxy.getNumber()); break;
	        case "productionDate": QueryTools.orderBy(request.sort, plbProxy.getProductionDate()); break;
	        case "expiryDate": QueryTools.orderBy(request.sort, plbProxy.getExpiryDate()); break;
	        default: QueryTools.orderBy(request.sort, plbProxy.getId());
        }
        return plbProxy;
    }    

    @Transactional
    public ApiPaginatedList<ApiProductLabelBatch> listProductLabelBatches(CustomUserDetails authUser, Long labelId, ApiListProductLabelBatchesRequest request) throws ApiException {
    	productQueries.checkProductLabelPermission(authUser, labelId);
    	return PaginationTools.createPaginatedResponse(em, request, () -> productLabelBatchListQueryObject(labelId, request), 
    			plb -> ProductApiTools.toApiProductLabelBatch(authUser.getUserId(), plb)); 
	}
    
    @Transactional
	public void deleteProductLabelBatch(CustomUserDetails authUser, Long id) throws ApiException {
		ProductLabelBatch plb = fetchProductLabelBatch(authUser, id);
		em.remove(plb);
	}

    @Transactional
	public ApiProductLabelBatch getProductLabelBatchPublic(String uid, String number) {
    	ProductLabelBatch plbProxy = Torpedo.from(ProductLabelBatch.class);
    	Torpedo.where(plbProxy.getLabel().getUuid()).eq(uid).
    		and(plbProxy.getNumber()).eq(number);
    	Optional<ProductLabelBatch> plb = Torpedo.select(plbProxy).get(em);
    	return plb.isPresent() ? ProductApiTools.toApiProductLabelBatch(null, plb.get()) : null;
	}

    @Transactional
	public boolean checkProductLabelBatchAuthenticityPublic(String labelUid, ApiProductLabelBatchCheckAuthenticity request, HttpServletRequest servletRequest) {
    	boolean response = false;
    	ApiProductLabelBatch b = getProductLabelBatchPublic(labelUid, request.number);
    	
    	if (b != null) {
    		if (request.productionDate != null && request.expiryDate != null) {
    			response = request.productionDate.equals(b.productionDate) && request.expiryDate.equals(b.expiryDate);
    		} else if (request.productionDate != null) {
    			response = request.productionDate.equals(b.productionDate);
    		} else if (request.expiryDate != null) {
    			response = request.expiryDate.equals(b.expiryDate);
    		}
    	}
    	requestLogEngine.log(servletRequest, RequestLogType.VERIFY_BATCH, labelUid, request, response);
    	return response;
	}

    @Transactional
	public List<ApiLocation> checkProductLabelBatchOriginPublic(String labelUid, ApiProductLabelBatchCheckOrigin request, HttpServletRequest servletRequest) {
    	ApiProductLabelBatch b = getProductLabelBatchPublic(labelUid, request.number);
    	List<ApiLocation> response;
    	
    	if (b != null) response = b.getLocations();
    	else response = List.of();
    	
    	requestLogEngine.log(servletRequest, RequestLogType.VERIFY_BATCH_ORIGIN, labelUid, request, b != null);
    	return response;
	}

    private KnowledgeBlog knowledgeBlogListQueryObject(Long productId, ApiListKnowledgeBlogRequest request) {
    	KnowledgeBlog kbProxy = Torpedo.from(KnowledgeBlog.class);
        
        OnGoingLogicalCondition condition = Torpedo.condition();
        condition = condition.and(kbProxy.getProduct().getId()).eq(productId); 
        if (request.type != null) {
        	condition = condition.and(kbProxy.getType()).eq(request.type);
        }
        Torpedo.where(condition);
        switch (request.sortBy) {
	        case "type": QueryTools.orderBy(request.sort, kbProxy.getType()); break;
	        default: QueryTools.orderBy(request.sort, kbProxy.getId());
        }
        return kbProxy;
    }	
    
	@Transactional
	public ApiPaginatedList<ApiKnowledgeBlogBase> listKnowledgeBlogs(CustomUserDetails authUser, Long productId, ApiListKnowledgeBlogRequest request) throws ApiException {
		checkProductPermissionAssoc(authUser, productId);
    	return PaginationTools.createPaginatedResponse(em, request, () -> knowledgeBlogListQueryObject(productId, request), 
    			ProductApiTools::toApiKnowledgeBlogBase); 
	}

    @Transactional
	public void updateKnowledgeBlog(CustomUserDetails authUser, ApiKnowledgeBlog request) throws ApiException {
    	KnowledgeBlog kb = fetchKnowledgeBlogAssoc(authUser, request.id);
    	productApiTools.updateKnowledgeBlog(authUser.getUserId(), kb, request);
	}

    @Transactional
	public ApiKnowledgeBlog getKnowledgeBlog(CustomUserDetails authUser, Long id) throws ApiException {
    	KnowledgeBlog kb = fetchKnowledgeBlogAssoc(authUser, id);
    	return productApiTools.toApiKnowledgeBlog(authUser.getUserId(), kb);
	}

    @Transactional
	public ApiKnowledgeBlog getKnowledgeBlogPublic(Long id) throws ApiException {
    	KnowledgeBlog kb = Queries.get(em, KnowledgeBlog.class, id);
    	
		if (kb == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid id");
		}		    	
    	return productApiTools.toApiKnowledgeBlog(null, kb);
	}
    
    @Transactional
	public void addKnowledgeBlog(CustomUserDetails authUser, Long productId, ApiKnowledgeBlog request) throws ApiException {
    	Product p = fetchProductAssoc(authUser, productId);
    	
    	KnowledgeBlog kb = new KnowledgeBlog();
    	kb.setType(request.type);
    	kb.setProduct(p);
    	productApiTools.updateKnowledgeBlog(authUser.getUserId(), kb, request);
    	em.persist(kb);
	}
    
    private ProductLabelFeedback feedbackListQueryObject(String labelUid, ApiListProductLabelFeedbackRequest request) {
    	ProductLabelFeedback plfProxy = Torpedo.from(ProductLabelFeedback.class);
        
        OnGoingLogicalCondition condition = Torpedo.condition(); // .Torpedo conditions = new ArrayList<>();
        condition = condition.and(plfProxy.getLabel().getUuid()).eq(labelUid); 
        if (request.type != null) {
        	condition = condition.and(plfProxy.getType()).eq(request.type);
        }
        Torpedo.where(condition);
        switch (request.sortBy) {
	        case "type": QueryTools.orderBy(request.sort, plfProxy.getType()); break;
	        default: QueryTools.orderBy(request.sort, plfProxy.getId());
        }
        return plfProxy;
    }	
    
    @Transactional
	public ApiPaginatedList<ApiProductLabelFeedback> listProductLabelFeedbacks(String labelUid, ApiListProductLabelFeedbackRequest request) throws ApiException {
    	return PaginationTools.createPaginatedResponse(em, request, () -> feedbackListQueryObject(labelUid, request), 
    			ProductApiTools::toApiProductLabelFeedback); 
	}
    
    @Transactional
	public void addProductLabelFeedback(String labelUid, ApiProductLabelFeedback request) throws ApiException {
    	ProductLabel pl = fetchProductLabelPublic(labelUid);
    	ProductLabelFeedback fb = new ProductLabelFeedback();
    	fb.setLabel(pl);
    	ProductApiTools.updateProductLabelFeedback(fb, request);
    	em.persist(fb);
	}

	public ApiFinalProduct getFinalProduct(Long productId, Long finalProductId, CustomUserDetails user) throws ApiException {

		// Check that the request user is enrolled in one of the connected companies
		checkProductPermissionAssoc(user, productId);

    	return ProductApiTools.toApiFinalProductWithLabels(fetchFinalProduct(productId, finalProductId));
	}

	public List<ApiProductLabelBase> getFinalProductLabels(Long productId,
														   Long finalProductId,
														   Boolean returnUnpublished,
														   CustomUserDetails user) throws ApiException {

		// Check that the request user is enrolled in one of the connected companies
		checkProductPermissionAssoc(user, productId);

		FinalProduct finalProduct = fetchFinalProduct(productId, finalProductId);

		return finalProduct.getFinalProductLabels()
				.stream()
				.filter(finalProductLabel -> {
					if (BooleanUtils.isNotTrue(returnUnpublished)) {
						return finalProductLabel.getProductLabel().getStatus().equals(ProductLabelStatus.PUBLISHED);
					}
					return true;
				})
				.map(finalProductLabel -> ProductApiTools.toApiProductLabelBase(finalProductLabel.getProductLabel()))
				.collect(Collectors.toList());
	}

	public ApiPaginatedList<ApiFinalProduct> getFinalProductList(ApiPaginatedRequest request,
																 FinalProductQueryRequest queryRequest,
																 CustomUserDetails user) throws ApiException {

		if (queryRequest.productId == null) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Product ID is required");
		}

		// Check that the request user is enrolled in one of the product's connected companies
		checkProductPermissionAssoc(user, queryRequest.productId);

		return PaginationTools.createPaginatedResponse(em, request,
				() -> finalProductQueryObject(
						request,
						queryRequest
				), ProductApiTools::toApiFinalProduct);
	}

	private FinalProduct finalProductQueryObject(ApiPaginatedRequest request,
												 FinalProductQueryRequest queryRequest) {

    	FinalProduct finalProductProxy = Torpedo.from(FinalProduct.class);
    	OnGoingLogicalCondition condition = Torpedo.condition();

    	if (queryRequest.productId != null) {
    		condition = condition.and(finalProductProxy.getProduct()).isNotNull()
					.and(finalProductProxy.getProduct().getId()).eq(queryRequest.productId);
		}

    	Torpedo.where(condition);

    	switch (request.sortBy) {
			case "name": QueryTools.orderBy(request.sort, finalProductProxy.getName()); break;
			case "description": QueryTools.orderBy(request.sort, finalProductProxy.getDescription()); break;
			default: QueryTools.orderBy(request.sort, finalProductProxy.getId());
		}

    	return finalProductProxy;
	}

	@Transactional
	public ApiBaseEntity createOrUpdateFinalProduct(CustomUserDetails authUser, Long productId, ApiFinalProduct apiFinalProduct) throws ApiException {

    	Product product = fetchProduct(authUser, productId);

		FinalProduct entity = apiFinalProduct.getId() != null
				? fetchFinalProduct(productId, apiFinalProduct.getId())
				: new FinalProduct();

		// Required fields
		if (apiFinalProduct.getName() == null)
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Final product name needs to be provided.");
		if (apiFinalProduct.getDescription() == null)
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Final product description needs to be provided.");
		if (apiFinalProduct.getMeasurementUnitType() == null || apiFinalProduct.getMeasurementUnitType().getId() == null)
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Measurement unit type ID needs to be provided.");

		entity.setProduct(product);
		entity.setName(apiFinalProduct.getName());
		entity.setDescription(apiFinalProduct.getDescription());
		entity.setMeasurementUnitType(measureUnitTypeService.fetchMeasureUnitType(apiFinalProduct.getMeasurementUnitType().getId()));

		// Create or update Final product labels (connections between a Final product and Product labels)
		entity.getFinalProductLabels().removeIf(finalProductLabel -> apiFinalProduct.getLabels().stream().noneMatch(
				apiProductLabel -> finalProductLabel.getProductLabel().getId().equals(apiProductLabel.getId())));

		for (ApiProductLabelBase apiProductLabel : apiFinalProduct.getLabels()) {
			FinalProductLabel finalProductLabel = entity.getFinalProductLabels().stream()
					.filter(fpl -> fpl.getProductLabel().getId().equals(apiProductLabel.getId())).findFirst()
					.orElse(new FinalProductLabel());
			if (finalProductLabel.getId() == null) {
				finalProductLabel.setFinalProduct(entity);
				finalProductLabel.setProductLabel(fetchProductLabel(apiProductLabel.getId()));
				entity.getFinalProductLabels().add(finalProductLabel);
			}
		}

		if (entity.getId() == null) {
			em.persist(entity);
		}
		return new ApiBaseEntity(entity);
	}

	@Transactional
	public void deleteFinalProduct(Long productId, Long finalProductId, CustomUserDetails authUser) throws ApiException {

		// Check if request user has product permissions
		checkProductPermission(authUser, productId);

		FinalProduct fp = fetchFinalProduct(productId, finalProductId);

    	em.remove(fp);
	}

    private void checkProductPermission(CustomUserDetails authUser, Long productId) throws ApiException {

		if (authUser.getUserRole() == UserRole.SYSTEM_ADMIN) return;

		// Get all the companies that are product admins (owner companies)
		List<Company> productOwnerCompanies = em.createNamedQuery("ProductCompany.getProductOwnerCompanies", Company.class)
				.setParameter("productId", productId).getResultList();

		// Check that the requesting user is enrolled in one of this companies
		boolean userEnrolledInOwnerCompany = false;
		for (Company productOwnerCompany : productOwnerCompanies) {
			userEnrolledInOwnerCompany = productOwnerCompany.getUsers()
					.stream()
					.map(CompanyUser::getUser)
					.anyMatch(u -> u.getId().equals(authUser.getUserId()));
			if (userEnrolledInOwnerCompany) {
				break;
			}
		}

		if (!userEnrolledInOwnerCompany) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Invalid product id or forbidden");
		}
    }
    
    private void checkProductPermissionAssoc(CustomUserDetails authUser, Long productId) throws ApiException {
		if (authUser.getUserRole() == UserRole.SYSTEM_ADMIN) return;
    
    	Number count = em.createQuery("SELECT count(p) FROM Product p INNER JOIN CompanyUser cu ON cu.company.id = p.company.id "
    			+ "WHERE cu.user.id = :userId AND p.id = :productId", Number.class).
    		setParameter("userId", authUser.getUserId()).
    		setParameter("productId", productId).
    		getSingleResult();
    	if (count.longValue() > 0) return;
    	
    	Number countAssoc = em.createQuery("SELECT count(pc) FROM ProductCompany pc INNER JOIN "
    			+ "CompanyUser cu ON cu.company.id = pc.company.id WHERE cu.user.id = :userId AND pc.product.id = :productId", Number.class).
    		setParameter("userId", authUser.getUserId()).
    		setParameter("productId", productId).
    		getSingleResult();
    	if (countAssoc.longValue() == 0L) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Invalid product id or forbidden");
		}		
    }
    
    private void checkProductLabelBatchPermissionAssoc(CustomUserDetails authUser, Long batchId) throws ApiException {
		if (authUser.getUserRole() == UserRole.SYSTEM_ADMIN) return;
    
    	Number count = em.createQuery("SELECT count(plb) FROM ProductLabelBatch plb INNER JOIN CompanyUser cu ON cu.company.id = plb.label.product.company.id "
    			+ "WHERE cu.user.id = :userId AND plb.id = :batchId", Number.class).
    		setParameter("userId", authUser.getUserId()).
    		setParameter("batchId", batchId).
    		getSingleResult();
    	if (count.longValue() > 0) return;
    	
    	Number countAssoc = em.createQuery("SELECT count(pc) FROM ProductCompany pc "
    			+ "INNER JOIN CompanyUser cu ON cu.company.id = pc.company.id "
    			+ "INNER JOIN ProductLabelBatch plb ON plb.label.product.id = pc.product.id "
    			+ "WHERE cu.user.id = :userId AND plb.id = :batchId", Number.class).
    		setParameter("userId", authUser.getUserId()).
    		setParameter("batchId", batchId).
    		getSingleResult();
    	if (countAssoc.longValue() == 0L) {    	
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Invalid batch id or forbidden");
		}		
    }
	
	private KnowledgeBlog fetchKnowledgeBlogAssoc(CustomUserDetails authUser, Long id) throws ApiException {
    	KnowledgeBlog kb = Queries.get(em, KnowledgeBlog.class, id);
    	
		if (kb == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid id");
		}		
		checkProductPermissionAssoc(authUser, kb.getProduct().getId());
		return kb;
	}	
    
	private Product fetchProduct(CustomUserDetails authUser, Long id) throws ApiException {
		checkProductPermission(authUser, id);
		Product p = Queries.get(em, Product.class, id);
		if (p == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid product id");
		}		
		return p;
	}
	
	private Product fetchProductAssoc(CustomUserDetails authUser, Long id) throws ApiException {
		checkProductPermissionAssoc(authUser, id);
		Product p = Queries.get(em, Product.class, id);
		if (p == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid product id");
		}		
		return p;
	}
	
	private ProductLabel fetchProductLabelPublic(String uid) throws ApiException {
		ProductLabel pl = Queries.getUniqueBy(em, ProductLabel.class, ProductLabel::getUuid, uid);
		if (pl == null || pl.getStatus() == ProductLabelStatus.UNPUBLISHED) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid label uid");
		}		
		return pl;
	}

	private ProductLabel fetchProductLabel(Long id) throws ApiException {
		ProductLabel pl = Queries.get(em, ProductLabel.class, id);
		if (pl == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid label id");
		}
		return pl;
	}
	
	private ProductLabelBatch fetchProductLabelBatch(CustomUserDetails authUser, Long id) throws ApiException {
		checkProductLabelBatchPermissionAssoc(authUser, id);		
		ProductLabelBatch plb = Queries.get(em, ProductLabelBatch.class, id);
		if (plb == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid batch id");
		}		
		return plb;
	}

	private FinalProduct fetchFinalProduct(Long productId, Long finalProductId) throws ApiException {

		FinalProduct entity = Queries.get(em, FinalProduct.class, finalProductId);

		// If FinalProduct is found, but productIds does not match, return entity not found
		if (entity == null
				|| productId == null
				|| entity.getProduct() == null
				|| !productId.equals(entity.getProduct().getId())) {

			throw new ApiException(ApiStatus.INVALID_REQUEST,
					"FinalProduct entity with ID '" + finalProductId  + "' not found.");
		}
		return entity;
	}
}
