# Database

Connection parameters, the entity model, and how schema migrations run.

## Version
MySQL `8.4.11`

## Connection

If you are using a database management tool, use the following parameters to create a connection:

- Hostname: `localhost`
- Port: `3306`
- Database: `inatrace`
- Username: `root`
- Password: `root`
- Use SSL: `true`
- Driver settings:
  - `allowPublicKeyRetrieval`: `true`

## Entities

A complete list of entities is available [here](https://github.com/agstack/inatrace-backend/tree/main/src/main/java/com/abelium/inatrace/db).

Below is the entity graph for an initialized INATrace database. The full-sized vector image can be found [here](images/inatrace_db.svg).

![INATrace DB entity graph](images/inatrace_db.svg)

### Users

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

### Translations

Some items have names, descriptions and other data in multiple languages. To enable extensible adding of translations, these entities have a one-to-many mapping to corresponding Translation entities (e.g. Company and CompanyTranslation).

- CompanyTranslation
- FacilityTranslation
- ProcessingActionTranslation
- ProcessingEvidenceFieldTranslation
- ProcessingEvidenceTypeTranslation
- ProductTypeTranslation
- SemiProductTranslation

## Schema and data updates

Flyway is used to update the database when adding, changing or removing rows or columns. Here's how to configure a migration:

- Create a new class in `com.abelium.inatrace.db.migrations` package and `implement JpaMigration`
- Name should be in format `V<yyyy>_<MM>_<dd>_<hh>_<mm>__<Descriptive_Operation_Name>`
- `@Override` the `migrate` method
- Implement the necessary additions, edits and deletions

### Running the migrations

The migrations run automatically at application startup. Once completed a record is created in table `schema_version`. A migration does not run again if a record already exists.
