package com.abelium.inatrace.components.product;

import com.abelium.inatrace.api.*;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.product.api.ApiProductType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.codebook.ProductTypeTranslation;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.types.Language;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Lazy
@Service
public class ProductTypeService extends BaseService {

    private static final String PRODUCT_TYPE_DOES_NOT_EXIST = "Product type does not exist!";

    @Transactional
    public ApiResponse<ApiBaseEntity> createProductType(ApiProductType apiProductType) throws ApiException{

        ProductType productType = ProductTypeMapper.toProductType(apiProductType);

        productType.setCode(apiProductType.getCode());
        em.persist(productType);

        if (apiProductType.getTranslations() == null || apiProductType.getTranslations().isEmpty()) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "English translation is required!");
        }

        apiProductType.getTranslations().stream().filter(productTypeTranslation -> productTypeTranslation != null &&
                Language.EN.equals(productTypeTranslation.getLanguage()) &&
                productTypeTranslation.getName() != null &&
                productTypeTranslation.getDescription() != null)
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiStatus.INVALID_REQUEST, "English translation is required!"));

        apiProductType.getTranslations().forEach(apiProductTypeTranslation -> {

            if (Language.EN.equals(apiProductTypeTranslation.getLanguage())) {
                // update the name from EN translation
                productType.setName(apiProductTypeTranslation.getName());
                productType.setDescription(apiProductTypeTranslation.getDescription());
            }

            ProductTypeTranslation translation = new ProductTypeTranslation();
            translation.setName(apiProductTypeTranslation.getName());
            translation.setDescription(apiProductTypeTranslation.getDescription());
            translation.setLanguage(apiProductTypeTranslation.getLanguage());
            translation.setProductType(productType);
            productType.getProductTypeTranslations().add(translation);
        });

        return new ApiResponse<>(new ApiBaseEntity(productType));
    }

    public ApiPaginatedResponse<ApiProductType> getProductTypes(Language language) {

        List<ApiProductType> productTypeList = em.createQuery("SELECT pt FROM ProductType pt", ProductType.class)
                .getResultList()
                .stream()
                .map(pt -> ProductTypeMapper.toApiProductTypeDetailed(pt, language))
                .collect(Collectors.toList());

        return new ApiPaginatedResponse<>(new ApiPaginatedList<>(productTypeList, productTypeList.size()));
    }

    public ApiResponse<ApiProductType> getProductType(Long id, Language language) throws ApiException {

        ProductType productType = em.find(ProductType.class, id);

        if (productType == null) {
            throw new ApiException(ApiStatus.NOT_FOUND, PRODUCT_TYPE_DOES_NOT_EXIST);
        }

        return new ApiResponse<>(ProductTypeMapper.toApiProductType(productType, language));
    }

    @Transactional
    public ApiResponse<ApiBaseEntity> updateProductType(ApiProductType apiProductType) throws ApiException {

        if (apiProductType.getId() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Id must be provided!");
        }

        ProductType productType = em.find(ProductType.class, apiProductType.getId());

        if (productType == null) {
            throw new ApiException(ApiStatus.NOT_FOUND, PRODUCT_TYPE_DOES_NOT_EXIST);
        }

        productType.setCode(apiProductType.getCode());

        if (apiProductType.getTranslations() == null || apiProductType.getTranslations().isEmpty()) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "English translation is required!");
        }

        apiProductType.getTranslations().stream().filter(productTypeTranslation -> productTypeTranslation != null &&
                        Language.EN.equals(productTypeTranslation.getLanguage()) &&
                        productTypeTranslation.getName() != null &&
                        productTypeTranslation.getDescription() != null)
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiStatus.INVALID_REQUEST, "English translation is required!"));

        productType.getProductTypeTranslations().removeIf(
                productTypeTranslation -> apiProductType.getTranslations().stream().noneMatch(
                        apiProductTypeTranslation -> productTypeTranslation.getLanguage()
                                .equals(apiProductTypeTranslation.getLanguage())));

        apiProductType.getTranslations().forEach(apiProductTypeTranslation -> {
            ProductTypeTranslation translation = productType.getProductTypeTranslations().stream()
                    .filter(productTypeTranslation ->
                            productTypeTranslation.getLanguage().equals(apiProductTypeTranslation.getLanguage()))
                    .findFirst()
                    .orElse(new ProductTypeTranslation());

            if (Language.EN.equals(apiProductTypeTranslation.getLanguage())) {
                // update the name from EN translation
                productType.setName(apiProductTypeTranslation.getName());
                productType.setDescription(apiProductTypeTranslation.getDescription());
            }

            translation.setName(apiProductTypeTranslation.getName());
            translation.setDescription(apiProductTypeTranslation.getDescription());
            translation.setLanguage(apiProductTypeTranslation.getLanguage());
            translation.setProductType(productType);
            productType.getProductTypeTranslations().add(translation);
        });

        return new ApiResponse<>(new ApiBaseEntity(productType));
    }

    @Transactional
    public ApiDefaultResponse deleteProductType(Long id) throws ApiException {

        if (id == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Id must be provided!");
        }

        ProductType productType = em.find(ProductType.class, id);

        if (productType == null) {
            throw new ApiException(ApiStatus.NOT_FOUND, PRODUCT_TYPE_DOES_NOT_EXIST);
        }

        if (Queries.isReferenced(em, id,
                "ValueChain.productType",
                "FarmPlantInformation.productType",
                "UserCustomerProductType.productType",
                "Plot.crop")) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR, "Cannot delete a product type that is in use");
        }

        em.remove(productType);

        return new ApiDefaultResponse();
    }

}
