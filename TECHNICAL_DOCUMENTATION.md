# INATrace Technical Documentation

## Introduction

INATrace is a comprehensive supply chain traceability platform designed to provide transparency and accountability
across agricultural value chains. This technical documentation provides detailed information about the system's
architecture, deployment options, configuration, and external integrations to assist developers, system administrators,
and technical stakeholders in understanding, deploying, and maintaining the platform.

## 1. High-Level Architecture Overview

### 1.1 System Components

The INATrace platform follows a modern microservices-based architecture consisting of the following core components:

- **Frontend Application**: Angular-based single-page application providing the user interface
- **Mobile Application**: React Native cross-platform mobile application for iOS and Android
- **Backend API**: Spring Boot REST API handling business logic and data management
- **Database Layer**: MySQL relational database for persistent data storage
- **File Storage**: Support for local and cloud-based file storage solutions
- **Authentication Service**: JWT-based authentication and authorization mechanism

### 1.2 Technology Stack

- **Frontend**: Angular, TypeScript, HTML5, CSS3
- **Mobile**: React Native, JavaScript/TypeScript
- **Backend**: Java, Spring Boot, Spring Security, Spring Data JPA
- **Database**: MySQL
- **Build Tools**: Maven (backend), npm/Angular CLI (frontend), npm/React Native CLI (mobile)
- **Container Support**: Docker, Docker Compose

The diagram below illustrates the high-level architecture of the INATrace platform, showing the interaction between the
main system components. The frontend and mobile applications communicate with the backend API through RESTful services,
while the backend manages data persistence through the MySQL database layer and handles file storage operations. The
authentication service ensures secure access control across all components using JWT tokens.

![INATrace High-Level architecture diagram](docs/images/INATrace_high-level_architecture.svg)

## 2. Deployment Topology

### 2.1 Kubernetes Deployment

The INATrace platform is deployed on Kubernetes (K8s) infrastructure, providing scalability, high availability, and
efficient resource management. The deployment architecture consists of three separate environments:

- **Test Environment**: Used for testing new features, bug fixes, and integration testing before promoting to production
- **Production Environment**: The live environment serving end users with production-grade data and configurations
- **Demo Environment**: A demonstration instance used for showcasing platform capabilities to potential clients and
  stakeholders

### 2.2 Ingress Configuration

Access to both frontend and backend services across all environments is managed through Nginx Ingress Controller. The
Nginx ingress handles:

- **Traffic Routing**: Directing incoming requests to appropriate frontend or backend services
- **SSL/TLS Termination**: Managing HTTPS certificates and secure connections
- **Load Balancing**: Distributing traffic across multiple service replicas
- **Path-Based Routing**: Routing requests based on URL paths to frontend or API endpoints

The diagram below shows the INATrace deployment topology on Kubernetes infrastructure. The platform utilizes a single
MySQL instance that hosts separate databases for each environment (Test, Production, and Demo), ensuring logical data
isolation while optimizing resource usage. File storage is implemented using NFS (Network File System) mounts, providing
shared and persistent storage across all environment pods. All environments are accessed through a unified Nginx Ingress
Controller that manages SSL/TLS termination, load balancing, and routing to the appropriate services.

![INATrace Deployment topology](docs/images/INATrace_Deployment_topology.svg)

## 3. Configuration Parameters

The INATrace platform requires proper configuration of both backend and frontend components to function correctly.
Configuration parameters control various aspects of the system, including database connections, external service
integrations, security settings, and application behavior. This section outlines the key configuration parameters for
each component.

