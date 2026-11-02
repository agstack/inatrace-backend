-- Synthetic graph and legacy indexes for the pinned fb4fecf schema.
-- Apply only to a disposable database after the historical source bootstrap.
SET FOREIGN_KEY_CHECKS=0;

INSERT INTO `User` VALUES (1001,'2026-10-21 21:24:37.164246','2026-10-21 21:24:37.164246','upgrade-fixture-user@inatrace.test',0,'EN','Upgrade','synthetic-only','USER','ACTIVE','Fixture');
INSERT INTO `Company` VALUES (2001,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'upgrade-fixture-company',NULL,NULL,'ACTIVE',NULL,NULL,NULL,NULL);
INSERT INTO `CompanyUser` VALUES (3001,0,'COMPANY_ADMIN',2001,1001);
INSERT INTO `FacilityLocation` VALUES (4001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,1.25,2.5,NULL,'upgrade-fixture-location',NULL,NULL);
INSERT INTO `Facility` VALUES (5001,'2026-10-21 21:24:37.186850','2026-10-21 21:24:37.186850',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,'upgrade-fixture-facility',2001,4001,4);
INSERT INTO `SemiProduct` VALUES (6001,'2026-10-21 21:24:37.190562','2026-10-21 21:24:37.190562','synthetic upgrade semi product',X'01',_binary '\0',_binary '\0','upgrade-fixture-semi-product',2);
INSERT INTO `ProcessingAction` VALUES (7001,'2026-10-21 21:24:37.196000','2026-10-21 21:24:37.196000',0,0.83,_binary '\0',NULL,'UPG',NULL,NULL,NULL,NULL,NULL,'PROCESSING',2001,NULL,6001,NULL,NULL);
INSERT INTO `ProcessingOrder` VALUES (8001,'2026-10-21 21:24:37.198937','2026-10-21 21:24:37.198937',0,1001,'2026-09-18',7001);
INSERT INTO `StockOrder` VALUES (9001,'2026-10-21 21:24:37.201098','2026-10-21 21:24:37.201098',70.00,70.00,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,30.00,'upgrade-fixture-source-stock',NULL,X'01',_binary '\0',X'01',NULL,NULL,'PURCHASE_ORDER',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,100.00,NULL,2001,NULL,1001,5001,NULL,2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,6001,NULL),(9002,'2026-10-21 21:24:37.203880','2026-10-21 21:24:37.203880',25.00,25.00,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0.00,'upgrade-fixture-output-stock',NULL,X'01',_binary '\0',_binary '\0',NULL,NULL,'PROCESSING_ORDER',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,25.00,NULL,2001,NULL,1001,5001,NULL,2,8001,NULL,NULL,NULL,NULL,NULL,NULL,NULL,6001,NULL);
INSERT INTO `Transaction` VALUES (10001,'2026-10-21 21:24:37.207484','2026-10-21 21:24:37.207484',NULL,0,1001,30.00,X'01',25.00,NULL,NULL,NULL,'EXECUTED',2001,NULL,2,6001,5001,9001,8001);
INSERT INTO `Document` VALUES (11001,'2026-10-21 21:24:37.212824','text/plain','upgrade-fixture-document.txt',12,'upgrade-fixture-document','GENERAL');
INSERT INTO `AuthenticationToken` VALUES (12001,0,'2030-01-01 00:00:00.000000','ACTIVE','upgrade-fixture-token',1001);

