# API

How clients authenticate, where the generated OpenAPI definition lives, and what the common requests look like.

## OpenAPI

Resources are annotated with Swagger annotations. After the application is started, the Swagger service definition JSON is served at `http://localhost:8080/v3/api-docs`, and Swagger UI is served at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

Both live at the root of the server rather than under `/api`. Every endpoint the application itself defines is published under `/api` — `PrefixedApiRequestHandler` adds that prefix to the mappings in the `com.abelium` packages — but springdoc's own endpoints are not touched by it. `http://localhost:8080/api/swagger-ui.html` does not exist.

### Postman

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

## Authentication

Clients authenticate against `POST /api/user/login` endpoint and receive a JWT token in the `Set-Cookie` response header. The token is valid for 1 hour. See section [Common requests](#common-requests) below for a sample login request.

| Response header | Value                                                                                            |
|-----------------|--------------------------------------------------------------------------------------------------|
| Set-Cookie      | inatrace-accessToken=`JWT`; Path=/; Max-Age=3600; Expires=Thu, 24 Mar 2022 13:33:34 GMT; HttpOnly |

When accessing secured endpoints, the token has to be provided in the `Cookie` request header.

| Request header | Value                      |
|----------------|----------------------------|
| Cookie         | inatrace-accessToken=`JWT` |

## Endpoints

A complete list of endpoints is available [here](https://github.com/agstack/inatrace-backend/tree/main/src/main/java/com/abelium/inatrace/components).

## Common requests

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
It always contains attribute [`status`](https://github.com/agstack/inatrace-backend/blob/main/src/main/java/com/abelium/inatrace/api/ApiStatus.java).
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

## Currency service

Currency service manages exchange rate data retrieval and currency conversion. 

### Exchange rate retrieval

The service uses [exchangeratesapi.io](http://exchangeratesapi.io/) API for fetching currency conversion rates. It runs daily at 00:01 system time. The API is limited to 250 requests per day.

### Currency conversion

The service exposes methods `convert` and `convertAtDate` to convert between any two supported currencies. `convert` uses the latest localy stored rate. `convertAtDate` uses the rate at the specified date. If the exchange rate for the specified date is not stored locally it fetches it from the API.