### 3.1 Backend Configuration
- **INATRACE_ENV**: Specifies the environment in which the application is running (TEST, PROD, or DEMO)
- **INATRACE_DATABASE_NAME**: Specifies the name of the MySQL database to use
- **INATRACE_DATABASE_HOSTNAME**: Specifies the hostname or IP address of the MySQL database server
- **SPRING_DATASOURCE_USERNAME**: Specifies the username to use for database access
- **SPRING_DATASOURCE_PASSWORD**: Specifies the password to use for database access
- **INATRACE_FILESTORAGE_ROOT**: Specifies the root directory for file storage
- **INATRACE_IMPORT_PATH**: Specifies the path to the directory containing CSV files to import
- **INATRACE_DOCUMENTS_ROOT**: Specifies the root directory for uploaded documents
- **SPRING_MAIL_PROTOCOL**: Specifies the protocol to use for sending email notifications
- **SPRING_MAIL_HOST**: Specifies the hostname or IP address of the SMTP server
- **SPRING_MAIL_PORT**: Specifies the port number of the SMTP server
- **SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH**: Specifies whether to use authentication when connecting to the SMTP server
- **SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLED**: Specifies whether to enable TLS encryption when connecting to the SMTP server
- **SPRING_MAIL_USERNAME**: Specifies the username to use for SMTP authentication
- **SPRING_MAIL_PASSWORD**: Specifies the password to use for SMTP authentication
- **INATRACE_MAIL_TEMPLATE_FROM**: Specifies the sender address for email notifications
- **INATRACE_MAIL_SENDINGENABLED**: Specifies whether email notifications are enabled
- **INATRACE_EMAILCONFIRMATION_URL**: Specifies the URL to use for email confirmation links
- **INATRACE_PASSWORDRESET_URL**: Specifies the URL to use for password reset links
- **INATRACE_EXCHANGERATE_APIKEY**: Specifies the API key for the Exchange Rates API
- **INATRACE_AUTH_JWTSIGNINGKEY**: Specifies the signing key for JWT tokens (random, at least 32 bytes; required)
- **INATRACE_REQUESTLOG_TOKEN**: Specifies the token to use for public request logging
- **BEYCO_OAUTH2_CLIENTID**: Specifies the OAuth2 client ID for Beyco API authentication
- **BEYCO_OAUTH2_CLIENTSECRET**: Specifies the OAuth2 client secret for Beyco API authentication
- **BEYCO_OAUTH2_URL**: Specifies the URL for Beyco API authentication
- **BEYCO_INTEGRATION_URL**: Specifies the URL for Beyco API integration
- **INATRACE_AUTH_ACCESSTOKENEXPIRATIONSEC**: Specifies the access token expiration time in seconds
- **INATRACE_AGSTACK_BASEURL**: Specifies the base URL for the AgStack API
- **INATRACE_AGSTACK_LOGINBASEURL**: Specifies the base URL for the AgStack login API
- **INATRACE_AGSTACK_EMAIL**: Specifies the email address to use for AgStack authentication
- **INATRACE_AGSTACK_PASSWORD**: Specifies the password to use for AgStack authentication
- **INATRACE_GEOID_BASEURL**: Specifies the base URL of the FAO GeoID registry used to resolve the GeoID column of a farmer import, e.g. `https://data.apps.fao.org/geoid`; leave empty to skip the lookup
- **INATRACE_GEOID_TIMEOUTSECONDS**: Specifies how long a GeoID lookup may take before the import falls back to the row's own coordinates (default 15)

For farmer imports, a GeoID that cannot be resolved requires a valid geometry in the Geo Data column; otherwise the GeoID cell is reported as invalid. Fallback geometry is not assigned the unverified GeoID. Kobo geometry columns with recognisable names (for example, `GPS`, `geoshape`, `polygon` or `polygone`) are detected automatically; numeric answers alone are not treated as coordinates. When a populated repeat sheet has no recognisable geometry column or has multiple candidates, the response lists its headers and the user must choose the geometry column or explicitly skip that sheet. Nothing is imported until all such choices have been made. The import and both duplicate-confirmation endpoints accept the optional repeated `koboSelections` query parameter, in the format `sheetIndex:columnIndex` (zero-based), or `sheetIndex:-1` to skip a sheet. Confirmations must supply the same selections so the backend can re-read the original document.

Potential duplicate farmers require an explicit choice. The confirmation endpoints reload the original uploaded document and require the same document ownership and company authorization as the import. Repeated plot confirmations skip a plot only when its coordinates, crop, name, size, unit and plant count match exactly (and the GeoID matches when one is supplied). After parsing the spreadsheet and resolving GeoIDs, a fresh transaction serializes confirmations within the same company and rechecks access. Existing plot registration calls may still occur while the lock is held. Creating a separate farmer records an idempotency decision in `FarmerImportDecision`: repeating the same decision for the same company, document and farmer does not create another farmer. To deliberately create another separate farmer, upload a new document. The decision table stores IDs and a hash of identifying fields rather than the fields themselves; decisions are deleted when their associated document or company is deleted.

### 3.2 Frontend Configuration
- **ENVIRONMENT_NAME**: Specifies the name of the environment in which the application is running (TEST, PROD, or DEMO)
- **APP_BASE_URL**: Specifies the base URL for the frontend application
- **QR_CODE_BASE_PATH**: Specifies the base path for product public pages
- **RELATIVE_FILE_UPLOAD_URL**: Specifies the relative path for file uploads
- **RELATIVE_FILE_UPLOAD_URL_MANUAL_TYPE**: Specifies the relative path for manual file uploads
- **RELATIVE_IMAGE_UPLOAD_URL**: Specifies the relative path for image uploads
- **RELATIVE_IMAGE_UPLOAD_URL_ALL_SIZES**: Specifies the relative path for all-size image uploads
- **BEYCO_AUTH_URL**: Specifies the URL for Beyco API authentication
- **BEYCO_CLIENT_ID**: Specifies the OAuth2 client ID for Beyco API authentication
- **GOOGLE_MAPS_API_KEY**: Specifies the Google Maps API key
- **TOKEN_FOR_PUBLIC_LOG_ROUTE**: Specifies the token to use for public request logging
- **MAPBOX_ACCESS_TOKEN**: Specifies the Mapbox access token
