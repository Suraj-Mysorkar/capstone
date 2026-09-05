import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  fetchApplicationById,
  fetchAuditLogs,
  submitManagerCallback,
  notifyDocumentUploaded,
  requestDocumentsFromCustomer,
  fetchApplicationDocuments,
  fetchCustomerDocuments,
  updateDocumentStatus
} from '../services/api';
import {
  ArrowLeft,
  UploadCloud,
  CheckCircle2,
  AlertTriangle,
  FileText,
  ExternalLink,
  ShieldCheck,
  Lock,
  Search,
  CheckCircle,
  Mail,
  Send
} from 'lucide-react';
import WorkflowDiagram from '../components/WorkflowDiagram';

function statusBadge(s) {
  if (s === 'APPROVED')                 return <span className="badge badge-approved">Approved</span>;
  if (s === 'REJECTED')                 return <span className="badge badge-rejected">Rejected</span>;
  if (s === 'MANUAL_REVIEW_REQUIRED')    return <span className="badge badge-review">Manual Review</span>;
  if (s === 'DOCUMENTS_SUBMITTED')       return <span className="badge" style={{ background: 'rgba(0, 210, 255, 0.15)', color: 'var(--accent)', border: '1px solid var(--accent)' }}>Documents Submitted</span>;
  if (s === 'DOCUMENT_REVIEW_PENDING')   return <span className="badge badge-warning" style={{ background: '#f59e0b20', color: '#d97706', border: '1px solid #d97706' }}>Awaiting Documents</span>;
  return <span className="badge badge-default">{(s || '').replace(/_/g, ' ')}</span>;
}

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:0 }).format(n);
}

const MANDATORY_REQUIREMENTS = [
  {
    code: 'IDENTITY_PROOF',
    label: 'Government Photo ID (PAN Card - Mandatory)',
    keywords: ['IDENTITY', 'ID_PROOF', 'PAN', 'AADHAAR', 'PASSPORT']
  },
  {
    code: 'ADDRESS_PROOF',
    label: 'Address Proof (Aadhaar Card / Utility Bill / Rent Agreement)',
    keywords: ['ADDRESS', 'UTILITY', 'BILL']
  },
  {
    code: 'INCOME_PROOF',
    label: 'Income Proof (Salary Slips for Last 3 Months / Form 16 / ITR)',
    keywords: ['INCOME', 'SALARY', 'PAYSLIP', 'ITR', 'FORM_16']
  },
  {
    code: 'BANK_STATEMENT',
    label: 'Bank Account Statement (Operational Statement for Last 6 Months)',
    keywords: ['BANK', 'STATEMENT']
  }
];

