import React, { useState, useEffect } from 'react';
import {
  uploadDocument,
  fetchDocumentById,
  fetchDocumentTypes,
  fetchDocumentBlobUrl,
  fetchCustomerDocuments
} from '../services/api';
import {
  FolderUp,
  Search,
  FileText,
  Image as ImageIcon,
  ExternalLink,
  Download,
  Eye,
  CheckCircle,
  AlertCircle,
  FileCheck,
  RefreshCw,
  Clock,
  Layers
} from 'lucide-react';

export default function DocumentsPage() {
  const [tab, setTab] = useState('upload');
  const [docTypesList, setDocTypesList] = useState([]);
  const [customerDocs, setCustomerDocs] = useState([]);
  const [loadingCustomerDocs, setLoadingCustomerDocs] = useState(false);

  // Upload state
  const [customerId, setCustomerId] = useState('CUST-3');
  const [appId, setAppId] = useState('');
  const [docType, setDocType] = useState('IDENTITY_PROOF');
  const [docName, setDocName] = useState('');
  const [file, setFile] = useState(null);
  const [filePreviewUrl, setFilePreviewUrl] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [uploadError, setUploadError] = useState('');

  // Fetch / Viewer state
  const [docId, setDocId] = useState('3');
  const [docResult, setDocResult] = useState(null);
  const [docError, setDocError] = useState('');
  const [docLoading, setDocLoading] = useState(false);
  const [previewBlobUrl, setPreviewBlobUrl] = useState(null);

  // Load document types and existing customer documents on mount
  useEffect(() => {
    fetchDocumentTypes().then(types => {
      if (Array.isArray(types) && types.length > 0) {
        setDocTypesList(types);
        setDocType(types[0].typeCode || types[0].code || 'IDENTITY_PROOF');
      }
    });

    loadCustomerDocuments('CUST-3');
  }, []);

  const loadCustomerDocuments = async (cust = customerId) => {
    if (!cust) return;
    setLoadingCustomerDocs(true);
    try {
      const docs = await fetchCustomerDocuments(cust);
      setCustomerDocs(Array.isArray(docs) ? docs : []);
    } catch (e) {
      console.warn('Could not fetch customer documents:', e);
    } finally {
      setLoadingCustomerDocs(false);
    }
  };

  // Handle local file selection
  const handleFileChange = (e) => {
    const selectedFile = e.target.files?.[0];
    if (!selectedFile) return;

    setFile(selectedFile);
    if (!docName) {
      setDocName(selectedFile.name);
    }

    try {
      const url = URL.createObjectURL(selectedFile);
      setFilePreviewUrl(url);
    } catch (err) {
      console.warn('Preview URL creation failed:', err);
    }
  };

  const doUpload = async () => {
    if (!customerId.trim() || !file) {
      setUploadError('Customer ID and file are required.');
      return;
    }

    setUploading(true);
    setUploadError('');
    setUploadResult(null);

    const fd = new FormData();
    fd.append('customerId', customerId.trim());
    if (appId.trim()) fd.append('applicationId', appId.trim());
    fd.append('documentType', docType);
    fd.append('docType', docType);
    if (docName.trim()) fd.append('documentName', docName.trim());
    fd.append('file', file);

    try {
      const res = await uploadDocument(fd);
      setUploadResult(res);

      const createdId = res.documentId || res.id;
      if (createdId) {
        setDocId(String(createdId));
      }

      // Refresh documents list
      await loadCustomerDocuments(customerId.trim());
    } catch (e) {
      setUploadError(e.message || 'Failed to upload document to Azure Blob Storage.');
    } finally {
      setUploading(false);
    }
  };

  const doFetch = async (targetId = docId) => {
    const cleanId = String(targetId).trim();
    if (!cleanId) {
      setDocError('Please enter a valid Document ID.');
      return;
    }

    setDocLoading(true);
    setDocError('');
    setDocResult(null);
    setPreviewBlobUrl(null);

    try {
      const res = await fetchDocumentById(cleanId);
      if (res && (res.documentId || res.id)) {
        setDocResult(res);

        // Fetch streaming blob URL for reliable browser rendering
        const effectiveId = res.documentId || res.id;
        const blobUrl = await fetchDocumentBlobUrl(effectiveId, res.contentType);
        setPreviewBlobUrl(blobUrl);
      } else {
        setDocError(res?.message || `Document '${cleanId}' not found.`);
      }
    } catch (e) {
      setDocError(e.message || `Error retrieving document '${cleanId}'.`);
    } finally {
      setDocLoading(false);
    }
  };

  // File type detection helpers
  const isPdf = (doc) => {
    if (!doc) return false;
    const type = (doc.contentType || doc.mimeType || '').toLowerCase();
    const name = (doc.originalFileName || doc.fileName || doc.documentName || '').toLowerCase();
    return type.includes('pdf') || name.endsWith('.pdf');
  };

  const isImage = (doc) => {
    if (!doc) return false;
    const type = (doc.contentType || doc.mimeType || '').toLowerCase();
    const name = (doc.originalFileName || doc.fileName || doc.documentName || '').toLowerCase();
    return type.includes('image') || name.match(/\.(jpg|jpeg|png|webp|gif)$/);
  };

  return (
    <div className="page" style={{ maxWidth: 1200, margin: '0 auto', paddingBottom: 40 }}>
      {/* Page Header */}
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div>
          <h2 style={{ fontSize: '1.45rem', fontWeight: 800 }}>Document Management & Azure Storage</h2>
          <p style={{ color: 'var(--muted)', fontSize: '0.85rem', marginTop: 4 }}>
            Direct ingestion to Azure Blob Storage with automated verification & in-browser preview.
          </p>
        </div>
        <button
          className="btn btn-ghost"
          style={{ fontSize: '0.8rem', padding: '6px 12px' }}
          onClick={() => loadCustomerDocuments(customerId)}
          disabled={loadingCustomerDocs}
        >
          <RefreshCw size={14} className={loadingCustomerDocs ? 'spin' : ''} /> Refresh Documents
        </button>
      </div>

      {/* Tabs */}
      <div className="tabs" style={{ marginBottom: 20 }}>
        <button
          className={`tab-btn${tab === 'upload' ? ' active' : ''}`}
          onClick={() => setTab('upload')}
          style={{ display: 'flex', alignItems: 'center', gap: 8 }}
        >
          <FolderUp size={16} /> Upload Document
        </button>
        <button
          className={`tab-btn${tab === 'fetch' ? ' active' : ''}`}
          onClick={() => {
            setTab('fetch');
            if (docId && !docResult) doFetch(docId);
          }}
          style={{ display: 'flex', alignItems: 'center', gap: 8 }}
        >
          <Eye size={16} /> Fetch & View Document (In-Browser)
        </button>
      </div>

      {/* ── TAB 1: UPLOAD DOCUMENT ── */}
      {tab === 'upload' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
            <FolderUp size={18} color="var(--accent)" /> Upload KYC / Supporting Document to Azure
          </div>

          {uploadError && (
            <div className="error-box" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
              <AlertCircle size={18} /> {uploadError}
            </div>
          )}

          {uploadResult && (
            <div className="success-box" style={{ marginBottom: 24 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700, fontSize: '0.95rem' }}>
                <CheckCircle size={18} color="var(--green)" /> Document Successfully Uploaded to Azure Blob Storage!
              </div>
              <div className="detail-grid mt-3" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
                <div>
                  <span className="text-muted" style={{ fontSize: '0.75rem' }}>DOCUMENT ID</span>
                  <div className="font-mono" style={{ fontWeight: 700, color: 'var(--accent)' }}>
                    {uploadResult.documentId || uploadResult.id}
                  </div>
                </div>
                <div>
                  <span className="text-muted" style={{ fontSize: '0.75rem' }}>TYPE</span>
                  <div>{uploadResult.documentType || uploadResult.docType}</div>
                </div>
                <div>
                  <span className="text-muted" style={{ fontSize: '0.75rem' }}>STATUS</span>
                  <span className="badge badge-approved">{uploadResult.status || 'UPLOADED'}</span>
                </div>
              </div>

              <div className="mt-4 flex-row" style={{ gap: 10 }}>
                <button
                  className="btn btn-primary"
                  style={{ fontSize: '0.82rem', padding: '6px 16px' }}
                  onClick={() => {
                    const idToFetch = String(uploadResult.documentId || uploadResult.id);
                    setDocId(idToFetch);
                    setTab('fetch');
                    doFetch(idToFetch);
                  }}
                >
                  <Eye size={15} /> View in Browser Viewer
                </button>
              </div>
            </div>
          )}

          <div className="form-grid">
            <div className="form-group">
              <label className="form-label">Customer ID *</label>
              <input
                className="form-input"
                value={customerId}
                onChange={e => {
                  setCustomerId(e.target.value);
                  loadCustomerDocuments(e.target.value);
                }}
                placeholder="e.g. CUST-3"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Application ID (optional)</label>
              <input
                className="form-input"
                value={appId}
                onChange={e => setAppId(e.target.value)}
                placeholder="e.g. APP-1D2BDA62 (or leave empty)"
              />
            </div>

            <div className="form-group">
              <label className="form-label">Document Type *</label>
              <select className="form-select" value={docType} onChange={e => setDocType(e.target.value)}>
                {docTypesList.length > 0 ? (
                  docTypesList.map(t => (
                    <option key={t.typeCode || t.code} value={t.typeCode || t.code}>
                      {t.categoryName || t.description || t.typeCode} ({t.typeCode || t.code})
                    </option>
                  ))
                ) : (
                  <>
                    <option value="IDENTITY_PROOF">Identity Proof (Aadhaar / PAN)</option>
                    <option value="INCOME_PROOF">Income Proof (Salary Slips / Form 16)</option>
                    <option value="ADDRESS_PROOF">Address Proof (Utility Bill / Passport)</option>
                    <option value="BANK_STATEMENT">Bank Statement (Latest 6 Months)</option>
                    <option value="PHOTOGRAPH">Photograph (Passport Size)</option>
                    <option value="EMPLOYMENT_PROOF">Employment Proof (Offer Letter / ID)</option>
                  </>
                )}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Document Display Name (optional)</label>
              <input
                className="form-input"
                value={docName}
                onChange={e => setDocName(e.target.value)}
                placeholder="e.g. PAN_Card_Front.pdf"
              />
            </div>
          </div>

          {/* File Selector Dropzone */}
          <div className="form-group mt-4">
            <label className="form-label">Select File (PDF, PNG, JPG, JPEG) *</label>
            <div
              style={{
                border: '2px dashed var(--border)',
                borderRadius: 'var(--radius)',
                padding: '24px 16px',
                textAlign: 'center',
                background: 'rgba(255, 255, 255, 0.02)',
                cursor: 'pointer'
              }}
              onClick={() => document.getElementById('file-input-el')?.click()}
            >
              <input
                id="file-input-el"
                type="file"
                accept=".pdf,.jpg,.jpeg,.png,.webp"
                onChange={handleFileChange}
                style={{ display: 'none' }}
              />
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                <FolderUp size={32} color="var(--accent)" />
                <div style={{ fontWeight: 600 }}>{file ? file.name : 'Click to Browse or Select a File'}</div>
                <div style={{ color: 'var(--muted)', fontSize: '0.8rem' }}>
                  {file
                    ? `${(file.size / 1024).toFixed(1)} KB — Ready to upload`
                    : 'Supported formats: PDF, JPG, JPEG, PNG, WEBP (Max 15MB)'}
                </div>
              </div>
            </div>
          </div>

          {/* Client-side Live Preview before upload */}
          {filePreviewUrl && (
            <div className="mt-4 p-4 card" style={{ background: 'rgba(0,0,0,0.3)', border: '1px solid var(--border)' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: 10, color: 'var(--muted)' }}>
                Selected File Preview:
              </div>
              {file?.type?.startsWith('image/') ? (
                <div style={{ textAlign: 'center' }}>
                  <img
                    src={filePreviewUrl}
                    alt="Preview"
                    style={{ maxHeight: 260, maxWidth: '100%', borderRadius: 8, border: '1px solid var(--border)' }}
                  />
                </div>
              ) : (
                <iframe
                  src={filePreviewUrl}
                  title="PDF Preview"
                  style={{ width: '100%', height: 300, border: 'none', borderRadius: 8 }}
                />
              )}
            </div>
          )}

          <div className="mt-6 flex-row" style={{ justifyContent: 'flex-end' }}>
            <button className="btn btn-primary" onClick={doUpload} disabled={uploading || !file}>
              {uploading ? (
                <>
                  <RefreshCw size={16} className="spin" /> Uploading to Azure Blob Storage…
                </>
              ) : (
                <>
                  <FolderUp size={16} /> Upload Document
                </>
              )}
            </button>
          </div>
        </div>
      )}

      {/* ── TAB 2: FETCH & IN-BROWSER VIEWER ── */}
      {tab === 'fetch' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
            <Search size={18} color="var(--accent)" /> Fetch Document & In-Browser Viewer
          </div>

          {docError && (
            <div className="error-box" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
              <AlertCircle size={18} /> {docError}
            </div>
          )}

          <div className="flex-row" style={{ alignItems: 'flex-end', gap: 12, marginBottom: 20 }}>
            <div className="form-group" style={{ flex: 1, marginBottom: 0 }}>
              <label className="form-label">Document ID</label>
              <input
                className="form-input"
                value={docId}
                onChange={e => setDocId(e.target.value)}
                placeholder="Enter Document ID (e.g. 1, 2, 3, etc.)"
                onKeyDown={e => e.key === 'Enter' && doFetch()}
              />
            </div>
            <button className="btn btn-primary" onClick={() => doFetch()} disabled={docLoading}>
              {docLoading ? (
                <>
                  <RefreshCw size={16} className="spin" /> Fetching…
                </>
              ) : (
                <>
                  <Eye size={16} /> Fetch & View
                </>
              )}
            </button>
          </div>

          {/* Customer Documents Quick List */}
          {customerDocs.length > 0 && (
            <div className="mb-6 p-4 card" style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border)' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6, color: 'var(--muted)' }}>
                <Layers size={14} /> Available Documents for {customerId}:
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {customerDocs.map(doc => (
                  <button
                    key={doc.documentId}
                    className={`btn ${String(docId) === String(doc.documentId) ? 'btn-primary' : 'btn-ghost'}`}
                    style={{ fontSize: '0.75rem', padding: '4px 10px' }}
                    onClick={() => {
                      setDocId(String(doc.documentId));
                      doFetch(doc.documentId);
                    }}
                  >
                    {isPdf(doc) ? '📄' : '🖼️'} ID: {doc.documentId} — {doc.documentName || doc.originalFileName || doc.documentType}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Document Metadata Display */}
          {docResult && (
            <div className="mt-2">
              <div style={{ fontWeight: 700, fontSize: '1.05rem', marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                <FileCheck size={18} color="var(--green)" /> Document Metadata (ID: {docResult.documentId || docResult.id})
              </div>

              <div className="detail-grid mb-6">
                {[
                  ['Document ID', docResult.documentId || docResult.id],
                  ['Customer ID', docResult.customerId || '—'],
                  ['Application ID', docResult.applicationId || '—'],
                  ['Document Type', docResult.documentType || docResult.docType || '—'],
                  ['File Name', docResult.originalFileName || docResult.fileName || docResult.documentName || '—'],
                  ['Content Type', docResult.contentType || docResult.mimeType || 'application/pdf'],
                  ['File Size', docResult.fileSizeBytes ? `${(docResult.fileSizeBytes / 1024).toFixed(1)} KB (${docResult.fileSizeBytes} bytes)` : '—'],
                  ['Status', docResult.status || 'UPLOADED'],
                  ['Uploaded At', docResult.createdAt ? new Date(docResult.createdAt).toLocaleString() : '—'],
                ].map(([label, val]) => (
                  <div key={label} className="detail-field">
                    <div className="detail-field-label">{label}</div>
                    <div className="detail-field-value font-mono" style={{ fontSize: '.82rem' }}>
                      {val}
                    </div>
                  </div>
                ))}
              </div>

              {/* Action Toolbar */}
              <div
                className="flex-row"
                style={{
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '12px 16px',
                  background: 'rgba(255,255,255,0.03)',
                  borderRadius: 8,
                  marginBottom: 16,
                  border: '1px solid var(--border)'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 600, fontSize: '0.9rem' }}>
                  {isPdf(docResult) ? <FileText size={18} color="var(--accent)" /> : <ImageIcon size={18} color="var(--accent2)" />}
                  <span>In-Browser Document Viewer</span>
                </div>

                <div className="flex-row" style={{ gap: 8 }}>
                  {previewBlobUrl && (
                    <>
                      <a
                        href={previewBlobUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="btn btn-ghost"
                        style={{ fontSize: '0.8rem', padding: '6px 12px' }}
                      >
                        <ExternalLink size={14} /> Open in New Tab
                      </a>
                      <a
                        href={previewBlobUrl}
                        download={docResult.originalFileName || docResult.fileName || 'document.pdf'}
                        className="btn btn-primary"
                        style={{ fontSize: '0.8rem', padding: '6px 12px' }}
                      >
                        <Download size={14} /> Download
                      </a>
                    </>
                  )}
                </div>
              </div>

              {/* IN-BROWSER VIEWER FOR PDF OR IMAGE ON SAME PAGE */}
              <div
                className="document-browser-frame card"
                style={{
                  background: '#040711',
                  borderRadius: 8,
                  padding: 12,
                  border: '1px solid var(--border)',
                  overflow: 'hidden'
                }}
              >
                {previewBlobUrl ? (
                  isPdf(docResult) ? (
                    <div style={{ width: '100%' }}>
                      <iframe
                        src={previewBlobUrl}
                        title={`PDF Preview: ${docResult.documentId}`}
                        style={{
                          width: '100%',
                          height: '650px',
                          borderRadius: 6,
                          border: '1px solid var(--border)',
                          background: '#ffffff'
                        }}
                      />
                    </div>
                  ) : isImage(docResult) ? (
                    <div style={{ textAlign: 'center', padding: '20px 0' }}>
                      <img
                        src={previewBlobUrl}
                        alt={docResult.originalFileName || 'Document Image'}
                        style={{
                          maxWidth: '100%',
                          maxHeight: '650px',
                          objectFit: 'contain',
                          borderRadius: 8,
                          boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
                          border: '1px solid var(--border)'
                        }}
                      />
                    </div>
                  ) : (
                    <div style={{ width: '100%' }}>
                      <iframe
                        src={previewBlobUrl}
                        title={`Document View: ${docResult.documentId}`}
                        style={{
                          width: '100%',
                          height: '550px',
                          borderRadius: 6,
                          border: 'none'
                        }}
                      />
                    </div>
                  )
                ) : (
                  <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--muted)' }}>
                    <RefreshCw size={24} className="spin" style={{ margin: '0 auto 12px' }} />
                    <div>Loading document into browser preview…</div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
