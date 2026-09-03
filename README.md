# INATrace

Open-source blockchain-based track and trace system for an agricultural commodities (such as coffee) supply
chain run. It provides transparency and creation of trust through
digitalization of supply chains, connects every actor along the supply chain, assures quality and fair pricing.

Project is composed of 4 parts:

* [Frontend application](https://github.com/INATrace/fe/tree/main)
* [Mobile app](https://github.com/INATrace/mobile/tree/main)
* [Java backend](https://github.com/INATrace/backend/tree/main)
* [Coffee network](https://github.com/INATrace/coffee-network/tree/main)

# INATrace 2
This new major release includes new functionalities, refactorings, optimizations and bugfixes. The most important additions and changes are:
* Added support for generic value chains. Different value chains with their specific settings can now be configured in the system.
* Multi-tenant system support.
* Reorganized the content in the Product section. This section now includes only the content that is related to a product.
* Introduced a new section "Company". This section includes all the content that is related to the company's work process within the value chain.
* The configuration of farmers and collectors is decoupled from the product, and it's part of the Company section.
* Added support for importing farmers from a provided Excel file.
* The company customers are now decoupled from the Stakeholders in the product section and are configured as part of the Company section.
* The configuration of facilities and processing actions is now part of the company's profile.
* The semi-products configuration is now part of the system settings instead of the product section.
* Reorganized the content of the Value chain tab inside the Stakeholders section. The Value chain now includes new company roles. Added is also a section for product admin companies.
* Added support for defining Processing evidence fields in the system settings.
* Translation for facilities, processing actions, semi-products and processing evidence types and fields can be provided in the system as part of its definition.
* Added support for currencies in the system. The enabled currencies can be selected in the system settings. These currencies then appear as select options in various parts of the system where the user is expected to select a currency.
* Added exchange rates for the enabled currencies that are synced daily. The currencies data is provided by the https://exchangeratesapi.io/ API.
* The product section now includes Final products. Final products represent the output of final processing. The final products can be configured by the product admin company.
* When placing a customer order, now we select a final product instead of a sellable semi-product.
* Added support for new types of processing actions.
* Added support for bulk purchases for semi-products.
* Various changes and addition of new functionalities for purchases, processing and payments.

# Technical Documentation
For detailed technical documentation, please refer to [TECHNICAL_DOCUMENTATION.md](TECHNICAL_DOCUMENTATION.md).

# Backend

## Installing / Getting started

### Requirements
* Java `17` or higher
* Maven `3.8.5`
* MySQL `8.4.6`

### Optional
* Docker
* Mailhog

### How to run
1. Clone the repository

2. Import as maven project to your preferred IDE

3. Prepare environment
   1. [Create database](#create-database)
   2. (*OPTIONAL*) [Email testing](#email-testing)
   3. [Spring configuration](#spring-configuration)

4. Run `INATraceBackendApplication.java`

#### Create database

Spin up a container:

```
docker run --name inatrace-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=inatrace -e MYSQL_USER=inatrace -e MYSQL_PASSWORD=inatrace -p 3306:3306 -d mysql:8.4.6
```

Tables will be created and prefilled with starter data on application startup.

#### Email testing

MailHog is an email testing tool for developers. It runs a SMTP server on port `1025` which intercepts messages and displays them in a GUI.

Spin up a container:

```
docker run --name inatrace-mailhog -p 1025:1025 -p 8025:8025 -d mailhog/mailhog:v1.0.1
```

The GUI is available at [`localhost:8025`](http://localhost:8025).

#### Spring configuration

Spring uses `application.properties` file stored in `src/main/resources` for configuration. A template is provided, see instructions below.

- Make a copy of `application.properties.template` and save it as `application.properties`
- Fill the missing values

*NOTE*: The values defined below are applicable for a local development environment. For other environments, change the values accordingly.

###### Datasource

- `INATrace.database.name`: `inatrace`
- `spring.datasource.username`: `inatrace`
- `spring.datasource.password`: `inatrace`

###### SMTP

- `spring.mail.protocol`: `smtp`
- `spring.mail.host`: `localhost`
- `spring.mail.port`: `1025`
- `spring.mail.username`
- `spring.mail.password`
- `spring.mail.properties.mail.smtp.ssl.checkserveridentity`: `false`
- `spring.mail.properties.mail.smtps.auth`: `false`

###### Email template

- `INAtrace.mail.template.from`: `info@inatrace.com`
- `INAtrace.mail.redirect`
- `INAtrace.mail.sendingEnabled`: `true` (`false` by default) 
- `INATrace.loginManager.mail`: `registrations@inatrace.com` (Email address to receive notifications for new registrations)
- `INAtrace.info.mail`: `info@inatrace.com` (Contact email)

###### Security

- `INATrace.auth.jwtSigningKey`: `sign` (Key for signing JWT tokens)
- `INATrace.requestLog.token`: `token` (Key for authorizing log requests)

###### Storage

- `INATrace.fileStorage.root`: Path on local filesystem for saving images, documents, etc. (e.g. `C:\\Users\\Name\\inatrace-backend` or `/home/name/inatrace-backend`) 

###### Exchange rates API

- `INAtrace.exchangerate.apiKey`: API key for exchange rate service. Create a free account at [https://exchangeratesapi.io](https://exchangeratesapi.io/) to get an API key.

###### Beyco integration
INATrace supports integration with the Beyco platform. This allows users to create Beyco offers automatically from INATrace stock orders. For more info about Beyco, please go to: `https://beyco.nl`. This integration is optional. Integration properties are following:
- `beyco.oauth2.clientId`: `clientId`
- `beyco.oauth2.clientSecret`: `clientSecret`
- `beyco.oauth2.url`: `url`

The values of these properties are provided by Beyco. If integration with Byeco is not needed, the values of these properties should be empty.

## APIs

### OpenAPI

Resources are annotated with Swagger annotations. After the application is started, the Swagger service definition JSON is served at `http://localhost:8080/v3/api-docs`, and Swagger UI is served at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

Both live at the root of the server rather than under `/api`. Every endpoint the application itself defines is published under `/api` — `PrefixedApiRequestHandler` adds that prefix to the mappings in the `com.abelium` packages — but springdoc's own endpoints are not touched by it. `http://localhost:8080/api/swagger-ui.html` does not exist.

#### Postman

Using Postman, you can create a collection from the Swagger definition.

- Select `Import > Link`
- Enter [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- Click `Continue`
- Review configuration
- Click `Import`
- Change host variable
  - Click on newly created collection
  - Click `Variables`
  - Edit row `baseUrl` to contain `localhost:8080` as current value

All requests are populated with sample requests, but the content is random. Fill in data to accommodate your test case.

### Authentication

Clients authenticate against `POST /api/user/login` endpoint and receive a JWT token in the `Set-Cookie` response header. The token is valid for 1 hour. See section [Common requests](#common-requests) below for a sample login request.

| Response header | Value                                                                                            |
|-----------------|--------------------------------------------------------------------------------------------------|
| Set-Cookie      | inatrace-accessToken=`JWT`; Path=/; Max-Age=3600; Expires=Thu, 24 Mar 2022 13:33:34 GMT; HttpOnly |

When accessing secured endpoints, the token has to be provided in the `Cookie` request header.

| Request header | Value                      |
|----------------|----------------------------|
| Cookie         | inatrace-accessToken=`JWT` |

### Endpoints

A complete list of endpoints is available [here](https://github.com/INATrace/backend/tree/main/src/main/java/com/abelium/inatrace/components).

### Common requests

- Create new user (not activated): `POST /api/user/register`

```json
{
  "email": "test@user.com",
  "password": "password",
  "name": "Test",
  "surname": "User"
}
```

- Confirm email: `POST /api/user/confirm_email`

Insert token from the activation email message

```json
{
  "token": "2b7875bc-13ec-4cc3-bf75-21e9a5847d44"
}
```

- Log in as admin: `POST /api/user/login`

```json
{
  "username": "",
  "password": ""
}
```

- Activate user (logged in as admin): `POST /api/user/admin/execute/ACTIVATE_USER`

Find the user ID in the database.

```json
{
  "id": "2"
}
```

- Upload image: `POST /api/common/image`

Body: `form-data`<br>
Key: `file`<br>
Value: select file

Response:

```json
{
  "status": "OK",
  "data": {
    "id": 1,
    "storageKey": "b822e079-7584-4489-ac78-81292d0c2c8c",
    "name": "roasted_coffee_beans.jpg",
    "contentType": "image/jpeg",
    "size": 599153
  }
}
```

- Create or update company: `POST /api/company/create` or `PUT /api/company/profile`

```json
{
  "name": "Coffe company",
  "abbreviation": "CCC",
  "headquarters": {
    "address": "Coffee street",
    "city": "Java",
    "state": "Java",
    "zip": "1000",
    "country": {
      "id": "104"
    }
  },
  "about": "Making great coffee",
  "manager": "Mana Ger",
  "webPage": "inatrace.org",
  "email": "info@inatrace.org",
  "logo": {
    "storageKey": "b822e079-7584-4489-ac78-81292d0c2c8c"
  }
}
```

Response:

```json
{
  "status": "OK",
  "data": {
    "id": 1
  }
}
```

- Create or update product: `POST /api/product/create` or `PUT /api/product`

```
{
  "id":"integer",
  "name":"string",
  "photo":"ApiDocument",
  "description":"string",
  "origin": ApiProductOrigin,
  "process": ApiProcess,
  "responsibility": ApiResponsibility,
  "sustainability": ApiSustainability,
  "associatedCompanies": ApiProductCompany,
  "company": ApiCompany,
  "labels": ApiProductLabelValues,
  "settings": ApiProductSettings,
  "knowledgeBlog":"boolean"
}
```

Response is structured in the following way:
It always contains attribute [`status`](https://github.com/INATrace/backend/blob/main/src/main/java/com/abelium/INATrace/api/ApiStatus.java).
If response is successful, then `status` is equal to 'OK' and appropriate response can be found under `data` attribute.
If response is unsuccessful (see the above link for other statuses), then `errorMessage` attribute is returned.

Example of successful and unsuccessful response
```
{
  "status": "OK",
  "data": {
    "id": 4,
    "email": "example@example.com",
    "name": "Example",
    "surname": "Example",
    "status": "ACTIVE",
    "role": "SYSTEM_ADMIN",
    "actions": [
      "VIEW_USER_PROFILE",
      "UPDATE_USER_PROFILE"
    ],
    "companyIds": [
      1
    ]
  }
}
```

```
{
  "status": "AUTH_ERROR",
  "errorMessage": "Invalid credentials"
}
```

### Currency service

Currency service manages exchange rate data retrieval and currency conversion. 

#### Exchange rate retrieval

The service uses [exchangeratesapi.io](http://exchangeratesapi.io/) API for fetching currency conversion rates. It runs daily at 00:01 system time. The API is limited to 250 requests per day.

#### Currency conversion

The service exposes methods `convert` and `convertAtDate` to convert between any two supported currencies. `convert` uses the latest localy stored rate. `convertAtDate` uses the rate at the specified date. If the exchange rate for the specified date is not stored locally it fetches it from the API.

## Database

### Version
MySQL `8.4.6`

### Connection

If you are using a database management tool, use the following parameters to create a connection:

- Hostname: `localhost`
- Port: `3306`
- Database: `inatrace`
- Username: `root`
- Password: `root`
- Use SSL: `true`
- Driver settings:
  - `allowPublicKeyRetrieval`: `true`

### Entities

A complete list of entities is available [here](https://github.com/INATrace/backend/tree/main/src/main/java/com/abelium/inatrace/db).

Below is the entity graph for an initialized INATrace database. The full-sized vector image can be found [here](docs/images/inatrace_db.svg).

![INATrace DB entity graph](docs/images/inatrace_db.svg)

#### Users

There are two types of users:

- System users (User)
- Company users (CompanyUser)

*System users* are used for logging in. Based on their role, they have different permissions inside the system. Available roles are:

- User - can access resources that are owned by the company where the user is part of.
- System admin - can administer system-wide resources and settings.
- Regional admin - can administer resources that are owned by the company/ies where the user is part of. 

*Company users* are essentially role mappings for system users within a company. Based on their role, they have different permissions in the context of the company. A system user with role *User* can have the role *Admin* in a company. Available company roles are:

- User
- Admin
- Manager
- Accountant

#### Translations

Some items have names, descriptions and other data in multiple languages. To enable extensible adding of translations, these entities have a one-to-many mapping to corresponding Translation entities (e.g. Company and CompanyTranslation).

- CompanyTranslation
- FacilityTranslation
- ProcessingActionTranslation
- ProcessingEvidenceFieldTranslation
- ProcessingEvidenceTypeTranslation
- ProductTypeTranslation
- SemiProductTranslation

### Schema and data updates

Flyway is used to update the database when adding, changing or removing rows or columns. Here's how to configure a migration:

- Create a new class in `com.abelium.inatrace.db.migrations` package and `implement JpaMigration`
- Name should be in format `V<yyyy>_<MM>_<dd>_<hh>_<mm>__<Descriptive_Operation_Name>`
- `@Override` the `migrate` method
- Implement the necessary additions, edits and deletions

#### Running the migrations

The migrations run automatically at application startup. Once completed a record is created in table `schema_version`. A migration does not run again if a record already exists.

## Building

To build an executable `jar` run the following command in the project root directory:

```
mvn clean install
```

### Docker image

#### Base

`eclipse-temurin:17-jre`

Since only the major version is specified, the build process will always pull the latest minor and patch versions automatically.

#### Command syntax

To build and tag a Docker image run `docker-build.sh` in the project root directory. The script runs `mvn clean package` then builds and tags a Docker image with the resulting `jar`.

```
./docker-build.sh <repo name (local or remote)> <tag> [push]
```

#### Example

```
./docker-build.sh inatrace-be 2.4.0 push
```

## Contribution

Project INATrace welcomes contribution from everyone. See CONTRIBUTING.md for help getting started.

## License 

Copyright (c) 2023 Anteja ECG d.o.o., GIZ - Deutsche Gesellschaft für Internationale Zusammenarbeit GmbH, Sunesis ltd.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
