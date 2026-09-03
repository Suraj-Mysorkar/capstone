# Everyday Bank — Loan Portal (customer-service-ui)

React + Vite portal that lets a **customer** self-register (with a password),
sign in, browse loan schemes on the home page, calculate an EMI, upload KYC /
income documents, apply for a loan and track it.

It is the customer-facing counterpart of `capstone-ui` (the bank officer / manager
console) and uses the **same auth pattern** (username/password → JWT → Bearer
header) and the **same backend microservices**, so everything a customer does here
shows up in `capstone-ui` and fires the same events. Its **visual theme is
deliberately distinct** (warm aubergine + amber/emerald, pill nav) — same
components, different skin.

| Action here | Backend | Notes |
|-------------|---------|-------|
| Register | `customer-service` `POST /api/customers/auth/register` | creates the `customer_profiles` row **and** a row in the shared **`Users`** table (`user_role = 'customer'`, chosen password), fires `com.bank.customer.registered`, returns a JWT (auto sign-in) |
| Register (customer sync) | `loan-service` `POST /api/v1/loans/customers` | upserts the shared `Customers` table so a loan officer can help this customer in `capstone-ui` immediately |
| Sign in | `customer-service` `POST /api/customers/auth/login` | validates username (email) + password against the `Users` table, returns a JWT |
| Apply for loan | `loan-service` `POST /api/v1/loans/apply` | `LOAN_APPLICATION_SUBMITTED` + status events on `loan-events-topic` |
| Upload documents (during apply) | `document-service` `POST /api/v1/documents/upload` + `loan-service` `POST /applications/{id}/document-uploaded` | document status events; `LOAN_APPLICATION_APPROVED` / `LOAN_MANUAL_REVIEW_REQUIRED` |

## Auth

Mirrors capstone-ui: `context/AuthContext.jsx` + `components/ProtectedRoute.jsx`.
The JWT is stored in `localStorage` (`csp_token` / `csp_user`) and sent as
`Authorization: Bearer <token>` on every API call. The shared `Users` table is the
one the employee login (`user-validator`) also uses — customers just get
`user_role = 'customer'`.

Passwords are stored as-is in `Users.login_password` (plaintext), matching the
existing `user-validator` scheme in this codebase. The portal JWT is a self-signed
HS256 token (`PortalJwtIssuer`, secret `app.portal.jwt.secret`).

## Pages

| Route | What it does |
|-------|--------------|
| `/login` | Email + password sign in |
| `/register` | First name … + **create password / confirm password**; on success creates the account, signs in, and mirrors the customer to loan-service |
| `/` Home | **Loan schemes grid (centre of the page)** + onboarding strip, my application/document counts, pipeline pie, quick actions |
| `/emi` | EMI + amortization calculator |
| `/documents` | Upload documents + list & preview my own documents (filed under my customer id) |
| `/apply` | Loan application form, prefilled from my profile; **documents are uploaded inline** and submitted with the application |
| `/applications` | My applications, filterable by status |
| `/applications/:id` | Overview + progress trail (Workflow tab removed — customer-facing) |
| `/settings` | Configure the three service base URLs |

Manager-only capabilities from `capstone-ui` (manager decision callback, manager
document review / verify-reject, portfolio analytics, demo scenarios) are
intentionally **not** included.

## Running everything locally

```bash
# 1. loan-service  (port 8080, shared Azure SQL)  — JDK 17
cd loan-service
JAVA_HOME=<jdk17> mvn -DskipTests package
JAVA_HOME=<jdk17> java -jar target/loan-service-1.0.0.jar

# 2. customer-service  (port 8081, embedded H2)  — JDK 17
cd customer-service
JAVA_HOME=<jdk17> ./mvnw -DskipTests package
JAVA_HOME=<jdk17> java -jar target/customer-service-1.0.0.jar --spring.profiles.active=local

# 3. this portal  (port 5173)
cd customer-service-ui
npm install && npm run dev
```

`document-service` can stay on the deployed team6 instance (default). The
`capstone-ui` officer console can point at the deployed loan-service — it shares
the same database, so it still sees customers/applications created here.

## Configuration

Each service URL resolves: browser override (Settings page) → build-time env → default.

| Env var | Default |
|---------|---------|
| `VITE_CUSTOMER_API_URL` | `http://localhost:8081/api/customers` |
| `VITE_LOAN_API_URL` | `http://localhost:8080/api/v1/loans` (local — needs the `POST /customers` endpoint) |
| `VITE_DOC_API_URL` | `https://team6-document-service.azurewebsites.net/api/v1/documents` |

`loan-service` defaults to **local** because the customer-sync endpoint
(`POST /api/v1/loans/customers`) is not on the deployed team6 build yet. Run
`loan-service` locally (it connects to the shared Azure SQL DB, so the deployed
services still see the data), or point `VITE_LOAN_API_URL` at a redeployed
loan-service that includes the endpoint.

`customer-service` requires an Entra ID JWT unless it runs with the `local`
Spring profile — paste a token on the Settings page if needed. `loan-service` and
`document-service` are open (`@CrossOrigin("*")`).

## Running

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # -> dist/
npm run lint       # oxlint
```

## Deploy — Azure Static Web Apps

`public/staticwebapp.config.json` handles SPA deep-link fallback.

| Setting | Value |
|---------|-------|
| App location | `customer-service-ui` |
| Output location | `dist` |
| Build command | `npm run build` |
| Build env vars | `VITE_CUSTOMER_API_URL`, `VITE_LOAN_API_URL`, `VITE_DOC_API_URL` |

Each backend is a separate origin from the SWA, so it must allow the SWA origin
(via API Management CORS, a linked backend, or a prod CORS entry). The `local`
profile / `@CrossOrigin("*")` cover localhost dev.
