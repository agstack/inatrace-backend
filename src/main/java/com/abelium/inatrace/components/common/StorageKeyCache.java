package com.abelium.inatrace.components.common;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class StorageKeyCache {

	private static final Cache<String, CacheValue> cache = CacheBuilder.newBuilder().
			expireAfterWrite(Duration.ofMinutes(120)).
			build();

	public static class CacheValue {
		String dbStorageKey;	// Storage key in the database
		Long userId;			// user allowed to access a document, null if public
		String publicLabelUid;
		Long publicProductId;
		
		public CacheValue(String dbStorageKey, Long userId) {
			this.dbStorageKey = dbStorageKey;
			this.userId = userId;
		}
	}

	private static final ThreadLocal<PublicScope> publicScope = new ThreadLocal<>();

	/**
	 * Carries the publication boundary of media links emitted while mapping a public response.
	 * The scope is thread-bound so nested mapper calls do not need to know about the cache.
	 */
	public static final class PublicScope implements AutoCloseable {
		private final PublicScope previous;
		private final String labelUid;
		private final Long productId;

		private PublicScope(PublicScope previous, String labelUid, Long productId) {
			this.previous = previous;
			this.labelUid = labelUid;
			this.productId = productId;
		}

		@Override
		public void close() {
			if (previous == null) {
				publicScope.remove();
			} else {
				publicScope.set(previous);
			}
		}
	}

	public static PublicScope forPublicLabel(String labelUid) {
		PublicScope scope = new PublicScope(publicScope.get(), labelUid, null);
		publicScope.set(scope);
		return scope;
	}

	public static PublicScope forPublicProduct(Long productId) {
		PublicScope scope = new PublicScope(publicScope.get(), null, productId);
		publicScope.set(scope);
		return scope;
	}

	public static String put(String storageKey, Long userId) {
		if (storageKey == null) return null;
		
		String tempKey = UUID.randomUUID().toString();
		CacheValue value = new CacheValue(storageKey, userId);
		PublicScope scope = publicScope.get();
		if (userId == null && scope != null) {
			value.publicLabelUid = scope.labelUid;
			value.publicProductId = scope.productId;
		}
		cache.put(tempKey, value);
		return tempKey;
	}

	public static String get(String tempKey, Long userId) {
		CacheValue value = cache.getIfPresent(tempKey);
		return (value != null && (value.userId == null || value.userId.equals(userId))) ? value.dbStorageKey : null;
	}

	public static CacheValue getPublicValue(String tempKey) {
		CacheValue value = cache.getIfPresent(tempKey);
		return value != null && value.userId == null ? value : null;
	}

	public static String getPublicLabelUid(String tempKey) {
		CacheValue value = getPublicValue(tempKey);
		return value == null ? null : value.publicLabelUid;
	}

	public static Long getPublicProductId(String tempKey) {
		CacheValue value = getPublicValue(tempKey);
		return value == null ? null : value.publicProductId;
	}

	public static void invalidatePublicLabel(String labelUid) {
		cache.asMap().entrySet().removeIf(entry -> entry.getValue().userId == null
				&& Objects.equals(entry.getValue().publicLabelUid, labelUid));
	}

	public static void invalidatePublicProduct(Long productId) {
		cache.asMap().entrySet().removeIf(entry -> entry.getValue().userId == null
				&& Objects.equals(entry.getValue().publicProductId, productId));
	}
	
}
