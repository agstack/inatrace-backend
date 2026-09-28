package com.abelium.inatrace.components.codebook.currencies;

import com.abelium.inatrace.api.ApiPaginatedResponse;
import com.abelium.inatrace.components.codebook.currencies.api.ApiCurrencyType;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.currencies.api.ApiCurrencyRatesResponse;
import com.abelium.inatrace.components.currencies.api.ApiCurrencySymbolsResponse;
import com.abelium.inatrace.components.currencies.api.ApiCurrencyTypeRequest;
import com.abelium.inatrace.db.entities.codebook.CurrencyType;
import com.abelium.inatrace.db.entities.currencies.CurrencyPair;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.QueryTools;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CurrencyTypeService extends BaseService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyTypeService.class);
    private static final String CURRENCY = "currency";

    @Value("${INAtrace.exchangerate.apiKey}")
    private String apiKey;

    public List<ApiCurrencyType> getCurrencyTypeList() {
        List<CurrencyType> currencyTypeList = em.createNamedQuery("CurrencyType.getAllCurrencyTypes", CurrencyType.class).getResultList();
        return CurrencyTypeMapper.toApiCurrencyTypeList(currencyTypeList);
    }

    public ApiPaginatedResponse<ApiCurrencyType> getCurrencyTypeList(Boolean enabled, ApiCurrencyTypeRequest request) {
        return new ApiPaginatedResponse<>(PaginationTools.createPaginatedResponse(em, request, () -> currencyTypeProxy(enabled, request), CurrencyTypeMapper::toApiCurrencyType));
    }

    public CurrencyType getCurrencyType(Long id) {
        return em.find(CurrencyType.class, id);
    }

    public CurrencyType getCurrencyTypeByCode(String code) {
        return em.createNamedQuery("CurrencyType.getCurrencyTypeByCode", CurrencyType.class).setParameter("code", code).getSingleResult();
    }

    @Transactional
    public void updateStatus(Long id, Boolean enabled) {
        CurrencyType currencyType = em.find(CurrencyType.class, id);
        currencyType.setEnabled(enabled);
        if (enabled) {
            updateCurrencies();
        }
    }

    @Transactional
    public void updateCurrencies() {
        WebClient webClientSymbols = WebClient.create("http://api.exchangeratesapi.io/v1/symbols?access_key=" + apiKey);
        ApiCurrencySymbolsResponse apiCurrencySymbolsResponse = webClientSymbols
                .get()
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(ApiCurrencySymbolsResponse.class)
                .doOnError(e -> logger.error("Failed to fetch exchange rate data: {}", e.getMessage()))
                .onErrorReturn(new ApiCurrencySymbolsResponse())
                .block();
        if (apiCurrencySymbolsResponse != null && apiCurrencySymbolsResponse.isSuccess()) {
            Map<String, String> symbols = apiCurrencySymbolsResponse.getSymbols();
            for (Map.Entry<String, String> entry : symbols.entrySet()) {
                if (em.createNamedQuery("CurrencyType.getCurrencyTypeByCode").setParameter("code", entry.getKey()).getResultList().isEmpty()) {
                    CurrencyType currencyType = new CurrencyType();
                    currencyType.setCode(entry.getKey());
                    currencyType.setLabel(entry.getValue());
                    currencyType.setEnabled(Boolean.FALSE);
                    em.persist(currencyType);
                }
            }
        }

        WebClient webClientRates = WebClient.create("http://api.exchangeratesapi.io/v1/latest?access_key=" + apiKey + "&base=EUR");
        ApiCurrencyRatesResponse apiCurrencyResponse = webClientRates
                .get()
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(ApiCurrencyRatesResponse.class)
                .doOnError(e -> logger.error("Failed to fetch exchange rate data: {}", e.getMessage()))
                .onErrorReturn(new ApiCurrencyRatesResponse())
                .block();
        if (apiCurrencyResponse != null && apiCurrencyResponse.isSuccess()) {
            Map<String, BigDecimal> rates = apiCurrencyResponse.getRates();
            Date current = apiCurrencyResponse.getDate();

            List<String> enabled = getEnabledCurrencyCodes();

            for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
                if (enabled.contains(entry.getKey()) && em.createNamedQuery("CurrencyPair.rateAtDate").setParameter(CURRENCY, entry.getKey()).setParameter("date", current).getResultList().isEmpty()) {
                    CurrencyPair currencyPair = new CurrencyPair();
                    CurrencyType from = getCurrencyTypeByCode("EUR");
                    CurrencyType to = getCurrencyTypeByCode(entry.getKey());
                    currencyPair.setFrom(from);
                    currencyPair.setTo(to);
                    currencyPair.setDate(current);
                    currencyPair.setValue(entry.getValue());
                    em.persist(currencyPair);
                }
            }
        }
    }

    public List<String> getEnabledCurrencyCodes() {
        return em.createNamedQuery("CurrencyType.getEnabledCurrencyTypes", CurrencyType.class).getResultList().stream().map(CurrencyType::getCode).collect(
                Collectors.toList());
    }

    private CurrencyType currencyTypeProxy(Boolean enabled, ApiCurrencyTypeRequest request) {
        CurrencyType currencyTypeProxy = Torpedo.from(CurrencyType.class);

        OnGoingLogicalCondition condition = Torpedo.condition();

        if (enabled != null) {
            condition = condition.and(currencyTypeProxy.getEnabled()).eq(enabled);
        }

        if (request.getQuery() != null) {
            OnGoingLogicalCondition codeLikeQuery = Torpedo.condition(currencyTypeProxy.getCode()).like().any(request.getQuery());
            OnGoingLogicalCondition labelLikeQuery = Torpedo.condition(currencyTypeProxy.getLabel()).like().any(request.getQuery());
            condition = condition.and(codeLikeQuery.or(labelLikeQuery));
        }

        Torpedo.where(condition);

        switch (request.sortBy) {
            case "code":
                QueryTools.orderBy(request.sort, currencyTypeProxy.getCode());
                break;
            case "label":
                QueryTools.orderBy(request.sort, currencyTypeProxy.getLabel());
                break;
        }

        return currencyTypeProxy;
    }
}
