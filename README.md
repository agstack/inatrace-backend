# INATrace

INATrace is a digital open-source solution designed to enhance the economic conditions of smallholder farmers by improving the traceability of global supply chains. Funded by the German Federal Ministry for Economic Cooperation and Development (BMZ) and implemented by GIZ, INATrace provides an efficient internal management system for cooperatives, digitally stores supply chain data, and supports compliance with regulations like the EU Deforestation Regulation (EUDR).

Project is composed of 4 parts, coordinated from the [INATrace project hub](https://github.com/agstack/inatrace) (project-wide documentation and governance):

* [Frontend application](https://github.com/agstack/inatrace-frontend)
* [Mobile app](https://github.com/agstack/inatrace-mobile)
* [Java backend](https://github.com/agstack/inatrace-backend)
* [Coffee network](https://github.com/agstack/inatrace-coffee-network)

# Backend

The Java backend: a Spring Boot REST API over MySQL, serving the web frontend and
the mobile app.

## Quick start

```bash
# MySQL
docker run --name inatrace-mysql -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=inatrace -e MYSQL_USER=inatrace -e MYSQL_PASSWORD=inatrace \
  -p 3306:3306 -d mysql:8.4.11

cp src/main/resources/application.properties.template \
   src/main/resources/application.properties
# fill in the values, then run INATraceBackendApplication.java
```

Tables are created and prefilled with starter data on first startup. Full
instructions are in [docs/getting-started.md](docs/getting-started.md).

## Documentation

| Page | What it covers |
|---|---|
| [Getting started](docs/getting-started.md) | Requirements, running locally, database and mail containers |
| [Configuration](docs/configuration.md) | Every `application.properties` value, grouped by what it configures |
| [API](docs/api.md) | Authentication, the OpenAPI definition, common requests, the currency service |
| [Database](docs/database.md) | Connection parameters, entity model, users and roles, migrations |
| [Building](docs/building.md) | Producing a jar and a Docker image |

These pages are written to be read on GitHub, and are also published at
<https://docs.agstack.org/> — which imports this `docs/` directory directly, so
editing them here updates the site.

[TECHNICAL_DOCUMENTATION.md](TECHNICAL_DOCUMENTATION.md) covers the platform
architecture and the Kubernetes deployment topology. It is **not current** and is
deliberately left out of `docs/`, so that nothing stale is published to the
documentation site. Read it for background, verify before relying on it.

## Contribution

Project INATrace welcomes contribution from everyone. See CONTRIBUTING.md for help getting started.
Questions and discussion go to the [INATrace mailing list](https://lists.agstack.org/g/inatrace).

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
