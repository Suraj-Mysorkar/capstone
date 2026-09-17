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

/**
 * Upload a customer document.
 *
 * CORRECT FLOW (2 steps):
 *  1. POST /documents/api/v1/documents/upload  → document-service stores the file
 *  2. POST /loans/applications/{id}/document-uploaded → loan-service advances the
 *     durable workflow (DOCUMENT_REVIEW_PENDING → DOCUMENTS_SUBMITTED) and writes
 *     the audit trail.
 *
 * Previously only step 1 was called, bypassing the loan-service workflow entirely.
 */
export const uploadDocument = async (formData) => {
  // ── 1. Resolve & inject customerId if missing ──────────────────────────
  if (formData instanceof FormData && !formData.get('customerId')) {
    try {
      const rawUser = localStorage.getItem('csp_user');
      if (rawUser) {
        const user = JSON.parse(rawUser);
        const cid = user.customerServiceId || user.loanCustomerId || user.customerId;
        if (cid) formData.append('customerId', cid);
      }
      if (!formData.get('customerId')) {
        const token = localStorage.getItem('csp_token');
        if (token) {
          const claims = JSON.parse(decodeURIComponent(escape(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))));
          if (claims?.customerId) formData.append('customerId', claims.customerId);
        }
      }
    } catch {
      /* ignore */
    }
  }

  // Ensure both docType field names are present (document-service uses documentType,
  // but some older form paths set only docType).
  if (formData instanceof FormData) {
    const dt = formData.get('documentType') || formData.get('docType');
    if (dt) {
      if (!formData.get('docType')) formData.append('docType', dt);
      if (!formData.get('documentType')) formData.append('documentType', dt);
    }
  }

  // ── 2. Upload file to document-service (APIM-exposed route) ──────────
  const res = await authFetch(`${docBase()}/upload`, {
    method: 'POST',
    headers: getAuthHeaders(), // no Content-Type – browser sets multipart boundary
    body: formData,
  });

  let uploadData;
  if (res.ok) {
    uploadData = await res.json();
  } else {
    const err = await res.json().catch(() => ({ message: `Upload failed (${res.status})` }));
    throw new Error(err.message || `Upload failed with status ${res.status}`);
  }

  // ── 3. Notify loan-service workflow (advances audit trail & status) ───
  // Only when an applicationId was provided, since the workflow is per-application.
  const applicationId = formData instanceof FormData ? formData.get('applicationId') : null;
  if (applicationId) {
    try {
      const docType = (formData instanceof FormData)
        ? (formData.get('documentType') || formData.get('docType') || 'OTHER')
        : 'OTHER';
      const docName = (formData instanceof FormData)
        ? (formData.get('documentName') || '')
        : '';
      const customerId = (formData instanceof FormData) ? formData.get('customerId') : null;
      const docId = uploadData?.documentId || uploadData?.id;

      await notifyDocumentUploaded(applicationId, {
        documentIds: docId ? [String(docId)] : [],
        customerId: customerId || undefined,
        documentType: docType,
        documentName: docName,
        blobUrl: uploadData?.blobStoragePath || uploadData?.blobUrl || uploadData?.storageUri || '',
        blobPath: uploadData?.blobStoragePath || uploadData?.blobPath || uploadData?.storageUri || '',
        contentType: uploadData?.contentType || uploadData?.mimeType || 'application/octet-stream',
        fileSizeBytes: uploadData?.fileSizeBytes || uploadData?.fileSize || 0,
      });
    } catch (workflowErr) {
      // Don't fail the upload — the file is already stored in document-service.
      // The workflow notification is a best-effort call.
      console.warn('[uploadDocument] Loan-service workflow notification failed (upload still succeeded):', workflowErr.message);
    }
  }

  return uploadData;
};


