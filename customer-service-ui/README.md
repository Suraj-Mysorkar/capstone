# Customer Console (customer-service-ui)

React + Vite dashboard for the **customer-service** microservice — customer
registration, the onboarding-status state machine, profile editing, directory
search, and lookup. Same stack and visual system as `capstone-ui`.

## Stack

- React 19, Vite 8, React Router 7
- `recharts` (status distribution chart), `lucide-react` (icons)
- Plain `fetch` wrappers in `src/services/api.js` — no state library

## Pages

| Route | What it does | API |
|-------|--------------|-----|
| `/` Dashboard | Totals + onboarding-status distribution chart + recent customers | `GET /api/customers?size=&sort=` |
| `/customers` Directory | Paged table, filter by onboarding status | `GET /api/customers?status=&page=&size=` |
| `/customers/:id` Detail | Profile view/edit, lifecycle strip, **valid-only** status transitions, delete | `GET` / `PATCH /{id}` / `PATCH /{id}/onboarding-status` / `DELETE /{id}` |
| `/register` Register | New customer form (+ sample fill) | `POST /api/customers` |
| `/lookup` Lookup | Fetch one customer by id or email | `GET /api/customers/{id}` / `?email=` |
| `/settings` Settings | Set the API base URL + bearer token, test `/ping` | `GET /api/customers/ping` |

The allowed onboarding-status transitions in `src/lib/onboarding.js` mirror
`OnboardingStatusTransitionValidator` in the backend — the detail page only
offers legal next states.

## Configuration

The API base URL is resolved in this order:

1. **Browser override** — set on the Settings page, stored in `localStorage`
   (`cs_ui_api_base`). Lets a already-built/deployed app be pointed at any
   backend without a rebuild.
2. **Build-time env** — `VITE_CUSTOMER_API_URL` (see `.env.example`).
3. **Default** — `http://localhost:8081/api/customers`.

| Env var | Notes |
|---------|-------|
| `VITE_CUSTOMER_API_URL` | customer-service base path, **including** `/api/customers`. Read at build time. |

### Authentication

Every endpoint except `/ping` requires a Microsoft Entra ID JWT. There is no
login flow here — paste an access token on the **Settings** page. It is stored
in this browser's `localStorage` (`cs_ui_token`) and sent as
`Authorization: Bearer …`. Without a token, protected calls return 401 and the
UI shows a prompt to set one.

Scopes / roles the backend checks:
- `SCOPE_customers.read` — list / get
- `SCOPE_customers.write` — register / update / transition status
- `ROLE_customer_admin` — delete

## Running

```bash
npm install
npm run dev        # http://localhost:5173

# point at a running customer-service:
VITE_CUSTOMER_API_URL=http://localhost:8081/api/customers npm run dev
```

```bash
npm run build      # -> dist/
npm run lint       # oxlint
```

## Deploy — Azure Static Web Apps

`public/staticwebapp.config.json` (copied into `dist/` by the build) handles SPA
deep-link fallback. Build/deploy settings:

| Setting | Value |
|---------|-------|
| App location | `customer-service-ui` |
| Output location | `dist` |
| Build command | `npm run build` |
| Build env var | `VITE_CUSTOMER_API_URL=https://<your-customer-service-host>/api/customers` |

```bash
# with the SWA CLI
npm install
VITE_CUSTOMER_API_URL=https://<host>/api/customers npm run build
swa deploy ./dist --env production
```

If `VITE_CUSTOMER_API_URL` is missed at build time, open the deployed app's
**Settings** page and paste the backend URL there.

### CORS / the backend

customer-service is a **separate origin** from this Static Web App, so the
deployed customer-service must allow the SWA origin. Options:

- Run customer-service behind **Azure API Management** with a CORS policy (the
  design this service assumes), **or**
- add CORS to the customer-service prod security chain for
  `https://<your-swa>.azurestaticapps.net`, **or**
- attach this Static Web App to customer-service as a **linked backend** so
  calls are proxied same-origin (then set the API base to `/api/customers`).

The `local` Spring profile already sends permissive CORS, so localhost dev works
out of the box.
