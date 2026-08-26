import React, { useState } from 'react';
import { uploadDocument, fetchDocumentById } from '../services/api';
import { FolderUp, Search } from 'lucide-react';

const DOC_TYPES = ['IDENTITY_PROOF','INCOME_PROOF','ADDRESS_PROOF','BANK_STATEMENT'];

export default function DocumentsPage() {
  const [tab, setTab] = useState('upload');

  // Upload state
  const [customerId, setCustomerId]   = useState('');
  const [appId, setAppId]             = useState('');
  const [docType, setDocType]         = useState('IDENTITY_PROOF');
  const [file, setFile]               = useState(null);
  const [uploading, setUploading]     = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [uploadError, setUploadError] = useState('');

  // Fetch state
  const [docId, setDocId]             = useState('');
  const [docResult, setDocResult]     = useState(null);
  const [docError, setDocError]       = useState('');
  const [docLoading, setDocLoading]   = useState(false);

  const doUpload = async () => {
    if (!customerId || !file) { setUploadError('Customer ID and file are required.'); return; }
    setUploading(true); setUploadError(''); setUploadResult(null);
    const fd = new FormData();
    fd.append('customerId', customerId);
    if (appId) fd.append('applicationId', appId);
    fd.append('docType', docType);
    fd.append('file', file);
    try {
      const res = await uploadDocument(fd);
      setUploadResult(res);
    } catch (e) { setUploadError(e.message); }
    finally { setUploading(false); }
  };

  const doFetch = async () => {
    if (!docId) { setDocError('Enter a document ID.'); return; }
    setDocLoading(true); setDocError(''); setDocResult(null);
    try {
      const res = await fetchDocumentById(docId);
      if (res.documentId) setDocResult(res);
      else setDocError(res.message || 'Document not found.');
    } catch (e) { setDocError(e.message); }
    finally { setDocLoading(false); }
  };

  return (
    <div className="page">
      <div className="tabs">
        <button className={`tab-btn${tab==='upload' ? ' active':''}`} onClick={() => setTab('upload')}>
          Upload Document
        </button>
        <button className={`tab-btn${tab==='fetch' ? ' active':''}`} onClick={() => setTab('fetch')}>
          Fetch Document Metadata
        </button>
      </div>

      {tab === 'upload' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom:20, display:'flex', alignItems:'center', gap:8 }}>
            <FolderUp size={18}/> Upload KYC / Supporting Document
          </div>

          {uploadError && <div className="error-box">{uploadError}</div>}
          {uploadResult && (
            <div className="success-box">
              ✅ Document uploaded! <br/>
              <strong>Document ID:</strong> <span className="font-mono">{uploadResult.documentId}</span>
            </div>
          )}

          <div className="form-grid">
            <div className="form-group">
              <label className="form-label">Customer ID *</label>
              <input className="form-input" value={customerId} onChange={e=>setCustomerId(e.target.value)} placeholder="e.g. CUST-1001"/>
            </div>
            <div className="form-group">
              <label className="form-label">Application ID (optional)</label>
              <input className="form-input" value={appId} onChange={e=>setAppId(e.target.value)} placeholder="Leave empty to upload unlinked"/>
            </div>
            <div className="form-group">
              <label className="form-label">Document Type *</label>
              <select className="form-select" value={docType} onChange={e=>setDocType(e.target.value)}>
                {DOC_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g,' ')}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">File (PDF, JPG, PNG) *</label>
              <input
                className="form-input"
                type="file"
                accept=".pdf,.jpg,.jpeg,.png"
                onChange={e => setFile(e.target.files[0])}
                style={{ paddingTop:6 }}
              />
            </div>
          </div>

          <div className="mt-4">
            <button className="btn btn-primary" onClick={doUpload} disabled={uploading}>
              {uploading ? 'Uploading…' : 'Upload Document'}
            </button>
          </div>
        </div>
      )}

      {tab === 'fetch' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom:20, display:'flex', alignItems:'center', gap:8 }}>
            <Search size={18}/> Fetch Document Metadata
          </div>

          {docError && <div className="error-box">{docError}</div>}

          <div className="flex-row" style={{ alignItems:'flex-end', gap:12 }}>
            <div className="form-group" style={{ flex:1 }}>
              <label className="form-label">Document ID</label>
              <input className="form-input" value={docId} onChange={e=>setDocId(e.target.value)} placeholder="Enter document ID"/>
            </div>
            <button className="btn btn-primary" onClick={doFetch} disabled={docLoading} style={{ marginBottom:0 }}>
              {docLoading ? 'Fetching…' : 'Fetch'}
            </button>
          </div>

          {docResult && (
            <div className="detail-grid mt-4">
              {[
                ['Document ID',    docResult.documentId],
                ['Customer ID',    docResult.customerId],
                ['Application ID', docResult.applicationId || '—'],
                ['Document Type',  docResult.docType],
                ['File Name',      docResult.fileName],
                ['Content Type',   docResult.contentType],
                ['File Size',      docResult.fileSizeBytes + ' bytes'],
                ['Uploaded At',    new Date(docResult.uploadedAt).toLocaleString()],
              ].map(([l,v]) => (
                <div key={l} className="detail-field">
                  <div className="detail-field-label">{l}</div>
                  <div className="detail-field-value font-mono" style={{ fontSize:'.82rem' }}>{v}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
