const BASE = import.meta.env.VITE_LOAN_API_URL || 'https://team6-api-management.azure-api.net/loan-applications/api/v1/loans';
const DOC_BASE = import.meta.env.VITE_DOC_API_URL || 'https://team6-api-management.azure-api.net/documents/api/v1/documents';
const APIM_KEY = import.meta.env.VITE_APIM_SUBSCRIPTION_KEY || '';

// ── JWT Auth Header Helpers ──────────────────────────────────────────
export const getAuthToken = () => {
  try {
    return localStorage.getItem('capstone_employee_token') || '';
  } catch (e) {
    return '';
  }
};

export const getAuthHeaders = (extraHeaders = {}) => {
  const token = getAuthToken();
  const headers = {
    'Ocp-Apim-Subscription-Key': APIM_KEY,
    'client-key': APIM_KEY,
    ...extraHeaders
  };
  if (token) {
    headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  }
  return headers;
};

// ── Schemes & Customers ──────────────────────────────────────────────
export const fetchSchemes = () =>
  fetch(`${BASE}/schemes`, {
    headers: getAuthHeaders()
  }).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch schemes (${r.status})`);
    return r.json();
  });

export const fetchSchemeById = (id) =>
  fetch(`${BASE}/schemes/${id}`, {
    headers: getAuthHeaders()
  }).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch scheme (${r.status})`);
    return r.json();
  });

export const fetchCustomers = () =>
  fetch(`${BASE}/customers`, {
    headers: getAuthHeaders()
  }).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch customers (${r.status})`);
    return r.json();
  });

// ── EMI Calculator ───────────────────────────────────────────────────
export const calculateEmi = (body) =>
  fetch(`${BASE}/calculate-emi`, {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Failed to calculate EMI (${r.status})`);
    return r.json();
  });

// ── Documents ────────────────────────────────────────────────────────
export const fetchDocumentTypes = async () => {
  try {
    const r = await fetch(`${DOC_BASE}/types`, {
      headers: getAuthHeaders()
    });
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
  // Upload to Document Service
  try {
    const res = await fetch(`${DOC_BASE}/upload`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: formData
    });
    if (res.ok) {
      return await res.json();
    }
    const err = await res.json().catch(() => ({ message: `Document service upload failed (${res.status})` }));
    throw new Error(err.message || `Upload failed with status ${res.status}`);
  } catch (docErr) {
    throw new Error(docErr.message || 'Failed to upload document.');
  }
};