export default function ApplicationDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [app, setApp]             = useState(null);
  const [logs, setLogs]           = useState([]);
  const [customerDocs, setCustomerDocs] = useState([]);
  const [loading, setLoading]     = useState(true);
  const [tab, setTab]             = useState('details');

  // Document Email Request State
  const [selectedDocs, setSelectedDocs] = useState([]);
  const [customNotes, setCustomNotes]   = useState('');
  const [sendingEmail, setSendingEmail] = useState(false);
  const [emailStatusMsg, setEmailStatusMsg] = useState('');
  const [actionInProgressDocId, setActionInProgressDocId] = useState(null);

  // Manager callback
  const [decision, setDecision]   = useState('APPROVE');
  const [remarks, setRemarks]     = useState('');
  const [managerId, setManagerId] = useState('senior.underwriter@bank.com');
  const [cbLoading, setCbLoading] = useState(false);
  const [uploadingDoc, setUploadingDoc] = useState(false);
  const [cbResult, setCbResult]   = useState(null);
  const [cbError, setCbError]     = useState('');

  const load = async () => {
    if (!id) {
      console.error('Application ID is undefined');
      setApp(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const [a, l] = await Promise.all([
        fetchApplicationById(id),
        fetchAuditLogs(id),
      ]);
      setApp(a);
      setLogs(Array.isArray(l) ? l : []);

      const targetAppId = a?.applicationId || a?.id || id;
      if (targetAppId) {
        try {
          const appSpecificDocs = await fetchApplicationDocuments(targetAppId);
          if (Array.isArray(appSpecificDocs) && appSpecificDocs.length > 0) {
            setCustomerDocs(appSpecificDocs);
          } else if (Array.isArray(a?.documents) && a.documents.length > 0) {
            setCustomerDocs(a.documents);
          } else if (a?.customerId) {
            const allCustDocs = await fetchCustomerDocuments(a.customerId);
            const filtered = (Array.isArray(allCustDocs) ? allCustDocs : []).filter(
              d => d.applicationId === targetAppId || String(d.applicationId).toUpperCase() === String(targetAppId).toUpperCase()
            );
            setCustomerDocs(filtered);
          } else {
            setCustomerDocs([]);
          }
        } catch (docErr) {
          console.warn('Could not fetch application documents:', docErr);
          setCustomerDocs(Array.isArray(a?.documents) ? a.documents : []);
        }
      }
    } catch (e) {
      console.error('Error loading application data', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [id]);

  const doSendDocumentRequestEmail = async () => {
    if (!app?.customerEmail) {
      setEmailStatusMsg('❌ Customer email address is missing.');
      return;
    }
    setSendingEmail(true);
    setEmailStatusMsg('');
    try {
      const res = await requestDocumentsFromCustomer(id, {
        requiredDocumentTypes: selectedDocs,
        customNotes: customNotes.trim(),
      });
      setEmailStatusMsg(`✅ Document request email sent to ${app.customerEmail}! Applicant has received the list of required documents.`);
      await load();
    } catch (err) {
      setEmailStatusMsg('❌ Failed to send document request email: ' + (err.message || 'Server error'));
    } finally {
      setSendingEmail(false);
    }
  };

  // Safe callback submission without circular Event references
  const doCallback = async (decisionOverride, remarksOverride) => {
    const cleanDecision = (typeof decisionOverride === 'string' && decisionOverride) ? decisionOverride : decision;
    const cleanRemarks = (typeof remarksOverride === 'string' && remarksOverride)
      ? remarksOverride
      : (remarks.trim() || 'Underwriting manual review decision submitted');
    const cleanManagerId = (typeof managerId === 'string' && managerId) ? managerId.trim() : 'senior.underwriter@bank.com';

    if (cbLoading) return;
    setCbLoading(true);
    setCbResult(null);
    setCbError('');
    try {
      const res = await submitManagerCallback(id, {
        decision: cleanDecision,
        remarks: cleanRemarks,
        managerId: cleanManagerId,
      });
      setCbResult(res);
      if (res?.status) {
        setApp(currentApp => currentApp ? { ...currentApp, ...res } : currentApp);
      }
      await load();
    } catch(e) {
      setCbError(e.message || 'Manager decision submission failed');
    } finally {
      setCbLoading(false);
    }
  };

  const doUploadDocuments = async () => {
    if (uploadingDoc) return;
    setUploadingDoc(true);
    try {
      const docIds = customerDocs.length > 0
        ? customerDocs.map(d => String(d.documentId || d.id || '1'))
        : ['1'];
      await notifyDocumentUploaded(id, {
        documentIds: docIds,
        customerId: app?.customerId || 'CUST-DEFAULT'
      });
      await load();
    } catch (err) {
      console.error('Failed to notify document uploaded:', err);
    } finally {
      setUploadingDoc(false);
    }
  };

  // Navigate to Document Review Portal with auto-populated customerId and applicationId / loan account no
  const goToDocPortal = () => {
    const targetCustId = app?.customerId || '';
    const targetAppId = app?.applicationId || app?.id || id || '';
    const params = new URLSearchParams();
    if (targetCustId) params.set('customerId', targetCustId);
    if (targetAppId) params.set('applicationId', targetAppId);
    const queryString = params.toString() ? `?${params.toString()}` : '';
    navigate(`/documents${queryString}`, {
      state: { customerId: targetCustId, applicationId: targetAppId }
    });
  };

  if (loading) return <div className="page"><div className="spinner"/></div>;
  if (!app)    return <div className="page"><div className="error-box">Application not found.</div></div>;

  const riskColor = app.riskScore == null ? 'var(--muted)'
                  : app.riskScore <= 30   ? 'var(--green)'
                  : app.riskScore >= 70   ? 'var(--red)' : 'var(--yellow)';

  // Group documents by status
  const rejectedDocs = customerDocs.filter(
    d => String(d.status).toUpperCase() === 'REJECTED' || String(d.status).toUpperCase() === 'ACTION_REQUIRED'
  );

  const validUploadedDocs = customerDocs.filter(
    d => String(d.status).toUpperCase() !== 'REJECTED' && String(d.status).toUpperCase() !== 'ACTION_REQUIRED'
  );

  const verifiedDocs = customerDocs.filter(
    d => String(d.status).toUpperCase() === 'VERIFIED' || String(d.status).toUpperCase() === 'APPROVED'
  );

  // Check if a requirement is met by valid (unrejected) uploaded documents
  const isRequirementUploaded = (req) => {
    return validUploadedDocs.some(d => {
      const docStr = String(d.documentType || d.typeCode || d.documentName || '').toUpperCase();
      return req.keywords.some(kw => docStr.includes(kw));
    });
  };

  // Determine missing documents that were NOT uploaded by customer:
  // If customer has 0 uploaded documents, all mandatory requirements are missing.
  // If customer has uploaded at least 3 valid documents (standard submission), no mandatory doc is missing unless unmatched.
  const missingDocRequirements = customerDocs.length === 0
    ? MANDATORY_REQUIREMENTS
    : (validUploadedDocs.length < 3
        ? MANDATORY_REQUIREMENTS.filter(req => !isRequirementUploaded(req))
        : []);

  // Actionable documents: ONLY documents rejected by manager OR missing documents not uploaded by customer
  const actionableDocItems = [
    ...rejectedDocs.map(d => {
      const typeName = d.documentType || d.documentName || 'Document';
      return {
        id: `rejected-${d.documentId || d.id}`,
        label: `${typeName} (Re-upload Required: Rejected by reviewer${d.remarks ? ` - "${d.remarks}"` : ''})`,
        shortLabel: typeName,
        isRejected: true,
        remarks: d.remarks,
        doc: d
      };
    }),
    ...missingDocRequirements.map(m => ({
      id: `missing-${m.code}`,
      label: `${m.label} (Not Uploaded by Customer)`,
      shortLabel: m.label,
      isMissing: true
    }))
  ];

  // Box appears ONLY when application is active AND there are actionable items (rejected or missing documents)
  // If all documents are submitted and NONE are rejected, showUnderwriterDocRequestBox is FALSE!
  const showUnderwriterDocRequestBox = app &&
                                      app.status !== 'APPROVED' &&
                                      app.status !== 'REJECTED' &&
                                      actionableDocItems.length > 0;

  // Fully verified check for loan approval unlock
  const isDocReviewCompleted = customerDocs.length > 0 && verifiedDocs.length > 0 && rejectedDocs.length === 0;

  // Synchronize selectedDocs when actionableDocItems changes
  useEffect(() => {
    if (actionableDocItems.length > 0) {
      setSelectedDocs(actionableDocItems.map(item => item.label));
    } else {
      setSelectedDocs([]);
    }
  }, [customerDocs]);

  // Quick action for manager to verify or reject a document directly
  const handleQuickDocAction = async (docId, newStatus) => {
    let remarks = '';
    if (newStatus === 'REJECTED') {
      const inputRemarks = window.prompt(
        'Enter rejection reason to notify customer for re-upload:',
        'Document is unclear / illegible or does not meet compliance requirements. Please upload a clear valid copy.'
      );
      if (inputRemarks === null) return; // User cancelled
      remarks = inputRemarks.trim() || 'Document rejected during underwriting review. Please re-upload.';
    } else {
      remarks = 'Document verified and approved by underwriting manager.';
    }

    setActionInProgressDocId(docId);
    try {
      await updateDocumentStatus(docId, {
        status: newStatus,
        remarks,
        reviewerId: managerId || 'mgr1'
      });
      await load();
    } catch (err) {
      alert(`Failed to update document status: ${err.message || 'Server error'}`);
    } finally {
      setActionInProgressDocId(null);
    }
  };

  return (
    <div className="page">
      {/* Back + title */}
      <div className="flex-row" style={{ marginBottom: 24 }}>
        <button className="btn btn-ghost" style={{ padding: '7px 14px', fontSize: '.82rem' }} onClick={() => navigate('/applications')}>
          <ArrowLeft size={15}/> Back
        </button>
        <div style={{ fontWeight: 700, fontSize: '1.1rem', marginLeft: 4 }}>
          Application Detail: {app.applicationId || app.id}
        </div>
        <div className="ml-auto">{statusBadge(app.status)}</div>
      </div>

      {/* Tabs */}
      <div className="tabs">
        {['details', 'audit', 'manager', 'workflow'].map(t => (
          <button key={t} className={`tab-btn${tab===t?' active':''}`} onClick={() => setTab(t)}>
            {{ details: 'Overview', audit: 'Audit Trail', manager: 'Manager Callback', workflow: 'Workflow' }[t]}
          </button>
        ))}
      </div>

      {/* ── TAB 1: OVERVIEW ── */}
      {tab === 'details' && (
        <div className="gap-4">
          {/* Summary strip */}
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            <div className="card" style={{ flex: 1, minWidth: 200, padding: '20px 24px' }}>
              <div className="stat-label">Risk Score</div>
              <div style={{ fontSize: '3rem', fontWeight: 800, color: riskColor }}>
                {app.riskScore ?? '—'}
              </div>
              <div className="text-muted" style={{ marginTop: 4 }}>
                {app.riskScore == null ? '' : app.riskScore <= 30 ? 'Low Risk – Eligible for Auto-Approval' : app.riskScore >= 70 ? 'High Risk – Auto-Rejected' : 'Medium Risk – Manual Underwriter Review'}
              </div>
            </div>
            <div className="card" style={{ flex: 1, minWidth: 200, padding: '20px 24px' }}>
              <div className="stat-label">Loan Amount</div>
              <div style={{ fontSize: '1.8rem', fontWeight: 800 }}>{fmt(app.loanAmount)}</div>
              <div className="text-muted">{app.tenureMonths} months · {app.interestRate}% p.a.</div>
            </div>
            <div className="card" style={{ flex: 1, minWidth: 200, padding: '20px 24px' }}>
              <div className="stat-label">Monthly EMI</div>
              <div style={{ fontSize: '1.8rem', fontWeight: 800, color: 'var(--accent)' }}>{fmt(app.calculatedEMI)}</div>
              <div className="text-muted">DTI Ratio: {app.dtiRatio ?? '—'}%</div>
            </div>
          </div>

          {/* Document Status Banner when All Documents are Submitted & None Rejected */}
          {!showUnderwriterDocRequestBox && app.status !== 'APPROVED' && app.status !== 'REJECTED' && (
            <div
              style={{
                background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.08) 0%, rgba(20, 26, 50, 0.9) 100%)',
                border: '1.5px solid rgba(16, 185, 129, 0.4)',
                borderRadius: 14,
                padding: '16px 20px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: 12
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{ padding: 10, borderRadius: 10, background: 'rgba(16, 185, 129, 0.15)', color: 'var(--green)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <CheckCircle2 size={24} />
                </div>
                <div>
                  <h4 style={{ margin: 0, color: 'var(--green)', fontSize: '1.05rem', fontWeight: 700 }}>
                    All Required Documents Submitted ({validUploadedDocs.length} Documents on File)
                  </h4>
                  <p style={{ margin: '4px 0 0', fontSize: '.84rem', color: 'var(--text-muted)' }}>
                    Applicant <strong>{app.customerName}</strong> has submitted all required verification documents. No pending documents or re-requests required.
                  </p>
                </div>
              </div>
              <button
                className="btn btn-ghost"
                style={{ fontSize: '.78rem', color: 'var(--accent)', border: '1px solid rgba(0, 210, 255, 0.3)' }}
                onClick={goToDocPortal}
              >
                <ExternalLink size={13} style={{ marginRight: 5 }} />
                Open Documents Review Portal
              </button>
            </div>
          )}

          {/* Document Request & Pending Checklist Card (Shows ONLY when there are rejected or missing docs) */}
          {showUnderwriterDocRequestBox && (
            <div className="card p-6" style={{ background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.08) 0%, rgba(20, 26, 50, 0.9) 100%)', borderColor: rejectedDocs.length > 0 ? '#ef4444' : '#f59e0b', borderWidth: 1.5, borderRadius: 14 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{ padding: 10, borderRadius: 10, background: rejectedDocs.length > 0 ? 'rgba(239, 68, 68, 0.15)' : 'rgba(245, 158, 11, 0.15)', color: rejectedDocs.length > 0 ? '#ef4444' : '#d97706', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Mail size={24} />
                  </div>
                  <div>
                    <h4 style={{ margin: 0, color: rejectedDocs.length > 0 ? '#ef4444' : '#f59e0b', fontSize: '1.05rem', fontWeight: 700 }}>
                      {rejectedDocs.length > 0 && missingDocRequirements.length > 0
                        ? `Underwriter Action: ${rejectedDocs.length} Rejected & ${missingDocRequirements.length} Missing Document(s)`
                        : rejectedDocs.length > 0
                        ? `Underwriter Action: ${rejectedDocs.length} Document(s) Rejected — Request Re-upload`
                        : `Underwriter Action: Pending Documents & Request Email`}
                    </h4>
                    <p style={{ margin: '4px 0 0', fontSize: '.84rem', color: 'var(--text-muted)' }}>
                      Applicant <strong>{app.customerName}</strong> ({app.customerEmail}) · 
                      {rejectedDocs.length > 0 ? (
                        <span style={{ color: '#f87171', fontWeight: 600 }}> {rejectedDocs.length} document(s) marked rejected by reviewer</span>
                      ) : (
                        <span> Awaiting {missingDocRequirements.length} required document(s)</span>
                      )}
                      {validUploadedDocs.length > 0 && ` · ${validUploadedDocs.length} valid document(s) on file`}
                    </p>
                  </div>
                </div>
                <button
                  className="btn btn-ghost"
                  style={{ fontSize: '.78rem', color: 'var(--accent)', border: '1px solid rgba(0, 210, 255, 0.3)' }}
                  onClick={goToDocPortal}
                >
                  <ExternalLink size={13} style={{ marginRight: 5 }} />
                  Open Documents Review Portal
                </button>
              </div>

              {/* Actionable Documents Checklist Selector */}
              <div style={{ marginBottom: 16, background: 'rgba(0,0,0,0.25)', padding: '14px 16px', borderRadius: 10, border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ fontSize: '.84rem', fontWeight: 600, color: 'var(--text)', marginBottom: 10 }}>
                  Select documents to include in request notification email to applicant:
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 10 }}>
                  {actionableDocItems.map(item => {
                    const isChecked = selectedDocs.includes(item.label);
                    return (
                      <label
                        key={item.id}
                        style={{
                          display: 'flex',
                          alignItems: 'flex-start',
                          gap: 8,
                          fontSize: '.82rem',
                          color: isChecked ? '#fff' : 'var(--muted)',
                          cursor: 'pointer',
                          background: item.isRejected
                            ? (isChecked ? 'rgba(239, 68, 68, 0.15)' : 'rgba(239, 68, 68, 0.05)')
                            : (isChecked ? 'rgba(0, 210, 255, 0.08)' : 'transparent'),
                          padding: '8px 12px',
                          borderRadius: 6,
                          border: item.isRejected
                            ? (isChecked ? '1px solid rgba(239, 68, 68, 0.5)' : '1px solid rgba(239, 68, 68, 0.2)')
                            : (isChecked ? '1px solid rgba(0, 210, 255, 0.3)' : '1px solid transparent'),
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={e => {
                            if (e.target.checked) {
                              setSelectedDocs(prev => [...prev, item.label]);
                            } else {
                              setSelectedDocs(prev => prev.filter(d => d !== item.label));
                            }
                          }}
                          style={{ accentColor: item.isRejected ? '#ef4444' : 'var(--accent)', marginTop: 2 }}
                        />
                        <div>
                          <div style={{ fontWeight: 600, color: item.isRejected ? '#fca5a5' : '#fff' }}>
                            {item.shortLabel}
                          </div>
                          <div style={{ fontSize: '.74rem', color: item.isRejected ? '#f87171' : 'var(--muted)', marginTop: 2 }}>
                            {item.isRejected ? `❌ Rejected: ${item.remarks || 'Re-upload required'}` : '⚠️ Not uploaded yet'}
                          </div>
                        </div>
                      </label>
                    );
                  })}
                </div>

                {/* Custom Notes from Underwriter */}
                <div style={{ marginTop: 12 }}>
                  <label style={{ fontSize: '.78rem', color: 'var(--muted)', display: 'block', marginBottom: 4 }}>
                    Custom Underwriter Notes to include in email (optional):
                  </label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. Please provide clear colored scan of front & back sides with valid date."
                    value={customNotes}
                    onChange={e => setCustomNotes(e.target.value)}
                    style={{ fontSize: '.84rem', padding: '8px 12px', background: 'rgba(0,0,0,0.3)' }}
                  />
                </div>
              </div>

              {emailStatusMsg && (
                <div
                  style={{
                    marginBottom: 14,
                    padding: '10px 14px',
                    borderRadius: 8,
                    fontSize: '.85rem',
                    fontWeight: 600,
                    background: emailStatusMsg.startsWith('✅') ? 'rgba(0, 230, 118, 0.12)' : 'rgba(231, 76, 60, 0.12)',
                    color: emailStatusMsg.startsWith('✅') ? '#2ecc71' : '#e74c3c',
                    border: '1px solid ' + (emailStatusMsg.startsWith('✅') ? 'rgba(0, 230, 118, 0.3)' : 'rgba(231, 76, 60, 0.3)'),
                  }}
                >
                  {emailStatusMsg}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
                <div style={{ fontSize: '.8rem', color: 'var(--muted)' }}>
                  Recipient: <strong style={{ color: 'var(--accent)' }}>{app.customerEmail || 'No email provided'}</strong>
                </div>
                <button
                  className="btn btn-primary"
                  onClick={doSendDocumentRequestEmail}
                  disabled={sendingEmail || !app.customerEmail || selectedDocs.length === 0}
                  style={{ padding: '9px 18px', fontSize: '.88rem', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: 8 }}
                >
                  <Send size={15} />
                  {sendingEmail ? 'Sending Email to Customer…' : (
                    rejectedDocs.length > 0 && missingDocRequirements.length === 0
                      ? '📧 Send Re-upload Request for Rejected Document(s)'
                      : '📧 Send Document Request Email to Customer'
                  )}
                </button>
              </div>
            </div>
          )}

          {/* Customer & Document Status Row */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div className="card p-6">
              <div className="card-header-title" style={{ marginBottom: 16 }}>Customer Information</div>
              <div className="detail-grid">
                {[
                  ['Customer ID',     app.customerId],
                  ['Full Name',       app.customerName],
                  ['Email',           app.customerEmail],
                  ['Phone',           app.customerPhone],
                  ['Employment',      app.employmentType],
                  ['Monthly Income',  fmt(app.monthlyIncome)],
                  ['Existing Liabilities', fmt(app.existingLiabilities)],
                ].map(([l, v]) => (
                  <div key={l} className="detail-field">
                    <div className="detail-field-label">{l}</div>
                    <div className="detail-field-value">{v ?? '—'}</div>
                  </div>
                ))}
              </div>
            </div>

            <div className="card p-6">
              <div className="card-header-title" style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>Uploaded Documents ({customerDocs.length})</span>
                <button
                  className="btn btn-ghost"
                  style={{ fontSize: '.75rem', padding: '3px 8px' }}
                  onClick={goToDocPortal}
                >
                  <Search size={12} /> Document Review Portal →
                </button>
              </div>

              {customerDocs.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {customerDocs.map(d => (
                    <div
                      key={d.documentId || d.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '8px 12px',
                        borderRadius: 6,
                        background: 'rgba(255, 255, 255, 0.02)',
                        border: '1px solid var(--border)'
                      }}
                    >
                      <div>
                        <div style={{ fontSize: '.82rem', fontWeight: 600 }}>
                          ID: {d.documentId || d.id} — {d.documentName || d.originalFileName || d.documentType}
                        </div>
                        <div style={{ fontSize: '.72rem', color: 'var(--muted)' }}>
                          Type: {d.documentType} {d.fileSizeBytes ? `• ${(d.fileSizeBytes / 1024).toFixed(1)} KB` : ''}
                        </div>
                        {d.remarks && (
                          <div style={{ fontSize: '.72rem', color: d.status === 'REJECTED' ? '#ef4444' : 'var(--accent)', marginTop: 2 }}>
                            Note: {d.remarks}
                          </div>
                        )}
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span
                          className={`badge ${
                            d.status === 'VERIFIED' || d.status === 'APPROVED'
                              ? 'badge-approved'
                              : d.status === 'REJECTED' || d.status === 'ACTION_REQUIRED'
                              ? 'badge-rejected'
                              : 'badge-under-review'
                          }`}
                          style={{ fontSize: '.7rem' }}
                        >
                          {d.status || 'UPLOADED'}
                        </span>

                        {/* Quick Underwriter Verify / Reject buttons */}
                        {d.status !== 'VERIFIED' && d.status !== 'APPROVED' && (
                          <button
                            className="btn btn-ghost"
                            style={{ fontSize: '.7rem', padding: '3px 8px', color: 'var(--green)', border: '1px solid rgba(16, 185, 129, 0.3)' }}
                            title="Mark as Verified"
                            disabled={actionInProgressDocId === (d.documentId || d.id)}
                            onClick={() => handleQuickDocAction(d.documentId || d.id, 'VERIFIED')}
                          >
                            ✓ Verify
                          </button>
                        )}
                        {d.status !== 'REJECTED' && (
                          <button
                            className="btn btn-ghost"
                            style={{ fontSize: '.7rem', padding: '3px 8px', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.3)' }}
                            title="Reject Document"
                            disabled={actionInProgressDocId === (d.documentId || d.id)}
                            onClick={() => handleQuickDocAction(d.documentId || d.id, 'REJECTED')}
                          >
                            ✕ Reject
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{ color: 'var(--muted)', fontSize: '.82rem' }}>
                  No documents found for customer {app.customerId}.
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── TAB 2: AUDIT TRAIL ── */}
      {tab === 'audit' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 16 }}>Audit Trail</div>
          {logs.length === 0 ? (
            <div className="empty">No audit logs available.</div>
          ) : (
            <ul className="timeline">
              {logs.map((l, i) => (
                <li key={i} className="timeline-item">
                  <div className="timeline-dot"/>
                  <div style={{ fontWeight: 600, fontSize: '.88rem' }}>
                    {l.previousStatus
                      ? <>{l.previousStatus} <span style={{ color: 'var(--muted)' }}>→</span> {l.newStatus}</>
                      : l.newStatus}
                  </div>
                  <div className="timeline-meta">
                    By <strong>{l.changedBy}</strong> · {new Date(l.timestamp).toLocaleString()}
                  </div>
                  {l.comments && <div className="timeline-comment">{l.comments}</div>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* ── TAB 3: MANAGER CALLBACK ── */}
      {tab === 'manager' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
            <ShieldCheck size={20} color="var(--accent)" /> Manager Underwriting Decision Callback
          </div>
          <div className="text-muted" style={{ marginBottom: 20, fontSize: '.83rem' }}>
            Applications in <strong>DOCUMENT_REVIEW_PENDING</strong> or <strong>MANUAL_REVIEW_REQUIRED</strong> state can receive an underwriter approval once documents are reviewed.
          </div>

          {app.status !== 'MANUAL_REVIEW_REQUIRED' && app.status !== 'DOCUMENT_REVIEW_PENDING' && (
            <div className="info-box" style={{ marginBottom: 16 }}>
              This application is currently <strong>{app.status}</strong>. Manager callback is only applicable when status is DOCUMENT_REVIEW_PENDING or MANUAL_REVIEW_REQUIRED.
            </div>
          )}

          {cbError  && <div className="error-box" style={{ marginBottom: 16 }}>{cbError}</div>}
          {cbResult && (
            <div className="success-box" style={{ marginBottom: 16 }}>
              ✅ Callback submitted! New status: <strong>{cbResult.status}</strong>
            </div>
          )}

          {/* DOCUMENT REVIEW STATUS CHECK */}
          {!isDocReviewCompleted ? (
            <div
              style={{
                background: 'rgba(245, 158, 11, 0.1)',
                border: '1.5px solid #f59e0b',
                borderRadius: 8,
                padding: '16px 20px',
                marginBottom: 20
              }}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                <AlertTriangle color="#d97706" size={22} style={{ flexShrink: 0, marginTop: 2 }} />
                <div>
                  <div style={{ fontWeight: 700, fontSize: '0.95rem', color: '#d97706' }}>
                    Document Review Pending / Incomplete
                  </div>
                  <div style={{ fontSize: '0.82rem', color: 'var(--text)', marginTop: 4, lineHeight: 1.5 }}>
                    The customer ({app.customerId}) has not yet had their required KYC / Income documents reviewed and marked <strong>VERIFIED</strong>.
                    <br />
                    Loan approval is locked until document underwriting verification is completed.
                  </div>

                  <div style={{ display: 'flex', gap: 10, marginTop: 12, flexWrap: 'wrap' }}>
                    <button
                      className="btn btn-primary"
                      onClick={goToDocPortal}
                      style={{ fontSize: '.82rem', padding: '6px 14px', display: 'flex', alignItems: 'center', gap: 6 }}
                    >
                      <FileText size={15} /> Go to Document Review for {app.customerId} →
                    </button>
                    <button
                      className="btn"
                      onClick={() => doCallback('REJECT', 'Rejected during manual underwriting due to pending/invalid documents')}
                      style={{
                        background: '#dc2626',
                        color: '#fff',
                        fontSize: '.82rem',
                        padding: '6px 14px'
                      }}
                      disabled={cbLoading}
                    >
                      ✕ Reject Application
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div
              style={{
                background: 'rgba(16, 185, 129, 0.08)',
                border: '1px solid var(--green)',
                borderRadius: 8,
                padding: '12px 16px',
                marginBottom: 20,
                display: 'flex',
                alignItems: 'center',
                gap: 10
              }}
            >
              <CheckCircle size={18} color="var(--green)" />
              <div style={{ fontSize: '.85rem' }}>
                <strong>Document Review Complete:</strong> Verified supporting documents on file for {app.customerId}. Approval unlocked.
              </div>
            </div>
          )}

          {/* Form Controls */}
          <div className="form-grid">
            <div className="form-group">
              <label className="form-label">Decision *</label>
              <select
                className="form-select"
                value={decision}
                onChange={e => setDecision(e.target.value)}
                disabled={!isDocReviewCompleted && decision === 'APPROVE'}
              >
                <option value="APPROVE" disabled={!isDocReviewCompleted}>
                  APPROVE {isDocReviewCompleted ? '(Documents Verified)' : '(Locked - Doc Review Pending)'}
                </option>
                <option value="REJECT">REJECT</option>
                <option value="REQUEST_MORE_INFO">REQUEST_MORE_INFO</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Manager ID *</label>
              <input className="form-input" value={managerId} onChange={e => setManagerId(e.target.value)}/>
            </div>

            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label className="form-label">Remarks *</label>
              <input
                className="form-input"
                value={remarks}
                onChange={e => setRemarks(e.target.value)}
                placeholder="Enter underwriting justification"
              />
            </div>
          </div>

          {/* Action Buttons */}
          <div className="mt-4 flex-row" style={{ gap: 10 }}>
            {isDocReviewCompleted ? (
              <button
                className="btn btn-success"
                onClick={() => doCallback('APPROVE')}
                disabled={cbLoading}
                style={{ padding: '8px 18px', fontSize: '.88rem' }}
              >
                {cbLoading ? 'Submitting…' : '✓ Approve Loan Application'}
              </button>
            ) : (
              <button
                className="btn btn-ghost"
                onClick={goToDocPortal}
                style={{ padding: '8px 18px', fontSize: '.88rem', display: 'flex', alignItems: 'center', gap: 6 }}
              >
                <Lock size={15} /> Complete Document Review First →
              </button>
            )}

            <button
              className="btn btn-danger"
              onClick={() => doCallback('REJECT')}
              disabled={cbLoading}
              style={{ padding: '8px 18px', fontSize: '.88rem' }}
            >
              ✕ Reject Application
            </button>
          </div>
        </div>
      )}

      {/* ── TAB 4: WORKFLOW DIAGRAM ── */}
      {tab === 'workflow' && (
        <div className="card p-6">
          <WorkflowDiagram
            currentStatus={app.status}
            riskScore={app.riskScore}
            hasDocuments={isDocReviewCompleted || app.status === 'APPROVED'}
            decisionRemarks={app.decisionRemarks}
            isProcessing={cbLoading || uploadingDoc}
            onDecision={(newStatus) => {
              setDecision(newStatus);
              setRemarks('Decision processed via Interactive Workflow Diagram');
              doCallback(newStatus, 'Decision processed via Interactive Workflow Diagram');
            }}
            onUploadDocs={doUploadDocuments}
          />
        </div>
      )}
    </div>
  );
}
