package com.abelium.inatrace.components.common;

import com.abelium.inatrace.components.codebook.currencies.api.ApiCurrencyType;
import com.abelium.inatrace.components.common.mappers.CountryMapper;
import com.abelium.inatrace.db.entities.codebook.CurrencyType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.api.ApiCountry;
import com.abelium.inatrace.components.common.api.ApiDocument;
import com.abelium.inatrace.components.company.api.ApiAddress;
import com.abelium.inatrace.components.company.api.ApiGeoAddress;
import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.GeoAddress;

@Lazy
@Service
public class CommonApiTools {
	
	@Autowired
	private CommonService commonEngine;
	
	public static void updateApiBaseEntity(ApiBaseEntity apiEntity, BaseEntity entity) {
		apiEntity.id = entity.getId();
	}	

    public static ApiCountry toApiCountry(Country country) {
        if (country == null) {
            return null;
        }
        return CountryMapper.toApiCountry(country);
    }

    public static ApiDocument toApiDocument(Document document, Long userId) {
        if (document == null) {
            return null;
        }
        ApiDocument ad = new ApiDocument();
        ad.id = document.getId();
        ad.name = document.getName();
        //ad.storageKey = document.getStorageKey();
        ad.storageKey = StorageKeyCache.put(document.getStorageKey(), userId);
        ad.contentType = document.getContentType();
        ad.size = document.getSize();
        return ad;
    }
    
    public static void updateDocument(Document d, ApiDocument ad) {
        d.setName(ad.name);
        d.setStorageKey(ad.storageKey);
        d.setContentType(ad.contentType);
        d.setSize(ad.size);
    }
    
    public static ApiAddress toApiAddress(Address address) {
        if (address == null) {
            return null;
        }
        ApiAddress aa = new ApiAddress();
        updateApiAddress(aa, address);
        return aa;
    }

    public static ApiCurrencyType toApiCurrencyType(CurrencyType currencyType) {
        if (currencyType == null) {
            return null;
        }
        ApiCurrencyType apiCurrencyType = new ApiCurrencyType();
        apiCurrencyType.setId(currencyType.getId());
        apiCurrencyType.setCode(currencyType.getCode());
        apiCurrencyType.setEnabled(currencyType.getEnabled());
        apiCurrencyType.setLabel(currencyType.getLabel());

        return apiCurrencyType;
    }
    
    public static ApiGeoAddress toApiGeoAddress(GeoAddress address) {
        if (address == null) {
            return null;
        }
        ApiGeoAddress aa = new ApiGeoAddress();
        updateApiAddress(aa, address);
        aa.latitude = address.getLatitude();
        aa.longitude = address.getLongitude();
        return aa;
    }
    
    public static void updateApiAddress(ApiAddress aa, Address address) {
        aa.address = address.getAddress();
        aa.city = address.getCity();
        aa.state = address.getState();
        aa.zip = address.getZip();
        aa.country = toApiCountry(address.getCountry());
        aa.cell = address.getCell();
        aa.sector = address.getSector();
        aa.village = address.getVillage();
        aa.otherAddress = address.getOtherAddress();
        aa.hondurasDepartment = address.getHondurasDepartment();
        aa.hondurasFarm = address.getHondurasFarm();
        aa.hondurasMunicipality = address.getHondurasMunicipality();
        aa.hondurasVillage = address.getHondurasVillage();
    }

    public static void updateApiAddress(ApiAddress aa, GeoAddress address) {
        aa.address = address.getAddress();
        aa.city = address.getCity();
        aa.state = address.getState();
        aa.zip = address.getZip();
        aa.country = toApiCountry(address.getCountry());
        aa.cell = address.getCell();
        aa.sector = address.getSector();
        aa.village = address.getVillage();
        aa.otherAddress = address.getOtherAddress();
        aa.hondurasDepartment = address.getHondurasDepartment();
        aa.hondurasFarm = address.getHondurasFarm();
        aa.hondurasMunicipality = address.getHondurasMunicipality();
        aa.hondurasVillage = address.getHondurasVillage();
    }
    
    public void updateAddress(Address a, ApiAddress aa) throws ApiException {
        a.setAddress(aa.address);
        a.setCity(aa.city);
        a.setState(aa.state);
        a.setZip(aa.zip);
        a.setCountry(commonEngine.fetchCountry(aa.getCountry()));
    }

    public void updateAddress(GeoAddress a, ApiAddress aa) throws ApiException {
        a.setAddress(aa.address);
        a.setCity(aa.city);
        a.setState(aa.state);
        a.setZip(aa.zip);
        a.setCountry(commonEngine.fetchCountry(aa.getCountry()));
    }

    public void updateGeoAddress(GeoAddress a, ApiGeoAddress aa) throws ApiException {
    	updateAddress(a, aa);
        a.setLatitude(aa.latitude);
        a.setLongitude(aa.longitude);
    }

    public Address toAddress(ApiAddress aa) throws ApiException {
		if (aa == null) return null;
		
		Address a = new Address();
		updateAddress(a, aa);
		return a;
	}

    public GeoAddress toGeoAddress(ApiGeoAddress aa) throws ApiException {
		if (aa == null) return null;
		
		GeoAddress a = new GeoAddress();
		updateGeoAddress(a, aa);
		return a;
	}
    
}
