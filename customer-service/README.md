# Customer Service

Owns **Profile Management** and **Onboarding Status Tracking** for the loan
origination platform. Sits behind Azure API Management at `/api/customers`,
reads/writes Azure SQL Database, and publishes domain events to Azure Event
Grid for downstream consumers (Notification Service, Reporting Dashboard).

## Architecture fit

- **Client Layer** → customer portal & officer console call APIM
- **API Gateway Layer** → APIM authenticates the caller and forwards their
  identity as `X-User-Id` / `X-User-Role` headers (rate limiting handled
  centrally by APIM)
- **This service** → trusts those headers, enforces **role-based authorization**
  (`ROLE_CUSTOMER` / `ROLE_EMPLOYEE` / `ROLE_MANAGER`) via `@PreAuthorize`,
  applies business rules, persists to Azure SQL, publishes
  `CustomerRegisteredEvent` / `CustomerStatusChangedEvent`
- **Messaging & Events Layer** → Azure Event Grid topic, consumed by
  Notification Service and other event-driven services
- **Azure Managed Data Stores** → shared Azure SQL Database `smzen-capstone-db`
  (this service owns the `customer_profiles` table; the `Customers` table in the
  same DB belongs to loan-service / report-service)

## Tech stack

- Java 17, Spring Boot 3.3
- Spring Web, Spring Data JPA, Bean Validation
- Spring Security — pre-authenticated `X-User-Id` / `X-User-Role` header filter
  (APIM-injected identity), method-level `@PreAuthorize` role checks. Same
  pattern as `document-service` / `report-service`.
- Azure SDK: `azure-messaging-eventgrid`, `azure-identity` (Managed Identity)
- Flyway for schema migrations
- mssql-jdbc driver (Azure SQL Database)
- springdoc-openapi (Swagger UI at `/swagger-ui.html`)

## Configuration

By default (no profile active) the service connects to the **shared Azure SQL
Database `smzen-capstone-db`** — the same server used by loan-service and
report-service. Every value is overridable via environment variables (see
`application.yml` / `application-dev.yml` / `application-prod.yml`). You can also
drop a `secrets.properties` file next to the module (git-ignored) to supply
`DB_PASSWORD` etc. without exporting env vars.

| Variable                  | Description                                              |
|----------------------------|-----------------------------------------------------------|
| `DB_URL`                   | JDBC URL (default: shared Azure SQL `smzen-capstone-db`) |
| `DB_USERNAME` / `DB_PASSWORD` | Azure SQL credentials (prefer Entra ID auth in prod)   |
| `JWT_ISSUER_URI`           | Entra ID tenant issuer, e.g. `https://login.microsoftonline.com/<tenant-id>/v2.0` |
| `EVENTGRID_TOPIC_ENDPOINT` | Azure Event Grid topic endpoint                          |
| `EVENTGRID_TOPIC_KEY`      | Topic access key (omit in prod — uses Managed Identity via `DefaultAzureCredential`) |
| `PORT`                     | HTTP port (default `8081`)                                |

## Running locally

Against the shared Azure SQL DB (default — the machine must be allow-listed on
the server firewall):

```bash
./mvnw spring-boot:run
```

Against a local SQL Server / Azure SQL Edge container instead:

