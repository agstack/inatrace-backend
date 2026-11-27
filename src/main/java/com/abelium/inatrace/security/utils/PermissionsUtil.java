package com.abelium.inatrace.security.utils;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.UserRole;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Utility class for checking if user has required permission and/or enrollments.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
public final class PermissionsUtil {

	private PermissionsUtil() {
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Checks if the requesting user is part of a particular company.
	 */
	public static void checkUserIfCompanyEnrolled(List<CompanyUser> companyUsers, CustomUserDetails userToCheck) throws ApiException {
		if (companyUsers.stream().noneMatch(cu -> cu.getUser().getId().equals(userToCheck.getUserId()))) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Unknown user company!");
		}
	}

	/**
	 * Checks if the requesting user is part of a particular company or a System admin.
	 */
	public static void checkUserIfCompanyEnrolledOrSystemAdmin(List<CompanyUser> companyUsers, CustomUserDetails userToCheck) throws ApiException {
		if (!UserRole.SYSTEM_ADMIN.equals(userToCheck.getUserRole()) && companyUsers.stream().noneMatch(cu -> cu.getUser().getId().equals(userToCheck.getUserId()))) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Unknown user company or not system admin!");
		}
	}

	/**
	 * Checks if the requesting user is part of a particular company and is Company or System admin.
	 */
	public static void checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(List<CompanyUser> companyUsers, CustomUserDetails userToCheck) throws ApiException {

		CompanyUser companyUser = findCompanyUser(userToCheck.getUserId(), companyUsers);

		if (!UserRole.SYSTEM_ADMIN.equals(userToCheck.getUserRole()) && !CompanyUserRole.COMPANY_ADMIN.equals(companyUser.getRole())) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "User doesn't have required permission!");
		}
	}

	/**
	 * Checks if the requesting user is part of a at least one company that is connected with one or more products.
	 */
	public static void checkUserIfConnectedWithProducts(List<Product> products, CustomUserDetails userToCheck) throws ApiException {

		if (!isUserConnectedWithProducts(products, userToCheck)) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "User doesn't have required permission!");
		}
	}

	/**
	 * Returns whether the requesting user is part of at least one company connected
	 * with one or more of the provided products.
	 */
	public static boolean isUserConnectedWithProducts(List<Product> products, CustomUserDetails userToCheck) {

		for (Product companyProduct : products) {

			boolean connected = companyProduct.getAssociatedCompanies()
					.stream()
					.map(ProductCompany::getCompany)
					.distinct()
					.map(Company::getUsers)
					.anyMatch(companyUsers -> companyUsers
							.stream()
							.map(CompanyUser::getUser)
							.anyMatch(u -> u.getId().equals(userToCheck.getUserId())));

			if (connected) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Checks if Regional admin is connected with the user's companies for whom requests access.
	 */
	public static void checkRegionalAdminIfConnectedWithUser(List<Long> regionalAdminCompanyIds, List<Long> userCompanyIds) throws ApiException {
		if (!CollectionUtils.containsAny(userCompanyIds, regionalAdminCompanyIds)) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Regional admin not authorized!");
		}
	}

	private static CompanyUser findCompanyUser(Long userId, List<CompanyUser> companyUsers) throws ApiException {
		return companyUsers.stream()
				.filter(cu -> cu.getUser().getId().equals(userId)).findAny()
				.orElseThrow(() -> new ApiException(ApiStatus.UNAUTHORIZED, "Unknown user company!"));
	}

}
