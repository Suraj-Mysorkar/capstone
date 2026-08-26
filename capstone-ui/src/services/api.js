const BASE = 'http://localhost:8080/api/v1/loans';

// ── Schemes ─────────────────────────────────────────────────────────
export const fetchSchemes = () =>
  fetch(`${BASE}/schemes`).then(r => r.json());

export const fetchSchemeById = (id) =>
  fetch(`${BASE}/schemes/${id}`).then(r => r.json());

// ── EMI Calculator ───────────────────────────────────────────────────
export const calculateEmi = (body) =>
  fetch(`${BASE}/calculate-emi`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => r.json());

// ── Documents ────────────────────────────────────────────────────────
export const uploadDocument = (formData) =>
  fetch(`${BASE}/documents/upload`, { method: 'POST', body: formData }).then(r => {
    if (!r.ok) throw new Error('Upload failed');
    return r.json();
  });

export const fetchDocumentById = (id) =>
  fetch(`${BASE}/documents/${id}`).then(r => r.json());

// ── Applications ─────────────────────────────────────────────────────
export const fetchApplications = (status) => {
  const url = status ? `${BASE}/applications?status=${status}` : `${BASE}/applications`;
  return fetch(url).then(r => r.json());
};

export const applyLoan = (body) =>
  fetch(`${BASE}/apply`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => r.json());

export const fetchApplicationById = (id) =>
  fetch(`${BASE}/applications/${id}`).then(r => r.json());

export const fetchApplicationStatus = (id) =>
  fetch(`${BASE}/applications/${id}/status`).then(r => r.json());

export const fetchAuditLogs = (id) =>
  fetch(`${BASE}/applications/${id}/audit-logs`).then(r => r.json());

// ── Manager Callback ─────────────────────────────────────────────────
export const submitManagerCallback = (id, body) =>
  fetch(`${BASE}/applications/${id}/manager-callback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => r.json());
