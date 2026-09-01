const BASE = import.meta.env.VITE_LOAN_API_URL || 'https://team6-loan-service.azurewebsites.net/api/v1/loans';
const DOC_BASE = import.meta.env.VITE_DOC_API_URL || 'https://team6-document-service.azurewebsites.net/api/v1/documents';

// ── Schemes & Customers ──────────────────────────────────────────────
export const fetchSchemes = () =>
  fetch(`${BASE}/schemes`).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch schemes (${r.status})`);
    return r.json();
  });

export const fetchSchemeById = (id) =>
  fetch(`${BASE}/schemes/${id}`).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch scheme (${r.status})`);
    return r.json();
  });

export const fetchCustomers = () =>
  fetch(`${BASE}/customers`).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch customers (${r.status})`);
    return r.json();
  });

// ── EMI Calculator ───────────────────────────────────────────────────
export const calculateEmi = (body) =>
  fetch(`${BASE}/calculate-emi`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Failed to calculate EMI (${r.status})`);
    return r.json();
  });

// ── Documents ────────────────────────────────────────────────────────
export const fetchDocumentTypes = async () => {
  try {
    const r = await fetch(`${DOC_BASE}/types`);
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
    { typeCode: 'EMPLOYMENT_PROOF', categoryName: 'Employment Proof', allowedExtensions: 'pdf,jpg,jpeg,png' }
  ];
};

export const uploadDocument = async (formData) => {
  // 1. Primary: Upload to Azure Document Service (connects to Azure Blob Storage)
  try {
    const res = await fetch(`${DOC_BASE}/upload`, { method: 'POST', body: formData });
    if (res.ok) {
      return await res.json();
    }
    const err = await res.json().catch(() => ({ message: `Upload failed (${res.status})` }));
    throw new Error(err.message || `Upload failed with status ${res.status}`);
  } catch (docErr) {
    console.warn('Document service upload encountered error, attempting loan-service fallback:', docErr);
    // 2. Fallback: Upload to Loan Service document storage proxy
    try {
      const res = await fetch(`${BASE}/documents/upload`, { method: 'POST', body: formData });
      if (res.ok) {
        return await res.json();
      }
      const err = await res.json().catch(() => ({ message: `Loan service upload failed (${res.status})` }));
      throw new Error(err.message || docErr.message);
    } catch (loanErr) {
      throw new Error(docErr.message || loanErr.message || 'Failed to upload document.');
    }
  }
};

export const fetchDocumentById = async (id) => {
  const cleanId = String(id).trim().replace(/^DOC-/i, '');
  
  // 1. If numeric or standard ID, try document service first
  if (!isNaN(cleanId)) {
    try {
      const res = await fetch(`${DOC_BASE}/${cleanId}`);
      if (res.ok) {
        return await res.json();
      }
    } catch (e) {
      console.warn('Document service fetch failed, trying loan-service', e);
    }
  }

  // 2. Try Loan Service
  try {
    const fullId = String(id).startsWith('DOC-') ? id : `DOC-${id}`;
    const res = await fetch(`${BASE}/documents/${fullId}`);
    if (res.ok) {
      return await res.json();
    }
  } catch (e) {}

  // 3. Last attempt with raw ID on loan service
  const res = await fetch(`${BASE}/documents/${id}`);
  if (res.ok) {
    return await res.json();
  }

  throw new Error(`Document not found with ID: ${id}`);
};

export const getDocumentDownloadUrl = (id) => {
  const cleanId = String(id).trim().replace(/^DOC-/i, '');
  return `${DOC_BASE}/${cleanId}/download`;
};

export const fetchDocumentBlobUrl = async (documentId, contentType = 'application/pdf') => {
  const cleanId = String(documentId).trim().replace(/^DOC-/i, '');
  
  if (!isNaN(cleanId)) {
    try {
      const res = await fetch(`${DOC_BASE}/${cleanId}/download`);
      if (res.ok) {
        const blob = await res.blob();
        return URL.createObjectURL(blob);
      }
    } catch (e) {
      console.warn('Failed to stream document blob from document-service:', e);
    }
  }

  // If download stream is unavailable, fallback to download endpoint URL directly
  return `${DOC_BASE}/${cleanId}/download`;
};

export const updateDocumentStatus = async (documentId, payload) => {
  const cleanId = String(documentId).trim().replace(/^DOC-/i, '');
  const res = await fetch(`${DOC_BASE}/${cleanId}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: `Status update failed (${res.status})` }));
    throw new Error(err.message || 'Status update failed');
  }
  return res.json();
};

export const deleteDocumentById = async (documentId) => {
  const cleanId = String(documentId).trim().replace(/^DOC-/i, '');
  const res = await fetch(`${DOC_BASE}/${cleanId}`, {
    method: 'DELETE'
  });
  if (!res.ok && res.status !== 204) {
    const err = await res.json().catch(() => ({ message: 'Delete failed' }));
    throw new Error(err.message || 'Failed to delete document');
  }
  return true;
};

export const fetchCustomerDocuments = async (customerId) => {
  try {
    const r = await fetch(`${DOC_BASE}/customer/${customerId}`);
    if (r.ok) return await r.json();
  } catch (e) {}
  return [];
};

export const fetchApplicationDocuments = async (applicationId) => {
  try {
    const r = await fetch(`${DOC_BASE}/application/${applicationId}`);
    if (r.ok) return await r.json();
  } catch (e) {}
  return [];
};

// ── Applications ─────────────────────────────────────────────────────
export const fetchApplications = (status) => {
  const url = status ? `${BASE}/applications?status=${status}` : `${BASE}/applications`;
  return fetch(url).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch applications (${r.status})`);
    return r.json();
  });
};

export const applyLoan = (body) =>
  fetch(`${BASE}/apply`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Loan application failed (${r.status})`);
    return r.json();
  });

export const fetchApplicationById = (id) =>
  fetch(`${BASE}/applications/${id}`).then(r => {
    if (!r.ok) throw new Error(`Application not found (${r.status})`);
    return r.json();
  });

export const fetchApplicationStatus = (id) =>
  fetch(`${BASE}/applications/${id}/status`).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch status (${r.status})`);
    return r.json();
  });

export const fetchAuditLogs = (id) =>
  fetch(`${BASE}/applications/${id}/audit-logs`).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch audit logs (${r.status})`);
    return r.json();
  });

// ── Manager Callback ─────────────────────────────────────────────────
export const submitManagerCallback = (id, body) =>
  fetch(`${BASE}/applications/${id}/manager-callback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Manager decision submission failed (${r.status})`);
    return r.json();
  });

// ── Document Uploaded Notification ───────────────────────────────────
export const notifyDocumentUploaded = (id, body) =>
  fetch(`${BASE}/applications/${id}/document-uploaded`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Document notification failed (${r.status})`);
    return r.json();
  });

// ── Employee Authentication (Azure API Management) ───────────────────
export const employeeLogin = async (username, password) => {
  const APIM_LOGIN_URL = 'https://team6-api-management.azure-api.net/auth/internal/login';
  const res = await fetch(APIM_LOGIN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ username, password }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const errorMsg = data.error || data.message || `Login failed with status ${res.status}`;
    throw new Error(errorMsg);
  }
  return data;
};

