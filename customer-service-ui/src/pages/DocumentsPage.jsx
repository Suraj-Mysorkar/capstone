import React, { useState, useEffect } from 'react';
import {
  uploadDocument,
  fetchDocumentTypes,
  fetchDocumentBlobUrl,
  fetchCustomerDocuments,
  fetchApplications,
  deleteDocumentById,
} from '../services/loanApi';
import { useSession, docCustomerId } from '../lib/session';
import {
  FolderUp, FileText, Image as ImageIcon, ExternalLink, Download,
  Eye, CheckCircle, AlertCircle, RefreshCw, Layers, X, AlertTriangle, ArrowRight,
} from 'lucide-react';

export default function DocumentsPage() {
  const { session } = useSession();
  const activeCustomerId = docCustomerId(session);

  const [tab, setTab] = useState('list');
  const [docTypesList, setDocTypesList] = useState([]);

  const [customerDocs, setCustomerDocs] = useState([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [activeApps, setActiveApps] = useState([]);

  const [selectedDoc, setSelectedDoc] = useState(null);
  const [docLoading, setDocLoading] = useState(false);
  const [previewBlobUrl, setPreviewBlobUrl] = useState(null);
  const [docError, setDocError] = useState('');
  const [notice, setNotice] = useState('');

  const [uploadAppId, setUploadAppId] = useState('');
  const [uploadDocType, setUploadDocType] = useState('IDENTITY_PROOF');
  const [uploadDocName, setUploadDocName] = useState('');
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadFilePreviewUrl, setUploadFilePreviewUrl] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [uploadError, setUploadError] = useState('');

  useEffect(() => {
    fetchDocumentTypes().then((types) => {
      if (Array.isArray(types) && types.length > 0) {
        setDocTypesList(types);
        setUploadDocType(types[0].typeCode || types[0].code || 'IDENTITY_PROOF');
      }
    });
  }, []);

  const selectAndPreviewDoc = async (doc) => {
    if (!doc) return;
    setSelectedDoc(doc);
    setDocLoading(true);
    try {
      const blobUrl = await fetchDocumentBlobUrl(doc.documentId || doc.id, doc.contentType);
      setPreviewBlobUrl(blobUrl);
    } catch (e) {
      setPreviewBlobUrl(null);
    } finally {
      setDocLoading(false);
    }
  };

  const loadDocs = async () => {
    setLoadingDocs(true);
    setDocError('');
    try {
      if (activeCustomerId) {
        const list = await fetchCustomerDocuments(activeCustomerId);
        const arr = Array.isArray(list) ? list : [];
        setCustomerDocs(arr);
        if (arr.length > 0) selectAndPreviewDoc(arr[0]);
        else { setSelectedDoc(null); setPreviewBlobUrl(null); }
      }
      const email = (session?.email || '').toLowerCase();
      const apps = await fetchApplications();
      const mine = (Array.isArray(apps) ? apps : []).filter(
        (a) => (a.customerEmail || '').toLowerCase() === email || (activeCustomerId && a.customerId === activeCustomerId)
      );
      setActiveApps(mine);
    } catch (e) {
      setDocError(e.message || 'Failed to load your documents.');
    } finally {
      setLoadingDocs(false);
    }
  };

  useEffect(() => {
    loadDocs();
    // eslint-disable-next-line
  }, [activeCustomerId, session?.email]);

  const handleDelete = async (e, doc) => {
    e.stopPropagation();
    const docId = doc.documentId || doc.id;
    const title = doc.documentName || doc.originalFileName || `Document ${docId}`;
    if (!window.confirm(`Delete "${title}" (ID: ${docId})? This removes the file permanently.`)) return;
    try {
      await deleteDocumentById(docId);
      const remaining = customerDocs.filter((d) => String(d.documentId || d.id) !== String(docId));
      setCustomerDocs(remaining);
      setNotice(`Deleted "${title}".`);
      if (selectedDoc && String(selectedDoc.documentId || selectedDoc.id) === String(docId)) {
        if (remaining.length > 0) selectAndPreviewDoc(remaining[0]);
        else { setSelectedDoc(null); setPreviewBlobUrl(null); }
      }
    } catch (err) {
      alert(`Failed to delete: ${err.message}`);
    }
  };

  const handleFileChange = (e) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setUploadFile(f);
    if (!uploadDocName) setUploadDocName(f.name);
    try { setUploadFilePreviewUrl(URL.createObjectURL(f)); } catch {}
  };

  const doUpload = async () => {
    if (!activeCustomerId || !uploadFile) {
      setUploadError('A file is required.');
      return;
    }
    setUploading(true);
    setUploadError('');
    setUploadResult(null);
    const fd = new FormData();
    fd.append('customerId', activeCustomerId);
    if (uploadAppId.trim()) fd.append('applicationId', uploadAppId.trim());
    fd.append('documentType', uploadDocType);
    fd.append('docType', uploadDocType);
    if (uploadDocName.trim()) fd.append('documentName', uploadDocName.trim());
    fd.append('file', uploadFile);
    try {
      const res = await uploadDocument(fd);
      setUploadResult(res);
      await loadDocs();
    } catch (e) {
      setUploadError(e.message || 'Failed to upload document.');
    } finally {
      setUploading(false);
    }
  };

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
    return type.includes('image') || /\.(jpg|jpeg|png|webp|gif)$/.test(name);
  };

  return (
    <div className="page" style={{ maxWidth: '100%', padding: '16px 24px', height: 'calc(100vh - 68px)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <div className="flex-row" style={{ justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 800, margin: 0 }}>My Documents</h2>
            <p style={{ color: 'var(--muted)', fontSize: '0.78rem', margin: 0 }}>
              Upload and manage your KYC &amp; income proofs. Filed under <span className="font-mono">{activeCustomerId || '—'}</span>.
            </p>
          </div>
          <div className="tabs" style={{ margin: 0 }}>
            <button className={`tab-btn${tab === 'list' ? ' active' : ''}`} onClick={() => setTab('list')} style={{ padding: '6px 14px', fontSize: '.82rem', display: 'flex', gap: 6, alignItems: 'center' }}>
              <Eye size={15} /> My Documents
            </button>
            <button className={`tab-btn${tab === 'upload' ? ' active' : ''}`} onClick={() => setTab('upload')} style={{ padding: '6px 14px', fontSize: '.82rem', display: 'flex', gap: 6, alignItems: 'center' }}>
              <FolderUp size={15} /> Upload
            </button>
          </div>
        </div>
        <button className="btn btn-ghost" style={{ fontSize: '.78rem', padding: '5px 12px' }} onClick={loadDocs} disabled={loadingDocs}>
          <RefreshCw size={13} className={loadingDocs ? 'spin' : ''} /> Refresh
        </button>
      </div>

      {/* MANAGER REQUESTED DOCUMENTS BANNER */}
      {activeApps.some((a) => a.status === 'DOCUMENT_REVIEW_PENDING' || a.status === 'MANUAL_REVIEW_REQUIRED') && (
        <div style={{ marginBottom: 12, padding: '12px 16px', borderRadius: 10, background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.12) 0%, rgba(13, 20, 44, 0.95) 100%)', border: '1.5px solid #f59e0b', flexShrink: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <AlertTriangle color="#f59e0b" size={20} />
              <div>
                <div style={{ fontSize: '.88rem', fontWeight: 700, color: '#fbbf24' }}>
                  Action Required: Documents Requested by Your Loan Manager ({activeApps[0]?.assignedManagerName || (activeApps[0]?.assignedManager === 'markj' ? 'Mark Johnson' : activeApps[0]?.assignedManager || 'Dedicated Officer')})
                </div>
                <div style={{ fontSize: '.78rem', color: 'var(--muted)', marginTop: 2 }}>
                  Application: <strong style={{ color: '#fff' }}>{activeApps[0]?.applicationId}</strong> · Please upload the required documents to advance your application:
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {[
                { label: '🪪 Identity Proof (Aadhaar/Passport)', type: 'IDENTITY_PROOF' },
                { label: '💵 Income Proof (Salary Slips)', type: 'INCOME_PROOF' },
                { label: '🏦 Bank Statement (Last 6 Months)', type: 'BANK_STATEMENT' },
              ].map((btn) => (
                <button
                  key={btn.type}
                  className="btn btn-ghost"
                  style={{ fontSize: '.75rem', padding: '4px 10px', background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(245, 158, 11, 0.4)', color: '#fff' }}
                  onClick={() => {
                    setUploadDocType(btn.type);
                    if (activeApps[0]?.applicationId) setUploadAppId(activeApps[0].applicationId);
                    setTab('upload');
                  }}
                >
                  {btn.label} →
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {tab === 'list' && (
        <div style={{ display: 'grid', gridTemplateColumns: '420px 1fr', gap: 16, flex: 1, minHeight: 0, overflow: 'hidden' }}>
          <div className="card p-4" style={{ display: 'flex', flexDirection: 'column', gap: 12, overflowY: 'auto' }}>
            {notice && <div className="success-box" style={{ margin: 0, padding: '8px 12px', fontSize: '.8rem' }}>{notice}</div>}
            {docError && (
              <div className="error-box" style={{ margin: 0, display: 'flex', gap: 6, padding: '8px 12px', fontSize: '.8rem' }}>
                <AlertCircle size={15} /> {docError}
              </div>
            )}
            <div style={{ fontSize: '.8rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Layers size={14} color="var(--accent)" /> Documents ({customerDocs.length})
            </div>
            {customerDocs.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {customerDocs.map((doc) => {
                  const isSel = selectedDoc && String(selectedDoc.documentId || selectedDoc.id) === String(doc.documentId || doc.id);
                  return (
                    <div key={doc.documentId || doc.id} onClick={() => selectAndPreviewDoc(doc)}
                      style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 10px', borderRadius: 6, cursor: 'pointer', gap: 8, background: isSel ? 'rgba(0,210,255,0.12)' : 'rgba(255,255,255,0.02)', border: isSel ? '1px solid var(--accent)' : '1px solid var(--border)' }}>
                      <button title="Delete" onClick={(e) => handleDelete(e, doc)}
                        style={{ background: 'rgba(239,68,68,0.15)', border: '1px solid rgba(239,68,68,0.3)', color: '#ef4444', borderRadius: '50%', width: 20, height: 20, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', flexShrink: 0, padding: 0 }}>
                        <X size={11} strokeWidth={2.5} />
                      </button>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden', flex: 1 }}>
                        <span style={{ fontSize: '1rem' }}>{isPdf(doc) ? '📄' : '🖼️'}</span>
                        <div style={{ overflow: 'hidden' }}>
                          <div style={{ fontSize: '.78rem', fontWeight: 600, color: isSel ? 'var(--accent)' : 'var(--text)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            ID: {doc.documentId || doc.id} — {doc.documentName || doc.originalFileName || doc.documentType}
                          </div>
                          <div style={{ fontSize: '.7rem', color: 'var(--muted)' }}>
                            {doc.documentType || doc.docType}{doc.fileSizeBytes ? ` • ${(doc.fileSizeBytes / 1024).toFixed(1)} KB` : ''}
                          </div>
                        </div>
                      </div>
                      <span className={`badge ${doc.status === 'VERIFIED' || doc.status === 'APPROVED' ? 'badge-approved' : doc.status === 'REJECTED' || doc.status === 'ACTION_REQUIRED' ? 'badge-rejected' : 'badge-under-review'}`} style={{ fontSize: '.68rem', flexShrink: 0 }}>
                        {doc.status || 'UPLOADED'}
                      </span>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div style={{ padding: 12, textAlign: 'center', color: 'var(--muted)', fontSize: '.78rem', border: '1px dashed var(--border)', borderRadius: 6 }}>
                No documents yet. Use the Upload tab.
              </div>
            )}
          </div>

          <div className="card" style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#040711', overflow: 'hidden' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 16px', background: 'rgba(255,255,255,0.03)', borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '.88rem', fontWeight: 600 }}>
                {isPdf(selectedDoc) ? <FileText size={17} color="var(--accent)" /> : <ImageIcon size={17} color="var(--accent2)" />}
                <span>{selectedDoc ? `${selectedDoc.documentName || selectedDoc.originalFileName || 'Document'} (ID: ${selectedDoc.documentId || selectedDoc.id})` : 'Document Viewer'}</span>
                {selectedDoc && (
                  <span className={`badge ${selectedDoc.status === 'VERIFIED' || selectedDoc.status === 'APPROVED' ? 'badge-approved' : selectedDoc.status === 'REJECTED' || selectedDoc.status === 'ACTION_REQUIRED' ? 'badge-rejected' : 'badge-under-review'}`} style={{ fontSize: '.72rem', marginLeft: 6 }}>
                    {selectedDoc.status === 'VERIFIED' || selectedDoc.status === 'APPROVED' ? '✅ Approved' : selectedDoc.status === 'REJECTED' || selectedDoc.status === 'ACTION_REQUIRED' ? '❌ Rejected' : '⏳ ' + (selectedDoc.status || 'Under Review')}
                  </span>
                )}
              </div>
              {selectedDoc && previewBlobUrl && (
                <div className="flex-row" style={{ gap: 6 }}>
                  {(selectedDoc.status === 'REJECTED' || selectedDoc.status === 'ACTION_REQUIRED') && (
                    <button
                      className="btn btn-primary"
                      style={{ fontSize: '.72rem', padding: '3px 10px', background: '#ef4444', borderColor: '#ef4444', color: '#fff' }}
                      onClick={() => {
                        setUploadDocType(selectedDoc.documentType || 'IDENTITY_PROOF');
                        if (selectedDoc.applicationId) setUploadAppId(selectedDoc.applicationId);
                        setTab('upload');
                      }}
                    >
                      <FolderUp size={12} /> Re-upload Image
                    </button>
                  )}
                  <a href={previewBlobUrl} target="_blank" rel="noopener noreferrer" className="btn btn-ghost" style={{ fontSize: '.72rem', padding: '3px 8px' }}>
                    <ExternalLink size={12} /> Open
                  </a>
                  <a href={previewBlobUrl} download={selectedDoc.originalFileName || 'document'} className="btn btn-primary" style={{ fontSize: '.72rem', padding: '3px 8px' }}>
                    <Download size={12} /> Download
                  </a>
                </div>
              )}
            </div>

            {/* STATUS ALERT NOTIFICATION BANNER */}
            {selectedDoc && (selectedDoc.status === 'REJECTED' || selectedDoc.status === 'ACTION_REQUIRED') && (
              <div style={{ padding: '10px 16px', background: 'rgba(239, 68, 68, 0.15)', borderBottom: '1px solid rgba(239, 68, 68, 0.4)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexShrink: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <AlertTriangle color="#ef4444" size={18} />
                  <div>
                    <strong style={{ fontSize: '.84rem', color: '#ef4444' }}>This document was Rejected by your manager</strong>
                    <div style={{ fontSize: '.76rem', color: '#fca5a5', marginTop: 1 }}>
                      Remarks: {selectedDoc.remarks || 'Document image unreadable or invalid. Please upload a new image/document.'}
                    </div>
                  </div>
                </div>
                <button
                  className="btn btn-primary"
                  style={{ fontSize: '.75rem', padding: '4px 10px', background: '#ef4444', borderColor: '#dc2626', color: '#fff', whiteSpace: 'nowrap' }}
                  onClick={() => {
                    setUploadDocType(selectedDoc.documentType || 'IDENTITY_PROOF');
                    if (selectedDoc.applicationId) setUploadAppId(selectedDoc.applicationId);
                    setTab('upload');
                  }}
                >
                  🔄 Upload New Image / Document →
                </button>
              </div>
            )}

            {selectedDoc && (selectedDoc.status === 'VERIFIED' || selectedDoc.status === 'APPROVED') && (
              <div style={{ padding: '8px 16px', background: 'rgba(16, 185, 129, 0.12)', borderBottom: '1px solid rgba(16, 185, 129, 0.3)', display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                <CheckCircle color="#10b981" size={16} />
                <span style={{ fontSize: '.82rem', color: '#10b981', fontWeight: 600 }}>
                  Document Verified &amp; Approved: No further action required.
                </span>
              </div>
            )}

            <div style={{ flex: 1, minHeight: 0, position: 'relative', overflow: 'hidden' }}>
              {docLoading ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--muted)' }}>
                  <RefreshCw size={32} className="spin" style={{ marginBottom: 12, color: 'var(--accent)' }} />
                  <div style={{ fontSize: '.9rem' }}>Loading document…</div>
                </div>
              ) : previewBlobUrl ? (
                isImage(selectedDoc) ? (
                  <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
                    <img src={previewBlobUrl} alt={selectedDoc?.originalFileName || 'Document'} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', borderRadius: 8, border: '1px solid var(--border)' }} />
                  </div>
                ) : (
                  <iframe src={previewBlobUrl} title="Document Preview" style={{ width: '100%', height: '100%', border: 'none', background: '#fff' }} />
                )
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--muted)', textAlign: 'center', padding: 20 }}>
                  <FileText size={48} strokeWidth={1} style={{ marginBottom: 12, opacity: 0.5 }} />
                  <div style={{ fontSize: '.95rem', fontWeight: 600, color: 'var(--text)' }}>No Document Selected</div>
                  <div style={{ fontSize: '.8rem', marginTop: 4 }}>Select a document on the left to preview it.</div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {tab === 'upload' && (
        <div style={{ display: 'grid', gridTemplateColumns: '460px 1fr', gap: 16, flex: 1, minHeight: 0, overflow: 'hidden' }}>
          <div className="card p-4" style={{ overflowY: 'auto' }}>
            <div className="card-header-title" style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 6, fontSize: '.95rem' }}>
              <FolderUp size={16} color="var(--accent)" /> Upload Supporting Document
            </div>
            {uploadError && (
              <div className="error-box" style={{ display: 'flex', gap: 6, marginBottom: 12, padding: '8px 12px', fontSize: '.8rem' }}>
                <AlertCircle size={15} /> {uploadError}
              </div>
            )}
            {uploadResult && (
              <div className="success-box" style={{ marginBottom: 16, padding: '10px 14px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, fontSize: '.88rem' }}>
                  <CheckCircle size={16} color="var(--green)" /> Uploaded!
                </div>
                <div style={{ fontSize: '.8rem', marginTop: 6 }}>
                  Document ID: <strong className="font-mono" style={{ color: 'var(--accent)' }}>{uploadResult.documentId || uploadResult.id}</strong>
                </div>
                <button className="btn btn-primary" style={{ fontSize: '.78rem', padding: '5px 12px', marginTop: 8 }} onClick={() => setTab('list')}>
                  <Eye size={13} /> View in My Documents
                </button>
              </div>
            )}
            <div className="form-grid" style={{ gridTemplateColumns: '1fr', gap: 10 }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Filed under (your customer ID)</label>
                <input className="form-input" value={activeCustomerId} readOnly style={{ opacity: 0.8, background: 'rgba(255,255,255,0.04)' }} />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Application ID (optional)</label>
                <input className="form-input" value={uploadAppId} onChange={(e) => setUploadAppId(e.target.value)} placeholder="e.g. APP-1D2BDA62" style={{ padding: '7px 12px', fontSize: '.85rem' }} />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Document Type *</label>
                <select className="form-select" value={uploadDocType} onChange={(e) => setUploadDocType(e.target.value)} style={{ padding: '7px 12px', fontSize: '.85rem' }}>
                  {docTypesList.length > 0 ? docTypesList.map((t) => (
                    <option key={t.typeCode || t.code} value={t.typeCode || t.code}>
                      {t.categoryName || t.description || t.typeCode} ({t.typeCode || t.code})
                    </option>
                  )) : (
                    <>
                      <option value="IDENTITY_PROOF">Identity Proof</option>
                      <option value="INCOME_PROOF">Income Proof</option>
                      <option value="ADDRESS_PROOF">Address Proof</option>
                      <option value="BANK_STATEMENT">Bank Statement</option>
                      <option value="PHOTOGRAPH">Photograph</option>
                      <option value="EMPLOYMENT_PROOF">Employment Proof</option>
                    </>
                  )}
                </select>
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Display Name (optional)</label>
                <input className="form-input" value={uploadDocName} onChange={(e) => setUploadDocName(e.target.value)} placeholder="e.g. PAN_Card.pdf" style={{ padding: '7px 12px', fontSize: '.85rem' }} />
              </div>
            </div>
            <div className="form-group mt-3" style={{ marginBottom: 0 }}>
              <label className="form-label">Select File (PDF, PNG, JPG) *</label>
              <div style={{ border: '2px dashed var(--border)', borderRadius: 'var(--radius)', padding: '16px 12px', textAlign: 'center', background: 'rgba(255,255,255,0.02)', cursor: 'pointer' }}
                onClick={() => document.getElementById('cs-file-input')?.click()}>
                <input id="cs-file-input" type="file" accept=".pdf,.jpg,.jpeg,.png,.webp" onChange={handleFileChange} style={{ display: 'none' }} />
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                  <FolderUp size={26} color="var(--accent)" />
                  <div style={{ fontWeight: 600, fontSize: '.85rem' }}>{uploadFile ? uploadFile.name : 'Click to Browse'}</div>
                  <div style={{ color: 'var(--muted)', fontSize: '.75rem' }}>
                    {uploadFile ? `${(uploadFile.size / 1024).toFixed(1)} KB — ready` : 'PDF, JPG, PNG, WEBP (Max 15MB)'}
                  </div>
                </div>
              </div>
            </div>
            <div className="mt-4">
              <button className="btn btn-primary" onClick={doUpload} disabled={uploading || !uploadFile} style={{ width: '100%', justifyContent: 'center', padding: '9px 16px' }}>
                {uploading ? <><RefreshCw size={15} className="spin" /> Uploading…</> : <><FolderUp size={15} /> Upload Document</>}
              </button>
            </div>
          </div>

          <div className="card" style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#040711', overflow: 'hidden' }}>
            <div style={{ padding: '10px 16px', background: 'rgba(255,255,255,0.03)', borderBottom: '1px solid var(--border)', fontSize: '.88rem', fontWeight: 600, color: 'var(--muted)', flexShrink: 0 }}>
              Selected File Preview
            </div>
            <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
              {uploadFilePreviewUrl ? (
                uploadFile?.type?.startsWith('image/') ? (
                  <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
                    <img src={uploadFilePreviewUrl} alt="Preview" style={{ maxHeight: '100%', maxWidth: '100%', borderRadius: 8, border: '1px solid var(--border)' }} />
                  </div>
                ) : (
                  <iframe src={uploadFilePreviewUrl} title="Preview" style={{ width: '100%', height: '100%', border: 'none', background: '#fff' }} />
                )
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--muted)', textAlign: 'center', padding: 20 }}>
                  <FolderUp size={48} strokeWidth={1} style={{ marginBottom: 12, opacity: 0.5 }} />
                  <div style={{ fontSize: '.95rem', fontWeight: 600, color: 'var(--text)' }}>No File Selected</div>
                  <div style={{ fontSize: '.8rem', marginTop: 4 }}>Choose a file to see a preview.</div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
