# Getting started

Running the INATrace backend on a development machine. Configuration is covered separately in [Configuration](configuration.md).

## Requirements
* Java `17` or higher
* Maven `3.8.5`
* MySQL `8.4.11`

## Optional
* Docker
* Mailhog

## How to run
1. Clone the repository

2. Import as maven project to your preferred IDE

3. Prepare environment
   1. [Create database](#create-database)
   2. (*OPTIONAL*) [Email testing](#email-testing)
   3. [Spring configuration](configuration.md)

4. Run `INATraceBackendApplication.java`

### Create database

Spin up a container:

```
docker run --name inatrace-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=inatrace -e MYSQL_USER=inatrace -e MYSQL_PASSWORD=inatrace -p 3306:3306 -d mysql:8.4.11
```

Tables will be created and prefilled with starter data on application startup.

### Email testing

MailHog is an email testing tool for developers. It runs a SMTP server on port `1025` which intercepts messages and displays them in a GUI.

Spin up a container:

```
docker run --name inatrace-mailhog -p 1025:1025 -p 8025:8025 -d mailhog/mailhog:v1.0.1
```

The GUI is available at [`localhost:8025`](http://localhost:8025).
