package com.abelium.inatrace.components.common.mappers;

import com.abelium.inatrace.components.common.api.ApiCountry;
import com.abelium.inatrace.db.entities.common.Country;

public class CountryMapper {

    public static ApiCountry toApiCountry(Country entity) {
        // Older addresses may have been stored without a country: read them as "no country".
        if (entity == null) {
            return null;
        }
        ApiCountry apiCountry = new ApiCountry();
        apiCountry.setId(entity.getId());
        apiCountry.setName(entity.getName());
        apiCountry.setCode(entity.getCode());
        apiCountry.setLatitude(entity.getLatitude());
        apiCountry.setLongitude(entity.getLongitude());
        return apiCountry;
    }

}