export const fetchDocumentById = async (id) => {
  const cleanId = String(id).trim().replace(/^DOC-/i, '');
  
  // 1. If numeric or standard ID, try document service first
  if (!isNaN(cleanId)) {
    try {
      const res = await fetch(`${DOC_BASE}/${cleanId}`, {
        headers: getAuthHeaders()
      });
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
    const res = await fetch(`${BASE}/documents/${fullId}`, {
      headers: getAuthHeaders()
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (e) {}

  // 3. Last attempt with raw ID on loan service
  const res = await fetch(`${BASE}/documents/${id}`, {
    headers: getAuthHeaders()
  });
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
      const res = await fetch(`${DOC_BASE}/${cleanId}/download`, {
        headers: getAuthHeaders()
      });
      if (res.ok) {
        const blob = await res.blob();
        return URL.createObjectURL(blob);
      }
    } catch (e) {
      console.warn('Failed to stream document blob from document-service:', e);
    }
  }

  return `${DOC_BASE}/${cleanId}/download`;
};

export const updateDocumentStatus = async (documentId, payload) => {
  const cleanId = String(documentId).trim().replace(/^DOC-/i, '');
  const res = await fetch(`${DOC_BASE}/${cleanId}/status`, {
    method: 'PUT',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
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
  
  // 1. Delete from document-service
  try {
    await fetch(`${DOC_BASE}/${cleanId}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
  } catch (e) {
    console.warn('[deleteDocumentById] document-service delete error:', e);
  }

  // 2. Delete from loan-service
  try {
    await fetch(`${BASE}/documents/${cleanId}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
    await fetch(`${BASE}/documents/DOC-${cleanId}`, {
      method: 'DELETE',
      headers: getAuthHeaders()
    });
  } catch (e) {
    console.warn('[deleteDocumentById] loan-service delete error:', e);
  }

  return true;
};

export const fetchCustomerDocuments = async (customerId) => {
  if (!customerId) return [];
  const docMap = new Map();

  const normalizeDoc = (d) => {
    if (!d) return null;
    const docId = d.documentId || d.id;
    const docType = d.documentType || d.docType || 'OTHER';
    const name = d.documentName || d.originalFileName || d.fileName || docType;
    const blob = d.blobPath || d.blobStoragePath || d.blobUrl || '';
    const appId = d.applicationId || '';

    const key = blob ? `${appId}::${blob}` : `${appId}::${docType}::${name}`;

    return {
      key,
      doc: {
        ...d,
        id: docId,
        documentId: docId,
        customerId: d.customerId || customerId,
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

  try {
    const r = await fetch(`${DOC_BASE}/customer/${encodeURIComponent(customerId)}`, {
      headers: getAuthHeaders()
    });
    if (r.ok) {
      const json = await r.json();
      if (Array.isArray(json)) addDocs(json);
    }
  } catch (e) {}

  try {
    const altCid = customerId.startsWith('CUST-') ? customerId.replace(/^CUST-/, '') : `CUST-${customerId}`;
    const r2 = await fetch(`${DOC_BASE}/customer/${encodeURIComponent(altCid)}`, {
      headers: getAuthHeaders()
    });
    if (r2.ok) {
      const json = await r2.json();
      if (Array.isArray(json)) addDocs(json);
    }
  } catch (e) {}

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
    const r = await fetch(`${DOC_BASE}/application/${encodeURIComponent(applicationId)}`, {
      headers: getAuthHeaders()
    });
    if (r.ok) {
      const json = await r.json();
      if (Array.isArray(json) && json.length > 0) {
        addDocs(json);
        return Array.from(docMap.values());
      }
    }
  } catch (e) {}

  // 2. loan-service fallback: application details
  try {
    const r2 = await fetch(`${BASE}/applications/${encodeURIComponent(applicationId)}`, {
      headers: getAuthHeaders()
    });
    if (r2.ok) {
      const app = await r2.json();
      if (Array.isArray(app?.documents)) addDocs(app.documents);
    }
  } catch (e) {}

  return Array.from(docMap.values());
};

// ── Applications ─────────────────────────────────────────────────────
export const fetchApplications = (status) => {
  const url = status ? `${BASE}/applications?status=${status}` : `${BASE}/applications`;
  return fetch(url, {
    headers: getAuthHeaders()
  }).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch applications (${r.status})`);
    return r.json();
  });
};

export const applyLoan = (body) =>
  fetch(`${BASE}/apply`, {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Loan application failed (${r.status})`);
    return r.json();
  });

export const fetchApplicationById = (id) =>
  fetch(`${BASE}/applications/${id}`, {
    headers: getAuthHeaders()
  }).then(r => {
    if (!r.ok) throw new Error(`Application not found (${r.status})`);
    return r.json();
  });

export const fetchApplicationStatus = (id) =>
  fetch(`${BASE}/applications/${id}/status`, {
    headers: getAuthHeaders()
  }).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch status (${r.status})`);
    return r.json();
  });

export const fetchAuditLogs = (id) =>
  fetch(`${BASE}/applications/${id}/audit-logs`, {
    headers: getAuthHeaders()
  }).then(r => {
    if (!r.ok) throw new Error(`Failed to fetch audit logs (${r.status})`);
    return r.json();
  });

// ── Manager Callback ─────────────────────────────────────────────────
export const submitManagerCallback = (id, body) =>
  fetch(`${BASE}/applications/${id}/manager-callback`, {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Manager decision submission failed (${r.status})`);
    return r.json();
  });

// ── Document Uploaded & Request Notifications ─────────────────────────
export const notifyDocumentUploaded = (id, body) =>
  fetch(`${BASE}/applications/${id}/document-uploaded`, {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Document notification failed (${r.status})`);
    return r.json();
  });

export const requestDocumentsFromCustomer = (id, body = {}) =>
  fetch(`${BASE}/applications/${id}/request-documents`, {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  }).then(r => {
    if (!r.ok) throw new Error(`Request documents email failed (${r.status})`);
    return r.json();
  });

// ── Employee Authentication (Azure API Management Gateway) ───────────
export const employeeLogin = async (username, password) => {
  const APIM_LOGIN_URL = 'https://team6-api-management.azure-api.net/auth/internal/login';
  const res = await fetch(APIM_LOGIN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Ocp-Apim-Subscription-Key': APIM_KEY,
      'client-key': APIM_KEY,
      'X-User-Role': 'ROLE_MANAGER',
    },
    body: JSON.stringify({ username, password }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const errorMsg = data.error || data.message || `Invalid username or password (${res.status})`;
    throw new Error(errorMsg);
  }

  // Extract and persist JWT token
  const token = data.token || data.jwt || data.accessToken || data.access_token || data.id_token || (typeof data === 'string' ? data : null);
  if (token) {
    localStorage.setItem('capstone_employee_token', token);
  }

  return data;
};

// ── Live Notifications (SSE / WebSockets) ─────────────────────────────
const NOTIF_ROOT = BASE.replace(/\/loans\/?$/, '/notifications');

export const getNotificationStreamUrl = (username = 'mgr1') => {
  return `${NOTIF_ROOT}/stream?username=${encodeURIComponent(username)}&subscription-key=${APIM_KEY}`;
};

export const fetchNotifications = (username = 'mgr1') =>
  fetch(`${NOTIF_ROOT}?username=${encodeURIComponent(username)}`, {
    headers: getAuthHeaders()
  }).then(r => {
    if (!r.ok) return [];
    return r.json();
  }).catch(() => []);

export const markNotificationAsRead = (id) =>
  fetch(`${NOTIF_ROOT}/${id}/read`, {
    method: 'POST',
    headers: getAuthHeaders()
  }).then(r => r.json()).catch(() => ({ success: false }));

export const markAllNotificationsAsRead = (username = 'mgr1') =>
  fetch(`${NOTIF_ROOT}/read-all?username=${encodeURIComponent(username)}`, {
    method: 'POST',
    headers: getAuthHeaders()
  }).then(r => r.json()).catch(() => ({ success: false }));

export const sendTestNotification = (params = {}) => {
  const q = new URLSearchParams(params).toString();
  return fetch(`${NOTIF_ROOT}/test?${q}`, {
    method: 'POST',
    headers: getAuthHeaders()
  }).then(r => r.json());
};
