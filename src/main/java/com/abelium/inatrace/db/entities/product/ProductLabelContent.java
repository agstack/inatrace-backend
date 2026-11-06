package com.abelium.inatrace.db.entities.product;

import com.abelium.inatrace.db.entities.company.Company;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class ProductLabelContent extends ProductContent {
	
	@Version
	private long entityVersion;
	
	/**
	 * company - owner - for view only
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private Company company;
	
	/**
	 * origin - farmer location - Use the pins on the map to mark locations of farmers or suppliers and 
	 * indicate the number of farmers or suppliers you work with directly or indirectly at each location. 
	 */
    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductLocation> originLocations = new HashSet<>();
	
	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public Set<ProductLocation> getOriginLocations() {
		return originLocations;
	}

	public void setOriginLocations(Set<ProductLocation> originLocations) {
		this.originLocations = originLocations;
	}

	// Create a copy for persisting to db
	public static ProductLabelContent fromProduct(Product p) {
		ProductLabelContent plc = new ProductLabelContent();
		plc.setName(p.getName());
		plc.setPhoto(p.getPhoto());
		plc.setDescription(p.getDescription());
		plc.setOriginText(p.getOriginText());
		plc.setProcess(p.getProcess().copy());
		plc.setResponsibility(p.getResponsibility().copy());
		plc.setSustainability(p.getSustainability().copy());
		plc.setSettings(p.getSettings().copy());
		plc.setCompany(p.getCompany()); // no copy -- read only!
		plc.setOriginLocations(p.getOriginLocations().stream().map(location -> {
			ProductLocation copy = location.copy();
			copy.setContent(plc);
			return copy;
		}).collect(Collectors.toSet()));

		// If product has journey points set, copy them to the Product label
		if (p.getJourney() != null) {
			plc.setJourney(p.getJourney().copy());
		}

		plc.setBusinessToCustomerSettings(p.getBusinessToCustomerSettings().copy());
		return plc;
	}
}
