package com.abelium.inatrace.components.user;

import com.abelium.inatrace.api.ApiDefaultResponse;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.ApiUserRole;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.components.company.CompanyQueries;
import com.abelium.inatrace.components.mail.MailEngine;
import com.abelium.inatrace.components.user.api.*;
import com.abelium.inatrace.components.user.types.UserAction;
import com.abelium.inatrace.db.entities.auth.AuthenticationToken;
import com.abelium.inatrace.db.entities.auth.ConfirmationToken;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.PasswordTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.types.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.Function;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Torpedo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Lazy
@Service
public class UserService extends BaseService {

    @Value("${INATrace.loginManager.mail}")
    private String loginManagerMail;
    
    @Value("${INATrace.emailConfirmation.url}")
    private String emailConfirmationUrl;
    
    @Value("${INATrace.passwordReset.url}")
    private String passwordResetUrl;

	@Value("${INATrace.env}")
	private String env;
    
    @Autowired
    private MailEngine mailEngine;
    
    @Autowired
    private NotificationService notificationEngine;
    
    @Autowired
    private UserQueries userQueries;

    @Autowired
    private CompanyQueries companyQueries;
    
    @Autowired
    private TokenService tokenEngine;
    
	@Transactional
	public User fetchUserByEmail(String email) {
		return Queries.getUniqueBy(em, User.class, User::getEmail, email);
	}

	public User fetchUserById(Long id) throws ApiException {
		User user = Queries.get(em, User.class, id);
		if (user == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user ID");
		}

		return user;
	}

	private UserCustomer fetchUserCustomerById(Long id) throws ApiException {

		UserCustomer userCustomer = Queries.get(em, UserCustomer.class, id);
		if (userCustomer == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user customer ID");
		}

		return userCustomer;
	}

	@Transactional
	public ResponseEntity<ApiDefaultResponse> login(ApiLoginRequest loginRequest) throws ApiException {
		User user = Queries.getUniqueBy(em, User.class, User::getEmail, loginRequest.getUsername());
		
		if (user == null) {
			throw new ApiException(ApiStatus.AUTH_ERROR, "Invalid credentials");
		}
		checkUserMayStartSession(user);
		if (!new BCryptPasswordEncoder().matches(loginRequest.password, user.getPassword())) {
			throw new ApiException(ApiStatus.AUTH_ERROR, "Invalid credentials");
		}
		return loginUser(user);
	}

	/**
	 * Whether an account is allowed to hold a session: only an ACTIVE one.
	 *
	 * <p>Administrator approval is the only admission control in a system that lets anyone
	 * register. Stated positively so that a status added later fails closed; a deny-list would
	 * let it through by default. {@link AuthenticationToken#isValid()} applies the same rule to
	 * refresh tokens.
	 */
	private boolean mayStartSession(User user) {
		return user.getStatus() == UserStatus.ACTIVE;
	}

