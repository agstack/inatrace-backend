package com.abelium.inatrace.db.entities.product;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.product.enums.FairPricesUnit;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table
public class BusinessToCustomerSettings extends TimestampEntity {

    @Version
    private Long entityVersion;

    @Column(name = "primary_color", length = Lengths.HEX_COLOR)
    private String primaryColor;

    @Column(name = "secondary_color", length = Lengths.HEX_COLOR)
    private String secondaryColor;

    @Column(name = "tertiary_color", length = Lengths.HEX_COLOR)
    private String tertiaryColor;

    @Column(name = "quaternary_color", length = Lengths.HEX_COLOR)
    private String quaternaryColor;

    @Column(name = "product_title_color", length = Lengths.HEX_COLOR)
    private String productTitleColor;

    @Column(name = "heading_color", length = Lengths.HEX_COLOR)
    private String headingColor;

    @Column(name = "text_color", length = Lengths.HEX_COLOR)
    private String textColor;

    @Column(name = "tab_fair_prices")
    private Boolean tabFairPrices;

    @Column(name = "tab_producers")
    private Boolean tabProducers;

    @Column(name = "tab_quality")
    private Boolean tabQuality;

    @Column(name = "tab_feedback")
    private Boolean tabFeedback;

    @Column(name = "order_fair_prices")
    private Long orderFairPrices;

    @Column(name = "order_producers")
    private Long orderProducers;

    @Column(name = "order_quality")
    private Long orderQuality;

    @Column(name = "order_feedback")
    private Long orderFeedback;

    @Column(name = "graphic_fair_prices")
    private Boolean graphicFairPrices;

    @Column(name = "graphic_increase_of_income")
    private Boolean graphicIncreaseOfIncome;

    @Column(name = "graphic_quality")
    private Boolean graphicQuality;

    @Column(name = "graphic_price_to_producer")
    @Enumerated(EnumType.STRING)
    private FairPricesUnit graphicPriceToProducer;

    @Column(name = "graphic_farm_gate_price")
    @Enumerated(EnumType.STRING)
    private FairPricesUnit graphicFarmGatePrice;

    @Column(name = "manual_farm_gate_price")
    private BigDecimal manualFarmGatePrice;

    @Column(name = "manual_producer_price")
    private BigDecimal manualProducerPrice;

    @Column(name = "container_size")
    private BigDecimal containerSize;

    @Column(name = "world_market")
    private BigDecimal worldMarket;

    @Column(name = "fair_trade")
    private BigDecimal fairTrade;

