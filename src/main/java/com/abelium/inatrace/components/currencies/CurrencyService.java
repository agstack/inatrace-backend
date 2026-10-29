package com.abelium.inatrace.components.currencies;

import com.abelium.inatrace.configuration.ApiRequestObservability;
import com.abelium.inatrace.configuration.SafeExceptionLog;

import com.abelium.inatrace.components.codebook.currencies.CurrencyTypeService;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.currencies.api.ApiCurrencyRatesResponse;
import com.abelium.inatrace.db.entities.codebook.CurrencyType;
import com.abelium.inatrace.db.entities.currencies.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class CurrencyService extends BaseService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyService.class);
    private static final String CURRENCY = "currency";

    @Autowired
    private CurrencyTypeService currencyTypeService;

    @Value("${INAtrace.exchangerate.apiKey}")
    private String apiKey;

    @Value("${INAtrace.exchangerate.baseURL:http://api.exchangeratesapi.io/v1}")
    private String baseURL;

    @Value("${INAtrace.exchangerate.requestTimeoutMs:30000}")
    private long requestTimeoutMs;

    public BigDecimal convertFromEur(String to, BigDecimal value) {
        return value.multiply(em.createNamedQuery("CurrencyPair.latestRate", BigDecimal.class).setParameter(CURRENCY, to).getResultList().get(0));
    }

    public BigDecimal convertToEur(String from, BigDecimal value) {
        return value.divide(em.createNamedQuery("CurrencyPair.latestRate", BigDecimal.class).setParameter(CURRENCY, from).getResultList().get(0), 6, RoundingMode.HALF_UP);
    }

    @Transactional
    public BigDecimal convertFromEurAtDate(String to, BigDecimal value, Date date) {

        List<BigDecimal> rates = rateAtDateQuery(to, date).getResultList();
        BigDecimal rate;
        if (rates.isEmpty()) {
            fetchRates(date);
            rate = rateAtDateQuery(to, date).getSingleResult();
        } else {
            rate = rates.get(0);
        }
        return value.multiply(rate);
    }

    @Transactional
    public BigDecimal convertToEurAtDate(String from, BigDecimal value, Date date) {

        List<BigDecimal> rates = rateAtDateQuery(from, date).getResultList();
        BigDecimal rate;
        if (rates.isEmpty()) {
            fetchRates(date);
            rate = rateAtDateQuery(from, date).getSingleResult();
        } else {
            rate = rates.get(0);
        }
        return value.divide(rate, 6, RoundingMode.HALF_UP);
    }

    public BigDecimal convert(String from, String to, BigDecimal value) {

        if (from.equals(to)) {
            return value;
        } else if ("EUR".equals(from)) {
            return this.convertFromEur(to, value);
        } else if ("EUR".equals(to)) {
            return this.convertToEur(from, value);
        } else {
            return this.convertFromEur(to, this.convertToEur(from, value));
        }
    }

    @Transactional
    public BigDecimal convertAtDate(String from, String to, BigDecimal value, Date date) {

        if (from.equals(to)) {
            return value;
        } else if ("EUR".equals(from)) {
            return this.convertFromEurAtDate(to, value, date);
        } else if ("EUR".equals(to)) {
            return this.convertToEurAtDate(from, value, date);
        } else {
            return this.convertFromEurAtDate(to, this.convertToEurAtDate(from, value, date), date);
        }
    }

    public void fetchRates(Date date) {
        String requestId = ApiRequestObservability.requestId();
        String isoDate = DateTimeFormatter.ISO_LOCAL_DATE.format(date.toInstant().atZone(ZoneId.of("GMT")));
        WebClient webClient = WebClient.create(baseURL);
        ApiCurrencyRatesResponse apiCurrencyRatesResponse = webClient
                .get()
                .uri(builder -> builder.pathSegment(isoDate).queryParam("access_key", apiKey).queryParam("base", "EUR").build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().bodyToMono(ApiCurrencyRatesResponse.class)
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .doOnError(e -> SafeExceptionLog.error(logger, "Failed to fetch historical exchange rates", e, requestId))
                .onErrorReturn(new ApiCurrencyRatesResponse())
                .block();
        if (validRates(apiCurrencyRatesResponse)
                && date.toInstant().atZone(ZoneId.of("GMT")).toLocalDate().equals(
                        apiCurrencyRatesResponse.getDate().toInstant().atZone(ZoneId.of("GMT")).toLocalDate())) {
            Map<String, BigDecimal> rates = apiCurrencyRatesResponse.getRates();
            Date current = apiCurrencyRatesResponse.getDate();

            List<String> enabled = currencyTypeService.getEnabledCurrencyCodes();

            for (Map.Entry<String, BigDecimal> entry : rates.entrySet()) {
                if (enabled.contains(entry.getKey()) && rateAtDateQuery(entry.getKey(), current).getResultList().isEmpty()) {
                    CurrencyPair currencyPair = new CurrencyPair();
                    CurrencyType from = currencyTypeService.getCurrencyTypeByCode("EUR");
                    CurrencyType to = currencyTypeService.getCurrencyTypeByCode(entry.getKey());
                    currencyPair.setFrom(from);
                    currencyPair.setTo(to);
                    currencyPair.setDate(current);
                    currencyPair.setValue(entry.getValue());
                    em.persist(currencyPair);
                }
            }
        }
    }

    public static boolean validRates(ApiCurrencyRatesResponse response) {
        return response != null && response.isSuccess() && "EUR".equals(response.getBase()) && response.getDate() != null
                && response.getRates() != null && !response.getRates().isEmpty()
                && response.getRates().values().stream().allMatch(rate -> rate != null && rate.signum() > 0);
    }

    private TypedQuery<BigDecimal> rateAtDateQuery(String currency, Date date) {
        return em.createNamedQuery("CurrencyPair.rateAtDate", BigDecimal.class).setParameter(CURRENCY, currency).setParameter("date", date);
    }

}