```bash
docker run -e "ACCEPT_EULA=1" -e "MSSQL_SA_PASSWORD=changeMe123!" \
  -p 1433:1433 -d mcr.microsoft.com/azure-sql-edge

SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Flyway runs automatically on startup and creates the `customer_profiles` table.
`baseline-version` is `0`, so V1 is applied even when the target database already
contains other services' tables.

## API

Roles come from the APIM-injected `X-User-Role` header (`ROLE_CUSTOMER` /
`ROLE_EMPLOYEE` / `ROLE_MANAGER`); "staff" = `ROLE_EMPLOYEE` or `ROLE_MANAGER`.

| Method | Path                                  | Allowed roles                   | Description                        |
|--------|----------------------------------------|----------------------------------|-------------------------------------|
| POST   | `/api/customers/auth/register`         | public                           | Portal self-registration (returns a JWT) |
| POST   | `/api/customers/auth/login`            | public                           | Portal login                        |
| POST   | `/api/customers`                       | staff                            | Register a customer profile (officer console) |
| GET    | `/api/customers/me`                    | any signed-in user               | The caller's own profile, resolved from `X-User-Id` (portal uses this after login to fetch the email) |
| GET    | `/api/customers/{id}`                  | customer, staff                  | Get profile by id                   |
| GET    | `/api/customers?email=`                | customer, staff                  | Look up by email                    |
| GET    | `/api/customers?status=&page=&size=`   | staff                            | List/filter by onboarding status    |
| PATCH  | `/api/customers/{id}`                  | customer, staff                  | Update profile fields               |
| PATCH  | `/api/customers/{id}/onboarding-status`| staff                            | Transition onboarding status        |
| DELETE | `/api/customers/{id}`                  | manager                          | Delete a customer profile           |
| GET    | `/api/customers/ping`                  | public                           | Liveness check                      |
| POST   | `/api/customers/loan-manager-assignments` | public (server-to-server)     | Assign a loan manager to a customer's loan application & notify the customer |
| GET    | `/api/customers/loan-manager-assignments?customerId=` | customer, staff   | List a customer's loan manager assignments |

Calling protected endpoints directly (bypassing APIM) requires the headers, e.g.
`-H 'X-User-Id: 1001' -H 'X-User-Role: ROLE_EMPLOYEE'`.

### Portal login & the email

Customers sign in with their **customer id** — the `Users.loginid` handle (or the
numeric `User_ID`), never the email. `PortalAuthService.login()` matches on that
and reads the email from the row. The gateway login flow
(`/auth/customer/login` → user-validator) mints a token that carries only the id,
so the portal calls `GET /api/customers/me` right after login to pull the email
and profile from the DB (`X-User-Id` → `Users` row → `customer_profiles`) and fill
the session — no email is added to the login token or response.

### Loan manager assignment

When a customer applies for a loan, the **loan-service** calls
`POST /api/customers/loan-manager-assignments` with the customer id (and the
application id / loan details). customer-service then:

1. picks the loan manager carrying the fewest current assignments from the pool
   in the shared `Users` table (`user_role = 'manager'`, seeded at startup by
   `LoanManagerSeeder` — `mgr1`…`mgr5`, password `Password@123`),
2. records the assignment in `loan_manager_assignments` (idempotent per
   `applicationId`),
3. publishes `com.bank.customer.loanmanagerassigned` to Event Grid so the
   **notification service** emails the customer who is handling their application.

Request body: `{ "customerId": "<uuid>", "applicationId": "APP-…", "loanType": "PERSONAL_LOAN", "loanAmount": 500000 }`
(`customerEmail` / `customerName` are accepted as a fallback when the profile is
not known here).

Onboarding status follows a fixed state machine (see
`OnboardingStatusTransitionValidator`):

```
REGISTERED → DOCUMENTS_PENDING → DOCUMENTS_SUBMITTED → KYC_IN_REVIEW
  → KYC_APPROVED → ONBOARDING_COMPLETE
  → KYC_REJECTED → (back to DOCUMENTS_PENDING)
Any state → SUSPENDED
```

## Events published

| Event type                              | Trigger                          |
|-----------------------------------------|-----------------------------------|
| `com.bank.customer.registered`          | New customer registration         |
| `com.bank.customer.statuschanged`       | Onboarding status transition      |
| `com.bank.customer.loanmanagerassigned` | Loan manager assigned on loan application |

Events are published as CloudEvents to the shared Event Grid topic. Publish
failures are logged but do not fail the originating request (the SQL write
has already committed).

`com.bank.customer.loanmanagerassigned` data payload:

```json
{
  "customerId": "…", "customerEmail": "…", "customerName": "…",
  "applicationId": "APP-…", "loanType": "PERSONAL_LOAN", "loanAmount": 500000,
  "managerName": "Arjun Rao", "managerLogin": "mgr.arjun", "managerEmail": "…",
  "message": "A relationship manager (Arjun Rao) has been assigned to your loan application APP-… …",
  "occurredAt": "2026-09-03T…Z"
}
```

> **notification-service:** add a branch in `NotificationFunction.handleGenericEvent`
> for `com.bank.customer.loanmanagerassigned` that emails `customerEmail` using
> `message` / `managerName`. Until then the existing generic loan-event routing
> still sends the customer a loan notification email for this event.

## Build & test

**Requires JDK 17.** The build is pinned to a Java 17 toolchain by
`maven-enforcer-plugin` and fails fast on any other JDK.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
./mvnw clean verify     # unit + MockMvc integration tests (*IT) against H2
./mvnw spring-boot:run
docker build -t customer-service .
```

## Deploying

Container is designed for Azure App Service (Linux, container) or AKS behind
APIM. In production, omit `EVENTGRID_TOPIC_KEY` and grant the service's
Managed Identity the **EventGrid Data Sender** role on the topic; `DB_URL`
should point at Azure SQL Database with Entra ID or a Key Vault-sourced
password.
