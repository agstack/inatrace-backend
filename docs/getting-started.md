# Getting started

Running the INATrace backend on a development machine. Configuration is covered separately in [Configuration](configuration.md).

## Requirements
* Java `17` or higher
* Maven `3.8.5`
* Docker with Compose, for MySQL `8.4.11` (or your own MySQL `8.4.11`)

## Optional
* Mailpit, to catch the e-mails the backend sends (the compose file below starts it)

## How to run
1. Clone the repository

2. Import as maven project to your preferred IDE

3. Prepare environment
   1. [Create database](#create-database)
   2. (*OPTIONAL*) [Email testing](#email-testing)
   3. [Spring configuration](configuration.md)

4. Run `INATraceBackendApplication.java`

### Create database

`compose-dev.yaml` in the project root starts the development infrastructure, MySQL and
[Mailpit](#email-testing):

```
docker compose -f compose-dev.yaml up -d
```

MySQL listens on `localhost:3306`, with database, user and password `inatrace`. Tables
will be created and prefilled with starter data on application startup. The data is kept
in a Docker volume between restarts:

```
docker compose -f compose-dev.yaml down       # stop, keeping the data
docker compose -f compose-dev.yaml down -v    # stop and erase the data
```

### Email testing

[Mailpit](https://mailpit.axllent.org/) is an email testing tool for developers. It runs
an SMTP server on port `1025` which intercepts messages and displays them in a GUI. The
same `compose-dev.yaml` starts it.

The GUI is available at [`localhost:8025`](http://localhost:8025). Point the
[SMTP settings](configuration.md#smtp) at `localhost:1025` and set
`INATrace.mail.sendingEnabled = true`; any username and password are accepted.
