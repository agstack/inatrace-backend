package com.abelium.inatrace.components.stockorder;

import com.abelium.inatrace.components.company.api.ApiUserCustomer;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrderHistoryTimelineItem;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.Plot;
import com.abelium.inatrace.db.entities.common.PlotCoordinate;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.common.UserCustomerLocation;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.UserCustomerType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stock-order GeoJSON export must preserve per-plot attribution. The previous
 * implementation merged every plot into a MultiPolygon with empty properties, which made
 * the file unusable for EUDR due diligence.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockOrderGeoDataExportTest extends AbstractMySqlIntegrationTest {

    @Autowired private StockOrderService stockOrderService;
    @Autowired private EntityManager em;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("the export emits one feature per plot with farmer, plot, country and area attributes")
    void exportsOneFeaturePerPlotWithAttribution() throws Exception {
        Country brazil = country("BR", "Brazil");
        UserCustomer farmerA = farmer("FARMER-A", "Ana", "Cacau", brazil);
        UserCustomer farmerB = farmer("FARMER-B", "Beto", "Cacau", brazil);

        Plot polygonA = plot(farmerA, "Highland Plot 01", 12.5, "ha",
                coordinates(-47.966669, -15.732298, -47.961185, -15.866855,
                        -47.772904, -15.855425, -47.825001, -15.715582));
        Plot pointA = plot(farmerA, "Point A", null, null,
                coordinates(-47.900000, -15.800000));
        Plot polygonB = plot(farmerB, "Plot B", null, null,
                coordinates(-46.900000, -14.800000, -46.890000, -14.800000,
                        -46.890000, -14.810000, -46.900000, -14.810000));
        Plot twoPointB = plot(farmerB, "Two Point B", null, null,
                coordinates(-46.700000, -14.700000, -46.710000, -14.710000));
        plot(farmerA, "No coordinates", null, null, List.of());

        em.flush();
        em.clear();

        byte[] bytes = stockOrderService.createGeoJsonFromDeliveries(List.of(timeline(farmerA, farmerB)));

        assertTrue(bytes.length > 0, "the export must contain the plottable farmers");
        JsonNode root = objectMapper.readTree(bytes);

        assertEquals("FeatureCollection", root.path("type").asText());
        assertEquals(4, root.path("features").size(), root.toString());

        JsonNode featureA = featureByPlotId(root, polygonA.getId());
        assertEquals("Polygon", featureA.path("geometry").path("type").asText());
        assertEquals("FARMER-A", featureA.path("properties").path("FarmerInternalId").asText());
        assertEquals(farmerA.getId().longValue(), featureA.path("properties").path("FarmerId").asLong());
        assertEquals("Ana", featureA.path("properties").path("FarmerName").asText());
        assertEquals("Cacau", featureA.path("properties").path("FarmerSurname").asText());
        assertEquals(polygonA.getId().longValue(), featureA.path("properties").path("PlotId").asLong());
        assertEquals("Highland Plot 01", featureA.path("properties").path("PlotName").asText());
        assertEquals("Highland Plot 01", featureA.path("properties").path("ProductionPlace").asText());
        assertEquals("BR", featureA.path("properties").path("ProducerCountry").asText());
        assertEquals(12.5, featureA.path("properties").path("Area").asDouble(), 0.001);
        assertEquals("ha", featureA.path("properties").path("AreaUnit").asText());
        assertEquals("Polygon", featureA.path("properties").path("GeometryType").asText());

        JsonNode ring = featureA.path("geometry").path("coordinates").path(0);
        assertEquals(5, ring.size(), "the polygon ring must be closed: " + ring);
        assertEquals(ring.path(0), ring.path(ring.size() - 1));

        JsonNode pointFeature = featureByPlotId(root, pointA.getId());
        assertEquals("Point", pointFeature.path("geometry").path("type").asText());
        assertEquals(2, pointFeature.path("geometry").path("coordinates").size());
        assertFalse(pointFeature.path("properties").has("Area"),
                "a point has no area: " + pointFeature);

        JsonNode polygonBFeature = featureByPlotId(root, polygonB.getId());
        assertEquals("Polygon", polygonBFeature.path("geometry").path("type").asText());
        assertTrue(polygonBFeature.path("properties").path("Area").asDouble() > 0,
                "polygon area must fall back to the geometry: " + polygonBFeature);

        JsonNode twoPointFeature = featureByPlotId(root, twoPointB.getId());
        assertEquals("MultiPoint", twoPointFeature.path("geometry").path("type").asText());
        assertEquals(2, twoPointFeature.path("geometry").path("coordinates").size());
    }

    @Test
    @DisplayName("a farmer with no plottable geometry produces an empty payload")
    void skipsPlotsWithoutCoordinatesAndReturnsEmptyPayload() throws Exception {
        UserCustomer farmer = farmer("FARMER-EMPTY", "No", "Plots", country("BR", "Brazil"));
        plot(farmer, "No coordinates", null, null, List.of());

        em.flush();
        em.clear();

        byte[] bytes = stockOrderService.createGeoJsonFromDeliveries(List.of(timeline(farmer)));

        assertEquals(0, bytes.length);
    }

    @Test
    @DisplayName("a timeline without producers produces an empty payload")
    void returnsEmptyPayloadWithoutProducers() throws Exception {
        byte[] bytes = stockOrderService.createGeoJsonFromDeliveries(List.of(new ApiStockOrderHistoryTimelineItem()));

        assertEquals(0, bytes.length);
    }

    private JsonNode featureByPlotId(JsonNode root, Long plotId) {
        for (JsonNode feature : root.path("features")) {
            if (plotId.longValue() == feature.path("properties").path("PlotId").asLong()) {
                return feature;
            }
        }
        throw new AssertionError("no feature for plot " + plotId + ": " + root);
    }

    private ApiStockOrderHistoryTimelineItem timeline(UserCustomer... farmers) {
        List<ApiStockOrder> purchaseOrders = new ArrayList<>();
        for (UserCustomer farmer : farmers) {
            ApiUserCustomer producer = new ApiUserCustomer();
            producer.setId(farmer.getId());
            ApiStockOrder order = new ApiStockOrder();
            order.setProducerUserCustomer(producer);
            purchaseOrders.add(order);
        }
        ApiStockOrderHistoryTimelineItem item = new ApiStockOrderHistoryTimelineItem();
        item.setPurchaseOrders(purchaseOrders);
        return item;
    }

    private Country country(String code, String name) {
        Country country = new Country();
        country.setCode(code);
        country.setName(name);
        em.persist(country);
        return country;
    }

    private UserCustomer farmer(String internalId, String name, String surname, Country country) {
        UserCustomer farmer = new UserCustomer();
        farmer.setFarmerCompanyInternalId(internalId);
        farmer.setName(name);
        farmer.setSurname(surname);
        farmer.setType(UserCustomerType.FARMER);

        UserCustomerLocation location = new UserCustomerLocation();
        Address address = new Address();
        address.setCountry(country);
        location.setAddress(address);
        em.persist(location);
        farmer.setUserCustomerLocation(location);
        em.persist(farmer);
        return farmer;
    }

    private Plot plot(UserCustomer farmer, String name, Double size, String unit, List<Double> coordinates) {
        Plot plot = new Plot();
        plot.setFarmer(farmer);
        plot.setPlotName(name);
        plot.setSize(size);
        plot.setUnit(unit);
        em.persist(plot);

        for (int i = 0; i < coordinates.size(); i += 2) {
            PlotCoordinate coordinate = new PlotCoordinate();
            coordinate.setLatitude(coordinates.get(i + 1));
            coordinate.setLongitude(coordinates.get(i));
            coordinate.setPlot(plot);
            plot.getCoordinates().add(coordinate);
            em.persist(coordinate);
        }
        return plot;
    }

    private List<Double> coordinates(double... values) {
        List<Double> result = new ArrayList<>();
        for (double value : values) {
            result.add(value);
        }
        return result;
    }
}
