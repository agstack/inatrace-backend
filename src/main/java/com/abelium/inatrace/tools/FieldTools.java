package com.abelium.inatrace.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.ClassUtils;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.product.api.ApiProduct;

public class FieldTools {
	
	public static List<Object> getFieldValues(Object object, List<String> fieldNames) throws ApiException {
		if (object == null) return new ArrayList<>();
        
		List<Object> result = new ArrayList<>(fieldNames.size());
		for (String fn : fieldNames) {
			try {
				result.add(PropertyUtils.getNestedProperty(object, fn));
			} catch (Exception e) {
				throw new ApiException(ApiStatus.INVALID_REQUEST, "Error accessing field '" + fn + "'");
			}
		}
		return result;
	}
	
	public static Object getFieldValue(Object object, String fieldName) throws ApiException {
		if (object == null) return null;
        
		try {
			return PropertyUtils.getNestedProperty(object, fieldName);
		} catch (Exception e) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Error accessing field '" + fieldName + "'");
		}
	}

	public static void updateFieldValues(ApiProduct ap, List<String> fieldNames, List<Object> fieldObjects) throws ApiException {
		if (fieldNames.size() != fieldObjects.size()) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "invalid length of fields");
		}
		for (int i = 0; i < fieldObjects.size(); i++) {
			try {
				setPropertyRecursive(ap, fieldNames.get(i), fieldObjects.get(i));
			} catch (Exception e) {
				throw new ApiException(ApiStatus.ERROR, "Error setting field '" + fieldNames.get(i) + "'");
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private static void setPropertyRecursive(Object o, String propName, Object propValue) throws Exception {
		Class<?> propClass = PropertyUtils.getPropertyType(o, propName);
		if (ClassUtils.isPrimitiveOrWrapper(propClass) || ClassUtils.isAssignable(propClass, CharSequence.class)) {
			PropertyUtils.setNestedProperty(o, propName, propValue);
		} else if (propValue instanceof Map) {
			Object prop = PropertyUtils.getProperty(o, propName);
			for (var kv : ((Map<String, Object>) propValue).entrySet()) {
				setPropertyRecursive(prop, kv.getKey(), kv.getValue());
			}
		}
	}
	
}
