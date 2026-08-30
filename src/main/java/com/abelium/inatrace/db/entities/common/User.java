package com.abelium.inatrace.db.entities.common;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

@Entity
@Audited
@Table(indexes = { @Index(columnList = "status"), @Index(columnList = "email", unique = true) })
public class User extends TimestampEntity {

	@Version
	private long entityVersion;
	
    /**
     * Email - username
     */
    @Column(unique = true, length = Lengths.EMAIL)
    private String email;

	/**
	 * password
	 */
	@NotAudited
	private String password;    
    
	/**
	 * status - unconfirmed first, confirmed_email after email confirmation, 
	 * active after admin activation, deactivated
	 */
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM, nullable = false)
	private UserStatus status = UserStatus.UNCONFIRMED;

	/**
	 * name -> to profile?
	 */
	@Column(length = Lengths.NAME)
	private String name;

	/**
	 * surname -> to profile?
	 */
	@Column(length = Lengths.SURNAME)
	private String surname;
	
	/**
	 * language
	 */
	@Enumerated(EnumType.STRING)
    @Column(nullable = false, length = Lengths.ENUM)
	private Language language = Language.EN;

	/**
	 * user role
	 */
    @Enumerated(EnumType.STRING)
    @Column(length = Lengths.ENUM)
	private UserRole role = UserRole.USER;

	/**
	 * User connected companies
	 */
	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@NotAudited
	private Set<CompanyUser> userCompanies;

    public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSurname() {
		return surname;
	}

	public void setSurname(String surname) {
		this.surname = surname;
	}

	public Language getLanguage() {
		return language;
	}

	public void setLanguage(Language language) {
		this.language = language;
	}

	public UserRole getRole() {
		return role;
	}

	public void setRole(UserRole role) {
		this.role = role;
	}

	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Collections.singletonList(getRole());
	}

	public Set<CompanyUser> getUserCompanies() {
		return userCompanies;
	}

	public void setUserCompanies(Set<CompanyUser> userCompanies) {
		this.userCompanies = userCompanies;
	}

}
