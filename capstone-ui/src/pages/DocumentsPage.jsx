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
  Layers,
  Sparkles
} from 'lucide-react';

export default function DocumentsPage() {
  const [tab, setTab] = useState('fetch');
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
    // Auto-fetch default document ID 3
    doFetch('3');
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
    <div className="page" style={{ maxWidth: '100%', padding: '16px 24px', height: 'calc(100vh - 68px)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* Top Header Bar */}
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 800, margin: 0 }}>Document Management & Azure Storage</h2>
            <p style={{ color: 'var(--muted)', fontSize: '0.78rem', margin: 0 }}>
              Direct ingestion to Azure Blob Storage with automated verification & split-screen browser preview.
            </p>
          </div>

          <div className="tabs" style={{ margin: 0 }}>
            <button
              className={`tab-btn${tab === 'fetch' ? ' active' : ''}`}
              onClick={() => {
                setTab('fetch');
                if (docId && !docResult) doFetch(docId);
              }}
              style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 14px', fontSize: '0.82rem' }}
            >
              <Eye size={15} /> Fetch & View In-Browser
            </button>
            <button
              className={`tab-btn${tab === 'upload' ? ' active' : ''}`}
              onClick={() => setTab('upload')}
              style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 14px', fontSize: '0.82rem' }}
            >
              <FolderUp size={15} /> Upload Document
            </button>
          </div>
        </div>

        <button
          className="btn btn-ghost"
          style={{ fontSize: '0.78rem', padding: '5px 12px' }}
          onClick={() => loadCustomerDocuments(customerId)}
          disabled={loadingCustomerDocs}
        >
          <RefreshCw size={13} className={loadingCustomerDocs ? 'spin' : ''} /> Refresh Documents
        </button>
      </div>

      {/* ── TAB 1: FETCH & VIEW (SIDE-BY-SIDE SPLIT SCREEN) ── */}
      {tab === 'fetch' && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '380px 1fr',
            gap: 16,
            flex: 1,
            minHeight: 0,
            overflow: 'hidden'
          }}
        >
          {/* Left Column: Search, Available Docs, and Metadata */}
          <div
            className="card p-4"
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
              overflowY: 'auto',
              maxHeight: '100%'
            }}
          >
            <div className="card-header-title" style={{ fontSize: '0.95rem', display: 'flex', alignItems: 'center', gap: 6 }}>
              <Search size={16} color="var(--accent)" /> Search Document
            </div>

            {docError && (
              <div className="error-box" style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 12px', fontSize: '0.8rem' }}>
                <AlertCircle size={15} /> {docError}
              </div>
            )}

            <div className="flex-row" style={{ gap: 8 }}>
              <input
                className="form-input"
                value={docId}
                onChange={e => setDocId(e.target.value)}
                placeholder="Enter Document ID (e.g. 1, 2, 3)"
                onKeyDown={e => e.key === 'Enter' && doFetch()}
                style={{ fontSize: '0.85rem', padding: '7px 12px' }}
              />
              <button
                className="btn btn-primary"
                onClick={() => doFetch()}
                disabled={docLoading}
                style={{ padding: '7px 14px', fontSize: '0.85rem', flexShrink: 0 }}
              >
                {docLoading ? <RefreshCw size={14} className="spin" /> : <Eye size={14} />} Fetch
              </button>
            </div>

            {/* Quick Document Picker */}
            {customerDocs.length > 0 && (
              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--muted)', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 5 }}>
                  <Layers size={13} /> Available for {customerId}:
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 5, maxHeight: 130, overflowY: 'auto' }}>
                  {customerDocs.map(doc => (
                    <button
                      key={doc.documentId}
                      className={`btn ${String(docId) === String(doc.documentId) ? 'btn-primary' : 'btn-ghost'}`}
                      style={{
                        fontSize: '0.75rem',
                        padding: '6px 10px',
                        justifyContent: 'flex-start',
                        textAlign: 'left',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis'
                      }}
                      onClick={() => {
                        setDocId(String(doc.documentId));
                        doFetch(doc.documentId);
                      }}
                    >
                      <span>{isPdf(doc) ? '📄' : '🖼️'}</span>
                      <strong style={{ marginLeft: 4 }}>ID: {doc.documentId}</strong> — {doc.documentName || doc.originalFileName || doc.documentType}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Document Metadata Summary */}
            {docResult && (
              <div style={{ borderTop: '1px solid var(--border)', paddingTop: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
                <div style={{ fontWeight: 700, fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <FileCheck size={16} color="var(--green)" /> Metadata Details
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: '0.8rem' }}>
                  {[
                    ['Document ID', docResult.documentId || docResult.id],
                    ['Customer ID', docResult.customerId || '—'],
                    ['Application ID', docResult.applicationId || '—'],
                    ['Document Type', docResult.documentType || docResult.docType || '—'],
                    ['File Name', docResult.originalFileName || docResult.fileName || docResult.documentName || '—'],
                    ['Content Type', docResult.contentType || docResult.mimeType || 'application/pdf'],
                    ['File Size', docResult.fileSizeBytes ? `${(docResult.fileSizeBytes / 1024).toFixed(1)} KB` : '—'],
                    ['Status', docResult.status || 'UPLOADED'],
                    ['Uploaded At', docResult.createdAt ? new Date(docResult.createdAt).toLocaleString() : '—'],
                  ].map(([lbl, val]) => (
                    <div key={lbl} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                      <span style={{ color: 'var(--muted)' }}>{lbl}</span>
                      <span className="font-mono" style={{ fontWeight: 600, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', textAlign: 'right' }}>
                        {val}
                      </span>
                    </div>
                  ))}
                </div>

                {/* Actions */}
                {previewBlobUrl && (
                  <div className="flex-row" style={{ gap: 8, marginTop: 4 }}>
                    <a
                      href={previewBlobUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn btn-ghost"
                      style={{ fontSize: '0.78rem', padding: '6px 10px', flex: 1, justifyContent: 'center' }}
                    >
                      <ExternalLink size={13} /> Open Tab
                    </a>
                    <a
                      href={previewBlobUrl}
                      download={docResult.originalFileName || docResult.fileName || 'document.pdf'}
                      className="btn btn-primary"
                      style={{ fontSize: '0.78rem', padding: '6px 10px', flex: 1, justifyContent: 'center' }}
                    >
                      <Download size={13} /> Download
                    </a>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Right Column: Max-Space In-Browser Document Viewer */}
          <div
            className="card"
            style={{
              display: 'flex',
              flexDirection: 'column',
              height: '100%',
              background: '#040711',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius)',
              overflow: 'hidden'
            }}
          >
            {/* Viewer Top Status Bar */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '10px 16px',
                background: 'rgba(255,255,255,0.03)',
                borderBottom: '1px solid var(--border)',
                flexShrink: 0
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.88rem', fontWeight: 600 }}>
                {isPdf(docResult) ? <FileText size={17} color="var(--accent)" /> : <ImageIcon size={17} color="var(--accent2)" />}
                <span>
                  {docResult ? `${docResult.documentName || docResult.originalFileName || 'Document'} (ID: ${docResult.documentId || docResult.id})` : 'Live In-Browser Document Viewer'}
                </span>
              </div>

              {docResult && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span className="badge badge-approved" style={{ fontSize: '0.7rem' }}>
                    {docResult.documentType || docResult.docType || 'VERIFIED'}
                  </span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--muted)' }}>
                    {docResult.fileSizeBytes ? `${(docResult.fileSizeBytes / 1024).toFixed(1)} KB` : ''}
                  </span>
                </div>
              )}
            </div>

            {/* Viewer Content Viewport */}
            <div style={{ flex: 1, minHeight: 0, position: 'relative', overflow: 'hidden' }}>
              {docLoading ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--muted)' }}>
                  <RefreshCw size={32} className="spin" style={{ marginBottom: 12, color: 'var(--accent)' }} />
                  <div style={{ fontSize: '0.9rem', fontWeight: 500 }}>Streaming document from Azure Blob Storage…</div>
                </div>
              ) : previewBlobUrl ? (
                isPdf(docResult) ? (
                  <iframe
                    src={previewBlobUrl}
                    title={`PDF Preview: ${docResult?.documentId}`}
                    style={{
                      width: '100%',
                      height: '100%',
                      border: 'none',
                      background: '#ffffff'
                    }}
                  />
                ) : isImage(docResult) ? (
                  <div
                    style={{
                      width: '100%',
                      height: '100%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      padding: 20,
                      background: 'radial-gradient(ellipse at center, rgba(15,23,42,0.6), #020617)'
                    }}
                  >
                    <img
                      src={previewBlobUrl}
                      alt={docResult?.originalFileName || 'Document Image'}
                      style={{
                        maxWidth: '100%',
                        maxHeight: '100%',
                        objectFit: 'contain',
                        borderRadius: 8,
                        boxShadow: '0 12px 36px rgba(0,0,0,0.6)',
                        border: '1px solid var(--border)'
                      }}
                    />
                  </div>
                ) : (
                  <iframe
                    src={previewBlobUrl}
                    title={`Document View: ${docResult?.documentId}`}
                    style={{
                      width: '100%',
                      height: '100%',
                      border: 'none',
                      background: '#ffffff'
                    }}
                  />
                )
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--muted)', textAlign: 'center', padding: 20 }}>
                  <FileText size={48} strokeWidth={1} style={{ marginBottom: 12, opacity: 0.5 }} />
                  <div style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text)' }}>No Document Selected</div>
                  <div style={{ fontSize: '0.8rem', marginTop: 4 }}>Select a document from the left panel or enter a Document ID to preview.</div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── TAB 2: UPLOAD DOCUMENT (SPLIT SCREEN) ── */}
      {tab === 'upload' && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '480px 1fr',
            gap: 16,
            flex: 1,
            minHeight: 0,
            overflow: 'hidden'
          }}
        >
          {/* Left: Upload Form */}
          <div className="card p-4" style={{ overflowY: 'auto', maxHeight: '100%' }}>
            <div className="card-header-title" style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.95rem' }}>
              <FolderUp size={16} color="var(--accent)" /> Upload KYC / Supporting Document
            </div>

            {uploadError && (
              <div className="error-box" style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 12, padding: '8px 12px', fontSize: '0.8rem' }}>
                <AlertCircle size={15} /> {uploadError}
              </div>
            )}

            {uploadResult && (
              <div className="success-box" style={{ marginBottom: 16, padding: '10px 14px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, fontSize: '0.88rem' }}>
                  <CheckCircle size={16} color="var(--green)" /> Uploaded to Azure Blob Storage!
                </div>
                <div style={{ fontSize: '0.8rem', marginTop: 6 }}>
                  Document ID: <strong className="font-mono" style={{ color: 'var(--accent)' }}>{uploadResult.documentId || uploadResult.id}</strong>
                </div>
                <button
                  className="btn btn-primary"
                  style={{ fontSize: '0.78rem', padding: '5px 12px', marginTop: 8 }}
                  onClick={() => {
                    const idToFetch = String(uploadResult.documentId || uploadResult.id);
                    setDocId(idToFetch);
                    setTab('fetch');
                    doFetch(idToFetch);
                  }}
                >
                  <Eye size={13} /> View in Browser Viewer
                </button>
              </div>
            )}

            <div className="form-grid" style={{ gridTemplateColumns: '1fr', gap: 10 }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Customer ID *</label>
                <input
                  className="form-input"
                  value={customerId}
                  onChange={e => {
                    setCustomerId(e.target.value);
                    loadCustomerDocuments(e.target.value);
                  }}
                  placeholder="e.g. CUST-3"
                  style={{ padding: '7px 12px', fontSize: '0.85rem' }}
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Application ID (optional)</label>
                <input
                  className="form-input"
                  value={appId}
                  onChange={e => setAppId(e.target.value)}
                  placeholder="e.g. APP-1D2BDA62"
                  style={{ padding: '7px 12px', fontSize: '0.85rem' }}
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Document Type *</label>
                <select
                  className="form-select"
                  value={docType}
                  onChange={e => setDocType(e.target.value)}
                  style={{ padding: '7px 12px', fontSize: '0.85rem' }}
                >
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

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Document Display Name (optional)</label>
                <input
                  className="form-input"
                  value={docName}
                  onChange={e => setDocName(e.target.value)}
                  placeholder="e.g. PAN_Card_Front.pdf"
                  style={{ padding: '7px 12px', fontSize: '0.85rem' }}
                />
              </div>
            </div>

            {/* Dropzone */}
            <div className="form-group mt-3" style={{ marginBottom: 0 }}>
              <label className="form-label">Select File (PDF, PNG, JPG, JPEG) *</label>
              <div
                style={{
                  border: '2px dashed var(--border)',
                  borderRadius: 'var(--radius)',
                  padding: '16px 12px',
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
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                  <FolderUp size={26} color="var(--accent)" />
                  <div style={{ fontWeight: 600, fontSize: '0.85rem' }}>{file ? file.name : 'Click to Browse File'}</div>
                  <div style={{ color: 'var(--muted)', fontSize: '0.75rem' }}>
                    {file
                      ? `${(file.size / 1024).toFixed(1)} KB — Ready to upload`
                      : 'PDF, JPG, PNG, WEBP (Max 15MB)'}
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-4">
              <button
                className="btn btn-primary"
                onClick={doUpload}
                disabled={uploading || !file}
                style={{ width: '100%', justifyContent: 'center', padding: '9px 16px', fontSize: '0.88rem' }}
              >
                {uploading ? (
                  <>
                    <RefreshCw size={15} className="spin" /> Uploading to Azure Blob Storage…
                  </>
                ) : (
                  <>
                    <FolderUp size={15} /> Upload Document
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Right: Live Pre-Upload Preview */}
          <div
            className="card"
            style={{
              display: 'flex',
              flexDirection: 'column',
              height: '100%',
              background: '#040711',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius)',
              overflow: 'hidden'
            }}
          >
            <div
              style={{
                padding: '10px 16px',
                background: 'rgba(255,255,255,0.03)',
                borderBottom: '1px solid var(--border)',
                fontSize: '0.88rem',
                fontWeight: 600,
                color: 'var(--muted)',
                flexShrink: 0
              }}
            >
              Selected File Live Preview
            </div>

            <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
              {filePreviewUrl ? (
                file?.type?.startsWith('image/') ? (
                  <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
                    <img
                      src={filePreviewUrl}
                      alt="Preview"
                      style={{ maxHeight: '100%', maxWidth: '100%', borderRadius: 8, border: '1px solid var(--border)' }}
                    />
                  </div>
                ) : (
                  <iframe
                    src={filePreviewUrl}
                    title="PDF Preview"
                    style={{ width: '100%', height: '100%', border: 'none', background: '#fff' }}
                  />
                )
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--muted)', textAlign: 'center', padding: 20 }}>
                  <FolderUp size={48} strokeWidth={1} style={{ marginBottom: 12, opacity: 0.5 }} />
                  <div style={{ fontSize: '0.95rem', fontWeight: 600, color: 'var(--text)' }}>No File Selected Yet</div>
                  <div style={{ fontSize: '0.8rem', marginTop: 4 }}>Select a file on the left panel to see an instant pre-upload preview.</div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