    @Column(name = "average_region_farm_gate_price")
    private BigDecimal averageRegionFarmGatePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productFont_id", nullable = true)
    private Document productFont;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "textFont_id", nullable = true)
    private Document textFont;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landingPageImage_id", nullable = true)
    private Document landingPageImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landingPageBackgroundImage_id", nullable = true)
    private Document landingPageBackgroundImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "headerBackgroundImage_id", nullable = true)
    private Document headerBackgroundImage;

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public String getTertiaryColor() {
        return tertiaryColor;
    }

    public void setTertiaryColor(String tertiaryColor) {
        this.tertiaryColor = tertiaryColor;
    }

    public String getQuaternaryColor() {
        return quaternaryColor;
    }

    public void setQuaternaryColor(String quaternaryColor) {
        this.quaternaryColor = quaternaryColor;
    }

    public String getProductTitleColor() {
        return productTitleColor;
    }

    public void setProductTitleColor(String productTitleColor) {
        this.productTitleColor = productTitleColor;
    }

    public String getHeadingColor() {
        return headingColor;
    }

    public void setHeadingColor(String headingColor) {
        this.headingColor = headingColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(String textColor) {
        this.textColor = textColor;
    }

    public Boolean getTabFairPrices() {
        return tabFairPrices;
    }

    public void setTabFairPrices(Boolean tabFairPrices) {
        this.tabFairPrices = tabFairPrices;
    }

    public Boolean getTabProducers() {
        return tabProducers;
    }

    public void setTabProducers(Boolean tabProducers) {
        this.tabProducers = tabProducers;
    }

    public Boolean getTabQuality() {
        return tabQuality;
    }

    public void setTabQuality(Boolean tabQuality) {
        this.tabQuality = tabQuality;
    }

    public Boolean getTabFeedback() {
        return tabFeedback;
    }

    public void setTabFeedback(Boolean tabFeedback) {
        this.tabFeedback = tabFeedback;
    }

    public Long getOrderFairPrices() {
        return orderFairPrices;
    }

    public void setOrderFairPrices(Long orderFairPrices) {
        this.orderFairPrices = orderFairPrices;
    }

    public Long getOrderProducers() {
        return orderProducers;
    }

    public void setOrderProducers(Long orderProducers) {
        this.orderProducers = orderProducers;
    }

    public Long getOrderQuality() {
        return orderQuality;
    }

    public void setOrderQuality(Long orderQuality) {
        this.orderQuality = orderQuality;
    }

    public Long getOrderFeedback() {
        return orderFeedback;
    }

    public void setOrderFeedback(Long orderFeedback) {
        this.orderFeedback = orderFeedback;
    }

    public Document getProductFont() {
        return productFont;
    }

    public void setProductFont(Document productFont) {
        this.productFont = productFont;
    }

    public Document getTextFont() {
        return textFont;
    }

    public void setTextFont(Document textFont) {
        this.textFont = textFont;
    }

    public Document getLandingPageImage() {
        return landingPageImage;
    }

    public void setLandingPageImage(Document headerImage) {
        this.landingPageImage = headerImage;
    }

    public Document getLandingPageBackgroundImage() {
        return landingPageBackgroundImage;
    }

    public void setLandingPageBackgroundImage(Document headerBackgroundImage) {
        this.landingPageBackgroundImage = headerBackgroundImage;
    }

    public Document getHeaderBackgroundImage() {
        return headerBackgroundImage;
    }

    public void setHeaderBackgroundImage(Document headerBackgroundImage) {
        this.headerBackgroundImage = headerBackgroundImage;
    }

    public Boolean getGraphicFairPrices() {
        return graphicFairPrices;
    }

    public void setGraphicFairPrices(Boolean graphicFairPrices) {
        this.graphicFairPrices = graphicFairPrices;
    }

    public Boolean getGraphicIncreaseOfIncome() {
        return graphicIncreaseOfIncome;
    }

    public void setGraphicIncreaseOfIncome(Boolean graphicIncreaseOfIncome) {
        this.graphicIncreaseOfIncome = graphicIncreaseOfIncome;
    }

    public Boolean getGraphicQuality() {
        return graphicQuality;
    }

    public void setGraphicQuality(Boolean graphicQuality) {
        this.graphicQuality = graphicQuality;
    }

    public FairPricesUnit getGraphicPriceToProducer() {
        return graphicPriceToProducer;
    }

    public void setGraphicPriceToProducer(FairPricesUnit graphicPriceToProducer) {
        this.graphicPriceToProducer = graphicPriceToProducer;
    }

    public FairPricesUnit getGraphicFarmGatePrice() {
        return graphicFarmGatePrice;
    }

    public void setGraphicFarmGatePrice(FairPricesUnit graphicFarmGatePrice) {
        this.graphicFarmGatePrice = graphicFarmGatePrice;
    }

    public BigDecimal getManualFarmGatePrice() {
        return manualFarmGatePrice;
    }

    public void setManualFarmGatePrice(BigDecimal manualFarmGatePrice) {
        this.manualFarmGatePrice = manualFarmGatePrice;
    }

    public BigDecimal getManualProducerPrice() {
        return manualProducerPrice;
    }

    public void setManualProducerPrice(BigDecimal manualProducerPrice) {
        this.manualProducerPrice = manualProducerPrice;
    }

    public BigDecimal getContainerSize() {
        return containerSize;
    }

    public void setContainerSize(BigDecimal containerSize) {
        this.containerSize = containerSize;
    }

    public BigDecimal getWorldMarket() {
        return worldMarket;
    }

    public void setWorldMarket(BigDecimal worldMarket) {
        this.worldMarket = worldMarket;
    }

    public BigDecimal getFairTrade() {
        return fairTrade;
    }

    public void setFairTrade(BigDecimal fairTrade) {
        this.fairTrade = fairTrade;
    }

    public BigDecimal getAverageRegionFarmGatePrice() {
        return averageRegionFarmGatePrice;
    }

    public void setAverageRegionFarmGatePrice(BigDecimal averageRegionFarmGatePrice) {
        this.averageRegionFarmGatePrice = averageRegionFarmGatePrice;
    }

    public BusinessToCustomerSettings copy() {

        BusinessToCustomerSettings businessToCustomerSettings = new BusinessToCustomerSettings();

        businessToCustomerSettings.setPrimaryColor(getPrimaryColor());
        businessToCustomerSettings.setSecondaryColor(getSecondaryColor());
        businessToCustomerSettings.setTertiaryColor(getTertiaryColor());
        businessToCustomerSettings.setQuaternaryColor(getQuaternaryColor());

        businessToCustomerSettings.setProductTitleColor(getProductTitleColor());
        businessToCustomerSettings.setHeadingColor(getHeadingColor());
        businessToCustomerSettings.setTextColor(getTextColor());

        businessToCustomerSettings.setTabFairPrices(getTabFairPrices());
        businessToCustomerSettings.setTabProducers(getTabProducers());
        businessToCustomerSettings.setTabQuality(getTabQuality());
        businessToCustomerSettings.setTabFeedback(getTabFeedback());

        businessToCustomerSettings.setOrderFairPrices(getOrderFairPrices());
        businessToCustomerSettings.setOrderFeedback(getOrderFeedback());
        businessToCustomerSettings.setOrderProducers(getOrderProducers());
        businessToCustomerSettings.setOrderQuality(getOrderQuality());

        businessToCustomerSettings.setProductFont(getProductFont());
        businessToCustomerSettings.setTextFont(getTextFont());

        businessToCustomerSettings.setLandingPageImage(getLandingPageImage());
        businessToCustomerSettings.setLandingPageBackgroundImage(getLandingPageBackgroundImage());
        businessToCustomerSettings.setHeaderBackgroundImage(getHeaderBackgroundImage());

        businessToCustomerSettings.setGraphicFairPrices(getGraphicFairPrices());
        businessToCustomerSettings.setGraphicIncreaseOfIncome(getGraphicIncreaseOfIncome());
        businessToCustomerSettings.setGraphicQuality(getGraphicQuality());
        businessToCustomerSettings.setGraphicPriceToProducer(getGraphicPriceToProducer());
        businessToCustomerSettings.setGraphicFarmGatePrice(getGraphicFarmGatePrice());

        businessToCustomerSettings.setManualFarmGatePrice(getManualFarmGatePrice());
        businessToCustomerSettings.setManualProducerPrice(getManualProducerPrice());
        businessToCustomerSettings.setContainerSize(getContainerSize());
        businessToCustomerSettings.setWorldMarket(getWorldMarket());
        businessToCustomerSettings.setFairTrade(getWorldMarket());
        businessToCustomerSettings.setAverageRegionFarmGatePrice(getAverageRegionFarmGatePrice());

        return businessToCustomerSettings;
    }
}
