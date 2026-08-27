import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { fetchApplicationById, fetchAuditLogs, submitManagerCallback, notifyDocumentUploaded } from '../services/api';
import { ArrowLeft, UploadCloud, CheckCircle2, AlertTriangle, FileText } from 'lucide-react';
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

export default function ApplicationDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [app, setApp]     = useState(null);
  const [logs, setLogs]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab]     = useState('details');

  // Document upload state
  const [uploadDocIds, setUploadDocIds] = useState('DOC-KYC-VERIFIED, DOC-INCOME-VERIFIED');
  const [uploadingDoc, setUploadingDoc] = useState(false);
  const [docUploadMsg, setDocUploadMsg] = useState('');

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
    } catch (e) {
      console.error('Error loading application data', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [id]);

  const doUploadDocuments = async () => {
    if (!uploadDocIds.trim()) return;
    setUploadingDoc(true);
    setDocUploadMsg('');
    try {
      const docList = uploadDocIds.split(',').map(s => s.trim()).filter(Boolean);
      const updated = await notifyDocumentUploaded(id, {
        documentIds: docList,
        customerId: app?.customerId || 'CUST-DEFAULT',
      });
      setDocUploadMsg('✅ Documents successfully uploaded! Workflow advanced.');
      if (updated?.status) {
        setApp(updated);
      }
      await load();
    } catch (err) {
      setDocUploadMsg('❌ Error uploading documents: ' + err.message);
    } finally {
      setUploadingDoc(false);
    }
  };

  const doCallback = async (decisionOverride = decision, remarksOverride = remarks) => {
    setCbLoading(true); setCbResult(null); setCbError('');
    try {
      const res = await submitManagerCallback(id, {
        decision: decisionOverride,
        remarks: remarksOverride,
        managerId,
      });
      setCbResult(res);
      if (res?.status) {
        setApp(currentApp => currentApp ? { ...currentApp, ...res } : currentApp);
      }
      await load();
    } catch(e) { setCbError(e.message); }
    finally { setCbLoading(false); }
  };

  if (loading) return <div className="page"><div className="spinner"/></div>;
  if (!app)    return <div className="page"><div className="error-box">Application not found.</div></div>;

  const riskColor = app.riskScore == null ? 'var(--muted)'
                  : app.riskScore <= 30   ? 'var(--green)'
                  : app.riskScore >= 70   ? 'var(--red)' : 'var(--yellow)';

  return (
    <div className="page">
      {/* Back + title */}
      <div className="flex-row" style={{ marginBottom:24 }}>
        <button className="btn btn-ghost" style={{ padding:'7px 14px', fontSize:'.82rem' }} onClick={() => navigate('/applications')}>
          <ArrowLeft size={15}/> Back
        </button>
        <div style={{ fontWeight:700, fontSize:'1.1rem', marginLeft:4 }}>
          Application Detail: {app.applicationId || app.id}
        </div>
        <div className="ml-auto">{statusBadge(app.status)}</div>
      </div>

      {/* Tabs */}
      <div className="tabs">
        {['details','audit','manager','workflow'].map(t => (
          <button key={t} className={`tab-btn${tab===t?' active':''}`} onClick={() => setTab(t)}>
            {{ details:'Overview', audit:'Audit Trail', manager:'Manager Callback', workflow:'Workflow' }[t]}
          </button>
        ))}
      </div>

      {tab === 'details' && (
        <div className="gap-4">
          {/* Summary strip */}
          <div style={{ display:'flex', gap:16, flexWrap:'wrap' }}>
            <div className="card" style={{ flex:1, minWidth:200, padding:'20px 24px' }}>
              <div className="stat-label">Risk Score</div>
              <div style={{ fontSize:'3rem', fontWeight:800, color:riskColor }}>
                {app.riskScore ?? '—'}
              </div>
              <div className="text-muted" style={{ marginTop:4 }}>
                {app.riskScore == null ? '' : app.riskScore <= 30 ? 'Low Risk – Eligible for Auto-Approval' : app.riskScore >= 70 ? 'High Risk – Auto-Rejected' : 'Medium Risk – Manual Underwriter Review'}
              </div>
            </div>
            <div className="card" style={{ flex:1, minWidth:200, padding:'20px 24px' }}>
              <div className="stat-label">Loan Amount</div>
              <div style={{ fontSize:'1.8rem', fontWeight:800 }}>{fmt(app.loanAmount)}</div>
              <div className="text-muted">{app.tenureMonths} months · {app.interestRate}% p.a.</div>
            </div>
            <div className="card" style={{ flex:1, minWidth:200, padding:'20px 24px' }}>
              <div className="stat-label">Monthly EMI</div>
              <div style={{ fontSize:'1.8rem', fontWeight:800, color:'var(--accent)' }}>{fmt(app.calculatedEMI)}</div>
              <div className="text-muted">DTI Ratio: {app.dtiRatio ?? '—'}%</div>
            </div>
          </div>

          {/* Conditional Document Action Card when documents are missing or review pending */}
          {app.status === 'DOCUMENT_REVIEW_PENDING' && (
            <div className="card p-6" style={{ background: '#f59e0b10', borderColor: '#f59e0b', borderWidth: 1.5 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                <AlertTriangle color="#d97706" size={24} />
                <div>
                  <h4 style={{ margin: 0, color: '#d97706' }}>Action Required: Upload Verification Documents</h4>
                  <p style={{ margin: '4px 0 0', fontSize: '.88rem', color: 'var(--text-muted)' }}>
                    Documents are mandatory for approval. Once uploaded, if low-risk (Score ≤ 30), this application will be <strong>Auto-Approved</strong> immediately. If medium-risk, it will be routed to the Operations Manager.
                  </p>
                </div>
              </div>
              {docUploadMsg && <div style={{ marginBottom: 12, fontWeight: 600, fontSize: '.9rem' }}>{docUploadMsg}</div>}
              <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                <input
                  className="form-input"
                  style={{ flex: 1, minWidth: 250 }}
                  value={uploadDocIds}
                  onChange={e => setUploadDocIds(e.target.value)}
                  placeholder="e.g. DOC-KYC-01, DOC-SALARY-01"
                />
                <button className="btn btn-primary" onClick={doUploadDocuments} disabled={uploadingDoc}>
                  <UploadCloud size={16} style={{ marginRight: 6 }} />
                  {uploadingDoc ? 'Uploading…' : 'Submit Verification Documents'}
                </button>
              </div>
            </div>
          )}

          {/* Customer */}
          <div className="card p-6">
            <div className="card-header-title" style={{ marginBottom:16 }}>Customer Information</div>
            <div className="detail-grid">
              {[
                ['Customer ID',     app.customerId],
                ['Full Name',       app.customerName],
                ['Email',           app.customerEmail],
                ['Phone',           app.customerPhone],
                ['Employment',      app.employmentType],
                ['Monthly Income',  fmt(app.monthlyIncome)],
                ['Existing Liabilities', fmt(app.existingLiabilities)],
              ].map(([l,v]) => (
                <div key={l} className="detail-field">
                  <div className="detail-field-label">{l}</div>
                  <div className="detail-field-value">{v}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Loan */}
          <div className="card p-6">
            <div className="card-header-title" style={{ marginBottom:16 }}>Loan Details</div>
            <div className="detail-grid">
              {[
                ['Application ID',      app.applicationId || app.id],
                ['Scheme ID',           app.schemeId],
                ['Loan Type',           app.loanType?.replace(/_/g,' ')],
                ['Amount',              fmt(app.loanAmount)],
                ['Tenure',              app.tenureMonths + ' months'],
                ['Interest Rate',       app.interestRate + '% p.a.'],
                ['Status',              app.status],
                ['Documents Provided',  app.documents?.length > 0 || app.status === 'APPROVED' ? '✅ Verified' : '⚠️ Pending'],
                ['Orchestration ID',    app.orchestrationInstanceId || '—'],
                ['Assigned Manager',    app.assignedManager || '—'],
                ['Decision Remarks',    app.decisionRemarks || '—'],
                ['Created At',          new Date(app.createdAt).toLocaleString()],
                ['Updated At',          new Date(app.updatedAt).toLocaleString()],
              ].map(([l,v]) => (
                <div key={l} className="detail-field">
                  <div className="detail-field-label">{l}</div>
                  <div className="detail-field-value" style={{ fontSize:'.82rem' }}>{v}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {tab === 'audit' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom:20 }}>Audit Trail</div>
          {logs.length === 0 ? (
            <div className="empty">No audit logs available.</div>
          ) : (
            <ul className="timeline">
              {logs.map((l,i) => (
                <li key={i} className="timeline-item">
                  <div className="timeline-dot"/>
                  <div style={{ fontWeight:600, fontSize:'.88rem' }}>
                    {l.previousStatus
                      ? <>{l.previousStatus} <span style={{color:'var(--muted)'}}>→</span> {l.newStatus}</>
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

      {tab === 'manager' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom:8 }}>Manager Decision Callback</div>
          <div className="text-muted" style={{ marginBottom:20, fontSize:'.83rem' }}>
            Only applications in <strong>MANUAL_REVIEW_REQUIRED</strong> state can receive a manager decision.
          </div>

          {app.status !== 'MANUAL_REVIEW_REQUIRED' && (
            <div className="info-box">
              This application is currently <strong>{app.status}</strong>. Manager callback is only applicable when status is MANUAL_REVIEW_REQUIRED.
            </div>
          )}

          {cbError  && <div className="error-box">{cbError}</div>}
          {cbResult && (
            <div className="success-box">
              ✅ Callback submitted! New status: <strong>{cbResult.status}</strong>
            </div>
          )}

          <div className="form-grid">
            <div className="form-group">
              <label className="form-label">Decision *</label>
              <select className="form-select" value={decision} onChange={e=>setDecision(e.target.value)}>
                <option value="APPROVE">APPROVE</option>
                <option value="REJECT">REJECT</option>
                <option value="REQUEST_MORE_INFO">REQUEST_MORE_INFO</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Manager ID *</label>
              <input className="form-input" value={managerId} onChange={e=>setManagerId(e.target.value)}/>
            </div>
            <div className="form-group" style={{ gridColumn:'1 / -1' }}>
              <label className="form-label">Remarks *</label>
              <input className="form-input" value={remarks} onChange={e=>setRemarks(e.target.value)} placeholder="Enter underwriting justification"/>
            </div>
          </div>

          <div className="mt-4 flex-row">
            <button className="btn btn-success" onClick={doCallback} disabled={cbLoading}>
              {cbLoading ? 'Submitting…' : '✓ Submit Decision'}
            </button>
            <button className="btn btn-danger" onClick={() => { setDecision('REJECT'); }}>
              Pre-fill Reject
            </button>
          </div>
        </div>
      )}

      {tab === 'workflow' && (
        <div className="card p-6">
          <WorkflowDiagram
            currentStatus={app.status}
            riskScore={app.riskScore}
            hasDocuments={app.documents?.length > 0 || app.status === 'APPROVED'}
            decisionRemarks={app.decisionRemarks}
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
