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
		PublicScopeType publicScopeType;
		String publicLabelUid;
		Long publicProductId;
		String publicQrTag;
		Long publicCertificationId;
		
		public CacheValue(String dbStorageKey, Long userId) {
			this.dbStorageKey = dbStorageKey;
			this.userId = userId;
		}

		public String getDbStorageKey() {
			return dbStorageKey;
		}

		public PublicScopeType getPublicScopeType() {
			return publicScopeType;
		}

		public String getPublicLabelUid() {
			return publicLabelUid;
		}

		public Long getPublicProductId() {
			return publicProductId;
		}

		public String getPublicQrTag() {
			return publicQrTag;
		}

		public Long getPublicCertificationId() {
			return publicCertificationId;
		}
	}

	public enum PublicScopeType {
		LABEL,
		PRODUCT,
		QR_CERTIFICATE
	}

	private static final ThreadLocal<PublicScope> publicScope = new ThreadLocal<>();

	/**
	 * Carries the publication boundary of media links emitted while mapping a public response.
	 * The scope is thread-bound so nested mapper calls do not need to know about the cache.
	 */
	public static final class PublicScope implements AutoCloseable {
		private final PublicScope previous;
		private final PublicScopeType type;
		private final String labelUid;
		private final Long productId;
		private final String qrTag;
		private final Long certificationId;

		private PublicScope(PublicScope previous, PublicScopeType type, String labelUid, Long productId,
				String qrTag, Long certificationId) {
			this.previous = previous;
			this.type = type;
			this.labelUid = labelUid;
			this.productId = productId;
			this.qrTag = qrTag;
			this.certificationId = certificationId;
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
		PublicScope scope = new PublicScope(publicScope.get(), PublicScopeType.LABEL, labelUid, null, null, null);
		publicScope.set(scope);
		return scope;
	}

	public static PublicScope forPublicProduct(Long productId) {
		PublicScope scope = new PublicScope(publicScope.get(), PublicScopeType.PRODUCT, null, productId, null, null);
		publicScope.set(scope);
		return scope;
	}

	public static PublicScope forPublicQrCertificate(String qrTag, Long certificationId) {
		PublicScope scope = new PublicScope(publicScope.get(), PublicScopeType.QR_CERTIFICATE, null, null, qrTag, certificationId);
		publicScope.set(scope);
		return scope;
	}

	public static String put(String storageKey, Long userId) {
		return userId == null ? putPublic(storageKey) : putPrivate(storageKey, userId);
	}

	public static String putPrivate(String storageKey, Long userId) {
		if (storageKey == null) return null;
		if (userId == null) throw new IllegalArgumentException("A private storage key requires a user");

		String tempKey = UUID.randomUUID().toString();
		cache.put(tempKey, new CacheValue(storageKey, userId));
		return tempKey;
	}

	public static String putPublic(String storageKey) {
		if (storageKey == null) return null;

		PublicScope scope = publicScope.get();
		if (scope == null) throw new IllegalStateException("A public storage key requires an explicit public scope");

		String tempKey = UUID.randomUUID().toString();
		CacheValue value = new CacheValue(storageKey, null);
		value.publicScopeType = scope.type;
		value.publicLabelUid = scope.labelUid;
		value.publicProductId = scope.productId;
		value.publicQrTag = scope.qrTag;
		value.publicCertificationId = scope.certificationId;
		cache.put(tempKey, value);
		return tempKey;
	}

	public static String getPrivate(String tempKey, Long userId) {
		CacheValue value = cache.getIfPresent(tempKey);
		return (value != null && value.userId != null && value.userId.equals(userId)) ? value.dbStorageKey : null;
	}

	public static String getPublic(String tempKey) {
		CacheValue value = getPublicValue(tempKey);
		return value != null && value.publicScopeType != null ? value.dbStorageKey : null;
	}

	public static CacheValue getPublicValue(String tempKey) {
		CacheValue value = cache.getIfPresent(tempKey);
		return value != null && value.userId == null ? value : null;
	}

	public static String getPublicLabelUid(String tempKey) {
		CacheValue value = getPublicValue(tempKey);
		return value == null ? null : value.getPublicLabelUid();
	}

	public static Long getPublicProductId(String tempKey) {
		CacheValue value = getPublicValue(tempKey);
		return value == null ? null : value.getPublicProductId();
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
