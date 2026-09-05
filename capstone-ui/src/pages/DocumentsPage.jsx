import React, { useState, useEffect } from 'react';
import { useSearchParams, useLocation } from 'react-router-dom';
import {
  uploadDocument,
  fetchDocumentById,
  fetchDocumentTypes,
  fetchDocumentBlobUrl,
  fetchCustomerDocuments,
  fetchApplicationDocuments,
  fetchApplications,
  updateDocumentStatus,
  deleteDocumentById
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
  User,
  Mail,
  ShieldCheck,
  AlertTriangle,
  X,
  Trash2,
  CheckSquare,
  Square,
  Filter
} from 'lucide-react';

export default function DocumentsPage() {
  const [searchParams] = useSearchParams();
  const location = useLocation();

  // Read customerId and applicationId from URL query params or router state
  const paramCustomerId = searchParams.get('customerId') || searchParams.get('custId') || location.state?.customerId || '';
  const paramAppId = searchParams.get('applicationId') || searchParams.get('appId') || searchParams.get('loanAccountNo') || location.state?.applicationId || '';

  const initialCustId = paramCustomerId || (paramAppId ? paramAppId : 'CUST-3');
  const initialAppId = paramAppId || 'ALL';

  const [tab, setTab] = useState('fetch');
  const [docTypesList, setDocTypesList] = useState([]);
  
  // Customer & Application Search & Documents list
  const [searchCustomerId, setSearchCustomerId] = useState(initialCustId);
  const [activeCustomerId, setActiveCustomerId] = useState(initialCustId);
  const [customerDocs, setCustomerDocs] = useState([]);
  const [allApps, setAllApps] = useState([]);
  const [customerApps, setCustomerApps] = useState([]);
  const [selectedAppId, setSelectedAppId] = useState(initialAppId);
  const [loadingCustomerDocs, setLoadingCustomerDocs] = useState(false);

  // Multi-Select Batch Actions State
  const [selectedDocIds, setSelectedDocIds] = useState(new Set());
  const [batchSubmitting, setBatchSubmitting] = useState(false);

  // Selected Document & In-Browser Viewer state
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [docLoading, setDocLoading] = useState(false);
  const [previewBlobUrl, setPreviewBlobUrl] = useState(null);
  const [docError, setDocError] = useState('');

  // Manager Document Review form state
  const [reviewStatus, setReviewStatus] = useState('VERIFIED');
  const [managerRemarks, setManagerRemarks] = useState('');
  const [managerId, setManagerId] = useState('senior.underwriter@bank.com');
  const [recipientEmail, setRecipientEmail] = useState('itsarpitgupta@gmail.com');
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [reviewSuccessMsg, setReviewSuccessMsg] = useState('');
  const [reviewErrorMsg, setReviewErrorMsg] = useState('');

  // Upload Tab state (auto-populated if navigated from application)
  const [uploadCustId, setUploadCustId] = useState(paramCustomerId || 'CUST-3');
  const [uploadAppId, setUploadAppId] = useState(paramAppId || '');
  const [uploadDocType, setUploadDocType] = useState('IDENTITY_PROOF');
  const [uploadDocName, setUploadDocName] = useState('');
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadFilePreviewUrl, setUploadFilePreviewUrl] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [uploadError, setUploadError] = useState('');

  // Load document types and customer documents on mount
  useEffect(() => {
    fetchDocumentTypes().then(types => {
      if (Array.isArray(types) && types.length > 0) {
        setDocTypesList(types);
        setUploadDocType(types[0].typeCode || types[0].code || 'IDENTITY_PROOF');
      }
    });

    fetchApplications().then(apps => {
      if (Array.isArray(apps)) setAllApps(apps);
    }).catch(() => {});

    if (paramCustomerId || paramAppId) {
      const searchTarget = paramCustomerId || paramAppId;
      handleSearchCustomer(searchTarget, paramAppId || null);
    } else {
      handleSearchCustomer('CUST-3');
    }
  }, [paramCustomerId, paramAppId]);

  // Fetch all documents for a customer ID or Application ID
  const handleSearchCustomer = async (inputVal = searchCustomerId, specificAppId = null) => {
    const cleanInput = String(inputVal || '').trim();
    if (!cleanInput) {
      setDocError('Please enter a valid Customer ID or Application ID.');
      return;
    }

    setLoadingCustomerDocs(true);
    setDocError('');
    setReviewSuccessMsg('');
    setReviewErrorMsg('');
    setSelectedDocIds(new Set());

    try {
      let docList = [];
      let activeCust = cleanInput;
      const targetApp = specificAppId || (cleanInput.toUpperCase().startsWith('APP-') ? cleanInput : null);

      // Check if targetApp is provided or input starts with APP-
      if (targetApp) {
        try {
          const appDocs = await fetchApplicationDocuments(targetApp);
          if (Array.isArray(appDocs) && appDocs.length > 0) {
            docList = appDocs;
            if (appDocs[0].customerId) {
              activeCust = appDocs[0].customerId;
            }
          }
        } catch (appErr) {
          console.warn('Could not fetch by application ID:', appErr);
        }
      }

      // If no docs found from application endpoint, or cleanInput is a Customer ID, fetch customer docs
      if (docList.length === 0) {
        try {
          const docs = await fetchCustomerDocuments(cleanInput);
          if (Array.isArray(docs) && docs.length > 0) {
            docList = docs;
          }
        } catch (custErr) {
          console.warn('Could not fetch by customer ID:', custErr);
        }
      }

      // If still empty and targetApp is set, try fetching customer docs using activeCust
      if (docList.length === 0 && activeCust && activeCust !== targetApp) {
        try {
          const docs = await fetchCustomerDocuments(activeCust);
          if (Array.isArray(docs) && docs.length > 0) {
            docList = docs;
          }
        } catch {}
      }

      const effectiveAppId = targetApp || 'ALL';
      setSelectedAppId(effectiveAppId);
      setActiveCustomerId(activeCust);
      setSearchCustomerId(activeCust);
      setUploadCustId(activeCust);
      if (targetApp) setUploadAppId(targetApp);
      setCustomerDocs(docList);

      // Load applications for this customer
      let currentApps = allApps;
      if (!currentApps || currentApps.length === 0) {
        try {
          currentApps = await fetchApplications();
          setAllApps(currentApps);
        } catch {}
      }

      const matchingApps = (Array.isArray(currentApps) ? currentApps : []).filter(
        a => (a.customerId || '').toLowerCase() === activeCust.toLowerCase() ||
             (a.customerEmail || '').toLowerCase() === activeCust.toLowerCase() ||
             (targetApp && (a.applicationId === targetApp || a.id === targetApp))
      );
      setCustomerApps(matchingApps);

      const visibleDocs = effectiveAppId === 'ALL'
        ? docList
        : docList.filter(d => d.applicationId === effectiveAppId || String(d.applicationId).toUpperCase() === String(effectiveAppId).toUpperCase());

      if (visibleDocs.length > 0) {
        selectAndPreviewDoc(visibleDocs[0]);
      } else if (docList.length > 0) {
        selectAndPreviewDoc(docList[0]);
      } else {
        setSelectedDoc(null);
        setPreviewBlobUrl(null);
        setDocError(`No documents found for: ${cleanInput}${targetApp ? ` (Application: ${targetApp})` : ''}`);
      }
    } catch (e) {
      setDocError(e.message || `Failed to fetch documents for: ${cleanInput}`);
      setCustomerDocs([]);
      setSelectedDoc(null);
      setPreviewBlobUrl(null);
    } finally {
      setLoadingCustomerDocs(false);
    }
  };

  // Select a document and load preview
  const selectAndPreviewDoc = async (doc) => {
    if (!doc) return;
    setSelectedDoc(doc);
    setDocLoading(true);
    setDocError('');
    setReviewSuccessMsg('');
    setReviewErrorMsg('');

    const effectiveId = doc.documentId || doc.id;
    try {
      const blobUrl = await fetchDocumentBlobUrl(effectiveId, doc.contentType);
      setPreviewBlobUrl(blobUrl);

      if (doc.status === 'REJECTED' || doc.status === 'ACTION_REQUIRED') {
        setReviewStatus('REJECTED');
        setManagerRemarks('Document review failed. Please upload updated document with clear details.');
      } else {
        setReviewStatus('VERIFIED');
        setManagerRemarks('Document meets all compliance and underwriting criteria. Verified & Approved.');
      }
    } catch (e) {
      console.warn('Could not stream document blob:', e);
      setPreviewBlobUrl(null);
    } finally {
      setDocLoading(false);
    }
  };

  // Handle Document Delete with confirmation alert
  const handleDeleteDocument = async (e, docToDelete) => {
    e.stopPropagation();
    const docId = docToDelete.documentId || docToDelete.id;
    const docTitle = docToDelete.documentName || docToDelete.originalFileName || `Document ${docId}`;

    const confirmed = window.confirm(`⚠️ Are you sure you want to delete this document?\n\nDocument ID: ${docId}\nName: ${docTitle}\n\nThis will remove the file from Azure Storage.`);
    if (!confirmed) return;

    try {
      await deleteDocumentById(docId);
      const remainingDocs = customerDocs.filter(d => String(d.documentId || d.id) !== String(docId));
      setCustomerDocs(remainingDocs);
      setReviewSuccessMsg(`🗑️ Document '${docTitle}' (ID: ${docId}) deleted successfully.`);

      if (selectedDoc && String(selectedDoc.documentId || selectedDoc.id) === String(docId)) {
        if (remainingDocs.length > 0) {
          selectAndPreviewDoc(remainingDocs[0]);
        } else {
          setSelectedDoc(null);
          setPreviewBlobUrl(null);
        }
      }
    } catch (err) {
      alert(`Failed to delete document: ${err.message}`);
    }
  };

  // Submit Manager Document Review Decision & Dispatch Customer Email
  const doSubmitReview = async (forcedStatus = null) => {
    const targetStatus = forcedStatus || reviewStatus;
    if (!selectedDoc) {
      setReviewErrorMsg('Please select a customer document to review.');
      return;
    }

    setReviewSubmitting(true);
    setReviewSuccessMsg('');
    setReviewErrorMsg('');

    const effectiveId = selectedDoc.documentId || selectedDoc.id;
    const remarksText = managerRemarks.trim() || (targetStatus === 'VERIFIED'
      ? 'Document verified and approved by Operations Manager.'
      : 'Document review failed. Additional supporting documents required from customer.');

    const payload = {
      status: targetStatus,
      remarks: remarksText,
      verifiedBy: managerId.trim() || 'senior.underwriter@bank.com',
      customerEmail: recipientEmail.trim() || 'itsarpitgupta@gmail.com'
    };

    try {
      const updated = await updateDocumentStatus(effectiveId, payload);
      const updatedDoc = { ...selectedDoc, ...updated, status: targetStatus };
      setSelectedDoc(updatedDoc);

      setCustomerDocs(prev =>
        prev.map(d => (String(d.documentId || d.id) === String(effectiveId) ? updatedDoc : d))
      );

      setReviewSuccessMsg(
        `✅ Review submitted (${targetStatus})! Event published to Azure Service Bus & email dispatched to ${payload.customerEmail}.`
      );
    } catch (e) {
      setReviewErrorMsg(e.message || 'Failed to submit document review decision.');
    } finally {
      setReviewSubmitting(false);
    }
  };

  // Toggle selection for a single document
  const toggleSelectDoc = (docId, e) => {
    e.stopPropagation();
    const strId = String(docId);
    setSelectedDocIds(prev => {
      const next = new Set(prev);
      if (next.has(strId)) next.delete(strId);
      else next.add(strId);
      return next;
    });
  };

  // Toggle select all visible documents
  const toggleSelectAll = (visibleDocs) => {
    if (selectedDocIds.size === visibleDocs.length && visibleDocs.length > 0) {
      setSelectedDocIds(new Set());
    } else {
      setSelectedDocIds(new Set(visibleDocs.map(d => String(d.documentId || d.id))));
    }
  };

  // Batch approve or reject selected documents
  const doBatchReview = async (targetStatus) => {
    if (selectedDocIds.size === 0) return;
    setBatchSubmitting(true);
    setReviewSuccessMsg('');
    setReviewErrorMsg('');

    const remarksText = targetStatus === 'VERIFIED'
      ? 'Batch verified and approved by Operations Manager.'
      : 'Batch rejected by Operations Manager. Please upload clearer/valid documents.';
    const reviewer = managerId.trim() || 'senior.underwriter@bank.com';
    const email = recipientEmail.trim() || 'itsarpitgupta@gmail.com';

    let successCount = 0;
    let failedCount = 0;

    const idsToProcess = Array.from(selectedDocIds);
    for (const docId of idsToProcess) {
      try {
        await updateDocumentStatus(docId, {
          status: targetStatus,
          remarks: remarksText,
          verifiedBy: reviewer,
          customerEmail: email,
        });
        successCount++;
      } catch (err) {
        console.warn(`Failed to update document ${docId}:`, err);
        failedCount++;
      }
    }

    // Refresh state
    setCustomerDocs(prev =>
      prev.map(d => {
        if (selectedDocIds.has(String(d.documentId || d.id))) {
          return { ...d, status: targetStatus, remarks: remarksText };
        }
        return d;
      })
    );

    if (selectedDoc && selectedDocIds.has(String(selectedDoc.documentId || selectedDoc.id))) {
      setSelectedDoc(prev => prev ? { ...prev, status: targetStatus, remarks: remarksText } : prev);
    }

    setSelectedDocIds(new Set());
    setBatchSubmitting(false);

    if (successCount > 0) {
      setReviewSuccessMsg(`✅ Batch action completed: ${successCount} document(s) marked as ${targetStatus}.${failedCount > 0 ? ` (${failedCount} failed)` : ''}`);
    } else {
      setReviewErrorMsg(`❌ Failed to update selected documents.`);
    }
  };

  // Handle standalone upload tab file selection
  const handleUploadFileChange = (e) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setUploadFile(f);
    if (!uploadDocName) setUploadDocName(f.name);
    try {
      setUploadFilePreviewUrl(URL.createObjectURL(f));
    } catch (err) {}
  };

  // Standalone upload execution
  const doStandaloneUpload = async () => {
    if (!uploadCustId.trim() || !uploadFile) {
      setUploadError('Customer ID and file are required.');
      return;
    }

    setUploading(true);
    setUploadError('');
    setUploadResult(null);

    const fd = new FormData();
    fd.append('customerId', uploadCustId.trim());
    if (uploadAppId.trim()) fd.append('applicationId', uploadAppId.trim());
    fd.append('documentType', uploadDocType);
    fd.append('docType', uploadDocType);
    if (uploadDocName.trim()) fd.append('documentName', uploadDocName.trim());
    fd.append('file', uploadFile);

    try {
      const res = await uploadDocument(fd);
      setUploadResult(res);
      setSearchCustomerId(uploadCustId.trim());
    } catch (e) {
      setUploadError(e.message || 'Failed to upload document.');
    } finally {
      setUploading(false);
    }
  };

  // Helper file type checks
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
            <h2 style={{ fontSize: '1.25rem', fontWeight: 800, margin: 0 }}>Document Management & Underwriting Review</h2>
            <p style={{ color: 'var(--muted)', fontSize: '0.78rem', margin: 0 }}>
              Search documents by Customer ID, review supporting proofs, write suggestions, and dispatch customer emails.
            </p>
          </div>

          <div className="tabs" style={{ margin: 0 }}>
            <button
              className={`tab-btn${tab === 'fetch' ? ' active' : ''}`}
              onClick={() => setTab('fetch')}
              style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 14px', fontSize: '0.82rem' }}
            >
              <Eye size={15} /> Customer Documents & Review
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
          onClick={() => handleSearchCustomer(searchCustomerId)}
          disabled={loadingCustomerDocs}
        >
          <RefreshCw size={13} className={loadingCustomerDocs ? 'spin' : ''} /> Refresh Documents
        </button>
      </div>

      {/* ── TAB 1: CUSTOMER DOCUMENTS SEARCH & MANAGER REVIEW (SPLIT SCREEN) ── */}
      {tab === 'fetch' && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '520px 1fr',
            gap: 16,
            flex: 1,
            minHeight: 0,
            overflow: 'hidden'
          }}
        >
          {/* Left Column: Customer Search, Application Cascading Selector, Document Multi-Select & Review Form */}
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
            {/* 1. Primary Customer Search Input & Cascading Application Selector */}
            <div style={{ background: 'rgba(255,255,255,0.02)', padding: 12, borderRadius: 8, border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div>
                <label className="form-label" style={{ fontSize: '0.78rem', display: 'flex', alignItems: 'center', gap: 5, marginBottom: 6 }}>
                  <User size={14} color="var(--accent)" /> Search Documents by Customer ID / Application ID *
                </label>
                <div className="flex-row" style={{ gap: 8 }}>
                  <input
                    className="form-input"
                    value={searchCustomerId}
                    onChange={e => setSearchCustomerId(e.target.value)}
                    placeholder="e.g. CUST-3, APP-D2E4EF4B"
                    onKeyDown={e => e.key === 'Enter' && handleSearchCustomer(searchCustomerId)}
                    style={{ fontSize: '0.85rem', padding: '7px 12px', flex: 1 }}
                  />
                  <button
                    className="btn btn-primary"
                    onClick={() => handleSearchCustomer(searchCustomerId)}
                    disabled={loadingCustomerDocs}
                    style={{ padding: '7px 14px', fontSize: '0.85rem', flexShrink: 0 }}
                  >
                    {loadingCustomerDocs ? <RefreshCw size={14} className="spin" /> : <Search size={14} />} Fetch
                  </button>
                </div>
              </div>

              {/* Cascading Application Selector */}
              <div>
                <label className="form-label" style={{ fontSize: '0.75rem', display: 'flex', alignItems: 'center', gap: 5, marginBottom: 4, color: 'var(--muted)' }}>
                  <Filter size={13} color="var(--accent)" /> Filter by Loan Application
                </label>
                <select
                  className="form-select"
                  value={selectedAppId}
                  onChange={e => {
                    const val = e.target.value;
                    setSelectedAppId(val);
                    setSelectedDocIds(new Set());
                    const filtered = val === 'ALL'
                      ? customerDocs
                      : customerDocs.filter(d => d.applicationId === val || String(d.applicationId).toUpperCase() === String(val).toUpperCase());
                    if (filtered.length > 0) selectAndPreviewDoc(filtered[0]);
                  }}
                  style={{ fontSize: '0.82rem', padding: '6px 10px', width: '100%', background: 'rgba(0,0,0,0.2)' }}
                >
                  <option value="ALL">📂 All Applications ({customerDocs.length} Total Documents)</option>
                  {selectedAppId && selectedAppId !== 'ALL' && !customerApps.some(a => (a.applicationId || a.id) === selectedAppId) && (
                    <option value={selectedAppId}>
                      {selectedAppId} — Active Loan Application
                    </option>
                  )}
                  {customerApps.map(a => {
                    const appId = a.applicationId || a.id;
                    const count = customerDocs.filter(d => d.applicationId === appId || String(d.applicationId).toUpperCase() === String(appId).toUpperCase()).length;
                    return (
                      <option key={appId} value={appId}>
                        {appId} — {a.schemeName || a.loanType || 'Loan'} ({count} docs) [{a.status}]
                      </option>
                    );
                  })}
                </select>
              </div>
            </div>

            {docError && (
              <div className="error-box" style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 12px', fontSize: '0.8rem' }}>
                <AlertCircle size={15} /> {docError}
              </div>
            )}

            {/* 2. Document List Header with Select All & Bulk Actions */}
            {(() => {
              const visibleDocs = selectedAppId === 'ALL'
                ? customerDocs
                : customerDocs.filter(d => d.applicationId === selectedAppId || String(d.applicationId).toUpperCase() === String(selectedAppId).toUpperCase());

              const allSelected = visibleDocs.length > 0 && visibleDocs.every(d => selectedDocIds.has(String(d.documentId || d.id)));

              return (
                <div>
                  <div style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--text)', marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <button
                        title={allSelected ? 'Deselect All' : 'Select All'}
                        onClick={() => toggleSelectAll(visibleDocs)}
                        style={{ background: 'transparent', border: 'none', cursor: 'pointer', padding: 0, color: allSelected ? 'var(--accent)' : 'var(--muted)', display: 'flex', alignItems: 'center' }}
                      >
                        {allSelected ? <CheckSquare size={16} /> : <Square size={16} />}
                      </button>
                      <Layers size={14} color="var(--accent)" />
                      <span>
                        Documents {selectedAppId !== 'ALL' ? `for ${selectedAppId}` : `for ${activeCustomerId}`} ({visibleDocs.length})
                      </span>
                    </div>

                    {selectedDocIds.size > 0 && (
                      <span className="badge" style={{ fontSize: '0.7rem', background: 'rgba(0, 210, 255, 0.15)', color: 'var(--accent)' }}>
                        {selectedDocIds.size} Selected
                      </span>
                    )}
                  </div>

                  {/* BULK ACTION BUTTONS BAR (Visible when 1+ docs are checked) */}
                  {selectedDocIds.size > 0 && (
                    <div
                      style={{
                        background: 'rgba(0, 210, 255, 0.08)',
                        border: '1px solid rgba(0, 210, 255, 0.3)',
                        borderRadius: 6,
                        padding: '8px 10px',
                        marginBottom: 10,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 8,
                        flexWrap: 'wrap'
                      }}
                    >
                      <span style={{ fontSize: '0.76rem', fontWeight: 600, color: 'var(--text)' }}>
                        Bulk Action ({selectedDocIds.size} docs):
                      </span>
                      <div className="flex-row" style={{ gap: 6 }}>
                        <button
                          className="btn btn-primary"
                          onClick={() => doBatchReview('VERIFIED')}
                          disabled={batchSubmitting}
                          style={{ fontSize: '0.74rem', padding: '4px 10px', background: '#10b981', borderColor: '#10b981', color: '#fff' }}
                        >
                          {batchSubmitting ? <RefreshCw size={12} className="spin" /> : <CheckCircle size={12} />} Approve All ({selectedDocIds.size})
                        </button>
                        <button
                          className="btn btn-primary"
                          onClick={() => doBatchReview('REJECTED')}
                          disabled={batchSubmitting}
                          style={{ fontSize: '0.74rem', padding: '4px 10px', background: '#ef4444', borderColor: '#ef4444', color: '#fff' }}
                        >
                          {batchSubmitting ? <RefreshCw size={12} className="spin" /> : <X size={12} />} Reject All ({selectedDocIds.size})
                        </button>
                        <button
                          className="btn btn-ghost"
                          onClick={() => setSelectedDocIds(new Set())}
                          style={{ fontSize: '0.72rem', padding: '4px 8px' }}
                        >
                          Clear
                        </button>
                      </div>
                    </div>
                  )}

                  {visibleDocs.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 240, overflowY: 'auto' }}>
                      {visibleDocs.map(doc => {
                        const effectiveId = String(doc.documentId || doc.id);
                        const isSelected = selectedDoc && String(selectedDoc.documentId || selectedDoc.id) === effectiveId;
                        const isChecked = selectedDocIds.has(effectiveId);

                        return (
                          <div
                            key={effectiveId}
                            onClick={() => selectAndPreviewDoc(doc)}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              padding: '8px 10px',
                              borderRadius: 6,
                              cursor: 'pointer',
                              background: isChecked ? 'rgba(0, 210, 255, 0.15)' : isSelected ? 'rgba(0, 210, 255, 0.08)' : 'rgba(255, 255, 255, 0.02)',
                              border: isChecked ? '1px solid var(--accent)' : isSelected ? '1px solid rgba(0, 210, 255, 0.5)' : '1px solid var(--border)',
                              transition: 'all 0.15s ease',
                              gap: 8
                            }}
                          >
                            {/* Checkbox for Batch Review */}
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={e => toggleSelectDoc(effectiveId, e)}
                              style={{ cursor: 'pointer', accentColor: 'var(--accent)', width: 15, height: 15, flexShrink: 0 }}
                            />

                            {/* Small 'x' Delete Button */}
                            <button
                              title="Delete this document"
                              onClick={e => handleDeleteDocument(e, doc)}
                              style={{
                                background: 'rgba(239, 68, 68, 0.15)',
                                border: '1px solid rgba(239, 68, 68, 0.3)',
                                color: '#ef4444',
                                borderRadius: '50%',
                                width: 18,
                                height: 18,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                cursor: 'pointer',
                                flexShrink: 0,
                                padding: 0,
                                transition: 'all 0.15s ease'
                              }}
                              onMouseEnter={e => {
                                e.currentTarget.style.background = '#ef4444';
                                e.currentTarget.style.color = '#ffffff';
                              }}
                              onMouseLeave={e => {
                                e.currentTarget.style.background = 'rgba(239, 68, 68, 0.15)';
                                e.currentTarget.style.color = '#ef4444';
                              }}
                            >
                              <X size={10} strokeWidth={2.5} />
                            </button>

                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden', flex: 1 }}>
                              <span style={{ fontSize: '1rem' }}>{isPdf(doc) ? '📄' : '🖼️'}</span>
                              <div style={{ overflow: 'hidden' }}>
                                <div style={{ fontSize: '0.78rem', fontWeight: 600, color: isSelected ? 'var(--accent)' : 'var(--text)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                  {doc.applicationId ? <span className="badge" style={{ fontSize: '0.65rem', marginRight: 6, background: 'rgba(0, 210, 255, 0.12)', color: 'var(--accent)' }}>{doc.applicationId}</span> : null}
                                  {doc.documentName || doc.originalFileName || doc.documentType}
                                </div>
                                <div style={{ fontSize: '0.7rem', color: 'var(--muted)' }}>
                                  ID: {effectiveId} • {doc.documentType || doc.docType} {doc.fileSizeBytes ? `• ${(doc.fileSizeBytes / 1024).toFixed(1)} KB` : ''}
                                </div>
                              </div>
                            </div>

                            <span
                              className={`badge ${
                                doc.status === 'VERIFIED' || doc.status === 'APPROVED'
                                  ? 'badge-approved'
                                  : doc.status === 'REJECTED' || doc.status === 'ACTION_REQUIRED'
                                  ? 'badge-rejected'
                                  : 'badge-under-review'
                              }`}
                              style={{ fontSize: '0.68rem', flexShrink: 0 }}
                            >
                              {doc.status || 'UPLOADED'}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <div style={{ padding: 12, textAlign: 'center', color: 'var(--muted)', fontSize: '0.78rem', background: 'rgba(255,255,255,0.01)', borderRadius: 6, border: '1px dashed var(--border)' }}>
                      No documents found {selectedAppId !== 'ALL' ? `for application ${selectedAppId}` : `for ${activeCustomerId}`}.
                    </div>
                  )}
                </div>
              );
            })()}

            {/* 3. MANAGER DOCUMENT REVIEW FORM (ALWAYS VISIBLE & PROMINENT) */}
            <div
              style={{
                borderTop: '1px solid var(--border)',
                paddingTop: 12,
                display: 'flex',
                flexDirection: 'column',
                gap: 10,
                background: 'rgba(255, 255, 255, 0.015)',
                padding: 12,
                borderRadius: 8,
                border: '1px solid rgba(0, 210, 255, 0.2)'
              }}
            >
              <div style={{ fontWeight: 700, fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: 6, color: 'var(--accent)' }}>
                <ShieldCheck size={17} /> Manager Document Review Form
              </div>

              {selectedDoc ? (
                <div style={{ fontSize: '0.75rem', color: 'var(--muted)', display: 'flex', justifyContent: 'space-between', borderBottom: '1px solid rgba(255,255,255,0.05)', paddingBottom: 6 }}>
                  <span>Reviewing: <strong style={{ color: 'var(--text)' }}>ID: {selectedDoc.documentId || selectedDoc.id}</strong> ({selectedDoc.documentType})</span>
                  <span className="font-mono">App: {selectedDoc.applicationId || '—'}</span>
                </div>
              ) : (
                <div style={{ fontSize: '0.75rem', color: 'var(--yellow)', padding: '4px 0' }}>
                  ⚠️ Please select a document from the list above to submit a review decision.
                </div>
              )}

              {reviewSuccessMsg && (
                <div className="success-box" style={{ fontSize: '0.78rem', padding: '8px 10px', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <CheckCircle size={14} color="var(--green)" /> {reviewSuccessMsg}
                </div>
              )}

              {reviewErrorMsg && (
                <div className="error-box" style={{ fontSize: '0.78rem', padding: '8px 10px', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <AlertCircle size={14} /> {reviewErrorMsg}
                </div>
              )}

              {/* Customer Recipient Email */}
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" style={{ fontSize: '0.75rem', display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Mail size={12} /> Customer Recipient Email (Receives Review Decision & Notes) *
                </label>
                <input
                  className="form-input"
                  value={recipientEmail}
                  onChange={e => setRecipientEmail(e.target.value)}
                  placeholder="customer@example.com"
                  style={{ fontSize: '0.8rem', padding: '6px 10px' }}
                />
              </div>

              {/* Manager Feedback / Remarks / Suggestions Textarea */}
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" style={{ fontSize: '0.75rem' }}>
                  Manager Remarks, Suggestions & Required Documents *
                </label>
                <textarea
                  className="form-input"
                  rows={3}
                  value={managerRemarks}
                  onChange={e => setManagerRemarks(e.target.value)}
                  placeholder="e.g. 'Document verified and approved' OR 'Salary slip for July is blurry. Please provide latest 3 months salary slips with official company seal.'"
                  style={{ fontSize: '0.8rem', padding: '6px 10px', resize: 'vertical' }}
                />
              </div>

              {/* Review Action Buttons */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 4 }}>
                <button
                  className="btn btn-primary"
                  onClick={() => {
                    setReviewStatus('VERIFIED');
                    doSubmitReview('VERIFIED');
                  }}
                  disabled={reviewSubmitting || !selectedDoc}
                  style={{
                    background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                    border: 'none',
                    fontSize: '0.78rem',
                    padding: '8px 10px',
                    justifyContent: 'center'
                  }}
                >
                  {reviewSubmitting && reviewStatus === 'VERIFIED' ? (
                    <RefreshCw size={13} className="spin" />
                  ) : (
                    <CheckCircle size={13} />
                  )}
                  Complete Review (Approve)
                </button>

                <button
                  className="btn"
                  onClick={() => {
                    setReviewStatus('REJECTED');
                    doSubmitReview('REJECTED');
                  }}
                  disabled={reviewSubmitting || !selectedDoc}
                  style={{
                    background: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)',
                    color: '#ffffff',
                    border: 'none',
                    fontSize: '0.78rem',
                    padding: '8px 10px',
                    justifyContent: 'center'
                  }}
                >
                  {reviewSubmitting && reviewStatus === 'REJECTED' ? (
                    <RefreshCw size={13} className="spin" />
                  ) : (
                    <AlertTriangle size={13} />
                  )}
                  Review Failed (Request Docs)
                </button>
              </div>
            </div>
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
                {isPdf(selectedDoc) ? <FileText size={17} color="var(--accent)" /> : <ImageIcon size={17} color="var(--accent2)" />}
                <span>
                  {selectedDoc
                    ? `${selectedDoc.documentName || selectedDoc.originalFileName || 'Document'} (ID: ${selectedDoc.documentId || selectedDoc.id})`
                    : `In-Browser Document Viewer (${activeCustomerId})`}
                </span>
              </div>

              {selectedDoc && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span
                    className={`badge ${
                      selectedDoc.status === 'VERIFIED' || selectedDoc.status === 'APPROVED'
                        ? 'badge-approved'
                        : selectedDoc.status === 'REJECTED' || selectedDoc.status === 'ACTION_REQUIRED'
                        ? 'badge-rejected'
                        : 'badge-under-review'
                    }`}
                    style={{ fontSize: '0.7rem' }}
                  >
                    {selectedDoc.status || 'UPLOADED'}
                  </span>
                  
                  {previewBlobUrl && (
                    <div className="flex-row" style={{ gap: 6 }}>
                      <a
                        href={previewBlobUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="btn btn-ghost"
                        style={{ fontSize: '0.72rem', padding: '3px 8px' }}
                      >
                        <ExternalLink size={12} /> Open Tab
                      </a>
                      <a
                        href={previewBlobUrl}
                        download={selectedDoc.originalFileName || selectedDoc.fileName || 'document.pdf'}
                        className="btn btn-primary"
                        style={{ fontSize: '0.72rem', padding: '3px 8px' }}
                      >
                        <Download size={12} /> Download
                      </a>
                    </div>
                  )}
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
                isPdf(selectedDoc) ? (
                  <iframe
                    src={previewBlobUrl}
                    title={`PDF Preview: ${selectedDoc?.documentId || selectedDoc?.id}`}
                    style={{
                      width: '100%',
                      height: '100%',
                      border: 'none',
                      background: '#ffffff'
                    }}
                  />
                ) : isImage(selectedDoc) ? (
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
                      alt={selectedDoc?.originalFileName || 'Document Image'}
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
                    title={`Document View: ${selectedDoc?.documentId || selectedDoc?.id}`}
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
                  <div style={{ fontSize: '0.8rem', marginTop: 4 }}>Select a document from the customer's list on the left to preview and review.</div>
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
                  Document ID: <strong className="font-mono" style={{ color: 'var(--accent)' }}>{uploadResult.documentId || uploadResult.id}</strong> • Customer: <strong className="font-mono">{uploadCustId}</strong>
                </div>
                <button
                  className="btn btn-primary"
                  style={{ fontSize: '0.78rem', padding: '5px 12px', marginTop: 8 }}
                  onClick={() => {
                    setSearchCustomerId(uploadCustId);
                    setTab('fetch');
                    handleSearchCustomer(uploadCustId);
                  }}
                >
                  <Eye size={13} /> View in Customer Documents & Review
                </button>
              </div>
            )}

            <div className="form-grid" style={{ gridTemplateColumns: '1fr', gap: 10 }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Customer ID *</label>
                <input
                  className="form-input"
                  value={uploadCustId}
                  onChange={e => setUploadCustId(e.target.value)}
                  placeholder="e.g. CUST-3"
                  style={{ padding: '7px 12px', fontSize: '0.85rem' }}
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Application ID (optional)</label>
                <input
                  className="form-input"
                  value={uploadAppId}
                  onChange={e => setUploadAppId(e.target.value)}
                  placeholder="e.g. APP-1D2BDA62"
                  style={{ padding: '7px 12px', fontSize: '0.85rem' }}
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Document Type *</label>
                <select
                  className="form-select"
                  value={uploadDocType}
                  onChange={e => setUploadDocType(e.target.value)}
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
                  value={uploadDocName}
                  onChange={e => setUploadDocName(e.target.value)}
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
                onClick={() => document.getElementById('standalone-file-input')?.click()}
              >
                <input
                  id="standalone-file-input"
                  type="file"
                  accept=".pdf,.jpg,.jpeg,.png,.webp"
                  onChange={handleUploadFileChange}
                  style={{ display: 'none' }}
                />
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                  <FolderUp size={26} color="var(--accent)" />
                  <div style={{ fontWeight: 600, fontSize: '0.85rem' }}>{uploadFile ? uploadFile.name : 'Click to Browse File'}</div>
                  <div style={{ color: 'var(--muted)', fontSize: '0.75rem' }}>
                    {uploadFile
                      ? `${(uploadFile.size / 1024).toFixed(1)} KB — Ready to upload`
                      : 'PDF, JPG, PNG, WEBP (Max 15MB)'}
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-4">
              <button
                className="btn btn-primary"
                onClick={doStandaloneUpload}
                disabled={uploading || !uploadFile}
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
              {uploadFilePreviewUrl ? (
                uploadFile?.type?.startsWith('image/') ? (
                  <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
                    <img
                      src={uploadFilePreviewUrl}
                      alt="Preview"
                      style={{ maxHeight: '100%', maxWidth: '100%', borderRadius: 8, border: '1px solid var(--border)' }}
                    />
                  </div>
                ) : (
                  <iframe
                    src={uploadFilePreviewUrl}
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
