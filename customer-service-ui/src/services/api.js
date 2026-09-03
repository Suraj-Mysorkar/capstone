// Thin fetch wrappers over the customer-service REST API (behind /api/customers).
//
// API base URL is resolved, in order:
//   1. in-browser override set on the Settings page   (localStorage: cs_ui_api_base)
//   2. build-time env var                              (VITE_CUSTOMER_API_URL)
//   3. localhost default                               (dev)
//
// (1) means a build deployed to Azure Static Web Apps can be pointed at the
// right backend from the browser even if the env var was not set at build time.

const DEFAULT_BASE = 'http://localhost:8081/api/customers';
const BASE_KEY = 'cs_ui_api_base';
const TOKEN_KEY = 'cs_ui_token';   // legacy manual override (Settings page)
const AUTH_TOKEN_KEY = 'csp_token'; // JWT from the portal login (AuthContext)

function readLS(key) {
  try {
    return localStorage.getItem(key) || '';
  } catch {
    return '';
  }
}

function writeLS(key, value) {
  try {
    if (value && value.trim()) localStorage.setItem(key, value.trim());
    else localStorage.removeItem(key);
  } catch {
    /* private mode / storage disabled — ignore */
  }
}

/** Effective API base URL, trailing slash stripped. */
export function apiBase() {
  const raw = readLS(BASE_KEY) || import.meta.env.VITE_CUSTOMER_API_URL || DEFAULT_BASE;
  return raw.replace(/\/+$/, '');
}

export function setApiBase(url) {
  writeLS(BASE_KEY, url);
}

/** Where the current base URL comes from — shown on the Settings page. */
export function apiBaseSource() {
  if (readLS(BASE_KEY)) return 'browser override';
  if (import.meta.env.VITE_CUSTOMER_API_URL) return 'build-time env (VITE_CUSTOMER_API_URL)';
  return 'default (localhost)';
}

// ── Bearer token ─────────────────────────────────────────────────────
// The portal login (AuthContext) stores a JWT under `csp_token`; a manual
// override can still be set on the Settings page under `cs_ui_token`.
export function getToken() {
  return readLS(AUTH_TOKEN_KEY) || readLS(TOKEN_KEY);
}

export function setToken(token) {
  writeLS(TOKEN_KEY, token);
}

export function getAuthHeaders(extra = {}) {
  const token = getToken();
  return token ? { ...extra, Authorization: `Bearer ${token}` } : extra;
}

const headers = getAuthHeaders;

async function handle(res) {
  const text = await res.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { message: text };
    }
  }
  if (!res.ok) {
    const err = new Error(
      body?.message || body?.error || `Request failed (HTTP ${res.status})`,
    );
    err.status = res.status;
    err.body = body;
    throw err;
  }
  return body;
}

// ── Health ───────────────────────────────────────────────────────────
export const ping = () => fetch(`${apiBase()}/ping`).then(handle);

// ── Portal auth (username / password, backed by the shared Users table) ──
export const authLogin = (username, password) =>
  fetch(`${apiBase()}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  }).then(handle);

export const authRegister = (payload) =>
  fetch(`${apiBase()}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }).then(handle);

// ── Customers ────────────────────────────────────────────────────────
export const registerCustomer = (payload) =>
  fetch(apiBase(), {
    method: 'POST',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(payload),
  }).then(handle);

export const getCustomer = (id) =>
  fetch(`${apiBase()}/${id}`, { headers: headers() }).then(handle);

export const getCustomerByEmail = (email) =>
  fetch(`${apiBase()}?email=${encodeURIComponent(email)}`, { headers: headers() }).then(handle);

export const listCustomers = ({ status, page = 0, size = 20, sort } = {}) => {
  const q = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) q.set('status', status);
  if (sort) q.set('sort', sort);
  return fetch(`${apiBase()}?${q.toString()}`, { headers: headers() }).then(handle);
};

export const updateCustomer = (id, patch) =>
  fetch(`${apiBase()}/${id}`, {
    method: 'PATCH',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(patch),
  }).then(handle);

export const updateOnboardingStatus = (id, status, reason) =>
  fetch(`${apiBase()}/${id}/onboarding-status`, {
    method: 'PATCH',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ status, reason: reason || null }),
  }).then(handle);

export const deleteCustomer = (id) =>
  fetch(`${apiBase()}/${id}`, { method: 'DELETE', headers: headers() }).then((res) => {
    if (res.status === 204 || res.ok) return true;
    return handle(res);
  });
