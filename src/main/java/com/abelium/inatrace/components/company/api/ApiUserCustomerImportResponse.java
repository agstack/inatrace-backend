package com.abelium.inatrace.components.company.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApiUserCustomerImportResponse {

    private Integer successful;
    private List<ApiUserCustomer> duplicates;
    private Map<String, List<String>> unresolvedKoboSheets;

    public Map<String, List<String>> getUnresolvedKoboSheets() { return unresolvedKoboSheets; }

    public void setUnresolvedKoboSheets(Map<String, List<String>> unresolvedKoboSheets) {
        this.unresolvedKoboSheets = unresolvedKoboSheets;
    }

    private List<ApiUserCustomerImportRowValidationError> validationErrors;

    public Integer getSuccessful() {
        return successful;
    }

    public void setSuccessful(Integer successful) {
        this.successful = successful;
    }

    public List<ApiUserCustomer> getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(List<ApiUserCustomer> duplicates) {
        this.duplicates = duplicates;
    }

    public List<ApiUserCustomerImportRowValidationError> getValidationErrors() {
        if (validationErrors == null) {
            validationErrors = new ArrayList<ApiUserCustomerImportRowValidationError>();
        }
        return validationErrors;
    }

}
