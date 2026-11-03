
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `ActivityProof`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ActivityProof` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `formalCreationDate` datetime(6) DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `validUntil` datetime(6) DEFAULT NULL,
  `document_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKemqkaop1au8l83r0ea5dw2ixw` (`document_id`),
  CONSTRAINT `FKemqkaop1au8l83r0ea5dw2ixw` FOREIGN KEY (`document_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ActivityProof` WRITE;
/*!40000 ALTER TABLE `ActivityProof` DISABLE KEYS */;
/*!40000 ALTER TABLE `ActivityProof` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `AnalyticsAggregate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `AnalyticsAggregate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `intValue` int DEFAULT NULL,
  `key1` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `key2` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `timestampValue` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXnfy1t9sfvtgde8eni5wg5efsp` (`key1`),
  KEY `IDX1w9f3cf0e5jftmj3v4mmck54m` (`key2`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `AnalyticsAggregate` WRITE;
/*!40000 ALTER TABLE `AnalyticsAggregate` DISABLE KEYS */;
/*!40000 ALTER TABLE `AnalyticsAggregate` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `AnalyticsAggregateItem`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `AnalyticsAggregateItem` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `intValue` int DEFAULT NULL,
  `itemKey` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `aggregate_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXp63bdsep5v1j21l5r5i3207in` (`itemKey`),
  KEY `FKa2apmrpkp3o44bqd3bukch4hk` (`aggregate_id`),
  CONSTRAINT `FKa2apmrpkp3o44bqd3bukch4hk` FOREIGN KEY (`aggregate_id`) REFERENCES `AnalyticsAggregate` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `AnalyticsAggregateItem` WRITE;
/*!40000 ALTER TABLE `AnalyticsAggregateItem` DISABLE KEYS */;
/*!40000 ALTER TABLE `AnalyticsAggregateItem` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `AuthenticationToken`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `AuthenticationToken` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entityVersion` bigint NOT NULL,
  `expiration` datetime(6) NOT NULL,
  `status` enum('ACTIVE','DISABLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `token` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpoc991v9pdc6h0sp0ebpqwnao` (`token`),
  KEY `IDXpoc991v9pdc6h0sp0ebpqwnao` (`token`),
  KEY `FKg35jtk8ndjjt0w0ft0tsrnd6d` (`user_id`),
  CONSTRAINT `FKg35jtk8ndjjt0w0ft0tsrnd6d` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `AuthenticationToken` WRITE;
/*!40000 ALTER TABLE `AuthenticationToken` DISABLE KEYS */;
INSERT INTO `AuthenticationToken` VALUES (12001,0,'2030-01-01 00:00:00.000000','ACTIVE','upgrade-fixture-token',1001);
/*!40000 ALTER TABLE `AuthenticationToken` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `BatchLocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `BatchLocation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_cell` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasDepartment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasFarm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasMunicipality` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasVillage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_otherAddress` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_sector` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_state` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_village` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_zip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `numberOfFarmers` int DEFAULT NULL,
  `pinName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_country_id` bigint DEFAULT NULL,
  `batch_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgw3r933r1vehpp3x2wtkwbkby` (`address_country_id`),
  KEY `FK7tn1lq3epx217kgwlr2ew1c2m` (`batch_id`),
  CONSTRAINT `FK7tn1lq3epx217kgwlr2ew1c2m` FOREIGN KEY (`batch_id`) REFERENCES `ProductLabelBatch` (`id`),
  CONSTRAINT `FKgw3r933r1vehpp3x2wtkwbkby` FOREIGN KEY (`address_country_id`) REFERENCES `Country` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `BatchLocation` WRITE;
/*!40000 ALTER TABLE `BatchLocation` DISABLE KEYS */;
/*!40000 ALTER TABLE `BatchLocation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `BulkPayment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `BulkPayment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `additionalCost` decimal(38,2) DEFAULT NULL,
  `additionalCostDescription` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `formalCreationTime` datetime(6) DEFAULT NULL,
  `paymentDescription` longtext COLLATE utf8mb4_unicode_ci,
  `paymentPurposeType` enum('ADVANCE_PAYMENT','FIRST_INSTALLMENT','INVOICE_PAYMENT','SECOND_INSTALLMENT','WOMEN_PREMIUM') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `receiptNumber` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `totalAmount` decimal(38,2) DEFAULT NULL,
  `createdBy_id` bigint NOT NULL,
  `payingCompany_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9tdn94o09x6sc2ym2thtc34v5` (`createdBy_id`),
  KEY `FK4kj37nte48242lehysge1ewlc` (`payingCompany_id`),
  CONSTRAINT `FK4kj37nte48242lehysge1ewlc` FOREIGN KEY (`payingCompany_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FK9tdn94o09x6sc2ym2thtc34v5` FOREIGN KEY (`createdBy_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `BulkPayment` WRITE;
/*!40000 ALTER TABLE `BulkPayment` DISABLE KEYS */;
/*!40000 ALTER TABLE `BulkPayment` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `BulkPaymentActivityProof`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `BulkPaymentActivityProof` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activityProof_id` bigint DEFAULT NULL,
  `bulkPayment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_BulkPaymentActivityProof_activityProof_id` (`activityProof_id`),
  KEY `FKak33ydw04ciktu4x5f0kalw3j` (`bulkPayment_id`),
  CONSTRAINT `FK3uo0yxwowclwdl8ibwo1im1m1` FOREIGN KEY (`activityProof_id`) REFERENCES `ActivityProof` (`id`),
  CONSTRAINT `FKak33ydw04ciktu4x5f0kalw3j` FOREIGN KEY (`bulkPayment_id`) REFERENCES `BulkPayment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `BulkPaymentActivityProof` WRITE;
/*!40000 ALTER TABLE `BulkPaymentActivityProof` DISABLE KEYS */;
/*!40000 ALTER TABLE `BulkPaymentActivityProof` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `BusinessToCustomerSettings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `BusinessToCustomerSettings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `average_region_farm_gate_price` decimal(38,2) DEFAULT NULL,
  `container_size` decimal(38,2) DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `fair_trade` decimal(38,2) DEFAULT NULL,
  `graphic_fair_prices` bit(1) DEFAULT NULL,
  `graphic_farm_gate_price` enum('DISABLED','PERCENT_VALUE','PER_CONTAINER','PER_KG') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `graphic_increase_of_income` bit(1) DEFAULT NULL,
  `graphic_price_to_producer` enum('DISABLED','PERCENT_VALUE','PER_CONTAINER','PER_KG') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `graphic_quality` bit(1) DEFAULT NULL,
  `heading_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `manual_farm_gate_price` decimal(38,2) DEFAULT NULL,
  `manual_producer_price` decimal(38,2) DEFAULT NULL,
  `order_fair_prices` bigint DEFAULT NULL,
  `order_feedback` bigint DEFAULT NULL,
  `order_producers` bigint DEFAULT NULL,
  `order_quality` bigint DEFAULT NULL,
  `primary_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_title_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quaternary_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `secondary_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tab_fair_prices` bit(1) DEFAULT NULL,
  `tab_feedback` bit(1) DEFAULT NULL,
  `tab_producers` bit(1) DEFAULT NULL,
  `tab_quality` bit(1) DEFAULT NULL,
  `tertiary_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `text_color` varchar(7) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `world_market` decimal(38,2) DEFAULT NULL,
  `headerBackgroundImage_id` bigint DEFAULT NULL,
  `landingPageBackgroundImage_id` bigint DEFAULT NULL,
  `landingPageImage_id` bigint DEFAULT NULL,
  `productFont_id` bigint DEFAULT NULL,
  `textFont_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_BTCSettings_productFont_id` (`productFont_id`),
  UNIQUE KEY `UK_LEGACY_BTCSettings_textFont_id` (`textFont_id`),
  UNIQUE KEY `UK_LEGACY_BTCSettings_landingPageImage_id` (`landingPageImage_id`),
  UNIQUE KEY `UK_LEGACY_BTCSettings_landingPageBackgroundImage_id` (`landingPageBackgroundImage_id`),
  UNIQUE KEY `UK_LEGACY_BTCSettings_headerBackgroundImage_id` (`headerBackgroundImage_id`),
  CONSTRAINT `FK62u27uf5v9nrfmc7oirw5a37j` FOREIGN KEY (`textFont_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FK7ugqrlx5w2t5vi106l0jj9qw9` FOREIGN KEY (`headerBackgroundImage_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FKjnq4oasl0pntc9g98bsy23ek9` FOREIGN KEY (`productFont_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FKringgjfrq2ocejmx3nk6mkyeo` FOREIGN KEY (`landingPageImage_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FKs2w0ruaupuejwhiylil5m01r7` FOREIGN KEY (`landingPageBackgroundImage_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `BusinessToCustomerSettings` WRITE;
/*!40000 ALTER TABLE `BusinessToCustomerSettings` DISABLE KEYS */;
/*!40000 ALTER TABLE `BusinessToCustomerSettings` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Certification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Certification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `validity` datetime(6) DEFAULT NULL,
  `certificate_id` bigint DEFAULT NULL,
  `stockOrder_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_Certification_certificate_id` (`certificate_id`),
  KEY `FK2r634dsqa5p95j8jo59yn2f8x` (`stockOrder_id`),
  CONSTRAINT `FK2r634dsqa5p95j8jo59yn2f8x` FOREIGN KEY (`stockOrder_id`) REFERENCES `StockOrder` (`id`),
  CONSTRAINT `FKaoo6433h2o8lb174so5kyb7xr` FOREIGN KEY (`certificate_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Certification` WRITE;
/*!40000 ALTER TABLE `Certification` DISABLE KEYS */;
/*!40000 ALTER TABLE `Certification` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Company`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Company` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `abbreviation` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `about` longtext COLLATE utf8mb4_unicode_ci,
  `allowBeycoIntegration` bit(1) DEFAULT NULL,
  `displayPrefferedWayOfPayment` bit(1) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `headquarters_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_cell` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_hondurasDepartment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_hondurasFarm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_hondurasMunicipality` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_hondurasVillage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_otherAddress` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_sector` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_state` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_village` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `headquarters_zip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `interview` longtext COLLATE utf8mb4_unicode_ci,
  `manager` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchaseProofDocumentMultipleFarmers` bit(1) DEFAULT NULL,
  `status` enum('ACTIVE','DEACTIVATED','REGISTERED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `webPage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency_id` bigint DEFAULT NULL,
  `headquarters_country_id` bigint DEFAULT NULL,
  `logo_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_Company_logo_id` (`logo_id`),
  KEY `IDXck8wy9n707u5ictdeeg36f3na` (`name`),
  KEY `FKbbghpoldo0lie5e285hny3sr8` (`currency_id`),
  KEY `FKqy4m2cx39jbat8b1iwc2b9y02` (`headquarters_country_id`),
  CONSTRAINT `FK5owxc6ljlrw1e5ir3w5afr7c5` FOREIGN KEY (`logo_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FKbbghpoldo0lie5e285hny3sr8` FOREIGN KEY (`currency_id`) REFERENCES `CurrencyType` (`id`),
  CONSTRAINT `FKqy4m2cx39jbat8b1iwc2b9y02` FOREIGN KEY (`headquarters_country_id`) REFERENCES `Country` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Company` WRITE;
/*!40000 ALTER TABLE `Company` DISABLE KEYS */;
INSERT INTO `Company` VALUES (2001,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'upgrade-fixture-company',NULL,NULL,'ACTIVE',NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `Company` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CompanyCertification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CompanyCertification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` longtext COLLATE utf8mb4_unicode_ci,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `validity` date DEFAULT NULL,
  `certificate_id` bigint DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `companyTranslation_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKn6v3ytd1je8o9oeb3w1tadvxp` (`certificate_id`),
  KEY `FKhr33i4m4lct9gtf2hs1ghmc1f` (`company_id`),
  KEY `FK7840sse63j43vc3rh7uh9ysky` (`companyTranslation_id`),
  CONSTRAINT `FK7840sse63j43vc3rh7uh9ysky` FOREIGN KEY (`companyTranslation_id`) REFERENCES `CompanyTranslation` (`id`),
  CONSTRAINT `FKhr33i4m4lct9gtf2hs1ghmc1f` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKn6v3ytd1je8o9oeb3w1tadvxp` FOREIGN KEY (`certificate_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CompanyCertification` WRITE;
/*!40000 ALTER TABLE `CompanyCertification` DISABLE KEYS */;
/*!40000 ALTER TABLE `CompanyCertification` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CompanyCustomer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CompanyCustomer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contact` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_latitude` double DEFAULT NULL,
  `location_longitude` double DEFAULT NULL,
  `location_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_cell` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_hondurasDepartment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_hondurasFarm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_hondurasMunicipality` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_hondurasVillage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_otherAddress` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_sector` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_state` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_village` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location_zip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `officialCompanyName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vatId` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `location_country_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgce6xf8xk0ca9ngsch6gy8rf8` (`company_id`),
  KEY `FKnftn8qjehp9ylkyqsr8xog93a` (`location_country_id`),
  KEY `FKqpkh66ddnr85q2xcmlmx5c3co` (`product_id`),
  CONSTRAINT `FKgce6xf8xk0ca9ngsch6gy8rf8` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKnftn8qjehp9ylkyqsr8xog93a` FOREIGN KEY (`location_country_id`) REFERENCES `Country` (`id`),
  CONSTRAINT `FKqpkh66ddnr85q2xcmlmx5c3co` FOREIGN KEY (`product_id`) REFERENCES `Product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CompanyCustomer` WRITE;
/*!40000 ALTER TABLE `CompanyCustomer` DISABLE KEYS */;
/*!40000 ALTER TABLE `CompanyCustomer` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CompanyDocument`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CompanyDocument` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` enum('MEET_THE_FARMER','PRODUCTION_RECORD','VIDEO') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `link` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quote` longtext COLLATE utf8mb4_unicode_ci,
  `type` enum('FILE','LINK') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `companyTranslation_id` bigint DEFAULT NULL,
  `document_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1lkncmvq1t6poo1lecqhgqplf` (`company_id`),
  KEY `FKjwpwtvfp6biu6gaogybxqa8y3` (`companyTranslation_id`),
  KEY `FKmclnbhregkbbug3250l50qxjx` (`document_id`),
  CONSTRAINT `FK1lkncmvq1t6poo1lecqhgqplf` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKjwpwtvfp6biu6gaogybxqa8y3` FOREIGN KEY (`companyTranslation_id`) REFERENCES `CompanyTranslation` (`id`),
  CONSTRAINT `FKmclnbhregkbbug3250l50qxjx` FOREIGN KEY (`document_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CompanyDocument` WRITE;
/*!40000 ALTER TABLE `CompanyDocument` DISABLE KEYS */;
/*!40000 ALTER TABLE `CompanyDocument` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CompanyTranslation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CompanyTranslation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `abbreviation` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `about` longtext COLLATE utf8mb4_unicode_ci,
  `interview` longtext COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `webPage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhg3olp2ynrkn4htdvmn7va2qs` (`company_id`,`language`),
  CONSTRAINT `FK38hkfpu1v49g48bpsh0yn6jo4` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CompanyTranslation` WRITE;
/*!40000 ALTER TABLE `CompanyTranslation` DISABLE KEYS */;
/*!40000 ALTER TABLE `CompanyTranslation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CompanyTranslation_mediaLinks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CompanyTranslation_mediaLinks` (
  `CompanyTranslation_id` bigint NOT NULL,
  `link` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `medium` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`CompanyTranslation_id`,`medium`),
  CONSTRAINT `FKkckdgt4lveapow6uvh93hl20f` FOREIGN KEY (`CompanyTranslation_id`) REFERENCES `CompanyTranslation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CompanyTranslation_mediaLinks` WRITE;
/*!40000 ALTER TABLE `CompanyTranslation_mediaLinks` DISABLE KEYS */;
/*!40000 ALTER TABLE `CompanyTranslation_mediaLinks` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CompanyUser`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CompanyUser` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entityVersion` bigint NOT NULL,
  `role` enum('ACCOUNTANT','COMPANY_ADMIN','COMPANY_USER','MANAGER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKooc3pd761luongqc14ep6jgfd` (`company_id`),
  KEY `FKh37pevau45fgnmyrq7o6nasyi` (`user_id`),
  CONSTRAINT `FKh37pevau45fgnmyrq7o6nasyi` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKooc3pd761luongqc14ep6jgfd` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CompanyUser` WRITE;
/*!40000 ALTER TABLE `CompanyUser` DISABLE KEYS */;
INSERT INTO `CompanyUser` VALUES (3001,0,'COMPANY_ADMIN',2001,1001);
/*!40000 ALTER TABLE `CompanyUser` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CompanyValueChain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CompanyValueChain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbbly17yyupinf6ryi9o4vjnta` (`valueChain_id`,`company_id`),
  KEY `FKnyye6j73kq2nib0lq76n27kq8` (`company_id`),
  CONSTRAINT `FK7k8lpq37srd9rj5iyfhi33gv` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`),
  CONSTRAINT `FKnyye6j73kq2nib0lq76n27kq8` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CompanyValueChain` WRITE;
/*!40000 ALTER TABLE `CompanyValueChain` DISABLE KEYS */;
/*!40000 ALTER TABLE `CompanyValueChain` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Company_mediaLinks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Company_mediaLinks` (
  `Company_id` bigint NOT NULL,
  `link` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `medium` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`Company_id`,`medium`),
  CONSTRAINT `FKev48m1qovkhuxk6xttmmfhu29` FOREIGN KEY (`Company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Company_mediaLinks` WRITE;
/*!40000 ALTER TABLE `Company_mediaLinks` DISABLE KEYS */;
/*!40000 ALTER TABLE `Company_mediaLinks` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ConfirmationToken`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ConfirmationToken` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entityVersion` bigint NOT NULL,
  `status` enum('ACTIVE','DISABLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `token` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('CONFIRM_EMAIL','PASSWORD_RESET') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5yeek6arig5t6anh1oo2s5sar` (`token`),
  KEY `IDX5yeek6arig5t6anh1oo2s5sar` (`token`),
  KEY `FKsjwvpslamugnwqhxsp09noep2` (`user_id`),
  CONSTRAINT `FKsjwvpslamugnwqhxsp09noep2` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ConfirmationToken` WRITE;
/*!40000 ALTER TABLE `ConfirmationToken` DISABLE KEYS */;
/*!40000 ALTER TABLE `ConfirmationToken` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Country`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Country` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqyh4l70f9l5k5jcv876rb4j89` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=250 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Country` WRITE;
/*!40000 ALTER TABLE `Country` DISABLE KEYS */;
INSERT INTO `Country` VALUES (1,'AF',NULL,NULL,'Afghanistan'),(2,'AX',NULL,NULL,'Ålland Islands'),(3,'AL',NULL,NULL,'Albania'),(4,'DZ',NULL,NULL,'Algeria'),(5,'AS',NULL,NULL,'American Samoa'),(6,'AD',NULL,NULL,'Andorra'),(7,'AO',NULL,NULL,'Angola'),(8,'AI',NULL,NULL,'Anguilla'),(9,'AQ',NULL,NULL,'Antarctica'),(10,'AG',NULL,NULL,'Antigua and Barbuda'),(11,'AR',NULL,NULL,'Argentina'),(12,'AM',NULL,NULL,'Armenia'),(13,'AW',NULL,NULL,'Aruba'),(14,'AU',NULL,NULL,'Australia'),(15,'AT',NULL,NULL,'Austria'),(16,'AZ',NULL,NULL,'Azerbaijan'),(17,'BS',NULL,NULL,'Bahamas'),(18,'BH',NULL,NULL,'Bahrain'),(19,'BD',NULL,NULL,'Bangladesh'),(20,'BB',NULL,NULL,'Barbados'),(21,'BY',NULL,NULL,'Belarus'),(22,'BE',NULL,NULL,'Belgium'),(23,'BZ',NULL,NULL,'Belize'),(24,'BJ',NULL,NULL,'Benin'),(25,'BM',NULL,NULL,'Bermuda'),(26,'BT',NULL,NULL,'Bhutan'),(27,'BO',NULL,NULL,'Bolivia, Plurinational State of'),(28,'BQ',NULL,NULL,'Bonaire, Sint Eustatius and Saba'),(29,'BA',NULL,NULL,'Bosnia and Herzegovina'),(30,'BW',NULL,NULL,'Botswana'),(31,'BV',NULL,NULL,'Bouvet Island'),(32,'BR',NULL,NULL,'Brazil'),(33,'IO',NULL,NULL,'British Indian Ocean Territory'),(34,'BN',NULL,NULL,'Brunei Darussalam'),(35,'BG',NULL,NULL,'Bulgaria'),(36,'BF',NULL,NULL,'Burkina Faso'),(37,'BI',NULL,NULL,'Burundi'),(38,'KH',NULL,NULL,'Cambodia'),(39,'CM',NULL,NULL,'Cameroon'),(40,'CA',NULL,NULL,'Canada'),(41,'CV',NULL,NULL,'Cape Verde'),(42,'KY',NULL,NULL,'Cayman Islands'),(43,'CF',NULL,NULL,'Central African Republic'),(44,'TD',NULL,NULL,'Chad'),(45,'CL',NULL,NULL,'Chile'),(46,'CN',NULL,NULL,'China'),(47,'CX',NULL,NULL,'Christmas Island'),(48,'CC',NULL,NULL,'Cocos (Keeling) Islands'),(49,'CO',NULL,NULL,'Colombia'),(50,'KM',NULL,NULL,'Comoros'),(51,'CG',NULL,NULL,'Congo'),(52,'CD',NULL,NULL,'Congo, the Democratic Republic of the'),(53,'CK',NULL,NULL,'Cook Islands'),(54,'CR',NULL,NULL,'Costa Rica'),(55,'CI',NULL,NULL,'Côte d\'Ivoire'),(56,'HR',NULL,NULL,'Croatia'),(57,'CU',NULL,NULL,'Cuba'),(58,'CW',NULL,NULL,'Curaçao'),(59,'CY',NULL,NULL,'Cyprus'),(60,'CZ',NULL,NULL,'Czech Republic'),(61,'DK',NULL,NULL,'Denmark'),(62,'DJ',NULL,NULL,'Djibouti'),(63,'DM',NULL,NULL,'Dominica'),(64,'DO',NULL,NULL,'Dominican Republic'),(65,'EC',NULL,NULL,'Ecuador'),(66,'EG',NULL,NULL,'Egypt'),(67,'SV',NULL,NULL,'El Salvador'),(68,'GQ',NULL,NULL,'Equatorial Guinea'),(69,'ER',NULL,NULL,'Eritrea'),(70,'EE',NULL,NULL,'Estonia'),(71,'ET',NULL,NULL,'Ethiopia'),(72,'FK',NULL,NULL,'Falkland Islands (Malvinas)'),(73,'FO',NULL,NULL,'Faroe Islands'),(74,'FJ',NULL,NULL,'Fiji'),(75,'FI',NULL,NULL,'Finland'),(76,'FR',NULL,NULL,'France'),(77,'GF',NULL,NULL,'French Guiana'),(78,'PF',NULL,NULL,'French Polynesia'),(79,'TF',NULL,NULL,'French Southern Territories'),(80,'GA',NULL,NULL,'Gabon'),(81,'GM',NULL,NULL,'Gambia'),(82,'GE',NULL,NULL,'Georgia'),(83,'DE',NULL,NULL,'Germany'),(84,'GH',NULL,NULL,'Ghana'),(85,'GI',NULL,NULL,'Gibraltar'),(86,'GR',NULL,NULL,'Greece'),(87,'GL',NULL,NULL,'Greenland'),(88,'GD',NULL,NULL,'Grenada'),(89,'GP',NULL,NULL,'Guadeloupe'),(90,'GU',NULL,NULL,'Guam'),(91,'GT',NULL,NULL,'Guatemala'),(92,'GG',NULL,NULL,'Guernsey'),(93,'GN',NULL,NULL,'Guinea'),(94,'GW',NULL,NULL,'Guinea-Bissau'),(95,'GY',NULL,NULL,'Guyana'),(96,'HT',NULL,NULL,'Haiti'),(97,'HM',NULL,NULL,'Heard Island and McDonald Islands'),(98,'VA',NULL,NULL,'Holy See (Vatican City State)'),(99,'HN',NULL,NULL,'Honduras'),(100,'HK',NULL,NULL,'Hong Kong'),(101,'HU',NULL,NULL,'Hungary'),(102,'IS',NULL,NULL,'Iceland'),(103,'IN',NULL,NULL,'India'),(104,'ID',NULL,NULL,'Indonesia'),(105,'IR',NULL,NULL,'Iran, Islamic Republic of'),(106,'IQ',NULL,NULL,'Iraq'),(107,'IE',NULL,NULL,'Ireland'),(108,'IM',NULL,NULL,'Isle of Man'),(109,'IL',NULL,NULL,'Israel'),(110,'IT',NULL,NULL,'Italy'),(111,'JM',NULL,NULL,'Jamaica'),(112,'JP',NULL,NULL,'Japan'),(113,'JE',NULL,NULL,'Jersey'),(114,'JO',NULL,NULL,'Jordan'),(115,'KZ',NULL,NULL,'Kazakhstan'),(116,'KE',NULL,NULL,'Kenya'),(117,'KI',NULL,NULL,'Kiribati'),(118,'KP',NULL,NULL,'Korea, Democratic People\'s Republic of'),(119,'KR',NULL,NULL,'Korea, Republic of'),(120,'KW',NULL,NULL,'Kuwait'),(121,'KG',NULL,NULL,'Kyrgyzstan'),(122,'LA',NULL,NULL,'Lao People\'s Democratic Republic'),(123,'LV',NULL,NULL,'Latvia'),(124,'LB',NULL,NULL,'Lebanon'),(125,'LS',NULL,NULL,'Lesotho'),(126,'LR',NULL,NULL,'Liberia'),(127,'LY',NULL,NULL,'Libya'),(128,'LI',NULL,NULL,'Liechtenstein'),(129,'LT',NULL,NULL,'Lithuania'),(130,'LU',NULL,NULL,'Luxembourg'),(131,'MO',NULL,NULL,'Macao'),(132,'MK',NULL,NULL,'Macedonia, the former Yugoslav Republic of'),(133,'MG',NULL,NULL,'Madagascar'),(134,'MW',NULL,NULL,'Malawi'),(135,'MY',NULL,NULL,'Malaysia'),(136,'MV',NULL,NULL,'Maldives'),(137,'ML',NULL,NULL,'Mali'),(138,'MT',NULL,NULL,'Malta'),(139,'MH',NULL,NULL,'Marshall Islands'),(140,'MQ',NULL,NULL,'Martinique'),(141,'MR',NULL,NULL,'Mauritania'),(142,'MU',NULL,NULL,'Mauritius'),(143,'YT',NULL,NULL,'Mayotte'),(144,'MX',NULL,NULL,'Mexico'),(145,'FM',NULL,NULL,'Micronesia, Federated States of'),(146,'MD',NULL,NULL,'Moldova, Republic of'),(147,'MC',NULL,NULL,'Monaco'),(148,'MN',NULL,NULL,'Mongolia'),(149,'ME',NULL,NULL,'Montenegro'),(150,'MS',NULL,NULL,'Montserrat'),(151,'MA',NULL,NULL,'Morocco'),(152,'MZ',NULL,NULL,'Mozambique'),(153,'MM',NULL,NULL,'Myanmar'),(154,'NA',NULL,NULL,'Namibia'),(155,'NR',NULL,NULL,'Nauru'),(156,'NP',NULL,NULL,'Nepal'),(157,'NL',NULL,NULL,'Netherlands'),(158,'NC',NULL,NULL,'New Caledonia'),(159,'NZ',NULL,NULL,'New Zealand'),(160,'NI',NULL,NULL,'Nicaragua'),(161,'NE',NULL,NULL,'Niger'),(162,'NG',NULL,NULL,'Nigeria'),(163,'NU',NULL,NULL,'Niue'),(164,'NF',NULL,NULL,'Norfolk Island'),(165,'MP',NULL,NULL,'Northern Mariana Islands'),(166,'NO',NULL,NULL,'Norway'),(167,'OM',NULL,NULL,'Oman'),(168,'PK',NULL,NULL,'Pakistan'),(169,'PW',NULL,NULL,'Palau'),(170,'PS',NULL,NULL,'Palestinian Territory, Occupied'),(171,'PA',NULL,NULL,'Panama'),(172,'PG',NULL,NULL,'Papua New Guinea'),(173,'PY',NULL,NULL,'Paraguay'),(174,'PE',NULL,NULL,'Peru'),(175,'PH',NULL,NULL,'Philippines'),(176,'PN',NULL,NULL,'Pitcairn'),(177,'PL',NULL,NULL,'Poland'),(178,'PT',NULL,NULL,'Portugal'),(179,'PR',NULL,NULL,'Puerto Rico'),(180,'QA',NULL,NULL,'Qatar'),(181,'RE',NULL,NULL,'Réunion'),(182,'RO',NULL,NULL,'Romania'),(183,'RU',NULL,NULL,'Russian Federation'),(184,'RW',NULL,NULL,'Rwanda'),(185,'BL',NULL,NULL,'Saint Barthélemy'),(186,'SH',NULL,NULL,'Saint Helena, Ascension and Tristan da Cunha'),(187,'KN',NULL,NULL,'Saint Kitts and Nevis'),(188,'LC',NULL,NULL,'Saint Lucia'),(189,'MF',NULL,NULL,'Saint Martin (French part)'),(190,'PM',NULL,NULL,'Saint Pierre and Miquelon'),(191,'VC',NULL,NULL,'Saint Vincent and the Grenadines'),(192,'WS',NULL,NULL,'Samoa'),(193,'SM',NULL,NULL,'San Marino'),(194,'ST',NULL,NULL,'Sao Tome and Principe'),(195,'SA',NULL,NULL,'Saudi Arabia'),(196,'SN',NULL,NULL,'Senegal'),(197,'RS',NULL,NULL,'Serbia'),(198,'SC',NULL,NULL,'Seychelles'),(199,'SL',NULL,NULL,'Sierra Leone'),(200,'SG',NULL,NULL,'Singapore'),(201,'SX',NULL,NULL,'Sint Maarten (Dutch part)'),(202,'SK',NULL,NULL,'Slovakia'),(203,'SI',NULL,NULL,'Slovenia'),(204,'SB',NULL,NULL,'Solomon Islands'),(205,'SO',NULL,NULL,'Somalia'),(206,'ZA',NULL,NULL,'South Africa'),(207,'GS',NULL,NULL,'South Georgia and the South Sandwich Islands'),(208,'SS',NULL,NULL,'South Sudan'),(209,'ES',NULL,NULL,'Spain'),(210,'LK',NULL,NULL,'Sri Lanka'),(211,'SD',NULL,NULL,'Sudan'),(212,'SR',NULL,NULL,'Suriname'),(213,'SJ',NULL,NULL,'Svalbard and Jan Mayen'),(214,'SZ',NULL,NULL,'Swaziland'),(215,'SE',NULL,NULL,'Sweden'),(216,'CH',NULL,NULL,'Switzerland'),(217,'SY',NULL,NULL,'Syrian Arab Republic'),(218,'TW',NULL,NULL,'Taiwan, Province of China'),(219,'TJ',NULL,NULL,'Tajikistan'),(220,'TZ',NULL,NULL,'Tanzania, United Republic of'),(221,'TH',NULL,NULL,'Thailand'),(222,'TL',NULL,NULL,'Timor-Leste'),(223,'TG',NULL,NULL,'Togo'),(224,'TK',NULL,NULL,'Tokelau'),(225,'TO',NULL,NULL,'Tonga'),(226,'TT',NULL,NULL,'Trinidad and Tobago'),(227,'TN',NULL,NULL,'Tunisia'),(228,'TR',NULL,NULL,'Turkey'),(229,'TM',NULL,NULL,'Turkmenistan'),(230,'TC',NULL,NULL,'Turks and Caicos Islands'),(231,'TV',NULL,NULL,'Tuvalu'),(232,'UG',NULL,NULL,'Uganda'),(233,'UA',NULL,NULL,'Ukraine'),(234,'AE',NULL,NULL,'United Arab Emirates'),(235,'GB',NULL,NULL,'United Kingdom'),(236,'US',NULL,NULL,'United States'),(237,'UM',NULL,NULL,'United States Minor Outlying Islands'),(238,'UY',NULL,NULL,'Uruguay'),(239,'UZ',NULL,NULL,'Uzbekistan'),(240,'VU',NULL,NULL,'Vanuatu'),(241,'VE',NULL,NULL,'Venezuela, Bolivarian Republic of'),(242,'VN',NULL,NULL,'Viet Nam'),(243,'VG',NULL,NULL,'Virgin Islands, British'),(244,'VI',NULL,NULL,'Virgin Islands, U.S.'),(245,'WF',NULL,NULL,'Wallis and Futuna'),(246,'EH',NULL,NULL,'Western Sahara'),(247,'YE',NULL,NULL,'Yemen'),(248,'ZM',NULL,NULL,'Zambia'),(249,'ZW',NULL,NULL,'Zimbabwe');
/*!40000 ALTER TABLE `Country` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CurrencyPair`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CurrencyPair` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `date` date DEFAULT NULL,
  `value` decimal(38,2) DEFAULT NULL,
  `from_id` bigint DEFAULT NULL,
  `to_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1t5j4yvhljgom8thrjual998g` (`from_id`),
  KEY `FKlm15bj6g8nrg74i4xlfxs6lbi` (`to_id`),
  CONSTRAINT `FK1t5j4yvhljgom8thrjual998g` FOREIGN KEY (`from_id`) REFERENCES `CurrencyType` (`id`),
  CONSTRAINT `FKlm15bj6g8nrg74i4xlfxs6lbi` FOREIGN KEY (`to_id`) REFERENCES `CurrencyType` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CurrencyPair` WRITE;
/*!40000 ALTER TABLE `CurrencyPair` DISABLE KEYS */;
/*!40000 ALTER TABLE `CurrencyPair` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `CurrencyType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CurrencyType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `CurrencyType` WRITE;
/*!40000 ALTER TABLE `CurrencyType` DISABLE KEYS */;
/*!40000 ALTER TABLE `CurrencyType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `contentType` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `size` bigint DEFAULT NULL,
  `storageKey` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('GENERAL','IMAGE') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlkixmvkl0tunyy6v0jbo1tsf8` (`storageKey`),
  KEY `IDXlkixmvkl0tunyy6v0jbo1tsf8` (`storageKey`)
) ENGINE=InnoDB AUTO_INCREMENT=11002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Document` WRITE;
/*!40000 ALTER TABLE `Document` DISABLE KEYS */;
INSERT INTO `Document` VALUES (11001,'2026-10-21 21:24:37.212824','text/plain','upgrade-fixture-document.txt',12,'upgrade-fixture-document','GENERAL');
/*!40000 ALTER TABLE `Document` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Facility`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Facility` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `displayMayInvolveCollectors` bit(1) DEFAULT NULL,
  `displayOrganic` bit(1) DEFAULT NULL,
  `displayPriceDeductionDamage` bit(1) DEFAULT NULL,
  `displayPriceDeterminedLater` bit(1) DEFAULT NULL,
  `displayTare` bit(1) DEFAULT NULL,
  `displayWeightDeductionDamage` bit(1) DEFAULT NULL,
  `displayWomenOnly` bit(1) DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `isCollectionFacility` bit(1) DEFAULT NULL,
  `isDeactivated` bit(1) DEFAULT NULL,
  `isPublic` bit(1) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `facilityLocation_id` bigint DEFAULT NULL,
  `facilityType_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_Facility_facilityLocation_id` (`facilityLocation_id`),
  KEY `FKmwbprmjxk6737yes985o85cmi` (`company_id`),
  KEY `FKcumgxtgyjr9fvqegw9vvtwdj6` (`facilityType_id`),
  CONSTRAINT `FKbydfckdpevkicna5tbl1dmtvq` FOREIGN KEY (`facilityLocation_id`) REFERENCES `FacilityLocation` (`id`),
  CONSTRAINT `FKcumgxtgyjr9fvqegw9vvtwdj6` FOREIGN KEY (`facilityType_id`) REFERENCES `FacilityType` (`id`),
  CONSTRAINT `FKmwbprmjxk6737yes985o85cmi` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Facility` WRITE;
/*!40000 ALTER TABLE `Facility` DISABLE KEYS */;
INSERT INTO `Facility` VALUES (5001,'2026-10-21 21:24:37.186850','2026-10-21 21:24:37.186850',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,'upgrade-fixture-facility',2001,4001,4);
/*!40000 ALTER TABLE `Facility` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FacilityFinalProduct`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FacilityFinalProduct` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `facility_id` bigint NOT NULL,
  `finalProduct_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc0nexm9vp5my189hg058pnpty` (`facility_id`),
  KEY `FKjg15plyreq6lcmhr95kqw630e` (`finalProduct_id`),
  CONSTRAINT `FKc0nexm9vp5my189hg058pnpty` FOREIGN KEY (`facility_id`) REFERENCES `Facility` (`id`),
  CONSTRAINT `FKjg15plyreq6lcmhr95kqw630e` FOREIGN KEY (`finalProduct_id`) REFERENCES `FinalProduct` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FacilityFinalProduct` WRITE;
/*!40000 ALTER TABLE `FacilityFinalProduct` DISABLE KEYS */;
/*!40000 ALTER TABLE `FacilityFinalProduct` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FacilityLocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FacilityLocation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_cell` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasDepartment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasFarm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasMunicipality` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasVillage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_otherAddress` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_sector` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_state` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_village` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_zip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `numberOfFarmers` int DEFAULT NULL,
  `pinName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `isPubliclyVisible` bit(1) DEFAULT NULL,
  `address_country_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4gx3rd9bs6efgswmxgj9gf3x4` (`address_country_id`),
  CONSTRAINT `FK4gx3rd9bs6efgswmxgj9gf3x4` FOREIGN KEY (`address_country_id`) REFERENCES `Country` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FacilityLocation` WRITE;
/*!40000 ALTER TABLE `FacilityLocation` DISABLE KEYS */;
INSERT INTO `FacilityLocation` VALUES (4001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,1.25,2.5,NULL,'upgrade-fixture-location',NULL,NULL);
/*!40000 ALTER TABLE `FacilityLocation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FacilitySemiProduct`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FacilitySemiProduct` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `facility_id` bigint NOT NULL,
  `semiProduct_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK790gxtge166vqt962rp196vn` (`facility_id`),
  KEY `FKdg7h24mnlambgii7dir3g5sga` (`semiProduct_id`),
  CONSTRAINT `FK790gxtge166vqt962rp196vn` FOREIGN KEY (`facility_id`) REFERENCES `Facility` (`id`),
  CONSTRAINT `FKdg7h24mnlambgii7dir3g5sga` FOREIGN KEY (`semiProduct_id`) REFERENCES `SemiProduct` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FacilitySemiProduct` WRITE;
/*!40000 ALTER TABLE `FacilitySemiProduct` DISABLE KEYS */;
/*!40000 ALTER TABLE `FacilitySemiProduct` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FacilityTranslation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FacilityTranslation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `facility_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6ed4v4q3ustuj2ub9d2nltcpe` (`facility_id`),
  CONSTRAINT `FK6ed4v4q3ustuj2ub9d2nltcpe` FOREIGN KEY (`facility_id`) REFERENCES `Facility` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FacilityTranslation` WRITE;
/*!40000 ALTER TABLE `FacilityTranslation` DISABLE KEYS */;
/*!40000 ALTER TABLE `FacilityTranslation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FacilityType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FacilityType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FacilityType` WRITE;
/*!40000 ALTER TABLE `FacilityType` DISABLE KEYS */;
INSERT INTO `FacilityType` VALUES (1,'WASHING_STATION','Washing station'),(2,'DRYING_BED','Drying bed'),(3,'HULLING_STATION','Hulling station'),(4,'STORAGE','Storage');
/*!40000 ALTER TABLE `FacilityType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FacilityValueChain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FacilityValueChain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `facility_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKq76hy2x2xa23oueptkxvnaw7q` (`facility_id`),
  KEY `FK1k8016c8hnoybrqutbojdsgon` (`valueChain_id`),
  CONSTRAINT `FK1k8016c8hnoybrqutbojdsgon` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`),
  CONSTRAINT `FKq76hy2x2xa23oueptkxvnaw7q` FOREIGN KEY (`facility_id`) REFERENCES `Facility` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FacilityValueChain` WRITE;
/*!40000 ALTER TABLE `FacilityValueChain` DISABLE KEYS */;
/*!40000 ALTER TABLE `FacilityValueChain` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FarmPlantInformation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FarmPlantInformation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `numberOfPlants` int DEFAULT NULL,
  `plantCultivatedArea` decimal(38,2) DEFAULT NULL,
  `productType_id` bigint DEFAULT NULL,
  `userCustomer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7go67a5u1nxudtpuj2u0hree` (`productType_id`),
  KEY `FKdxp7vd7t2vk0ddkl6svhvm3hj` (`userCustomer_id`),
  CONSTRAINT `FK7go67a5u1nxudtpuj2u0hree` FOREIGN KEY (`productType_id`) REFERENCES `ProductType` (`id`),
  CONSTRAINT `FKdxp7vd7t2vk0ddkl6svhvm3hj` FOREIGN KEY (`userCustomer_id`) REFERENCES `UserCustomer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FarmPlantInformation` WRITE;
/*!40000 ALTER TABLE `FarmPlantInformation` DISABLE KEYS */;
/*!40000 ALTER TABLE `FarmPlantInformation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FinalProduct`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FinalProduct` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entityVersion` bigint NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `measurementUnitType_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdhis2r5ykqmbn5851bit0k9m2` (`measurementUnitType_id`),
  KEY `FKsgf9nmb63egkc3wt0e27ndsqc` (`product_id`),
  CONSTRAINT `FKdhis2r5ykqmbn5851bit0k9m2` FOREIGN KEY (`measurementUnitType_id`) REFERENCES `MeasureUnitType` (`id`),
  CONSTRAINT `FKsgf9nmb63egkc3wt0e27ndsqc` FOREIGN KEY (`product_id`) REFERENCES `Product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FinalProduct` WRITE;
/*!40000 ALTER TABLE `FinalProduct` DISABLE KEYS */;
/*!40000 ALTER TABLE `FinalProduct` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `FinalProductLabel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FinalProductLabel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `finalProduct_id` bigint NOT NULL,
  `productLabel_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKonq6k76jrkw0nfci1r5jh29h6` (`finalProduct_id`),
  KEY `FKabpxmf2jga7s5jkqxl0v8bx5c` (`productLabel_id`),
  CONSTRAINT `FKabpxmf2jga7s5jkqxl0v8bx5c` FOREIGN KEY (`productLabel_id`) REFERENCES `ProductLabel` (`id`),
  CONSTRAINT `FKonq6k76jrkw0nfci1r5jh29h6` FOREIGN KEY (`finalProduct_id`) REFERENCES `FinalProduct` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `FinalProductLabel` WRITE;
/*!40000 ALTER TABLE `FinalProductLabel` DISABLE KEYS */;
/*!40000 ALTER TABLE `FinalProductLabel` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `GlobalSettings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `GlobalSettings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `isPublic` bit(1) DEFAULT NULL,
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK893h0n4eip1a71mlo73qarst1` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `GlobalSettings` WRITE;
/*!40000 ALTER TABLE `GlobalSettings` DISABLE KEYS */;
/*!40000 ALTER TABLE `GlobalSettings` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `KnowledgeBlog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `KnowledgeBlog` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci,
  `date` date DEFAULT NULL,
  `summary` longtext COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('FAIRNESS','PROVENANCE','QUALITY') COLLATE utf8mb4_unicode_ci NOT NULL,
  `youtubeUrl` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXj2wseaneec8w3jk29pdyakhu3` (`type`),
  KEY `FKdp3obm3682few5err1t9pilgo` (`product_id`),
  CONSTRAINT `FKdp3obm3682few5err1t9pilgo` FOREIGN KEY (`product_id`) REFERENCES `Product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `KnowledgeBlog` WRITE;
/*!40000 ALTER TABLE `KnowledgeBlog` DISABLE KEYS */;
/*!40000 ALTER TABLE `KnowledgeBlog` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `KnowledgeBlog_Document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `KnowledgeBlog_Document` (
  `KnowledgeBlog_id` bigint NOT NULL,
  `documents_id` bigint NOT NULL,
  KEY `FKmh4bqfxnxtte4o7mxu5ae5y00` (`documents_id`),
  KEY `FKdj09xtee48258tlupd90p0nm7` (`KnowledgeBlog_id`),
  CONSTRAINT `FKdj09xtee48258tlupd90p0nm7` FOREIGN KEY (`KnowledgeBlog_id`) REFERENCES `KnowledgeBlog` (`id`),
  CONSTRAINT `FKmh4bqfxnxtte4o7mxu5ae5y00` FOREIGN KEY (`documents_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `KnowledgeBlog_Document` WRITE;
/*!40000 ALTER TABLE `KnowledgeBlog_Document` DISABLE KEYS */;
/*!40000 ALTER TABLE `KnowledgeBlog_Document` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `MeasureUnitType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `MeasureUnitType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `weight` decimal(38,2) DEFAULT NULL,
  `underlyingMeasurementUnitType_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKss34jj4jan6qmexcyhgvccmfq` (`underlyingMeasurementUnitType_id`),
  CONSTRAINT `FKss34jj4jan6qmexcyhgvccmfq` FOREIGN KEY (`underlyingMeasurementUnitType_id`) REFERENCES `MeasureUnitType` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `MeasureUnitType` WRITE;
/*!40000 ALTER TABLE `MeasureUnitType` DISABLE KEYS */;
INSERT INTO `MeasureUnitType` VALUES (1,'VOLUME_L','liter',NULL,NULL),(2,'WEIGHT_KG','kg',1.00,NULL),(3,'BAG_60','Bag (60 kg)',60.00,NULL);
/*!40000 ALTER TABLE `MeasureUnitType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `amount` decimal(38,2) DEFAULT NULL,
  `amountPaidToTheCollector` decimal(38,2) DEFAULT NULL,
  `currency` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `formalCreationTime` date DEFAULT NULL,
  `orderReference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `paymentConfirmedAtTime` datetime(6) DEFAULT NULL,
  `paymentPurposeType` enum('ADVANCE_PAYMENT','FIRST_INSTALLMENT','INVOICE_PAYMENT','SECOND_INSTALLMENT','WOMEN_PREMIUM') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `paymentStatus` enum('CONFIRMED','UNCONFIRMED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `paymentType` enum('BANK_TRANSFER','CASH','CASH_VIA_COLLECTOR','CHEQUE','OFFSETTING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `preferredWayOfPayment` enum('BANK_TRANSFER','CASH','CASH_VIA_COLLECTOR','CHEQUE','OFFSETTING','UNKNOWN') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `productionDate` date DEFAULT NULL,
  `purchased` decimal(38,2) DEFAULT NULL,
  `receiptDocumentType` enum('PURCHASE_SHEET','RECEIPT') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `receiptNumber` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipientType` enum('COMPANY','USER_CUSTOMER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `totalPaid` decimal(38,2) DEFAULT NULL,
  `bulkPayment_id` bigint DEFAULT NULL,
  `createdBy_id` bigint NOT NULL,
  `payingCompany_id` bigint DEFAULT NULL,
  `paymentConfirmedByCompany_id` bigint DEFAULT NULL,
  `paymentConfirmedByUser_id` bigint DEFAULT NULL,
  `receiptDocument_id` bigint DEFAULT NULL,
  `recipientCompany_id` bigint DEFAULT NULL,
  `recipientUserCustomer_id` bigint DEFAULT NULL,
  `representativeOfRecipientUserCustomer_id` bigint DEFAULT NULL,
  `stockOrder_id` bigint DEFAULT NULL,
  `updatedBy_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_Payment_receiptDocument_id` (`receiptDocument_id`),
  KEY `FK3m9wbqfo12rn3mytqixccxta2` (`bulkPayment_id`),
  KEY `FKddubxt0xleyftt9jtl2umarko` (`createdBy_id`),
  KEY `FKmt37cugduilusyin97l7apajt` (`payingCompany_id`),
  KEY `FK2knh6y378q3wmqjrwpmqcv4ib` (`paymentConfirmedByCompany_id`),
  KEY `FK6rkw6qfvbpeqawd39n40etbd2` (`paymentConfirmedByUser_id`),
  KEY `FKi8y43p75khktv5au5pa12cxwl` (`recipientCompany_id`),
  KEY `FKgd557mftr3sbwakyhn8m8xhds` (`recipientUserCustomer_id`),
  KEY `FKkoipvqhpe48t9lnt7vkxw0jhv` (`representativeOfRecipientUserCustomer_id`),
  KEY `FK115u0lb664tkh1mx8ar66v04j` (`stockOrder_id`),
  KEY `FKglvtlmye2i8ap6yslve1ak7hj` (`updatedBy_id`),
  CONSTRAINT `FK115u0lb664tkh1mx8ar66v04j` FOREIGN KEY (`stockOrder_id`) REFERENCES `StockOrder` (`id`),
  CONSTRAINT `FK2knh6y378q3wmqjrwpmqcv4ib` FOREIGN KEY (`paymentConfirmedByCompany_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FK3m9wbqfo12rn3mytqixccxta2` FOREIGN KEY (`bulkPayment_id`) REFERENCES `BulkPayment` (`id`),
  CONSTRAINT `FK6rkw6qfvbpeqawd39n40etbd2` FOREIGN KEY (`paymentConfirmedByUser_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FK781d0x6ftnxfjmvls8lds91gq` FOREIGN KEY (`receiptDocument_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FKddubxt0xleyftt9jtl2umarko` FOREIGN KEY (`createdBy_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKgd557mftr3sbwakyhn8m8xhds` FOREIGN KEY (`recipientUserCustomer_id`) REFERENCES `UserCustomer` (`id`),
  CONSTRAINT `FKglvtlmye2i8ap6yslve1ak7hj` FOREIGN KEY (`updatedBy_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKi8y43p75khktv5au5pa12cxwl` FOREIGN KEY (`recipientCompany_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKkoipvqhpe48t9lnt7vkxw0jhv` FOREIGN KEY (`representativeOfRecipientUserCustomer_id`) REFERENCES `UserCustomer` (`id`),
  CONSTRAINT `FKmt37cugduilusyin97l7apajt` FOREIGN KEY (`payingCompany_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Payment` WRITE;
/*!40000 ALTER TABLE `Payment` DISABLE KEYS */;
/*!40000 ALTER TABLE `Payment` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Plot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Plot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `geoId` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lastUpdated` datetime(6) DEFAULT NULL,
  `numberOfPlants` int DEFAULT NULL,
  `organicStartOfTransition` datetime(6) DEFAULT NULL,
  `plotName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `size` double DEFAULT NULL,
  `unit` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `crop_id` bigint DEFAULT NULL,
  `farmer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKp866yyb8jfs2fawyol9hc0xq4` (`crop_id`),
  KEY `FKmckxho0qyajs99kvv1hstk7de` (`farmer_id`),
  CONSTRAINT `FKmckxho0qyajs99kvv1hstk7de` FOREIGN KEY (`farmer_id`) REFERENCES `UserCustomer` (`id`),
  CONSTRAINT `FKp866yyb8jfs2fawyol9hc0xq4` FOREIGN KEY (`crop_id`) REFERENCES `ProductType` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Plot` WRITE;
/*!40000 ALTER TABLE `Plot` DISABLE KEYS */;
/*!40000 ALTER TABLE `Plot` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `PlotCoordinate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PlotCoordinate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `plot_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKaajjvpqh3l69s73fo95qs99sk` (`plot_id`),
  CONSTRAINT `FKaajjvpqh3l69s73fo95qs99sk` FOREIGN KEY (`plot_id`) REFERENCES `Plot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `PlotCoordinate` WRITE;
/*!40000 ALTER TABLE `PlotCoordinate` DISABLE KEYS */;
/*!40000 ALTER TABLE `PlotCoordinate` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Process` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `production` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Process` WRITE;
/*!40000 ALTER TABLE `Process` DISABLE KEYS */;
/*!40000 ALTER TABLE `Process` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingAction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingAction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `estimatedOutputQuantityPerUnit` decimal(38,2) DEFAULT NULL,
  `finalProductAction` bit(1) DEFAULT NULL,
  `maxOutputWeight` decimal(38,2) DEFAULT NULL,
  `prefix` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publicTimelineIconType` enum('LEAF','OTHER','QRCODE','SHIP','WAREHOUSE') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publicTimelineLabel` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publicTimelineLocation` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `repackedOutputFinalProducts` bit(1) DEFAULT NULL,
  `sortOrder` bigint DEFAULT NULL,
  `type` enum('FINAL_PROCESSING','GENERATE_QR_CODE','PROCESSING','SHIPMENT','TRANSFER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `inputFinalProduct_id` bigint DEFAULT NULL,
  `inputSemiProduct_id` bigint DEFAULT NULL,
  `outputFinalProduct_id` bigint DEFAULT NULL,
  `qrCodeForFinalProduct_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6xslu9314yj51oq60nvfv59ja` (`company_id`),
  KEY `FK93id1dxrtncnhhlp37cbg4uka` (`inputFinalProduct_id`),
  KEY `FKhj2ps3oosqhrsp3jpt8twtbnt` (`inputSemiProduct_id`),
  KEY `FKjcb11b4o4wnrdrvgyjs8q0vlp` (`outputFinalProduct_id`),
  KEY `FKp80ufrwi1nwpouird3hfl15e7` (`qrCodeForFinalProduct_id`),
  CONSTRAINT `FK6xslu9314yj51oq60nvfv59ja` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FK93id1dxrtncnhhlp37cbg4uka` FOREIGN KEY (`inputFinalProduct_id`) REFERENCES `FinalProduct` (`id`),
  CONSTRAINT `FKhj2ps3oosqhrsp3jpt8twtbnt` FOREIGN KEY (`inputSemiProduct_id`) REFERENCES `SemiProduct` (`id`),
  CONSTRAINT `FKjcb11b4o4wnrdrvgyjs8q0vlp` FOREIGN KEY (`outputFinalProduct_id`) REFERENCES `FinalProduct` (`id`),
  CONSTRAINT `FKp80ufrwi1nwpouird3hfl15e7` FOREIGN KEY (`qrCodeForFinalProduct_id`) REFERENCES `FinalProduct` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingAction` WRITE;
/*!40000 ALTER TABLE `ProcessingAction` DISABLE KEYS */;
INSERT INTO `ProcessingAction` VALUES (7001,'2026-10-21 21:24:37.196000','2026-10-21 21:24:37.196000',0,0.83,_binary '\0',NULL,'UPG',NULL,NULL,NULL,NULL,NULL,'PROCESSING',2001,NULL,6001,NULL,NULL);
/*!40000 ALTER TABLE `ProcessingAction` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingActionFacility`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingActionFacility` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `facility_id` bigint NOT NULL,
  `processingAction_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKna81ra301vittgk2tpkdeytd6` (`facility_id`),
  KEY `FKloyqqfoerxmr52nogdragqho4` (`processingAction_id`),
  CONSTRAINT `FKloyqqfoerxmr52nogdragqho4` FOREIGN KEY (`processingAction_id`) REFERENCES `ProcessingAction` (`id`),
  CONSTRAINT `FKna81ra301vittgk2tpkdeytd6` FOREIGN KEY (`facility_id`) REFERENCES `Facility` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingActionFacility` WRITE;
/*!40000 ALTER TABLE `ProcessingActionFacility` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingActionFacility` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingActionOutputSemiProduct`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingActionOutputSemiProduct` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `maxOutputWeight` decimal(38,2) DEFAULT NULL,
  `repackedOutput` bit(1) DEFAULT NULL,
  `outputSemiProduct_id` bigint NOT NULL,
  `processingAction_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc4gp5jqufrbob7yfyxffhpxpq` (`outputSemiProduct_id`),
  KEY `FKp68rslba7686lvhswfpkhgf3f` (`processingAction_id`),
  CONSTRAINT `FKc4gp5jqufrbob7yfyxffhpxpq` FOREIGN KEY (`outputSemiProduct_id`) REFERENCES `SemiProduct` (`id`),
  CONSTRAINT `FKp68rslba7686lvhswfpkhgf3f` FOREIGN KEY (`processingAction_id`) REFERENCES `ProcessingAction` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingActionOutputSemiProduct` WRITE;
/*!40000 ALTER TABLE `ProcessingActionOutputSemiProduct` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingActionOutputSemiProduct` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingActionPEF`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingActionPEF` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mandatory` bit(1) DEFAULT NULL,
  `requiredOnQuote` bit(1) DEFAULT NULL,
  `processingAction_id` bigint NOT NULL,
  `processingEvidenceField_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkvffte8ib2rstrrfov5h1q300` (`processingAction_id`),
  KEY `FKdg5qwnv5t6ismr544k2odqi07` (`processingEvidenceField_id`),
  CONSTRAINT `FKdg5qwnv5t6ismr544k2odqi07` FOREIGN KEY (`processingEvidenceField_id`) REFERENCES `ProcessingEvidenceField` (`id`),
  CONSTRAINT `FKkvffte8ib2rstrrfov5h1q300` FOREIGN KEY (`processingAction_id`) REFERENCES `ProcessingAction` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingActionPEF` WRITE;
/*!40000 ALTER TABLE `ProcessingActionPEF` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingActionPEF` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingActionPET`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingActionPET` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mandatory` bit(1) DEFAULT NULL,
  `requiredOnQuote` bit(1) DEFAULT NULL,
  `requiredOneOfGroupIdForQuote` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `processingAction_id` bigint NOT NULL,
  `processingEvidenceType_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcm7qejvmwwudu002b7114rji0` (`processingAction_id`),
  KEY `FKt7i15u2grqh93v3w16fegkcd0` (`processingEvidenceType_id`),
  CONSTRAINT `FKcm7qejvmwwudu002b7114rji0` FOREIGN KEY (`processingAction_id`) REFERENCES `ProcessingAction` (`id`),
  CONSTRAINT `FKt7i15u2grqh93v3w16fegkcd0` FOREIGN KEY (`processingEvidenceType_id`) REFERENCES `ProcessingEvidenceType` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingActionPET` WRITE;
/*!40000 ALTER TABLE `ProcessingActionPET` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingActionPET` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingActionTranslation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingActionTranslation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `processingAction_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXaas8e9sv5ikwlbjpskfbpa7ds` (`processingAction_id`,`language`,`name`),
  CONSTRAINT `FKcbbfjxd6hbggu5b718siionqg` FOREIGN KEY (`processingAction_id`) REFERENCES `ProcessingAction` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingActionTranslation` WRITE;
/*!40000 ALTER TABLE `ProcessingActionTranslation` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingActionTranslation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingActionValueChain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingActionValueChain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `processingAction_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbmpejepywtnf6wrr56mqhn4vt` (`processingAction_id`),
  KEY `FKiuxvh53qg2h5x4kr0i8ln40v9` (`valueChain_id`),
  CONSTRAINT `FKbmpejepywtnf6wrr56mqhn4vt` FOREIGN KEY (`processingAction_id`) REFERENCES `ProcessingAction` (`id`),
  CONSTRAINT `FKiuxvh53qg2h5x4kr0i8ln40v9` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingActionValueChain` WRITE;
/*!40000 ALTER TABLE `ProcessingActionValueChain` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingActionValueChain` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingEvidenceField`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingEvidenceField` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `fieldName` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('DATE','EXCHANGE_RATE','INTEGER','NUMBER','OBJECT','PRICE','STRING','TEXT','TIMESTAMP') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX3tvjox6jnv10x6cs6gycqp0op` (`label`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingEvidenceField` WRITE;
/*!40000 ALTER TABLE `ProcessingEvidenceField` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingEvidenceField` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingEvidenceFieldTranslation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingEvidenceFieldTranslation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `label` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `processingEvidenceField_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKp5f7itfssgxk9o473ppken09e` (`processingEvidenceField_id`),
  CONSTRAINT `FKp5f7itfssgxk9o473ppken09e` FOREIGN KEY (`processingEvidenceField_id`) REFERENCES `ProcessingEvidenceField` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingEvidenceFieldTranslation` WRITE;
/*!40000 ALTER TABLE `ProcessingEvidenceFieldTranslation` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingEvidenceFieldTranslation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingEvidenceType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingEvidenceType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fairness` bit(1) DEFAULT NULL,
  `label` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provenance` bit(1) DEFAULT NULL,
  `quality` bit(1) DEFAULT NULL,
  `type` enum('CALCULATED','DOCUMENT','FIELD') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingEvidenceType` WRITE;
/*!40000 ALTER TABLE `ProcessingEvidenceType` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingEvidenceType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingEvidenceTypeTranslation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingEvidenceTypeTranslation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `label` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `processingEvidenceType_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjbfjeqv07ytvw1894v45kpo7k` (`processingEvidenceType_id`),
  CONSTRAINT `FKjbfjeqv07ytvw1894v45kpo7k` FOREIGN KEY (`processingEvidenceType_id`) REFERENCES `ProcessingEvidenceType` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingEvidenceTypeTranslation` WRITE;
/*!40000 ALTER TABLE `ProcessingEvidenceTypeTranslation` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProcessingEvidenceTypeTranslation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProcessingOrder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProcessingOrder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `initiatorUserId` bigint DEFAULT NULL,
  `processingDate` date DEFAULT NULL,
  `processingAction_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKehm7yh2vt9pyyt84nps0y5ht1` (`processingAction_id`),
  CONSTRAINT `FKehm7yh2vt9pyyt84nps0y5ht1` FOREIGN KEY (`processingAction_id`) REFERENCES `ProcessingAction` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProcessingOrder` WRITE;
/*!40000 ALTER TABLE `ProcessingOrder` DISABLE KEYS */;
INSERT INTO `ProcessingOrder` VALUES (8001,'2026-10-21 21:24:37.198937','2026-10-21 21:24:37.198937',0,1001,'2026-09-18',7001);
/*!40000 ALTER TABLE `ProcessingOrder` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` longtext COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `originText` longtext COLLATE utf8mb4_unicode_ci,
  `entityVersion` bigint NOT NULL,
  `status` enum('ACTIVE','DISABLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `businessToCustomerSettings_id` bigint DEFAULT NULL,
  `journey_id` bigint DEFAULT NULL,
  `photo_id` bigint DEFAULT NULL,
  `process_id` bigint DEFAULT NULL,
  `responsibility_id` bigint DEFAULT NULL,
  `settings_id` bigint DEFAULT NULL,
  `sustainability_id` bigint DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_Product_process_id` (`process_id`),
  UNIQUE KEY `UK_LEGACY_Product_responsibility_id` (`responsibility_id`),
  UNIQUE KEY `UK_LEGACY_Product_sustainability_id` (`sustainability_id`),
  UNIQUE KEY `UK_LEGACY_Product_journey_id` (`journey_id`),
  UNIQUE KEY `UK_LEGACY_Product_settings_id` (`settings_id`),
  UNIQUE KEY `UK_LEGACY_Product_businessToCustomerSettings_id` (`businessToCustomerSettings_id`),
  KEY `IDXgxubutkbk5o2a6aakbe7q9kww` (`name`),
  KEY `FKe2e5kw40uhrd3109q8hoj5crn` (`photo_id`),
  KEY `FKgqmlqsdx3a92mn4f5fr0c7mm6` (`company_id`),
  KEY `FKonsi6jg1xteevfxoi6n62wwnd` (`valueChain_id`),
  CONSTRAINT `FKcn4o73emn0h52j2nva4rteiby` FOREIGN KEY (`sustainability_id`) REFERENCES `Sustainability` (`id`),
  CONSTRAINT `FKe2e5kw40uhrd3109q8hoj5crn` FOREIGN KEY (`photo_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FKe3y2jt0njpw25mbqpexhgakfp` FOREIGN KEY (`businessToCustomerSettings_id`) REFERENCES `BusinessToCustomerSettings` (`id`),
  CONSTRAINT `FKgqmlqsdx3a92mn4f5fr0c7mm6` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKluwsxgwrwwkbbkqsm4d850ven` FOREIGN KEY (`journey_id`) REFERENCES `ProductJourney` (`id`),
  CONSTRAINT `FKn4yklv6n1rgy673t8mvrnrxem` FOREIGN KEY (`responsibility_id`) REFERENCES `Responsibility` (`id`),
  CONSTRAINT `FKonsi6jg1xteevfxoi6n62wwnd` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`),
  CONSTRAINT `FKsq8mv5bkm7u234ex7j18fxaqu` FOREIGN KEY (`settings_id`) REFERENCES `ProductSettings` (`id`),
  CONSTRAINT `FKsylrjatbji2vvpivyswu3nv0r` FOREIGN KEY (`process_id`) REFERENCES `Process` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Product` WRITE;
/*!40000 ALTER TABLE `Product` DISABLE KEYS */;
/*!40000 ALTER TABLE `Product` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductCompany`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductCompany` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` enum('ASSOCIATION','BUYER','EXPORTER','IMPORTER','OWNER','PROCESSOR','PRODUCER','ROASTER','TRADER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKl8wtvs6muyjxu24kb75ybylx5` (`company_id`),
  KEY `FK1sth1qq1mlpr9172k30h937m2` (`product_id`),
  CONSTRAINT `FK1sth1qq1mlpr9172k30h937m2` FOREIGN KEY (`product_id`) REFERENCES `Product` (`id`),
  CONSTRAINT `FKl8wtvs6muyjxu24kb75ybylx5` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductCompany` WRITE;
/*!40000 ALTER TABLE `ProductCompany` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductCompany` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductDataSharingAgreement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductDataSharingAgreement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `document_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbrv32h6qygwrkph15fitbot9w` (`document_id`),
  KEY `FKbutcvvnswgue59dtlyw4o8e3m` (`product_id`),
  CONSTRAINT `FKbrv32h6qygwrkph15fitbot9w` FOREIGN KEY (`document_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FKbutcvvnswgue59dtlyw4o8e3m` FOREIGN KEY (`product_id`) REFERENCES `Product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductDataSharingAgreement` WRITE;
/*!40000 ALTER TABLE `ProductDataSharingAgreement` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductDataSharingAgreement` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductJourney`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductJourney` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `markers` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductJourney` WRITE;
/*!40000 ALTER TABLE `ProductJourney` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductJourney` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductLabel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductLabel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entityVersion` bigint NOT NULL,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PUBLISHED','UNPUBLISHED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `uuid` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9vtfk5t5n8kjs631k8thdxkai` (`uuid`),
  KEY `FKdjk0mx1d2s1580qiody246qvw` (`content_id`),
  KEY `FKn3ulw2udilgadyve3d182tew8` (`product_id`),
  CONSTRAINT `FKdjk0mx1d2s1580qiody246qvw` FOREIGN KEY (`content_id`) REFERENCES `ProductLabelContent` (`id`),
  CONSTRAINT `FKn3ulw2udilgadyve3d182tew8` FOREIGN KEY (`product_id`) REFERENCES `Product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductLabel` WRITE;
/*!40000 ALTER TABLE `ProductLabel` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductLabel` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductLabelBatch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductLabelBatch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `checkAuthenticity` bit(1) DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `expiryDate` date DEFAULT NULL,
  `number` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `productionDate` date DEFAULT NULL,
  `traceOrigin` bit(1) DEFAULT NULL,
  `label_id` bigint DEFAULT NULL,
  `photo_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKndev6922kprich7xrl7j7d652` (`label_id`,`number`),
  KEY `IDXetkl7cyo38glfu40vefrbfqmo` (`number`),
  KEY `FKj8oxrgt7slr9f2hkrr6qdl10p` (`photo_id`),
  CONSTRAINT `FK1csjhtfu983nw13gg93b63hvu` FOREIGN KEY (`label_id`) REFERENCES `ProductLabel` (`id`),
  CONSTRAINT `FKj8oxrgt7slr9f2hkrr6qdl10p` FOREIGN KEY (`photo_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductLabelBatch` WRITE;
/*!40000 ALTER TABLE `ProductLabelBatch` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductLabelBatch` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductLabelCompanyDocument`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductLabelCompanyDocument` (
  `productLabelId` bigint NOT NULL,
  `companyDocumentId` bigint NOT NULL,
  PRIMARY KEY (`companyDocumentId`,`productLabelId`),
  KEY `FKptwas3tk4j0wqjrsfrwgetnwl` (`productLabelId`),
  CONSTRAINT `FKhm53uks8p24yrf5mv0qjvm432` FOREIGN KEY (`companyDocumentId`) REFERENCES `CompanyDocument` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKptwas3tk4j0wqjrsfrwgetnwl` FOREIGN KEY (`productLabelId`) REFERENCES `ProductLabel` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductLabelCompanyDocument` WRITE;
/*!40000 ALTER TABLE `ProductLabelCompanyDocument` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductLabelCompanyDocument` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductLabelContent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductLabelContent` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` longtext COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `originText` longtext COLLATE utf8mb4_unicode_ci,
  `entityVersion` bigint NOT NULL,
  `businessToCustomerSettings_id` bigint DEFAULT NULL,
  `journey_id` bigint DEFAULT NULL,
  `photo_id` bigint DEFAULT NULL,
  `process_id` bigint DEFAULT NULL,
  `responsibility_id` bigint DEFAULT NULL,
  `settings_id` bigint DEFAULT NULL,
  `sustainability_id` bigint DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_ProductLabelContent_process_id` (`process_id`),
  UNIQUE KEY `UK_LEGACY_ProductLabelContent_responsibility_id` (`responsibility_id`),
  UNIQUE KEY `UK_LEGACY_ProductLabelContent_sustainability_id` (`sustainability_id`),
  UNIQUE KEY `UK_LEGACY_ProductLabelContent_journey_id` (`journey_id`),
  UNIQUE KEY `UK_LEGACY_ProductLabelContent_settings_id` (`settings_id`),
  UNIQUE KEY `UK_LEGACY_ProductLabelContent_businessToCustomerSettings_id` (`businessToCustomerSettings_id`),
  KEY `FK4g938rxyuixeug0e5o0fgcevq` (`photo_id`),
  KEY `FKbdfylsr63yjdq22gjr5fk9cqt` (`company_id`),
  CONSTRAINT `FK1o4cf92nboed8jyamu3ifgvdh` FOREIGN KEY (`businessToCustomerSettings_id`) REFERENCES `BusinessToCustomerSettings` (`id`),
  CONSTRAINT `FK4g938rxyuixeug0e5o0fgcevq` FOREIGN KEY (`photo_id`) REFERENCES `Document` (`id`),
  CONSTRAINT `FK7hg994ujr4lt4qmk2i7ohqkke` FOREIGN KEY (`settings_id`) REFERENCES `ProductSettings` (`id`),
  CONSTRAINT `FKbdfylsr63yjdq22gjr5fk9cqt` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKhs7mjvu01745rkr4xwovsoequ` FOREIGN KEY (`responsibility_id`) REFERENCES `Responsibility` (`id`),
  CONSTRAINT `FKl3q28anaul4acukdgm7x1kjxd` FOREIGN KEY (`journey_id`) REFERENCES `ProductJourney` (`id`),
  CONSTRAINT `FKok98gsnbogntx0j5gavny1q35` FOREIGN KEY (`sustainability_id`) REFERENCES `Sustainability` (`id`),
  CONSTRAINT `FKrd70apnoah0q9utmjoo390m7g` FOREIGN KEY (`process_id`) REFERENCES `Process` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductLabelContent` WRITE;
/*!40000 ALTER TABLE `ProductLabelContent` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductLabelContent` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductLabelFeedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductLabelFeedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `feedback` longtext COLLATE utf8mb4_unicode_ci,
  `gdprConsent` bit(1) DEFAULT NULL,
  `privacyPolicyConsent` bit(1) DEFAULT NULL,
  `termsOfUseConsent` bit(1) DEFAULT NULL,
  `type` enum('COMPLAINT','PRAISE','PROPOSAL') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `label_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKij8bq5eplvc8ly8ky5wef25ba` (`label_id`),
  CONSTRAINT `FKij8bq5eplvc8ly8ky5wef25ba` FOREIGN KEY (`label_id`) REFERENCES `ProductLabel` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductLabelFeedback` WRITE;
/*!40000 ALTER TABLE `ProductLabelFeedback` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductLabelFeedback` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductLabelFeedback_questionnaireAnswers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductLabelFeedback_questionnaireAnswers` (
  `ProductLabelFeedback_id` bigint NOT NULL,
  `answer` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `questionKey` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`ProductLabelFeedback_id`,`questionKey`),
  CONSTRAINT `FK9183rb3ifck3t3k56o6s44llo` FOREIGN KEY (`ProductLabelFeedback_id`) REFERENCES `ProductLabelFeedback` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductLabelFeedback_questionnaireAnswers` WRITE;
/*!40000 ALTER TABLE `ProductLabelFeedback_questionnaireAnswers` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductLabelFeedback_questionnaireAnswers` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductLabel_fields`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductLabel_fields` (
  `ProductLabel_id` bigint NOT NULL,
  `fields_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fields_section` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fields_visible` bit(1) DEFAULT NULL,
  `fields_ORDER` int NOT NULL,
  PRIMARY KEY (`ProductLabel_id`,`fields_ORDER`),
  CONSTRAINT `FK1yy8s1hb30og30a9fg7ap8gbk` FOREIGN KEY (`ProductLabel_id`) REFERENCES `ProductLabel` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductLabel_fields` WRITE;
/*!40000 ALTER TABLE `ProductLabel_fields` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductLabel_fields` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductLocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductLocation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_cell` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasDepartment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasFarm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasMunicipality` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasVillage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_otherAddress` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_sector` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_state` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_village` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_zip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `numberOfFarmers` int DEFAULT NULL,
  `pinName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_country_id` bigint DEFAULT NULL,
  `content_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkirfhk5mx4ak0hs7wc74ntwl4` (`address_country_id`),
  KEY `FK8tao8cqd43mgghwrqsklj36yn` (`content_id`),
  KEY `FK4dpke2vdl5qsf0bk4htef6gd7` (`product_id`),
  CONSTRAINT `FK4dpke2vdl5qsf0bk4htef6gd7` FOREIGN KEY (`product_id`) REFERENCES `Product` (`id`),
  CONSTRAINT `FK8tao8cqd43mgghwrqsklj36yn` FOREIGN KEY (`content_id`) REFERENCES `ProductLabelContent` (`id`),
  CONSTRAINT `FKkirfhk5mx4ak0hs7wc74ntwl4` FOREIGN KEY (`address_country_id`) REFERENCES `Country` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductLocation` WRITE;
/*!40000 ALTER TABLE `ProductLocation` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductLocation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductOrder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductOrder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `deliveryDeadline` date NOT NULL,
  `orderId` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requiredOrganic` bit(1) DEFAULT NULL,
  `requiredWomensOnly` bit(1) DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `facility_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5bws3cmf2p0we5tvg2k961vow` (`customer_id`),
  KEY `FKncu91af8haiwa7h4nap56m8dc` (`facility_id`),
  CONSTRAINT `FK5bws3cmf2p0we5tvg2k961vow` FOREIGN KEY (`customer_id`) REFERENCES `CompanyCustomer` (`id`),
  CONSTRAINT `FKncu91af8haiwa7h4nap56m8dc` FOREIGN KEY (`facility_id`) REFERENCES `Facility` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductOrder` WRITE;
/*!40000 ALTER TABLE `ProductOrder` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductOrder` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductSettings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductSettings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `costBreakdown` bit(1) DEFAULT NULL,
  `gdprText` longtext COLLATE utf8mb4_unicode_ci,
  `incomeIncreaseDescription` longtext COLLATE utf8mb4_unicode_ci,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `privacyPolicyText` longtext COLLATE utf8mb4_unicode_ci,
  `termsOfUseText` longtext COLLATE utf8mb4_unicode_ci,
  `incomeIncreaseDocument_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKe79ndlekldujaawt2chbqo0mf` (`incomeIncreaseDocument_id`),
  CONSTRAINT `FKe79ndlekldujaawt2chbqo0mf` FOREIGN KEY (`incomeIncreaseDocument_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductSettings` WRITE;
/*!40000 ALTER TABLE `ProductSettings` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductSettings` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductSettings_pricingTransparency`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductSettings_pricingTransparency` (
  `ProductSettings_id` bigint NOT NULL,
  `ptValue` double DEFAULT NULL,
  `ptKey` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`ProductSettings_id`,`ptKey`),
  CONSTRAINT `FKs8opebx2q1e5mmxsqlxgbahjd` FOREIGN KEY (`ProductSettings_id`) REFERENCES `ProductSettings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductSettings_pricingTransparency` WRITE;
/*!40000 ALTER TABLE `ProductSettings_pricingTransparency` DISABLE KEYS */;
/*!40000 ALTER TABLE `ProductSettings_pricingTransparency` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductType` WRITE;
/*!40000 ALTER TABLE `ProductType` DISABLE KEYS */;
INSERT INTO `ProductType` VALUES (1,'2026-10-21 21:23:09.787244','2026-10-21 21:23:09.846282','COFFEE','Coffee product type',1,'Coffee'),(2,'2026-10-21 21:23:09.792818','2026-10-21 21:23:09.850482','MACADAMIA','Macadamia product type',1,'Macadamia');
/*!40000 ALTER TABLE `ProductType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ProductTypeTranslation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ProductTypeTranslation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `productType_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK12qoky74bjlnyc0jif8aq6yag` (`productType_id`),
  CONSTRAINT `FK12qoky74bjlnyc0jif8aq6yag` FOREIGN KEY (`productType_id`) REFERENCES `ProductType` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ProductTypeTranslation` WRITE;
/*!40000 ALTER TABLE `ProductTypeTranslation` DISABLE KEYS */;
INSERT INTO `ProductTypeTranslation` VALUES (1,'EN','Coffee product type',0,'Coffee',1),(2,'DE','Coffee product type',0,'Coffee',1),(3,'RW','Coffee product type',0,'Coffee',1),(4,'ES','Coffee product type',0,'Coffee',1),(5,'EN','Macadamia product type',0,'Macadamia',2),(6,'DE','Macadamia product type',0,'Macadamia',2),(7,'RW','Macadamia product type',0,'Macadamia',2),(8,'ES','Macadamia product type',0,'Macadamia',2);
/*!40000 ALTER TABLE `ProductTypeTranslation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `REVINFO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `REVINFO` (
  `REV` int NOT NULL AUTO_INCREMENT,
  `REVTSTMP` bigint DEFAULT NULL,
  PRIMARY KEY (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `REVINFO` WRITE;
/*!40000 ALTER TABLE `REVINFO` DISABLE KEYS */;
/*!40000 ALTER TABLE `REVINFO` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `RequestLog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `RequestLog` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `geoLocation_city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `geoLocation_country` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `geoLocation_latitude` double DEFAULT NULL,
  `geoLocation_longitude` double DEFAULT NULL,
  `ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `logKey` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('CLICK_CERT_STD','CLICK_COMPANY_PAGE','CLICK_PROD_REC','CLICK_SOCIAL_MEDIA','CLICK_VERIFY_BATCH','CLICK_VERIFY_BATCH_ORIGIN','LANDING_PAGE','VERIFY_BATCH','VERIFY_BATCH_ORIGIN','VISIT_QR') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `value1` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `value2` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXin6vvqt8h9o3tv14g7wc23o5x` (`creationTimestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `RequestLog` WRITE;
/*!40000 ALTER TABLE `RequestLog` DISABLE KEYS */;
/*!40000 ALTER TABLE `RequestLog` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Responsibility`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Responsibility` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `laborPolicies` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Responsibility` WRITE;
/*!40000 ALTER TABLE `Responsibility` DISABLE KEYS */;
/*!40000 ALTER TABLE `Responsibility` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `SemiProduct`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SemiProduct` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `isBuyable` bit(1) DEFAULT NULL,
  `isSKU` bit(1) DEFAULT NULL,
  `isSKUEndCustomer` bit(1) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `measurementUnitType_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKppde4op6gkof0g1ux590e4qq5` (`measurementUnitType_id`),
  CONSTRAINT `FKppde4op6gkof0g1ux590e4qq5` FOREIGN KEY (`measurementUnitType_id`) REFERENCES `MeasureUnitType` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `SemiProduct` WRITE;
/*!40000 ALTER TABLE `SemiProduct` DISABLE KEYS */;
INSERT INTO `SemiProduct` VALUES (6001,'2026-10-21 21:24:37.190562','2026-10-21 21:24:37.190562','synthetic upgrade semi product',_binary '',_binary '\0',_binary '\0','upgrade-fixture-semi-product',2);
/*!40000 ALTER TABLE `SemiProduct` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `SemiProductTranslation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SemiProductTranslation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `semiProduct_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKg7ujk5wv9ob8yikydyfufrpu9` (`semiProduct_id`),
  CONSTRAINT `FKg7ujk5wv9ob8yikydyfufrpu9` FOREIGN KEY (`semiProduct_id`) REFERENCES `SemiProduct` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `SemiProductTranslation` WRITE;
/*!40000 ALTER TABLE `SemiProductTranslation` DISABLE KEYS */;
/*!40000 ALTER TABLE `SemiProductTranslation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `StockOrder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `StockOrder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `availableQuantity` decimal(38,2) DEFAULT NULL,
  `balance` decimal(38,2) DEFAULT NULL,
  `comments` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cost` decimal(38,2) DEFAULT NULL,
  `creatorId` bigint DEFAULT NULL,
  `currency` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currencyForEndCustomer` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `damagedPriceDeduction` decimal(38,2) DEFAULT NULL,
  `damagedWeightDeduction` decimal(38,2) DEFAULT NULL,
  `deliveryTime` date DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `fulfilledQuantity` decimal(38,2) DEFAULT NULL,
  `identifier` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `internalLotNumber` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `isAvailable` bit(1) DEFAULT NULL,
  `isOpenOrder` bit(1) DEFAULT NULL,
  `isPurchaseOrder` bit(1) DEFAULT NULL,
  `lotPrefix` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `orderId` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `orderType` enum('GENERAL_ORDER','PROCESSING_ORDER','PURCHASE_ORDER','TRANSFER_ORDER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `organic` bit(1) DEFAULT NULL,
  `outQuantityNotInRange` bit(1) DEFAULT NULL,
  `paid` decimal(38,2) DEFAULT NULL,
  `preferredWayOfPayment` enum('BANK_TRANSFER','CASH','CASH_VIA_COLLECTOR','CHEQUE','OFFSETTING','UNKNOWN') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priceDeterminedLater` bit(1) DEFAULT NULL,
  `pricePerUnit` decimal(38,2) DEFAULT NULL,
  `pricePerUnitForEndCustomer` decimal(38,2) DEFAULT NULL,
  `productionDate` date DEFAULT NULL,
  `qrCodeTag` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `repackedOriginStockOrderId` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requiredWomensCoffee` bit(1) DEFAULT NULL,
  `sacNumber` int DEFAULT NULL,
  `tare` decimal(38,2) DEFAULT NULL,
  `totalGrossQuantity` decimal(38,2) DEFAULT NULL,
  `totalQuantity` decimal(38,2) DEFAULT NULL,
  `womenShare` bit(1) DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `consumerCompanyCustomer_id` bigint DEFAULT NULL,
  `createdBy_id` bigint NOT NULL,
  `facility_id` bigint DEFAULT NULL,
  `finalProduct_id` bigint DEFAULT NULL,
  `measurementUnitType_id` bigint DEFAULT NULL,
  `processingOrder_id` bigint DEFAULT NULL,
  `producerUserCustomer_id` bigint DEFAULT NULL,
  `productOrder_id` bigint DEFAULT NULL,
  `productionLocation_id` bigint DEFAULT NULL,
  `qrCodeTagFinalProduct_id` bigint DEFAULT NULL,
  `quoteCompany_id` bigint DEFAULT NULL,
  `quoteFacility_id` bigint DEFAULT NULL,
  `representativeOfProducerUserCustomer_id` bigint DEFAULT NULL,
  `semiProduct_id` bigint DEFAULT NULL,
  `updatedBy_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_StockOrder_productionLocation_id` (`productionLocation_id`),
  UNIQUE KEY `UK_LEGACY_StockOrder_consumerCompanyCustomer_id` (`consumerCompanyCustomer_id`),
  KEY `FKagwb3fo2tj9eut9mm291q6xf8` (`company_id`),
  KEY `FKichgxiwjfyw2oyt9n3p2togm7` (`createdBy_id`),
  KEY `FK8qviw6rhyt2prrhli1xvtx9jm` (`facility_id`),
  KEY `FK1w8xtfkxsjiompkr6ef0qj1er` (`finalProduct_id`),
  KEY `FKhml4tgblny1qllfs52iihwlv7` (`measurementUnitType_id`),
  KEY `FKnmdr1vvvpt0plaqkmeoi4p4c0` (`processingOrder_id`),
  KEY `FKi94c6srcmf4e24xh3egn5xu8m` (`producerUserCustomer_id`),
  KEY `FKf17kfi346gs25kvh0bgeqdm4h` (`productOrder_id`),
  KEY `FKtcmi4vhn99kmw7ck8r4idau59` (`qrCodeTagFinalProduct_id`),
  KEY `FK8uqjby0ch6ur40bf0qvoi89yo` (`quoteCompany_id`),
  KEY `FKptkjcd0enpouqqqghx51qb3jh` (`quoteFacility_id`),
  KEY `FKq3miflleer2mjagvxoi4rc9sb` (`representativeOfProducerUserCustomer_id`),
  KEY `FK2nx2nl0nc3t1j0y2a7lcri77r` (`semiProduct_id`),
  KEY `FK8rnxf717vsgx5d2mbqkbjknsn` (`updatedBy_id`),
  CONSTRAINT `FK1w8xtfkxsjiompkr6ef0qj1er` FOREIGN KEY (`finalProduct_id`) REFERENCES `FinalProduct` (`id`),
  CONSTRAINT `FK2nx2nl0nc3t1j0y2a7lcri77r` FOREIGN KEY (`semiProduct_id`) REFERENCES `SemiProduct` (`id`),
  CONSTRAINT `FK8qviw6rhyt2prrhli1xvtx9jm` FOREIGN KEY (`facility_id`) REFERENCES `Facility` (`id`),
  CONSTRAINT `FK8rnxf717vsgx5d2mbqkbjknsn` FOREIGN KEY (`updatedBy_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FK8uqjby0ch6ur40bf0qvoi89yo` FOREIGN KEY (`quoteCompany_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKagwb3fo2tj9eut9mm291q6xf8` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKf17kfi346gs25kvh0bgeqdm4h` FOREIGN KEY (`productOrder_id`) REFERENCES `ProductOrder` (`id`),
  CONSTRAINT `FKhml4tgblny1qllfs52iihwlv7` FOREIGN KEY (`measurementUnitType_id`) REFERENCES `MeasureUnitType` (`id`),
  CONSTRAINT `FKi94c6srcmf4e24xh3egn5xu8m` FOREIGN KEY (`producerUserCustomer_id`) REFERENCES `UserCustomer` (`id`),
  CONSTRAINT `FKichgxiwjfyw2oyt9n3p2togm7` FOREIGN KEY (`createdBy_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKn0wr1kysi3kksc68rgc2x64vm` FOREIGN KEY (`productionLocation_id`) REFERENCES `StockOrderLocation` (`id`),
  CONSTRAINT `FKnmdr1vvvpt0plaqkmeoi4p4c0` FOREIGN KEY (`processingOrder_id`) REFERENCES `ProcessingOrder` (`id`),
  CONSTRAINT `FKnyqxerfktpasiojdgb8f2d806` FOREIGN KEY (`consumerCompanyCustomer_id`) REFERENCES `CompanyCustomer` (`id`),
  CONSTRAINT `FKptkjcd0enpouqqqghx51qb3jh` FOREIGN KEY (`quoteFacility_id`) REFERENCES `Facility` (`id`),
  CONSTRAINT `FKq3miflleer2mjagvxoi4rc9sb` FOREIGN KEY (`representativeOfProducerUserCustomer_id`) REFERENCES `UserCustomer` (`id`),
  CONSTRAINT `FKtcmi4vhn99kmw7ck8r4idau59` FOREIGN KEY (`qrCodeTagFinalProduct_id`) REFERENCES `FinalProduct` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `StockOrder` WRITE;
/*!40000 ALTER TABLE `StockOrder` DISABLE KEYS */;
INSERT INTO `StockOrder` VALUES (9001,'2026-10-21 21:24:37.201098','2026-10-21 21:24:37.201098',70.00,70.00,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,30.00,'upgrade-fixture-source-stock',NULL,_binary '',_binary '\0',_binary '',NULL,NULL,'PURCHASE_ORDER',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,100.00,NULL,2001,NULL,1001,5001,NULL,2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,6001,NULL),(9002,'2026-10-21 21:24:37.203880','2026-10-21 21:24:37.203880',25.00,25.00,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'upgrade-fixture-output-stock',NULL,_binary '',_binary '\0',_binary '\0',NULL,NULL,'PROCESSING_ORDER',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,25.00,NULL,2001,NULL,1001,5001,NULL,2,8001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,6001,NULL);
/*!40000 ALTER TABLE `StockOrder` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `StockOrderActivityProof`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `StockOrderActivityProof` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activityProof_id` bigint DEFAULT NULL,
  `stockOrder_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_StockOrderActivityProof_activityProof_id` (`activityProof_id`),
  KEY `FKbnds9x997664q6unbjg7rx2gh` (`stockOrder_id`),
  CONSTRAINT `FKbnds9x997664q6unbjg7rx2gh` FOREIGN KEY (`stockOrder_id`) REFERENCES `StockOrder` (`id`),
  CONSTRAINT `FKmu82ets1wmxbf3h9bl0im1ych` FOREIGN KEY (`activityProof_id`) REFERENCES `ActivityProof` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `StockOrderActivityProof` WRITE;
/*!40000 ALTER TABLE `StockOrderActivityProof` DISABLE KEYS */;
/*!40000 ALTER TABLE `StockOrderActivityProof` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `StockOrderLocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `StockOrderLocation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_cell` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasDepartment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasFarm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasMunicipality` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasVillage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_otherAddress` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_sector` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_state` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_village` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_zip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `numberOfFarmers` int DEFAULT NULL,
  `pinName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_country_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfeklgqo1sjfn3snkpu9kb6v7p` (`address_country_id`),
  CONSTRAINT `FKfeklgqo1sjfn3snkpu9kb6v7p` FOREIGN KEY (`address_country_id`) REFERENCES `Country` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `StockOrderLocation` WRITE;
/*!40000 ALTER TABLE `StockOrderLocation` DISABLE KEYS */;
/*!40000 ALTER TABLE `StockOrderLocation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `StockOrderPEFieldValue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `StockOrderPEFieldValue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `booleanValue` bit(1) DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `instantValue` datetime(6) DEFAULT NULL,
  `numericValue` decimal(38,2) DEFAULT NULL,
  `stringValue` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `processingEvidenceField_id` bigint NOT NULL,
  `stockOrder_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsu77nf9u6gn6elvpluy71sys2` (`processingEvidenceField_id`),
  KEY `FK6jctmm1v20kvejkovp12u21wj` (`stockOrder_id`),
  CONSTRAINT `FK6jctmm1v20kvejkovp12u21wj` FOREIGN KEY (`stockOrder_id`) REFERENCES `StockOrder` (`id`),
  CONSTRAINT `FKsu77nf9u6gn6elvpluy71sys2` FOREIGN KEY (`processingEvidenceField_id`) REFERENCES `ProcessingEvidenceField` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `StockOrderPEFieldValue` WRITE;
/*!40000 ALTER TABLE `StockOrderPEFieldValue` DISABLE KEYS */;
/*!40000 ALTER TABLE `StockOrderPEFieldValue` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `StockOrderPETypeValue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `StockOrderPETypeValue` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `date` datetime(6) DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `otherEvidence` bit(1) DEFAULT NULL,
  `document_id` bigint DEFAULT NULL,
  `processingEvidenceType_id` bigint NOT NULL,
  `stockOrder_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnyfscqqrgy0g3g89fu52boexk` (`document_id`),
  KEY `FKkmqbs3wb284k8s7on8e5ffacm` (`processingEvidenceType_id`),
  KEY `FK4jk9t70quohsreomd57g2wn4o` (`stockOrder_id`),
  CONSTRAINT `FK4jk9t70quohsreomd57g2wn4o` FOREIGN KEY (`stockOrder_id`) REFERENCES `StockOrder` (`id`),
  CONSTRAINT `FKkmqbs3wb284k8s7on8e5ffacm` FOREIGN KEY (`processingEvidenceType_id`) REFERENCES `ProcessingEvidenceType` (`id`),
  CONSTRAINT `FKnyfscqqrgy0g3g89fu52boexk` FOREIGN KEY (`document_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `StockOrderPETypeValue` WRITE;
/*!40000 ALTER TABLE `StockOrderPETypeValue` DISABLE KEYS */;
/*!40000 ALTER TABLE `StockOrderPETypeValue` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Sustainability`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Sustainability` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `co2Footprint` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `packaging` longtext COLLATE utf8mb4_unicode_ci,
  `production` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Sustainability` WRITE;
/*!40000 ALTER TABLE `Sustainability` DISABLE KEYS */;
/*!40000 ALTER TABLE `Sustainability` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `Transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `currency` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint DEFAULT NULL,
  `initiationUserId` bigint DEFAULT NULL,
  `inputQuantity` decimal(38,2) DEFAULT NULL,
  `isProcessing` bit(1) DEFAULT NULL,
  `outputQuantity` decimal(38,2) DEFAULT NULL,
  `pricePerUnit` decimal(38,2) DEFAULT NULL,
  `rejectComment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipmentId` bigint DEFAULT NULL,
  `status` enum('CANCELED','EXECUTED','PENDING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `finalProduct_id` bigint DEFAULT NULL,
  `inputMeasureUnitType_id` bigint DEFAULT NULL,
  `semiProduct_id` bigint DEFAULT NULL,
  `sourceFacility_id` bigint DEFAULT NULL,
  `sourceStockOrder_id` bigint DEFAULT NULL,
  `targetProcessingOrder_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_Transaction_sourceStockOrder_id` (`sourceStockOrder_id`),
  UNIQUE KEY `UK_LEGACY_Transaction_inputMeasureUnitType_id` (`inputMeasureUnitType_id`),
  KEY `FKssfgs8d8bq29ioykwx3t233dj` (`company_id`),
  KEY `FK9cyn45y43ch7xl21yicklihxf` (`finalProduct_id`),
  KEY `FK36oii80imeh1kq9gvpp605ncw` (`semiProduct_id`),
  KEY `FKcieuhct8rhk3xjpkrxt4syaci` (`sourceFacility_id`),
  KEY `FKpeijim9773wvffefx1t5pn9x1` (`targetProcessingOrder_id`),
  CONSTRAINT `FK36oii80imeh1kq9gvpp605ncw` FOREIGN KEY (`semiProduct_id`) REFERENCES `SemiProduct` (`id`),
  CONSTRAINT `FK9cyn45y43ch7xl21yicklihxf` FOREIGN KEY (`finalProduct_id`) REFERENCES `FinalProduct` (`id`),
  CONSTRAINT `FKcieuhct8rhk3xjpkrxt4syaci` FOREIGN KEY (`sourceFacility_id`) REFERENCES `Facility` (`id`),
  CONSTRAINT `FKkclklaiwqr6uwngwqdxdov0f7` FOREIGN KEY (`inputMeasureUnitType_id`) REFERENCES `MeasureUnitType` (`id`),
  CONSTRAINT `FKpeijim9773wvffefx1t5pn9x1` FOREIGN KEY (`targetProcessingOrder_id`) REFERENCES `ProcessingOrder` (`id`),
  CONSTRAINT `FKpepsdbmh1iycjpy9puyjtrdb8` FOREIGN KEY (`sourceStockOrder_id`) REFERENCES `StockOrder` (`id`),
  CONSTRAINT `FKssfgs8d8bq29ioykwx3t233dj` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `Transaction` WRITE;
/*!40000 ALTER TABLE `Transaction` DISABLE KEYS */;
INSERT INTO `Transaction` VALUES (10001,'2026-10-21 21:24:37.207484','2026-10-21 21:24:37.207484',NULL,0,1001,30.00,_binary '',25.00,NULL,NULL,NULL,'EXECUTED',2001,NULL,2,6001,5001,9001,8001);
/*!40000 ALTER TABLE `Transaction` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `User`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `User` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('REGIONAL_ADMIN','SYSTEM_ADMIN','USER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACTIVE','CONFIRMED_EMAIL','DEACTIVATED','UNCONFIRMED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `surname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKe6gkqunxajvyxl5uctpl2vl2p` (`email`),
  KEY `IDXl0h6hvscrlrnctuk3r39iax10` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `User` WRITE;
/*!40000 ALTER TABLE `User` DISABLE KEYS */;
INSERT INTO `User` VALUES (1001,'2026-10-21 21:24:37.164246','2026-10-21 21:24:37.164246','upgrade-fixture-user@inatrace.test',0,'EN','Upgrade','synthetic-only','USER','ACTIVE','Fixture');
/*!40000 ALTER TABLE `User` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `UserCustomer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `UserCustomer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bank_accountHolderName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_accountNumber` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_additionalInformation` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_bankName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `farm_areaOrganicCertified` decimal(38,2) DEFAULT NULL,
  `farm_areaUnit` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `farm_organic` bit(1) DEFAULT NULL,
  `farm_startTransitionToOrganic` datetime(6) DEFAULT NULL,
  `farm_totalCultivatedArea` decimal(38,2) DEFAULT NULL,
  `farmerCompanyInternalId` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` enum('DIVERSE','FEMALE','MALE','N_A') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hasSmartphone` bit(1) DEFAULT NULL,
  `location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `surname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('COLLECTOR','FARMER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `userCustomerLocation_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_LEGACY_UserCustomer_userCustomerLocation_id` (`userCustomerLocation_id`),
  KEY `FK1lypepdlf8t1bp36ux8gjx1sb` (`company_id`),
  KEY `FKh10e8fei3oj1ns2egwk9mjb9v` (`product_id`),
  CONSTRAINT `FK1lypepdlf8t1bp36ux8gjx1sb` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`),
  CONSTRAINT `FKh10e8fei3oj1ns2egwk9mjb9v` FOREIGN KEY (`product_id`) REFERENCES `Product` (`id`),
  CONSTRAINT `FKo4999sgsqb35aanjay9qbyw99` FOREIGN KEY (`userCustomerLocation_id`) REFERENCES `UserCustomerLocation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `UserCustomer` WRITE;
/*!40000 ALTER TABLE `UserCustomer` DISABLE KEYS */;
/*!40000 ALTER TABLE `UserCustomer` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `UserCustomerAssociation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `UserCustomerAssociation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT NULL,
  `userCustomer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK66kikp7i5erki6wwqcpa54eqw` (`company_id`),
  KEY `FK1h9bies0dad9uehx69f8eus0k` (`userCustomer_id`),
  CONSTRAINT `FK1h9bies0dad9uehx69f8eus0k` FOREIGN KEY (`userCustomer_id`) REFERENCES `UserCustomer` (`id`),
  CONSTRAINT `FK66kikp7i5erki6wwqcpa54eqw` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `UserCustomerAssociation` WRITE;
/*!40000 ALTER TABLE `UserCustomerAssociation` DISABLE KEYS */;
/*!40000 ALTER TABLE `UserCustomerAssociation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `UserCustomerCertification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `UserCustomerCertification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` longtext COLLATE utf8mb4_unicode_ci,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `validity` date DEFAULT NULL,
  `certificate_id` bigint DEFAULT NULL,
  `userCustomer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKonaa6lryyxoeyncw3oc00etdl` (`certificate_id`),
  KEY `FKhnrfyo1pt9pcap2kbnqjrway8` (`userCustomer_id`),
  CONSTRAINT `FKhnrfyo1pt9pcap2kbnqjrway8` FOREIGN KEY (`userCustomer_id`) REFERENCES `UserCustomer` (`id`),
  CONSTRAINT `FKonaa6lryyxoeyncw3oc00etdl` FOREIGN KEY (`certificate_id`) REFERENCES `Document` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `UserCustomerCertification` WRITE;
/*!40000 ALTER TABLE `UserCustomerCertification` DISABLE KEYS */;
/*!40000 ALTER TABLE `UserCustomerCertification` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `UserCustomerCooperative`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `UserCustomerCooperative` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role` tinyint DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `userCustomer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKra3lrhwa6cwto03lqdei43i3q` (`company_id`),
  KEY `FKmgbf31ad862648ycv82hn9plb` (`userCustomer_id`),
  CONSTRAINT `FKmgbf31ad862648ycv82hn9plb` FOREIGN KEY (`userCustomer_id`) REFERENCES `UserCustomer` (`id`),
  CONSTRAINT `FKra3lrhwa6cwto03lqdei43i3q` FOREIGN KEY (`company_id`) REFERENCES `Company` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `UserCustomerCooperative` WRITE;
/*!40000 ALTER TABLE `UserCustomerCooperative` DISABLE KEYS */;
/*!40000 ALTER TABLE `UserCustomerCooperative` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `UserCustomerLocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `UserCustomerLocation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_cell` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasDepartment` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasFarm` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasMunicipality` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_hondurasVillage` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_otherAddress` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_sector` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_state` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_village` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_zip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entityVersion` bigint NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `numberOfFarmers` int DEFAULT NULL,
  `pinName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `isPubliclyVisible` bit(1) DEFAULT NULL,
  `address_country_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgqxj6jkxwi3gk0v917cv0ml67` (`address_country_id`),
  CONSTRAINT `FKgqxj6jkxwi3gk0v917cv0ml67` FOREIGN KEY (`address_country_id`) REFERENCES `Country` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `UserCustomerLocation` WRITE;
/*!40000 ALTER TABLE `UserCustomerLocation` DISABLE KEYS */;
/*!40000 ALTER TABLE `UserCustomerLocation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `UserCustomerProductType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `UserCustomerProductType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `productType_id` bigint DEFAULT NULL,
  `userCustomer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKasr1pd8ve7udq5yqy1y1ytvnu` (`productType_id`),
  KEY `FKf2s52ftyav5sko9qnsdm02w5o` (`userCustomer_id`),
  CONSTRAINT `FKasr1pd8ve7udq5yqy1y1ytvnu` FOREIGN KEY (`productType_id`) REFERENCES `ProductType` (`id`),
  CONSTRAINT `FKf2s52ftyav5sko9qnsdm02w5o` FOREIGN KEY (`userCustomer_id`) REFERENCES `UserCustomer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `UserCustomerProductType` WRITE;
/*!40000 ALTER TABLE `UserCustomerProductType` DISABLE KEYS */;
/*!40000 ALTER TABLE `UserCustomerProductType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `User_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `User_AUD` (
  `id` bigint NOT NULL,
  `REV` int NOT NULL,
  `REVTYPE` tinyint DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `language` enum('DE','EN','ES','RW') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('REGIONAL_ADMIN','SYSTEM_ADMIN','USER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACTIVE','CONFIRMED_EMAIL','DEACTIVATED','UNCONFIRMED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `surname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`REV`,`id`),
  CONSTRAINT `FKilft2rdosb65jocpcoan7xnjq` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `User_AUD` WRITE;
/*!40000 ALTER TABLE `User_AUD` DISABLE KEYS */;
/*!40000 ALTER TABLE `User_AUD` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ValueChain`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ValueChain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creationTimestamp` datetime(6) DEFAULT NULL,
  `updateTimestamp` datetime(6) DEFAULT NULL,
  `description` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `entityVersion` bigint NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `valueChainStatus` enum('DISABLED','ENABLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `createdBy_id` bigint NOT NULL,
  `productType_id` bigint NOT NULL,
  `updatedBy_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKikaj76lt1196kmhqat2nbtofy` (`createdBy_id`),
  KEY `FKbmtpdrafucwdq2y7cplshwvov` (`productType_id`),
  KEY `FK1952c58mq1ier9f3ndr71obp4` (`updatedBy_id`),
  CONSTRAINT `FK1952c58mq1ier9f3ndr71obp4` FOREIGN KEY (`updatedBy_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKbmtpdrafucwdq2y7cplshwvov` FOREIGN KEY (`productType_id`) REFERENCES `ProductType` (`id`),
  CONSTRAINT `FKikaj76lt1196kmhqat2nbtofy` FOREIGN KEY (`createdBy_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ValueChain` WRITE;
/*!40000 ALTER TABLE `ValueChain` DISABLE KEYS */;
/*!40000 ALTER TABLE `ValueChain` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ValueChainFacilityType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ValueChainFacilityType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `facilityType_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKp4nh3wfjqp8grrrny232u2jkc` (`valueChain_id`,`facilityType_id`),
  KEY `FK8rw55gunmmjto4yhk6ukghx68` (`facilityType_id`),
  CONSTRAINT `FK8rw55gunmmjto4yhk6ukghx68` FOREIGN KEY (`facilityType_id`) REFERENCES `FacilityType` (`id`),
  CONSTRAINT `FKputjpv9rra9ysqy7d99irpbk7` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ValueChainFacilityType` WRITE;
/*!40000 ALTER TABLE `ValueChainFacilityType` DISABLE KEYS */;
/*!40000 ALTER TABLE `ValueChainFacilityType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ValueChainMeasureUnitType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ValueChainMeasureUnitType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `measureUnitType_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbkw7lhamgim4k6ef78x7nh50q` (`valueChain_id`,`measureUnitType_id`),
  KEY `FKarstmfdm297s71hby08y03x9r` (`measureUnitType_id`),
  CONSTRAINT `FKagwkk2ipq0u8mo96n2ghopby` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`),
  CONSTRAINT `FKarstmfdm297s71hby08y03x9r` FOREIGN KEY (`measureUnitType_id`) REFERENCES `MeasureUnitType` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ValueChainMeasureUnitType` WRITE;
/*!40000 ALTER TABLE `ValueChainMeasureUnitType` DISABLE KEYS */;
/*!40000 ALTER TABLE `ValueChainMeasureUnitType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ValueChainProcEvidenceType`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ValueChainProcEvidenceType` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `processingEvidenceType_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6odw4g6gvmlt1xvbjqyf0bh51` (`valueChain_id`,`processingEvidenceType_id`),
  KEY `FKg2tq35skluy6funye0gvskn1q` (`processingEvidenceType_id`),
  CONSTRAINT `FKg2tq35skluy6funye0gvskn1q` FOREIGN KEY (`processingEvidenceType_id`) REFERENCES `ProcessingEvidenceType` (`id`),
  CONSTRAINT `FKm8ahpk3a1gonwspwf8khsnpd7` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ValueChainProcEvidenceType` WRITE;
/*!40000 ALTER TABLE `ValueChainProcEvidenceType` DISABLE KEYS */;
/*!40000 ALTER TABLE `ValueChainProcEvidenceType` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ValueChainProcessingEvidenceField`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ValueChainProcessingEvidenceField` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `processingEvidenceField_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6o5a8306ppost0mnskon3x9ox` (`processingEvidenceField_id`),
  KEY `FKa0ak0a42jvjf7jphuo1912i6s` (`valueChain_id`),
  CONSTRAINT `FK6o5a8306ppost0mnskon3x9ox` FOREIGN KEY (`processingEvidenceField_id`) REFERENCES `ProcessingEvidenceField` (`id`),
  CONSTRAINT `FKa0ak0a42jvjf7jphuo1912i6s` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ValueChainProcessingEvidenceField` WRITE;
/*!40000 ALTER TABLE `ValueChainProcessingEvidenceField` DISABLE KEYS */;
/*!40000 ALTER TABLE `ValueChainProcessingEvidenceField` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `ValueChainSemiProduct`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ValueChainSemiProduct` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `semiProduct_id` bigint NOT NULL,
  `valueChain_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlsuycp13s1d417cikemfdp0b7` (`valueChain_id`,`semiProduct_id`),
  KEY `FK509vur6rrf79cbr7tosmurh4l` (`semiProduct_id`),
  CONSTRAINT `FK509vur6rrf79cbr7tosmurh4l` FOREIGN KEY (`semiProduct_id`) REFERENCES `SemiProduct` (`id`),
  CONSTRAINT `FKqrjd1cghp33g1ndq85x1qi3a0` FOREIGN KEY (`valueChain_id`) REFERENCES `ValueChain` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ValueChainSemiProduct` WRITE;
/*!40000 ALTER TABLE `ValueChainSemiProduct` DISABLE KEYS */;
/*!40000 ALTER TABLE `ValueChainSemiProduct` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `schema_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_version` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `schema_version_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `schema_version` WRITE;
/*!40000 ALTER TABLE `schema_version` DISABLE KEYS */;
INSERT INTO `schema_version` VALUES (1,'1','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',NULL,'upgrade','2026-10-21 21:23:09',0,1),(2,'2020.03.27.15.00','Prefill Countries','CUSTOM','com.abelium.inatrace.db.migrations.V2020_03_27_15_00__Prefill_Countries',NULL,'upgrade','2026-10-21 21:23:09',175,1),(3,'2020.10.06.19.30','Update Label Content','CUSTOM','com.abelium.inatrace.db.migrations.V2020_10_06_19_30__Update_Label_Content',NULL,'upgrade','2026-10-21 21:23:09',84,1),(4,'2020.11.26.12.30','Update Company User Role','CUSTOM','com.abelium.inatrace.db.migrations.V2020_11_26_12_30__Update_Company_User_Role',NULL,'upgrade','2026-10-21 21:23:09',4,1),(5,'2021.08.11.11.33','Prefill FacilityTypes','CUSTOM','com.abelium.inatrace.db.migrations.V2021_08_11_11_33__Prefill_FacilityTypes',NULL,'upgrade','2026-10-21 21:23:09',6,1),(6,'2021.08.11.11.41','Prefill MeasureUnitTypes','CUSTOM','com.abelium.inatrace.db.migrations.V2021_08_11_11_41__Prefill_MeasureUnitTypes',NULL,'upgrade','2026-10-21 21:23:09',8,1),(7,'2021.10.12.10.01','Update Product Company','CUSTOM','com.abelium.inatrace.db.migrations.V2021_10_12_10_01__Update_Product_Company',NULL,'upgrade','2026-10-21 21:23:09',12,1),(8,'2021.10.21.14.33','Update Facility Translations','CUSTOM','com.abelium.inatrace.db.migrations.V2021_10_21_14_33__Update_Facility_Translations',NULL,'upgrade','2026-10-21 21:23:09',8,1),(9,'2021.10.24.17.22','Update Semi Product Translations','CUSTOM','com.abelium.inatrace.db.migrations.V2021_10_24_17_22__Update_Semi_Product_Translations',NULL,'upgrade','2026-10-21 21:23:09',5,1),(10,'2021.11.02.10.07','Update Processing Evidence Field Translations','CUSTOM','com.abelium.inatrace.db.migrations.V2021_11_02_10_07__Update_Processing_Evidence_Field_Translations',NULL,'upgrade','2026-10-21 21:23:09',6,1),(11,'2021.11.02.13.31','Update Processing Evidence Type Translations','CUSTOM','com.abelium.inatrace.db.migrations.V2021_11_02_13_31__Update_Processing_Evidence_Type_Translations',NULL,'upgrade','2026-10-21 21:23:09',5,1),(12,'2022.05.03.15.54','Update Business To Customer Settings','CUSTOM','com.abelium.inatrace.db.migrations.V2022_05_03_15_54__Update_Business_To_Customer_Settings',NULL,'upgrade','2026-10-21 21:23:09',3,1),(13,'2022.05.05.09.15','Update Business To Customer Settings Defaults','CUSTOM','com.abelium.inatrace.db.migrations.V2022_05_05_09_15__Update_Business_To_Customer_Settings_Defaults',NULL,'upgrade','2026-10-21 21:23:09',5,1),(14,'2022.05.17.08.51','Update Business To Customer Settings Graphics Defaults','CUSTOM','com.abelium.inatrace.db.migrations.V2022_05_17_08_51__Update_Business_To_Customer_Settings_Graphics_Defaults',NULL,'upgrade','2026-10-21 21:23:09',2,1),(15,'2022.08.30.11.00','Update Business To Customer Settings Add Prod Title Color','CUSTOM','com.abelium.inatrace.db.migrations.V2022_08_30_11_00__Update_Business_To_Customer_Settings_Add_Prod_Title_Color',NULL,'upgrade','2026-10-21 21:23:09',2,1),(16,'2023.03.09.14.33','Update Value Chains Add Coffee Product Type','CUSTOM','com.abelium.inatrace.db.migrations.V2023_03_09_14_33__Update_Value_Chains_Add_Coffee_Product_Type',NULL,'upgrade','2026-10-21 21:23:09',16,1),(17,'2023.04.12.14.00','Update Product Type Translations','CUSTOM','com.abelium.inatrace.db.migrations.V2023_04_12_14_00__Update_Product_Type_Translations',NULL,'upgrade','2026-10-21 21:23:09',25,1),(18,'2023.04.17.11.00','Update Product Type FieldName','CUSTOM','com.abelium.inatrace.db.migrations.V2023_04_17_11_00__Update_Product_Type_FieldName',NULL,'upgrade','2026-10-21 21:23:09',12,1),(19,'2023.05.16.10.34','Migrate repackedOutput to ProcActionOSM','CUSTOM','com.abelium.inatrace.db.migrations.V2023_05_16_10_34__Migrate_repackedOutput_to_ProcActionOSM',NULL,'upgrade','2026-10-21 21:23:09',4,1),(20,'2023.05.24.12.11','Rename  Role  ADMIN  into  SYSTEM ADMIN','CUSTOM','com.abelium.inatrace.db.migrations.V2023_05_24_12_11__Rename__Role__ADMIN__into__SYSTEM_ADMIN',NULL,'upgrade','2026-10-21 21:23:09',13,1),(21,'2023.05.24.17.09','Rename  CompanyUserRole  ADMIN  into  COMPANY ADMIN','CUSTOM','com.abelium.inatrace.db.migrations.V2023_05_24_17_09__Rename__CompanyUserRole__ADMIN__into__COMPANY_ADMIN',NULL,'upgrade','2026-10-21 21:23:09',3,1),(22,'2023.05.24.17.26','Rename  CompanyUserRole  USER  into  COMPANY USER','CUSTOM','com.abelium.inatrace.db.migrations.V2023_05_24_17_26__Rename__CompanyUserRole__USER__into__COMPANY_USER',NULL,'upgrade','2026-10-21 21:23:09',3,1),(23,'2023.06.14.10.04','Drop  column  ingredients  in  Product','SQL','V2023_06_14_10_04__Drop__column__ingredients__in__Product.sql',-74343480,'upgrade','2026-10-21 21:23:09',3,1),(24,'2023.06.14.10.14','Drop  column  ingredients  in  ProductLabelContent','SQL','V2023_06_14_10_14__Drop__column__ingredients__in__ProductLabelContent.sql',817597948,'upgrade','2026-10-21 21:23:09',2,1),(25,'2023.06.14.10.37','Delete  ingredients  in  ProductLabel fields','SQL','V2023_06_14_10_37__Delete__ingredients__in__ProductLabel_fields.sql',1719231191,'upgrade','2026-10-21 21:23:09',1,1),(26,'2023.06.14.11.54','Drop  column  nutritionalValue  in  Product  and  ProductLabelContent','SQL','V2023_06_14_11_54__Drop__column__nutritionalValue__in__Product__and__ProductLabelContent.sql',-1313619622,'upgrade','2026-10-21 21:23:09',3,1),(27,'2023.06.14.11.59','Delete  nutritionalValue  in  ProductLabel fields','SQL','V2023_06_14_11_59__Delete__nutritionalValue__in__ProductLabel_fields.sql',1966070497,'upgrade','2026-10-21 21:23:09',1,1),(28,'2023.06.14.12.07','Drop  column  howToUse  in  Product  and  ProductLabelContent','SQL','V2023_06_14_12_07__Drop__column__howToUse__in__Product__and__ProductLabelContent.sql',-1368618867,'upgrade','2026-10-21 21:23:09',3,1),(29,'2023.06.14.12.09','Delete  howToUse  in  ProductLabel fields','SQL','V2023_06_14_12_09__Delete__howToUse__in__ProductLabel_fields.sql',-1570785162,'upgrade','2026-10-21 21:23:10',0,1),(30,'2023.06.14.12.39','Drop  tables  Product keyMarketsShare  and  ProductLabelContent keyMarketsShare','SQL','V2023_06_14_12_39__Drop__tables__Product_keyMarketsShare__and__ProductLabelContent_keyMarketsShare.sql',-1263633717,'upgrade','2026-10-21 21:23:10',6,1),(31,'2023.06.14.12.41','Delete  keyMarketsShare  in  ProductLabel fields','SQL','V2023_06_14_12_41__Delete__keyMarketsShare__in__ProductLabel_fields.sql',-1104953907,'upgrade','2026-10-21 21:23:10',1,1),(32,'2023.06.14.13.13','Drop  columns  specialityDoc and specialityDesc  in  Product  and  ProductLabelContent','SQL','V2023_06_14_13_13__Drop__columns__specialityDoc_and_specialityDesc__in__Product__and__ProductLabelContent.sql',913740504,'upgrade','2026-10-21 21:23:10',8,1),(33,'2023.06.14.13.36','Delete  specialityDesc  and  specialityDoc  in  ProductLabel fields','SQL','V2023_06_14_13_36__Delete__specialityDesc__and__specialityDoc__in__ProductLabel_fields.sql',1109870293,'upgrade','2026-10-21 21:23:10',1,1),(34,'2023.06.14.15.21','Drop  tables  ProcessStandard  and  ProcessDocument','SQL','V2023_06_14_15_21__Drop__tables__ProcessStandard__and__ProcessDocument.sql',-52430660,'upgrade','2026-10-21 21:23:10',3,1),(35,'2023.06.14.15.23','Drop  columns  storage  and  codesOfConduct  in  Process','SQL','V2023_06_14_15_23__Drop__columns__storage__and__codesOfConduct__in__Process.sql',-1106702627,'upgrade','2026-10-21 21:23:10',2,1),(36,'2023.06.14.15.25','Delete  process  fields  in  ProductLabel fields','SQL','V2023_06_14_15_25__Delete__process__fields__in__ProductLabel_fields.sql',-801473405,'upgrade','2026-10-21 21:23:10',1,1),(37,'2023.06.15.07.40','Drop  columns  in  Responsability','SQL','V2023_06_15_07_40__Drop__columns__in__Responsability.sql',1094545354,'upgrade','2026-10-21 21:23:10',4,1),(38,'2023.06.15.07.48','Drop  table  ResponsibilityFarmerPicture','SQL','V2023_06_15_07_48__Drop__table__ResponsibilityFarmerPicture.sql',-1732656853,'upgrade','2026-10-21 21:23:10',3,1),(39,'2023.06.15.07.51','Delete  responsibility  fields  in  ProductLabel fields','SQL','V2023_06_15_07_51__Delete__responsibility__fields__in__ProductLabel_fields.sql',-842783622,'upgrade','2026-10-21 21:23:10',0,1),(40,'2023.06.15.09.50','Drop  column  knowledgeBlog  in  Product  and  ProductLabelContent','SQL','V2023_06_15_09_50__Drop__column__knowledgeBlog__in__Product__and__ProductLabelContent.sql',1246143562,'upgrade','2026-10-21 21:23:10',1,1),(41,'2023.06.15.10.02','Drop  columns  in  ProductSettings','SQL','V2023_06_15_10_02__Drop__columns__in__ProductSettings.sql',881802585,'upgrade','2026-10-21 21:23:10',1,1),(42,'2023.06.15.10.04','Delete  settings  fields  in  ProductLabel fields','SQL','V2023_06_15_10_04__Delete__settings__fields__in__ProductLabel_fields.sql',-1897187337,'upgrade','2026-10-21 21:23:10',0,1);
/*!40000 ALTER TABLE `schema_version` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
