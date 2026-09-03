import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  fetchApplicationById,
  fetchAuditLogs,
  submitManagerCallback,
  notifyDocumentUploaded,
  requestDocumentsFromCustomer,
  fetchCustomerDocuments
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
  if (s === 'DOCUMENT_REVIEW_PENDING')   return <span className="badge badge-warning" style={{ background: '#f59e0b20', color: '#d97706', border: '1px solid #d97706' }}>Awaiting Documents</span>;
  return <span className="badge badge-default">{s}</span>;
}

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:0 }).format(n);
}

const DEFAULT_REQUIRED_DOCS = [
  'Government Photo ID (PAN Card - Mandatory)',
  'Address Proof (Aadhaar Card / Passport / Recent Utility Bill)',
  'Income Proof (Salary Slips for Last 3 Months or Latest Form 16 / ITR)',
  'Bank Account Statement (Operational Bank Account Statement for Last 6 Months)'
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
  const [selectedDocs, setSelectedDocs] = useState(DEFAULT_REQUIRED_DOCS);
  const [customNotes, setCustomNotes]   = useState('');
  const [sendingEmail, setSendingEmail] = useState(false);
  const [emailStatusMsg, setEmailStatusMsg] = useState('');

  // Manager callback
  const [decision, setDecision]   = useState('APPROVE');
  const [remarks, setRemarks]     = useState('');
  const [managerId, setManagerId] = useState('senior.underwriter@bank.com');
  const [cbLoading, setCbLoading] = useState(false);
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

      if (a?.customerId) {
        try {
          const docs = await fetchCustomerDocuments(a.customerId);
          setCustomerDocs(Array.isArray(docs) ? docs : []);
        } catch (docErr) {
          console.warn('Could not fetch customer documents:', docErr);
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

  if (loading) return <div className="page"><div className="spinner"/></div>;
  if (!app)    return <div className="page"><div className="error-box">Application not found.</div></div>;

  const riskColor = app.riskScore == null ? 'var(--muted)'
                  : app.riskScore <= 30   ? 'var(--green)'
                  : app.riskScore >= 70   ? 'var(--red)' : 'var(--yellow)';

  // Check if Document Review is done and approved for this customer
  const verifiedDocs = customerDocs.filter(d => d.status === 'VERIFIED' || d.status === 'APPROVED');
  const rejectedDocs = customerDocs.filter(d => d.status === 'REJECTED' || d.status === 'ACTION_REQUIRED');
  const isDocReviewCompleted = (customerDocs.length > 0 && verifiedDocs.length > 0) || (app.documents && app.documents.length > 0);

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

          {/* Conditional Document Request Email Action Card */}
          {app.status === 'DOCUMENT_REVIEW_PENDING' && (
            <div className="card p-6" style={{ background: 'linear-gradient(135deg, rgba(245, 158, 11, 0.08) 0%, rgba(20, 26, 50, 0.9) 100%)', borderColor: '#f59e0b', borderWidth: 1.5, borderRadius: 14 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{ padding: 10, borderRadius: 10, background: 'rgba(245, 158, 11, 0.15)', color: '#d97706', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Mail size={24} />
                  </div>
                  <div>
                    <h4 style={{ margin: 0, color: '#f59e0b', fontSize: '1.05rem', fontWeight: 700 }}>
                      Action Required: Send Verification Documents Request Email
                    </h4>
                    <p style={{ margin: '4px 0 0', fontSize: '.84rem', color: 'var(--text-muted)' }}>
                      Notify applicant <strong>{app.customerName}</strong> ({app.customerEmail}) with the list of mandatory documents required for loan underwriting.
                    </p>
                  </div>
                </div>
                <button
                  className="btn btn-ghost"
                  style={{ fontSize: '.78rem', color: 'var(--accent)', border: '1px solid rgba(0, 210, 255, 0.3)' }}
                  onClick={() => navigate('/documents')}
                >
                  <ExternalLink size={13} style={{ marginRight: 5 }} />
                  Open Documents Review Portal
                </button>
              </div>

              {/* Required Document Checklist Selector */}
              <div style={{ marginBottom: 16, background: 'rgba(0,0,0,0.25)', padding: '14px 16px', borderRadius: 10, border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ fontSize: '.84rem', fontWeight: 600, color: 'var(--text)', marginBottom: 10 }}>
                  Select Document Requirements Checklist to include in email:
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 10 }}>
                  {DEFAULT_REQUIRED_DOCS.map(doc => {
                    const isChecked = selectedDocs.includes(doc);
                    return (
                      <label
                        key={doc}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          fontSize: '.82rem',
                          color: isChecked ? '#fff' : 'var(--muted)',
                          cursor: 'pointer',
                          background: isChecked ? 'rgba(0, 210, 255, 0.08)' : 'transparent',
                          padding: '6px 10px',
                          borderRadius: 6,
                          border: isChecked ? '1px solid rgba(0, 210, 255, 0.3)' : '1px solid transparent',
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={e => {
                            if (e.target.checked) {
                              setSelectedDocs(prev => [...prev, doc]);
                            } else {
                              setSelectedDocs(prev => prev.filter(d => d !== doc));
                            }
                          }}
                          style={{ accentColor: 'var(--accent)' }}
                        />
                        <span>{doc}</span>
                      </label>
                    );
                  })}
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
                  {sendingEmail ? 'Sending Email to Customer…' : '📧 Send Document Request Email to Customer'}
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
                  onClick={() => navigate('/documents')}
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
                      </div>

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
            Only applications in <strong>MANUAL_REVIEW_REQUIRED</strong> state can receive an underwriter approval.
          </div>

          {app.status !== 'MANUAL_REVIEW_REQUIRED' && (
            <div className="info-box" style={{ marginBottom: 16 }}>
              This application is currently <strong>{app.status}</strong>. Manager callback is only applicable when status is MANUAL_REVIEW_REQUIRED.
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
                      onClick={() => navigate('/documents')}
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
                onClick={() => navigate('/documents')}
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
