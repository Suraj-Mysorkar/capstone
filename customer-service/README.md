# Customer Service

Owns **Profile Management** and **Onboarding Status Tracking** for the loan
origination platform. Sits behind Azure API Management at `/api/customers`,
reads/writes Azure SQL Database, and publishes domain events to Azure Event
Grid for downstream consumers (Notification Service, Reporting Dashboard).

## Architecture fit

- **Client Layer** → Angular/Vue web app & mobile app call APIM
- **API Gateway Layer** → APIM validates the Entra ID JWT and routes
  `/api/customers/*` here (rate limiting handled centrally by APIM)
- **This service** → validates the JWT again (defense in depth), enforces
  authorization via scopes/roles, applies business rules, persists to Azure
  SQL, publishes `CustomerRegisteredEvent` / `CustomerStatusChangedEvent`
- **Messaging & Events Layer** → Azure Event Grid topic, consumed by
  Notification Service and other event-driven services
- **Azure Managed Data Stores** → Azure SQL Database (`customers` table)

## Tech stack

- Java 17, Spring Boot 3.3
- Spring Web, Spring Data JPA, Bean Validation
- Spring Security OAuth2 Resource Server (validates Entra ID-issued JWTs)
- Azure SDK: `azure-messaging-eventgrid`, `azure-identity` (Managed Identity)
- Flyway for schema migrations
- mssql-jdbc driver (Azure SQL Database)
- springdoc-openapi (Swagger UI at `/swagger-ui.html`)

## Configuration

All config is externalized via environment variables (see
`application.yml` / `application-dev.yml` / `application-prod.yml`):

| Variable                  | Description                                              |
|----------------------------|-----------------------------------------------------------|
| `DB_URL`                   | JDBC URL for Azure SQL Database                          |
| `DB_USERNAME` / `DB_PASSWORD` | Azure SQL credentials (prefer Entra ID auth in prod)   |
| `JWT_ISSUER_URI`           | Entra ID tenant issuer, e.g. `https://login.microsoftonline.com/<tenant-id>/v2.0` |
| `EVENTGRID_TOPIC_ENDPOINT` | Azure Event Grid topic endpoint                          |
| `EVENTGRID_TOPIC_KEY`      | Topic access key (omit in prod — uses Managed Identity via `DefaultAzureCredential`) |
| `PORT`                     | HTTP port (default `8081`)                                |

## Running locally

```bash
# requires a local SQL Server / Azure SQL Edge container, e.g.:
docker run -e "ACCEPT_EULA=1" -e "MSSQL_SA_PASSWORD=changeMe123!" \
  -p 1433:1433 -d mcr.microsoft.com/azure-sql-edge

export JWT_ISSUER_URI=https://login.microsoftonline.com/<your-tenant-id>/v2.0
export EVENTGRID_TOPIC_ENDPOINT=https://<your-topic>.<region>-1.eventgrid.azure.net/api/events
export EVENTGRID_TOPIC_KEY=<topic-key>   # local/dev only

mvn spring-boot:run
```

Flyway runs automatically on startup and creates the `customers` table.

## API

| Method | Path                                  | Auth (scope/role)               | Description                        |
|--------|----------------------------------------|----------------------------------|-------------------------------------|
| POST   | `/api/customers`                       | `customers.write` / `customer_admin` | Register a new customer             |
| GET    | `/api/customers/{id}`                  | `customers.read`                 | Get profile by id                   |
| GET    | `/api/customers?email=`                | `customers.read`                 | Look up by email                    |
| GET    | `/api/customers?status=&page=&size=`   | `customers.read`                 | List/filter by onboarding status    |
| PATCH  | `/api/customers/{id}`                  | `customers.write`                | Update profile fields               |
| PATCH  | `/api/customers/{id}/onboarding-status`| `customers.write`                | Transition onboarding status        |
| DELETE | `/api/customers/{id}`                  | `customer_admin`                 | Delete a customer profile           |
| GET    | `/api/customers/ping`                  | none                              | Liveness check                      |

Onboarding status follows a fixed state machine (see
`OnboardingStatusTransitionValidator`):

```
REGISTERED → DOCUMENTS_PENDING → DOCUMENTS_SUBMITTED → KYC_IN_REVIEW
  → KYC_APPROVED → ONBOARDING_COMPLETE
  → KYC_REJECTED → (back to DOCUMENTS_PENDING)
Any state → SUSPENDED
```

## Events published

| Event type                          | Trigger                          |
|--------------------------------------|-----------------------------------|
| `com.bank.customer.registered`      | New customer registration         |
| `com.bank.customer.statuschanged`   | Onboarding status transition      |

Events are published as CloudEvents to the shared Event Grid topic. Publish
failures are logged but do not fail the originating request (the SQL write
has already committed).

## Build & test

```bash
mvn clean verify        # runs unit + MockMvc integration tests against H2
mvn spring-boot:run
docker build -t customer-service .
```

## Deploying

Container is designed for Azure App Service (Linux, container) or AKS behind
APIM. In production, omit `EVENTGRID_TOPIC_KEY` and grant the service's
Managed Identity the **EventGrid Data Sender** role on the topic; `DB_URL`
should point at Azure SQL Database with Entra ID or a Key Vault-sourced
password.