export const fetchCustomerDocuments = async (customerId) => {
  let cid = customerId;
  if (!cid) {
    try {
      const rawUser = localStorage.getItem('csp_user');
      if (rawUser) {
        const u = JSON.parse(rawUser);
        cid = u.customerId || u.customerServiceId || u.loanCustomerId;
      }
      if (!cid) {
        const token = localStorage.getItem('csp_token');
        if (token) {
          const claims = JSON.parse(decodeURIComponent(escape(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))));
          cid = claims?.customerId;
        }
      }
    } catch {
      /* ignore */
    }
  }

  const docMap = new Map();

  const normalizeDoc = (d) => {
    if (!d) return null;
    const docId = d.documentId || d.id;
    const docType = d.documentType || d.docType || 'OTHER';
    const name = d.documentName || d.originalFileName || d.fileName || docType;
    const blob = d.blobPath || d.blobStoragePath || d.blobUrl || '';
    const appId = d.applicationId || '';

    // Primary unique deduplication key: application + blob path / docType
    const key = blob ? `${appId}::${blob}` : `${appId}::${docType}::${name}`;

    return {
      key,
      doc: {
        ...d,
        id: docId,
        documentId: docId,
        customerId: d.customerId || cid,
        applicationId: appId,
        documentType: docType,
        docType: docType,
        documentName: name,
        originalFileName: d.originalFileName || name,
        fileName: d.fileName || name,
        blobPath: blob,
        blobStoragePath: d.blobStoragePath || blob,
        blobUrl: d.blobUrl || blob,
        contentType: d.contentType || d.mimeType || 'application/pdf',
        fileSizeBytes: d.fileSizeBytes || d.fileSize || 0,
        status: d.status || 'UPLOADED',
        createdAt: d.createdAt || d.uploadedAt || new Date().toISOString(),
        uploadedAt: d.uploadedAt || d.createdAt || new Date().toISOString(),
      },
    };
  };

  const addDocs = (items) => {
    if (Array.isArray(items)) {
      for (const item of items) {
        const res = normalizeDoc(item);
        if (!res) continue;
        if (!docMap.has(res.key)) {
          docMap.set(res.key, res.doc);
        } else {
          // Merge preserving richer fields (such as numeric id, status, etc.)
          const existing = docMap.get(res.key);
          docMap.set(res.key, { ...res.doc, ...existing });
        }
      }
    }
  };

  if (cid) {
    // 1. Primary: document-service for this customer
    try {
      const r = await authFetch(`${docBase()}/customer/${encodeURIComponent(cid)}`, { headers: getAuthHeaders() });
      if (r.ok) {
        const json = await r.json();
        if (Array.isArray(json)) addDocs(json);
      }
    } catch {
      /* ignore */
    }

    try {
      const altCid = cid.startsWith('CUST-') ? cid.replace(/^CUST-/, '') : `CUST-${cid}`;
      const r2 = await authFetch(`${docBase()}/customer/${encodeURIComponent(altCid)}`, { headers: getAuthHeaders() });
      if (r2.ok) {
        const json = await r2.json();
        if (Array.isArray(json)) addDocs(json);
      }
    } catch {
      /* ignore */
    }
  }

  // 2. loan-service fallback: only check if document-service returned 0 documents
  if (docMap.size === 0) {
    try {
      const appsResp = await authFetch(`${loanBase()}/applications`, { headers: getAuthHeaders() });
      if (appsResp.ok) {
        const apps = await appsResp.json();
        const myApps = Array.isArray(apps) ? apps.filter((a) => {
          const appCid = a.customerId || '';
          return appCid === cid ||
            appCid === (cid.startsWith('CUST-') ? cid.replace(/^CUST-/, '') : `CUST-${cid}`);
        }) : [];
        for (const app of myApps) {
          if (Array.isArray(app.documents)) addDocs(app.documents);
        }
      }
    } catch {
      /* ignore */
    }
  }

  return Array.from(docMap.values());
};

export const fetchApplicationDocuments = async (applicationId) => {
  if (!applicationId) return [];
  const docMap = new Map();

  const normalizeDoc = (d) => {
    if (!d) return null;
    const docId = d.documentId || d.id;
    const docType = d.documentType || d.docType || 'OTHER';
    const name = d.documentName || d.originalFileName || d.fileName || docType;
    const blob = d.blobPath || d.blobStoragePath || d.blobUrl || '';
    const appId = d.applicationId || applicationId;

    const key = blob ? `${appId}::${blob}` : `${appId}::${docType}::${name}`;

    return {
      key,
      doc: {
        ...d,
        id: docId,
        documentId: docId,
        applicationId: appId,
        documentType: docType,
        docType: docType,
        documentName: name,
        originalFileName: d.originalFileName || name,
        fileName: d.fileName || name,
        blobPath: blob,
        blobStoragePath: d.blobStoragePath || blob,
        blobUrl: d.blobUrl || blob,
        contentType: d.contentType || d.mimeType || 'application/pdf',
        fileSizeBytes: d.fileSizeBytes || d.fileSize || 0,
        status: d.status || 'UPLOADED',
        createdAt: d.createdAt || d.uploadedAt || new Date().toISOString(),
        uploadedAt: d.uploadedAt || d.createdAt || new Date().toISOString(),
      },
    };
  };

  const addDocs = (items) => {
    if (Array.isArray(items)) {
      for (const item of items) {
        const res = normalizeDoc(item);
        if (!res) continue;
        if (!docMap.has(res.key)) {
          docMap.set(res.key, res.doc);
        } else {
          const existing = docMap.get(res.key);
          docMap.set(res.key, { ...res.doc, ...existing });
        }
      }
    }
  };

  // 1. Primary: document-service for this specific application
  try {
    const r = await authFetch(`${docBase()}/application/${encodeURIComponent(applicationId)}`, { headers: getAuthHeaders() });
    if (r.ok) {
      const json = await r.json();
      if (Array.isArray(json) && json.length > 0) {
        addDocs(json);
        return Array.from(docMap.values());
      }
    }
  } catch {
    /* ignore */
  }

  // 2. loan-service fallback: application details
  try {
    const r = await authFetch(`${loanBase()}/applications/${encodeURIComponent(applicationId)}`, { headers: getAuthHeaders() });
    if (r.ok) {
      const app = await r.json();
      if (Array.isArray(app?.documents)) addDocs(app.documents);
    }
  } catch {
    /* ignore */
  }

  return Array.from(docMap.values());
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
  
  // 1. Delete from document-service (storage + DB)
  try {
    await authFetch(`${docBase()}/${cleanId}`, { method: 'DELETE', headers: getAuthHeaders() });
  } catch (e) {
    console.warn('[deleteDocumentById] document-service delete failed:', e.message);
  }

  // 2. Delete from loan-service (LOAN_DOCUMENTS table)
  try {
    await authFetch(`${loanBase()}/documents/${cleanId}`, { method: 'DELETE', headers: getAuthHeaders() });
    await authFetch(`${loanBase()}/documents/DOC-${cleanId}`, { method: 'DELETE', headers: getAuthHeaders() });
  } catch (e) {
    console.warn('[deleteDocumentById] loan-service delete failed:', e.message);
  }

  return true;
};
