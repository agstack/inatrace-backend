# Configuration

Every property the backend reads, grouped by what it configures.

Spring uses `application.properties` file stored in `src/main/resources` for configuration. A template is provided, see instructions below.

- Make a copy of `application.properties.template` and save it as `application.properties`
- Fill the missing values

*NOTE*: The values defined below are applicable for a local development environment. For other environments, change the values accordingly.

The Docker image contains no configuration: mount the file at
`/app/config/application.properties` instead. See [Building](building.md#running).

## Datasource

- `INATrace.database.name`: `inatrace`
- `spring.datasource.username`: `inatrace`
- `spring.datasource.password`: `inatrace`

## SMTP

- `spring.mail.protocol`: `smtp`
- `spring.mail.host`: `localhost`
- `spring.mail.port`: `1025`
- `spring.mail.username`
- `spring.mail.password`
- `spring.mail.properties.mail.smtp.ssl.checkserveridentity`: `false`
- `spring.mail.properties.mail.smtps.auth`: `false`

## Email template

- `INAtrace.mail.template.from`: `info@inatrace.com`
- `INAtrace.mail.redirect`
- `INAtrace.mail.sendingEnabled`: `true` (`false` by default) 
- `INATrace.loginManager.mail`: `registrations@inatrace.com` (Email address to receive notifications for new registrations)
- `INAtrace.info.mail`: `info@inatrace.com` (Contact email)

## Security

- `INATrace.auth.jwtSigningKey`: `sign` (Key for signing JWT tokens)
- `INATrace.requestLog.token`: `token` (Key for authorizing log requests)

## Storage

- `INATrace.fileStorage.root`: Path on local filesystem for saving images, documents, etc. (e.g. `C:\\Users\\Name\\inatrace-backend` or `/home/name/inatrace-backend`). In the Docker image, use `/data/storage` and mount a volume there.

## Exchange rates API

- `INAtrace.exchangerate.apiKey`: API key for exchange rate service. Create a free account at [https://exchangeratesapi.io](https://exchangeratesapi.io/) to get an API key.

## Beyco integration
INATrace supports integration with the Beyco platform. This allows users to create Beyco offers automatically from INATrace stock orders. For more info about Beyco, please go to: `https://beyco.nl`. This integration is optional. Integration properties are following:
- `beyco.oauth2.clientId`: `clientId`
- `beyco.oauth2.clientSecret`: `clientSecret`
- `beyco.oauth2.url`: `url`

The values of these properties are provided by Beyco. If integration with Byeco is not needed, the values of these properties should be empty.
