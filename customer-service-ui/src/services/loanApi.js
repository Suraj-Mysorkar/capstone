// ── Loan-service + Document-service client ───────────────────────────────
//
// The customer portal talks to the SAME backend microservices as the internal
// capstone-ui (loan-service + document-service). Every write here therefore
// shows up in capstone-ui too, and fires the same Azure Service Bus /
// Event Grid events (loan-events-topic, document status events, etc.).
//
// Base URLs resolve, in order:
//   1. in-browser override from the Settings page (localStorage)
//   2. build-time env var
//   3. the shared Azure deployment default

import { getAuthHeaders } from './api';

const LOAN_KEY = 'cs_ui_loan_api_base';
const DOC_KEY = 'cs_ui_doc_api_base';

// Set VITE_LOAN_API_URL / VITE_DOC_API_URL at build time (or override on the
// Settings page). The loan-service must be a build that includes both the CORS
// filter and POST /api/v1/loans/customers (this repo's loan-service) and must
// allow this app's origin (APP_CORS_ALLOWED_ORIGINS). For local dev point these
// at http://localhost:8080 / :8082.
const LOAN_DEFAULT =
  import.meta.env.VITE_LOAN_API_URL ||
  'https://team6-api-management.azure-api.net/loan-applications/api/v1/loans';
const DOC_DEFAULT =
  import.meta.env.VITE_DOC_API_URL ||
  'https://team6-api-management.azure-api.net/documents/api/v1/documents';

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
    /* ignore */
  }
}

export const loanBase = () => (readLS(LOAN_KEY) || LOAN_DEFAULT).replace(/\/+$/, '');
export const docBase = () => (readLS(DOC_KEY) || DOC_DEFAULT).replace(/\/+$/, '');
export const setLoanBase = (url) => writeLS(LOAN_KEY, url);
export const setDocBase = (url) => writeLS(DOC_KEY, url);
export const loanBaseSource = () =>
  readLS(LOAN_KEY) ? 'browser override' : import.meta.env.VITE_LOAN_API_URL ? 'build-time env' : 'default (Azure)';
export const docBaseSource = () =>
  readLS(DOC_KEY) ? 'browser override' : import.meta.env.VITE_DOC_API_URL ? 'build-time env' : 'default (Azure)';

async function authFetch(url, options = {}) {
  const res = await fetch(url, options);
  if (res.status === 401) {
    try {
      localStorage.removeItem('csp_user');
      localStorage.removeItem('csp_token');
      window.dispatchEvent(new Event('auth:unauthorized'));
    } catch {}
  }
  return res;
}

// ── Schemes ─────────────────────────────────────────────────────────────
export const fetchSchemes = () =>
  authFetch(`${loanBase()}/schemes`, { headers: getAuthHeaders() }).then((r) => {
    if (!r.ok) throw new Error(`Failed to fetch schemes (${r.status})`);
    return r.json();
  });

export const fetchSchemeById = (id) =>
  authFetch(`${loanBase()}/schemes/${id}`, { headers: getAuthHeaders() }).then((r) => {
    if (!r.ok) throw new Error(`Failed to fetch scheme (${r.status})`);
    return r.json();
  });