	private void checkUserMayStartSession(User user) throws ApiException {
		if (!mayStartSession(user)) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Not confirmed or disabled");
		}
	}

	// Call from @Transactional method with fetched user
	private ResponseEntity<ApiDefaultResponse> loginUser(User user) throws ApiException {
		// Every path that mints cookies ends here, so the gate lives here too. Password reset
		// used to reach this method with a CONFIRMED_EMAIL user and walk past the check that
		// login applied one layer up; a future caller must not be able to repeat that.
		checkUserMayStartSession(user);

		AuthenticationToken authenticationToken = Queries.getUniqueBy(em, AuthenticationToken.class, 
				AuthenticationToken::getUser, user);
		String accessToken = tokenEngine.createAccessToken(user);
		String refreshToken = tokenEngine.createRefreshToken(user);
		
		if (authenticationToken != null) {
			authenticationToken.updateToken(refreshToken, tokenEngine.getRefreshTokenExpirationSec());
		} else {
			authenticationToken = new AuthenticationToken(user);
			authenticationToken.updateToken(refreshToken, tokenEngine.getRefreshTokenExpirationSec());
			em.persist(authenticationToken);
		}
		
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.add(HttpHeaders.SET_COOKIE, tokenEngine.createAccessCookie(accessToken)); 
		responseHeaders.add(HttpHeaders.SET_COOKIE, tokenEngine.createRefreshCookie(refreshToken));
		return ResponseEntity.ok().headers(responseHeaders).body(new ApiDefaultResponse());
	}
	
	@Transactional
	public ResponseEntity<ApiDefaultResponse> logout(HttpServletRequest request) throws ApiException {
		Cookie[] cookies = request.getCookies();

		if (cookies == null) {
			return ResponseEntity.ok().body(new ApiDefaultResponse());
		}
		
		HttpHeaders responseHeaders = new HttpHeaders();
		
		Optional<Cookie> refreshCookie = tokenEngine.getRefreshCookie(cookies);
		if (refreshCookie.isPresent()) {
			String dbToken = AuthenticationToken.encrypt(refreshCookie.get().getValue());
			AuthenticationToken authToken = Queries.getUniqueBy(em, AuthenticationToken.class, AuthenticationToken::getToken, dbToken);
		
			if (authToken != null) {
				authToken.setStatus(Status.DISABLED);
			}
			responseHeaders.add(HttpHeaders.SET_COOKIE, tokenEngine.createRemoveRefreshCookie());
		}
		
		Optional<Cookie> accessCookie = tokenEngine.getAccessCookie(cookies);
		if (accessCookie.isPresent()) {
			responseHeaders.add(HttpHeaders.SET_COOKIE, tokenEngine.createRemoveAccessCookie());
		}

		return ResponseEntity.ok().headers(responseHeaders).body(new ApiDefaultResponse());
	}

	@Transactional
	public AuthenticationToken fetchAndValidateToken(String token) throws ApiException {
		AuthenticationToken authenticationToken = Queries.getUniqueBy(em, AuthenticationToken.class, 
			AuthenticationToken::getToken, AuthenticationToken.encrypt(token));
		if (authenticationToken == null || !authenticationToken.isValid()) {
			throw new ApiException(ApiStatus.AUTH_ERROR, "Invalid authentication token");
		}
		return authenticationToken;
	}

	@Transactional
	public User fetchAndValidateUser(String token) throws ApiException {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<AuthenticationToken> root = query.from(AuthenticationToken.class);
        Predicate pred = cb.equal(root.get("token"), AuthenticationToken.encrypt(token));
        query.select(root.get("user")).where(pred);
        return em.createQuery(query).getSingleResult();
	}
	
	@Transactional
	public void requestResetPassword(String email) throws ApiException {
		User user = Queries.getUniqueBy(em, User.class, User::getEmail, email);
		if (user == null) {
			return;
		}
		if (user.getStatus() == UserStatus.DEACTIVATED || user.getStatus() == UserStatus.UNCONFIRMED) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user status");
		}
		
        Pair<ConfirmationToken, String> ctPair = 
				ConfirmationToken.create(user, ConfirmationTokenType.PASSWORD_RESET);
        em.persist(ctPair.getLeft());
        
        String htmlMail = notificationEngine.createPasswordResetEmail(passwordResetUrl + ctPair.getRight());
		mailEngine.sendSimpleMailAsync(user.getEmail(), "",
			"INATrace password reset",
			"",
			htmlMail);        
	}

	@Transactional
	public void createUser(ApiCreateUserRequest createUserRequest) throws ApiException {
        
        if (!PasswordTools.isPasswordComplex(createUserRequest.password)) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR, "Invalid password");
        }
        
        if (Queries.getUniqueBy(em, User.class, User::getEmail, createUserRequest.email) != null) {
        	throw new ApiException(ApiStatus.INVALID_REQUEST, "User already exists");
        }
        
        User user = new User();
        user.setEmail(createUserRequest.email);
        user.setPassword(new BCryptPasswordEncoder().encode(createUserRequest.password));
        user.setName(createUserRequest.name);
        user.setSurname(createUserRequest.surname);
        user.setLanguage(createUserRequest.language);
        em.persist(user);

		// If deployment environment is not 'DEMO', create confirmation email
		if (!"DEMO".equals(env)) {

			Pair<ConfirmationToken, String> ctPair =
					ConfirmationToken.create(user, ConfirmationTokenType.CONFIRM_EMAIL);
			em.persist(ctPair.getLeft());

			mailEngine.sendSimpleMailAsync(loginManagerMail, "", "New INATrace registration",
					"",
					"<p>New registration in INATrace system</p><br/>" +
							"<p>  User's email: " + createUserRequest.email + "</p><br/>" +
							"<p>Please approve the registrant as soon as possible</p>"
			);

			String htmlMail = notificationEngine.createEmailConfirmationEmail(createUserRequest.name,
					createUserRequest.surname, emailConfirmationUrl + ctPair.getRight());
			mailEngine.sendSimpleMailAsync(createUserRequest.email, "",
					"INATrace registration",
					"",
					htmlMail);
		} else {

			// In 'DEMO' environment, set the new user as ACTIVE and add it to the demo companies
			user.setStatus(UserStatus.ACTIVE);

			// Join the new user to all the active companies in the system
			List<Company> companies = companyQueries.fetchAllActiveCompanies();
			companies.forEach(c -> {
				CompanyUser companyUser = new CompanyUser();
				companyUser.setUser(user);
				companyUser.setCompany(c);
				companyUser.setRole(CompanyUserRole.COMPANY_ADMIN);
				em.persist(companyUser);
			});
		}
	}

	public ApiPaginatedList<ApiUserBase> adminListUsers(ApiListUsersRequest request) {
		return PaginationTools.createPaginatedResponse(em, request, () -> adminUserListQueryObject(request),
				UserApiTools::toApiUserBase);
	}

	public ApiPaginatedList<ApiUserBase> regionalAdminListUsers(CustomUserDetails authUser, ApiListUsersRequest request) {
		return PaginationTools.createPaginatedResponse1(em, request, () -> regionalAdminUserListQueryObject(
						authUser.getUserId(), request),
				UserApiTools::toApiUserBase);
	}
	
    private User adminUserListQueryObject(ApiListUsersRequest request) {

        User uProxy = Torpedo.from(User.class);
        
        OnGoingLogicalCondition condition = Torpedo.condition(); // .Torpedo conditions = new ArrayList<>();
        if (StringUtils.isNotBlank(request.query)) {
        	OnGoingLogicalCondition queryCondition = 
    				Torpedo.condition(uProxy.getName()).like().any(request.query).
        				   or(uProxy.getSurname()).like().any(request.query).
        				   or(uProxy.getEmail()).like().any(request.query);
        	condition = condition.and(queryCondition);
        	
        } else {
        	if (StringUtils.isNotBlank(request.email)) {
        		condition = condition.and(uProxy.getEmail()).like().any(request.email);
        	}
        	if (StringUtils.isNotBlank(request.surname)) {
        		condition = condition.and(uProxy.getSurname()).like().any(request.surname);
        	}
        }
        if (request.role != null) {
            condition = condition.and(Torpedo.condition(uProxy.getRole()).eq(request.role));
        }
        if (request.status != null) {
            condition = condition.and(Torpedo.condition(uProxy.getStatus()).eq(request.status));
        }
        Torpedo.where(condition);
        switch (request.sortBy) {
	        case "email": QueryTools.orderBy(request.sort, uProxy.getEmail()); break;
	        case "surname": QueryTools.orderBy(request.sort, uProxy.getSurname()); break;
	        default: QueryTools.orderBy(request.sort, uProxy.getName());
        }
        return uProxy;
    }

	private Function<User> regionalAdminUserListQueryObject(Long userId, ApiListUsersRequest request) {

		List<Long> companyIds = companyQueries.fetchCompanyIdsForUser(userId, List.of(CompanyStatus.ACTIVE));

		User uProxy = Torpedo.from(User.class);
		CompanyUser cuProxy = Torpedo.leftJoin(uProxy.getUserCompanies());

		OnGoingLogicalCondition condition = Torpedo.condition();

		OnGoingLogicalCondition connectedOrUnassignedCondition = Torpedo
				.condition(uProxy.getUserCompanies()).isEmpty()
				.or(cuProxy.getCompany().getId()).in(companyIds);
		condition = condition.and(connectedOrUnassignedCondition);

		OnGoingLogicalCondition statusCondition = Torpedo
				.condition(uProxy.getStatus()).eq(UserStatus.ACTIVE)
				.or(uProxy.getStatus()).eq(UserStatus.CONFIRMED_EMAIL)
				.or(uProxy.getStatus()).eq(UserStatus.UNCONFIRMED);
		condition = condition.and(statusCondition);

		if (StringUtils.isNotBlank(request.query)) {
			OnGoingLogicalCondition queryCondition = Torpedo
					.condition(uProxy.getName()).like().any(request.query)
					.or(uProxy.getSurname()).like().any(request.query)
					.or(uProxy.getEmail()).like().any(request.query);
			condition = condition.and(queryCondition);
		} else {
			if (StringUtils.isNotBlank(request.email)) {
				condition = condition.and(uProxy.getEmail()).like().any(request.email);
			}
			if (StringUtils.isNotBlank(request.surname)) {
				condition = condition.and(uProxy.getSurname()).like().any(request.surname);
			}
		}

		if (request.status != null) {
			condition = condition.and(Torpedo.condition(uProxy.getStatus()).eq(request.status));
		}

		if (request.role != null) {
			condition = condition.and(Torpedo.condition(uProxy.getRole()).eq(request.role));
		}

		Torpedo.where(condition);

		switch (request.sortBy) {
			case "email": QueryTools.orderBy(request.sort, uProxy.getEmail()); break;
			case "surname": QueryTools.orderBy(request.sort, uProxy.getSurname()); break;
			default: QueryTools.orderBy(request.sort, uProxy.getName());
		}

		return Torpedo.distinct(uProxy);
	}

    private Function<User> userListQueryObject(Long userId, ApiListUsersRequest request) {

    	List<Long> companyIds = companyQueries.fetchCompanyIdsForUser(userId, List.of(CompanyStatus.ACTIVE));
    	
    	CompanyUser cuProxy = Torpedo.from(CompanyUser.class);
        
        OnGoingLogicalCondition condition = Torpedo.condition(); // .Torpedo conditions = new ArrayList<>();
        condition = condition.and(cuProxy.getCompany().getId()).in(companyIds); 
        if (StringUtils.isNotBlank(request.query)) {
        	OnGoingLogicalCondition queryCondition = 
    				Torpedo.condition(cuProxy.getUser().getName()).like().any(request.query).
        				   or(cuProxy.getUser().getSurname()).like().any(request.query).
        				   or(cuProxy.getUser().getEmail()).like().any(request.query);
        	condition = condition.and(queryCondition);
        } else {
        	if (StringUtils.isNotBlank(request.email)) {
        		condition = condition.and(cuProxy.getUser().getEmail()).like().any(request.email);
        	}
        	if (StringUtils.isNotBlank(request.surname)) {
        		condition = condition.and(cuProxy.getUser().getSurname()).like().any(request.surname);
        	}
        }
        if (request.role != null) {
            condition = condition.and(Torpedo.condition(cuProxy.getUser().getRole()).eq(request.role));
        }
        if (request.status != null) {
            condition = condition.and(Torpedo.condition(cuProxy.getUser().getStatus()).eq(request.status));
        }
        Torpedo.where(condition);
        switch (request.sortBy) {
	        case "email": QueryTools.orderBy(request.sort, cuProxy.getUser().getEmail()); break;
	        case "surname": QueryTools.orderBy(request.sort, cuProxy.getUser().getSurname()); break;
	        default: QueryTools.orderBy(request.sort, cuProxy.getUser().getName());
        }
        return Torpedo.distinct(cuProxy.getUser());
    }
    
    @Transactional
	public ApiPaginatedList<ApiUserBase> listUsers(CustomUserDetails authUser, ApiListUsersRequest request) {
    	return PaginationTools.createPaginatedResponse1(em, request, () -> userListQueryObject(authUser.getUserId(), request), 
    			UserApiTools::toApiUserBase); 
	}

    @Transactional
	public ResponseEntity<ApiDefaultResponse> confirmEmail(String token) throws ApiException {
		ConfirmationToken confirmationToken = Queries.getUniqueBy(em, ConfirmationToken.class, 
				ConfirmationToken::getToken, ConfirmationToken.encrypt(token));
		if (confirmationToken == null || !confirmationToken.isValidEmailConfirmationToken()) {
			throw new ApiException(ApiStatus.AUTH_ERROR, "Invalid confirmation token");
		}
		confirmationToken.getUser().setStatus(UserStatus.CONFIRMED_EMAIL);
		confirmationToken.setStatus(Status.DISABLED);
		return ResponseEntity.ok().body(new ApiDefaultResponse());
	}
    
    @Transactional
	public ResponseEntity<ApiDefaultResponse> resetPassword(ApiResetPasswordRequest request) throws ApiException {
		ConfirmationToken confirmationToken = Queries.getUniqueBy(em, ConfirmationToken.class, 
				ConfirmationToken::getToken, ConfirmationToken.encrypt(request.token));
		if (confirmationToken == null || !confirmationToken.isValidPasswordResetToken()) {
			throw new ApiException(ApiStatus.AUTH_ERROR, "Invalid password reset token");
		}
        if (!PasswordTools.isPasswordComplex(request.password)) {
            throw new ApiException(ApiStatus.VALIDATION_ERROR, "Invalid password");
        }
        confirmationToken.setStatus(Status.DISABLED);
        confirmationToken.getUser().setPassword(new BCryptPasswordEncoder().encode(request.password));

        // The password is changed either way; the session is only granted to an account that could
        // have logged in with it. An account still awaiting approval gets a plain success and has
        // to come back through login once an administrator activates it. The frontend sends the
        // user to /home straight after a reset, so approved users keep that flow.
        if (!mayStartSession(confirmationToken.getUser())) {
            return ResponseEntity.ok().body(new ApiDefaultResponse());
        }
		return loginUser(confirmationToken.getUser());
	}
    

	public void activateUser(User user) throws ApiException {
		if (user.getStatus() != UserStatus.DEACTIVATED && user.getStatus() != UserStatus.CONFIRMED_EMAIL) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user status");
		}
		user.setStatus(UserStatus.ACTIVE);
		
        String htmlMail = notificationEngine.createConfirmationEmail(user.getName(), user.getSurname());
		mailEngine.sendSimpleMailAsync(user.getEmail(), "",
			"INATrace sign-up",
			"",
			htmlMail);
	}
	
	public void confirmUserEmail(User user) throws ApiException {
		if (user.getStatus() != UserStatus.UNCONFIRMED) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user status");
		}
		user.setStatus(UserStatus.CONFIRMED_EMAIL);
	}	
	

	public void deactivateUser(User user) throws ApiException {
		if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.CONFIRMED_EMAIL) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user status");
		}
		if (user.getRole() == UserRole.SYSTEM_ADMIN) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Cannot change status");
		}
		user.setStatus(UserStatus.DEACTIVATED);
	}
	
	private void setUserSystemAdmin(User user) throws ApiException {
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user status");
		}
		if (user.getRole() == UserRole.SYSTEM_ADMIN) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user role");
		}
		user.setRole(UserRole.SYSTEM_ADMIN);
	}

	private void setUserRegionalAdmin(User user) throws ApiException {
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user status");
		}
		if (user.getRole() == UserRole.REGIONAL_ADMIN) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user role");
		}
		user.setRole(UserRole.REGIONAL_ADMIN);
	}

	private void unsetUserSystemAdmin(User user) throws ApiException {
		if (user.getRole() != UserRole.SYSTEM_ADMIN) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user role");
		}
		user.setRole(UserRole.USER);
	}

	private void unsetUserRegionalAdmin(User user) throws ApiException {
		if (user.getRole() != UserRole.REGIONAL_ADMIN) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user role");
		}
		user.setRole(UserRole.USER);
	}
	
    @Transactional
	public ApiUserGet getProfileForUser(Long userId) throws ApiException {

    	User user = userQueries.fetchUser(userId);
    	List<UserAction> actions = new ArrayList<>();

    	actions.add(UserAction.VIEW_USER_PROFILE);
		actions.add(UserAction.UPDATE_USER_PROFILE);
		
		List<Long> companyIds = companyQueries.fetchCompanyIdsForUser(userId, 
				Arrays.asList(CompanyStatus.ACTIVE, CompanyStatus.REGISTERED));

		List<Long> companyIdsAdmin = companyQueries.fetchCompanyIdsForUserAdmin(userId);
		
		if (companyIds.size() == 0) actions.add(UserAction.CREATE_COMPANY);
		
		return UserApiTools.toApiUserGet(user, actions, companyIds, companyIdsAdmin);
	}
    
    @Transactional
	public ApiUser getProfileForAdmin(CustomUserDetails authUser, Long userId) throws ApiException {

    	User user = userQueries.fetchUser(userId);
    	List<UserAction> actions = new ArrayList<>();
		
		List<Long> companyIds = companyQueries.fetchCompanyIdsForUser(userId, 
				Arrays.asList(CompanyStatus.ACTIVE, CompanyStatus.REGISTERED));

	    List<Long> companyIdsAdmin = companyQueries.fetchCompanyIdsForUserAdmin(userId);

		// If regional admin, check that it's connected with the user profile (connected through company)
		if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {

			// If user is assigned to any company, check that the Regional admin has access to these companies
			if (!user.getUserCompanies().isEmpty()) {

				// Company IDs where the Regional admin is enrolled into
				List<Long> regionalAdminCompanyIds = companyQueries.fetchCompanyIdsForUser(authUser.getUserId(),
						Arrays.asList(CompanyStatus.ACTIVE, CompanyStatus.REGISTERED));

				PermissionsUtil.checkRegionalAdminIfConnectedWithUser(regionalAdminCompanyIds, companyIds);

				// Filter the user's company IDs lists (only the company IDs where both the User and the Regional admin are enrolled into should be present)
				companyIds = companyIds.stream().filter(cID -> regionalAdminCompanyIds.stream().anyMatch(cID::equals)).collect(
						Collectors.toList());

				companyIdsAdmin = companyIdsAdmin.stream().filter(cAdminID -> regionalAdminCompanyIds.stream().anyMatch(cAdminID::equals)).collect(
						Collectors.toList());
			}
		}

	    actions.add(UserAction.VIEW_USER_PROFILE);
	    actions.add(UserAction.UPDATE_USER_PROFILE);

		if (!authUser.getUserId().equals(userId)) {
			switch (user.getStatus()) {
				case ACTIVE: 
					actions.add(UserAction.DEACTIVATE_USER);

					// If Regional admin don't set any additional actions
					if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
						break;
					}

					if (user.getRole() == UserRole.SYSTEM_ADMIN) {
						actions.add(UserAction.UNSET_USER_SYSTEM_ADMIN);
					} else if (user.getRole() == UserRole.REGIONAL_ADMIN) {
						actions.add(UserAction.UNSET_USER_REGIONAL_ADMIN);
					} else {
						actions.add(UserAction.SET_USER_SYSTEM_ADMIN);
						actions.add(UserAction.SET_USER_REGIONAL_ADMIN);
					}

					break;
				case CONFIRMED_EMAIL: actions.addAll(Arrays.asList(UserAction.DEACTIVATE_USER, UserAction.ACTIVATE_USER)); break;
				case DEACTIVATED: actions.add(UserAction.ACTIVATE_USER); break;
				default:
			}
		}
		
		return UserApiTools.toApiUserGet(user, actions, companyIds, companyIdsAdmin);
	}

    @Transactional
	public void updateProfile(CustomUserDetails authUser, Long userId, ApiUserUpdate request) throws ApiException {

		// Check if Regional admin has access to the requested user
		if (!authUser.getUserId().equals(userId) && authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
			PermissionsUtil.checkRegionalAdminIfConnectedWithUser(
					companyQueries.fetchCompanyIdsForUser(authUser.getUserId(), Arrays.asList(CompanyStatus.ACTIVE, CompanyStatus.REGISTERED)),
					companyQueries.fetchCompanyIdsForUser(userId, Arrays.asList(CompanyStatus.ACTIVE, CompanyStatus.REGISTERED)));
		}

    	User user = userQueries.fetchUser(userId);
		user.setName(request.name);
		user.setSurname(request.surname);
		user.setLanguage(request.language);
	}

    @Transactional
	public void changeUserStatus(CustomUserDetails authUser, ApiUserRole request, UserAction action) throws ApiException {

    	if (authUser.getUserId().equals(request.id)) {
    		throw new ApiException(ApiStatus.INVALID_REQUEST, "Cannot change status/role for this user");
    	}

		User user = userQueries.fetchUser(request.id);

		// Check if Regional admin has access to the requested user
		if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {

			// Check if action is 'DEACTIVATE_USER' - this is not allowed by the Regional admin
			if (action == UserAction.DEACTIVATE_USER) {
				throw new ApiException(ApiStatus.UNAUTHORIZED, "Regional admin not authorized!");
			}

			// If user is assigned to any company, check that the Regional admin has access to these companies
			if (!user.getUserCompanies().isEmpty()) {
				PermissionsUtil.checkRegionalAdminIfConnectedWithUser(
						companyQueries.fetchCompanyIdsForUser(authUser.getUserId(), Arrays.asList(CompanyStatus.ACTIVE, CompanyStatus.REGISTERED)),
						companyQueries.fetchCompanyIdsForUser(request.id, Arrays.asList(CompanyStatus.ACTIVE, CompanyStatus.REGISTERED)));
			}
		}
		
		switch (action) {
			case ACTIVATE_USER: activateUser(user); break;
			case DEACTIVATE_USER: deactivateUser(user); break;
			case SET_USER_SYSTEM_ADMIN: setUserSystemAdmin(user); break;
			case UNSET_USER_SYSTEM_ADMIN: unsetUserSystemAdmin(user); break;
			case SET_USER_REGIONAL_ADMIN: setUserRegionalAdmin(user); break;
			case UNSET_USER_REGIONAL_ADMIN: unsetUserRegionalAdmin(user); break;
			case CONFIRM_USER_EMAIL: confirmUserEmail(user); break;
			default: throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid user status");
		}
	}

    @Transactional
	public ResponseEntity<ApiDefaultResponse> refreshAuthentication(HttpServletRequest request) throws ApiException {
		String token = tokenEngine.getRefreshToken(request.getCookies());
		
		try {
			AuthenticationToken dbToken = fetchAndValidateToken(token);
			String accessToken = tokenEngine.createAccessToken(dbToken.getUser());
			HttpHeaders responseHeaders = new HttpHeaders();
	
			responseHeaders.add(HttpHeaders.SET_COOKIE, tokenEngine.createAccessCookie(accessToken));
			return ResponseEntity.ok().headers(responseHeaders).body(new ApiDefaultResponse());
		} catch (Exception e) {
			throw new ApiException(ApiStatus.AUTH_ERROR, "Invalid or expired refresh token");
		}
	}
}
