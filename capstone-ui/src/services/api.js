const BASE = import.meta.env.VITE_LOAN_API_URL || 'https://team6-loan-service.azurewebsites.net/api/v1/loans';
const DOC_BASE = import.meta.env.VITE_DOC_API_URL || 'https://team6-document-service.azurewebsites.net/api/v1/documents';

// ── Schemes & Customers ──────────────────────────────────────────────
export const fetchSchemes = () =>
  fetch(`${BASE}/schemes`).then(r => r.json());

export const fetchSchemeById = (id) =>
  fetch(`${BASE}/schemes/${id}`).then(r => r.json());

export const fetchCustomers = () =>
  fetch(`${BASE}/customers`).then(r => r.json());

// ── EMI Calculator ───────────────────────────────────────────────────
export const calculateEmi = (body) =>
  fetch(`${BASE}/calculate-emi`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => r.json());

// ── Documents ────────────────────────────────────────────────────────
export const fetchDocumentTypes = () =>
  fetch(`${DOC_BASE}/types`).then(r => r.json()).catch(() => [
    { typeCode: 'IDENTITY_PROOF', categoryName: 'Identity Proof', allowedExtensions: 'pdf,jpg,jpeg,png' },
    { typeCode: 'INCOME_PROOF', categoryName: 'Income Proof', allowedExtensions: 'pdf,jpg,jpeg,png' },
    { typeCode: 'ADDRESS_PROOF', categoryName: 'Address Proof', allowedExtensions: 'pdf,jpg,jpeg,png' },
    { typeCode: 'BANK_STATEMENT', categoryName: 'Bank Statement', allowedExtensions: 'pdf' },
    { typeCode: 'PHOTOGRAPH', categoryName: 'Photograph', allowedExtensions: 'jpg,jpeg,png' },
    { typeCode: 'EMPLOYMENT_PROOF', categoryName: 'Employment Proof', allowedExtensions: 'pdf,jpg,jpeg,png' }
  ]);

export const uploadDocument = async (formData) => {
  try {
    const res = await fetch(`${DOC_BASE}/upload`, { method: 'POST', body: formData });
    if (res.ok) {
      return await res.json();
    }
    const err = await res.json().catch(() => ({ message: 'Document upload failed' }));
    throw new Error(err.message || 'Document upload failed');
  } catch (primaryErr) {
    // Fallback to loan-service document storage proxy if doc-service direct upload encounters network errors
    try {
      const res = await fetch(`${BASE}/documents/upload`, { method: 'POST', body: formData });
      if (res.ok) {
        return await res.json();
      }
    } catch (ignored) {}
    throw primaryErr;
  }
};

export const fetchDocumentById = async (id) => {
  const cleanId = String(id).replace(/^DOC-/i, '');
  try {
    const res = await fetch(`${DOC_BASE}/${cleanId}`);
    if (res.ok) {
      const data = await res.json();
      return data;
    }
  } catch (e) {}

  // Fallback to loan service endpoint
  return fetch(`${BASE}/documents/${id}`).then(r => {
    if (!r.ok) throw new Error('Document not found with ID: ' + id);
    return r.json();
  });
};

export const getDocumentDownloadUrl = (id) => {
  const cleanId = String(id).replace(/^DOC-/i, '');
  return `${DOC_BASE}/${cleanId}/download`;
};

export const fetchDocumentSasUrl = async (id) => {
  const cleanId = String(id).replace(/^DOC-/i, '');
  try {
    const res = await fetch(`${DOC_BASE}/${cleanId}/sas-url`);
    if (res.ok) {
      return await res.text();
    }
  } catch (e) {}
  return null;
};

export const fetchCustomerDocuments = (customerId) =>
  fetch(`${DOC_BASE}/customer/${customerId}`).then(r => r.json()).catch(() => []);

export const fetchApplicationDocuments = (applicationId) =>
  fetch(`${DOC_BASE}/application/${applicationId}`).then(r => r.json()).catch(() => []);

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

// ── Document Uploaded Notification ───────────────────────────────────
export const notifyDocumentUploaded = (id, body) =>
  fetch(`${BASE}/applications/${id}/document-uploaded`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => r.json());