// ── EMI Calculator ──────────────────────────────────────────────────────
export const calculateEmi = (body) =>
  authFetch(`${loanBase()}/calculate-emi`, {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then((r) => {
    if (!r.ok) throw new Error(`Failed to calculate EMI (${r.status})`);
    return r.json();
  });

// ── Applications ────────────────────────────────────────────────────────
export const fetchApplications = (status) => {
  const url = status ? `${loanBase()}/applications?status=${status}` : `${loanBase()}/applications`;
  return authFetch(url, { headers: getAuthHeaders() }).then((r) => {
    if (!r.ok) throw new Error(`Failed to fetch applications (${r.status})`);
    return r.json();
  });
};

export const applyLoan = (body) =>
  authFetch(`${loanBase()}/apply`, {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then(async (r) => {
    const data = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(data.message || `Loan application failed (${r.status})`);
    return data;
  });

export const fetchApplicationById = (id) =>
  authFetch(`${loanBase()}/applications/${id}`, { headers: getAuthHeaders() }).then((r) => {
    if (!r.ok) throw new Error(`Application not found (${r.status})`);
    return r.json();
  });

export const fetchApplicationStatus = (id) =>
  authFetch(`${loanBase()}/applications/${id}/status`, { headers: getAuthHeaders() }).then((r) => {
    if (!r.ok) throw new Error(`Failed to fetch status (${r.status})`);
    return r.json();
  });

export const fetchAuditLogs = (id) =>
  authFetch(`${loanBase()}/applications/${id}/audit-logs`, { headers: getAuthHeaders() }).then((r) => {
    if (!r.ok) throw new Error(`Failed to fetch audit logs (${r.status})`);
    return r.json();
  });

// Customer submits verification documents → advances the durable workflow.
export const notifyDocumentUploaded = (id, body) =>
  authFetch(`${loanBase()}/applications/${id}/document-uploaded`, {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then((r) => {
    if (!r.ok) throw new Error(`Document notification failed (${r.status})`);
    return r.json();
  });

// ── Documents ───────────────────────────────────────────────────────────
export const fetchDocumentTypes = async () => {
  try {
    const r = await authFetch(`${docBase()}/types`, { headers: getAuthHeaders() });
    if (r.ok) return await r.json();
  } catch (e) {
    console.warn('Could not fetch dynamic document types, using defaults', e);
  }
  return [
    { typeCode: 'IDENTITY_PROOF', categoryName: 'Identity Proof', allowedExtensions: 'pdf,jpg,jpeg,png' },
    { typeCode: 'INCOME_PROOF', categoryName: 'Income Proof', allowedExtensions: 'pdf,jpg,jpeg,png' },
    { typeCode: 'ADDRESS_PROOF', categoryName: 'Address Proof', allowedExtensions: 'pdf,jpg,jpeg,png' },
    { typeCode: 'BANK_STATEMENT', categoryName: 'Bank Statement', allowedExtensions: 'pdf' },
    { typeCode: 'PHOTOGRAPH', categoryName: 'Photograph', allowedExtensions: 'jpg,jpeg,png' },
    { typeCode: 'EMPLOYMENT_PROOF', categoryName: 'Employment Proof', allowedExtensions: 'pdf,jpg,jpeg,png' },
  ];
};

export const uploadDocument = async (formData) => {
  try {
    const res = await authFetch(`${docBase()}/upload`, { method: 'POST', headers: getAuthHeaders(), body: formData });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: `Upload failed (${res.status})` }));
    throw new Error(err.message || `Upload failed with status ${res.status}`);
  } catch (docErr) {
    console.warn('Document service upload failed, attempting loan-service fallback:', docErr);
    try {
      const res = await authFetch(`${loanBase()}/documents/upload`, { method: 'POST', headers: getAuthHeaders(), body: formData });
      if (res.ok) return await res.json();
      const err = await res.json().catch(() => ({ message: `Loan service upload failed (${res.status})` }));
      throw new Error(err.message || docErr.message);
    } catch (loanErr) {
      throw new Error(docErr.message || loanErr.message || 'Failed to upload document.');
    }
  }
};

export const fetchCustomerDocuments = async (customerId) => {
  try {
    const r = await authFetch(`${docBase()}/customer/me`, { headers: getAuthHeaders() });
    if (r.ok) return await r.json();
  } catch (e) {
    /* ignore */
  }
  return [];
};

export const fetchApplicationDocuments = async (applicationId) => {
  try {
    const r = await authFetch(`${docBase()}/application/${encodeURIComponent(applicationId)}`, { headers: getAuthHeaders() });
    if (r.ok) return await r.json();
  } catch (e) {
    /* ignore */
  }
  return [];
};

export const fetchDocumentBlobUrl = async (documentId, contentType = 'application/pdf') => {
  const cleanId = String(documentId).trim().replace(/^DOC-/i, '');
  if (!isNaN(cleanId)) {
    try {
      const res = await authFetch(`${docBase()}/${cleanId}/download`, { headers: getAuthHeaders() });
      if (res.ok) {
        const blob = await res.blob();
        return URL.createObjectURL(blob);
      }
    } catch (e) {
      console.warn('Failed to stream document blob:', e);
    }
  }
  return `${docBase()}/${cleanId}/download`;
};

export const deleteDocumentById = async (documentId) => {
  const cleanId = String(documentId).trim().replace(/^DOC-/i, '');
  const res = await authFetch(`${docBase()}/${cleanId}`, { method: 'DELETE', headers: getAuthHeaders() });
  if (!res.ok && res.status !== 204) {
    const err = await res.json().catch(() => ({ message: 'Delete failed' }));
    throw new Error(err.message || 'Failed to delete document');
  }
  return true;
};