ALTER TABLE `BulkPaymentActivityProof` ADD UNIQUE KEY `UK_LEGACY_BulkPaymentActivityProof_activityProof_id` (`activityProof_id`);
ALTER TABLE `BusinessToCustomerSettings` ADD UNIQUE KEY `UK_LEGACY_BTCSettings_productFont_id` (`productFont_id`);
ALTER TABLE `BusinessToCustomerSettings` ADD UNIQUE KEY `UK_LEGACY_BTCSettings_textFont_id` (`textFont_id`);
ALTER TABLE `BusinessToCustomerSettings` ADD UNIQUE KEY `UK_LEGACY_BTCSettings_landingPageImage_id` (`landingPageImage_id`);
ALTER TABLE `BusinessToCustomerSettings` ADD UNIQUE KEY `UK_LEGACY_BTCSettings_landingPageBackgroundImage_id` (`landingPageBackgroundImage_id`);
ALTER TABLE `BusinessToCustomerSettings` ADD UNIQUE KEY `UK_LEGACY_BTCSettings_headerBackgroundImage_id` (`headerBackgroundImage_id`);
ALTER TABLE `Certification` ADD UNIQUE KEY `UK_LEGACY_Certification_certificate_id` (`certificate_id`);
ALTER TABLE `Company` ADD UNIQUE KEY `UK_LEGACY_Company_logo_id` (`logo_id`);
ALTER TABLE `Facility` ADD UNIQUE KEY `UK_LEGACY_Facility_facilityLocation_id` (`facilityLocation_id`);
ALTER TABLE `Payment` ADD UNIQUE KEY `UK_LEGACY_Payment_receiptDocument_id` (`receiptDocument_id`);
ALTER TABLE `Product` ADD UNIQUE KEY `UK_LEGACY_Product_process_id` (`process_id`);
ALTER TABLE `Product` ADD UNIQUE KEY `UK_LEGACY_Product_responsibility_id` (`responsibility_id`);
ALTER TABLE `Product` ADD UNIQUE KEY `UK_LEGACY_Product_sustainability_id` (`sustainability_id`);
ALTER TABLE `Product` ADD UNIQUE KEY `UK_LEGACY_Product_journey_id` (`journey_id`);
ALTER TABLE `Product` ADD UNIQUE KEY `UK_LEGACY_Product_settings_id` (`settings_id`);
ALTER TABLE `Product` ADD UNIQUE KEY `UK_LEGACY_Product_businessToCustomerSettings_id` (`businessToCustomerSettings_id`);
ALTER TABLE `ProductLabelContent` ADD UNIQUE KEY `UK_LEGACY_ProductLabelContent_process_id` (`process_id`);
ALTER TABLE `ProductLabelContent` ADD UNIQUE KEY `UK_LEGACY_ProductLabelContent_responsibility_id` (`responsibility_id`);
ALTER TABLE `ProductLabelContent` ADD UNIQUE KEY `UK_LEGACY_ProductLabelContent_sustainability_id` (`sustainability_id`);
ALTER TABLE `ProductLabelContent` ADD UNIQUE KEY `UK_LEGACY_ProductLabelContent_journey_id` (`journey_id`);
ALTER TABLE `ProductLabelContent` ADD UNIQUE KEY `UK_LEGACY_ProductLabelContent_settings_id` (`settings_id`);
ALTER TABLE `ProductLabelContent` ADD UNIQUE KEY `UK_LEGACY_ProductLabelContent_businessToCustomerSettings_id` (`businessToCustomerSettings_id`);
ALTER TABLE `StockOrder` ADD UNIQUE KEY `UK_LEGACY_StockOrder_productionLocation_id` (`productionLocation_id`);
ALTER TABLE `StockOrder` ADD UNIQUE KEY `UK_LEGACY_StockOrder_consumerCompanyCustomer_id` (`consumerCompanyCustomer_id`);
ALTER TABLE `StockOrderActivityProof` ADD UNIQUE KEY `UK_LEGACY_StockOrderActivityProof_activityProof_id` (`activityProof_id`);
ALTER TABLE `Transaction` ADD UNIQUE KEY `UK_LEGACY_Transaction_sourceStockOrder_id` (`sourceStockOrder_id`);
ALTER TABLE `Transaction` ADD UNIQUE KEY `UK_LEGACY_Transaction_inputMeasureUnitType_id` (`inputMeasureUnitType_id`);
ALTER TABLE `UserCustomer` ADD UNIQUE KEY `UK_LEGACY_UserCustomer_userCustomerLocation_id` (`userCustomerLocation_id`);

SET FOREIGN_KEY_CHECKS=1;
