import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft, UploadCloud, AlertTriangle, Search,
} from 'lucide-react';
import {
  fetchApplicationById,
  fetchAuditLogs,
  notifyDocumentUploaded,
  fetchCustomerDocuments,
} from '../services/loanApi';

function statusBadge(s) {
  if (s === 'APPROVED') return <span className="badge badge-approved">Approved</span>;
  if (s === 'REJECTED') return <span className="badge badge-rejected">Rejected</span>;
  if (s === 'MANUAL_REVIEW_REQUIRED') return <span className="badge badge-review">Under Review</span>;
  if (s === 'DOCUMENT_REVIEW_PENDING') return <span className="badge badge-warning">Awaiting Documents</span>;
  return <span className="badge badge-default">{(s || '').replace(/_/g, ' ')}</span>;
}

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n || 0);
}

export default function ApplicationDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [app, setApp] = useState(null);
  const [logs, setLogs] = useState([]);
  const [customerDocs, setCustomerDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('details');

  const [uploadDocIds, setUploadDocIds] = useState('');
  const [uploadingDoc, setUploadingDoc] = useState(false);
  const [docUploadMsg, setDocUploadMsg] = useState('');

  const load = async () => {
    if (!id) { setApp(null); setLoading(false); return; }
    setLoading(true);
    try {
      const [a, l] = await Promise.all([fetchApplicationById(id), fetchAuditLogs(id)]);
      setApp(a);
      setLogs(Array.isArray(l) ? l : []);
      if (a?.customerId) {
        const docs = await fetchCustomerDocuments(a.customerId);
        setCustomerDocs(Array.isArray(docs) ? docs : []);
      }
    } catch (e) {
      console.error('Error loading application data', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);

  const doUploadDocuments = async () => {
    setUploadingDoc(true);
    setDocUploadMsg('');
    try {
      const docList = uploadDocIds.split(',').map((s) => s.trim()).filter(Boolean);
      const updated = await notifyDocumentUploaded(id, {
        documentIds: docList,
        customerId: app?.customerId || undefined,
      });
      setDocUploadMsg('✅ Documents submitted. Your application has moved to the next stage.');
      if (updated?.status) setApp(updated);
      await load();
    } catch (err) {
      setDocUploadMsg('❌ Error submitting documents: ' + err.message);
    } finally {
      setUploadingDoc(false);
    }
  };

  if (loading) return <div className="page"><div className="spinner" /></div>;
  if (!app) return <div className="page"><div className="error-box">Application not found.</div></div>;

  const riskColor = app.riskScore == null ? 'var(--muted)'
    : app.riskScore <= 30 ? 'var(--green)'
    : app.riskScore >= 70 ? 'var(--red)' : 'var(--yellow)';

  return (
    <div className="page">
      <div className="flex-row" style={{ marginBottom: 24 }}>
        <button className="btn btn-ghost" style={{ padding: '7px 14px', fontSize: '.82rem' }} onClick={() => navigate('/applications')}>
          <ArrowLeft size={15} /> Back
        </button>
        <div style={{ fontWeight: 700, fontSize: '1.1rem', marginLeft: 4 }}>
          Application: {app.applicationId}
        </div>
        <div className="ml-auto">{statusBadge(app.status)}</div>
      </div>

      <div className="tabs">
        {['details', 'audit'].map((t) => (
          <button key={t} className={`tab-btn${tab === t ? ' active' : ''}`} onClick={() => setTab(t)}>
            {{ details: 'Overview', audit: 'Progress Trail' }[t]}
          </button>
        ))}
      </div>

      {tab === 'details' && (
        <div className="gap-4">
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            <div className="card" style={{ flex: 1, minWidth: 200, padding: '20px 24px' }}>
              <div className="stat-label">Risk Score</div>
              <div style={{ fontSize: '3rem', fontWeight: 800, color: riskColor }}>{app.riskScore ?? '—'}</div>
              <div className="text-muted" style={{ marginTop: 4 }}>
                {app.riskScore == null ? '' : app.riskScore <= 30 ? 'Low Risk – Eligible for Auto-Approval' : app.riskScore >= 70 ? 'High Risk' : 'Medium Risk – Underwriter Review'}
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

          {/* DEDICATED ASSIGNED LOAN MANAGER CARD */}
          <div style={{ padding: '16px 20px', borderRadius: 12, background: 'linear-gradient(135deg, rgba(0, 210, 255, 0.08) 0%, rgba(13, 20, 44, 0.9) 100%)', border: '1px solid rgba(0, 210, 255, 0.3)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12, marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{ padding: 8, borderRadius: 8, background: 'rgba(0, 210, 255, 0.15)', color: 'var(--accent)', fontSize: '1.2rem' }}>
                  👨‍💼
                </div>
                <div>
                  <div style={{ fontSize: '.95rem', fontWeight: 700, color: 'var(--accent)' }}>
                    Assigned Loan Officer &amp; Relationship Manager
                  </div>
                  <div style={{ fontSize: '.8rem', color: 'var(--text-muted)' }}>
                    Dedicated bank officer assigned to process and assist your loan application
                  </div>
                </div>
              </div>
              <span className="badge badge-approved" style={{ fontSize: '.75rem' }}>Active &amp; Assigned</span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
              <div style={{ background: 'rgba(0,0,0,0.3)', padding: '10px 14px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ fontSize: '.72rem', color: 'var(--muted)' }}>Manager Name</div>
                <div style={{ fontSize: '.9rem', fontWeight: 700, color: '#fff' }}>Mark Johnson ({app.assignedManager || 'markj'})</div>
              </div>
              <div style={{ background: 'rgba(0,0,0,0.3)', padding: '10px 14px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ fontSize: '.72rem', color: 'var(--muted)' }}>Contact Mobile</div>
                <div style={{ fontSize: '.9rem', fontWeight: 700, color: 'var(--green)' }}>+1 (555) 019-2834</div>
              </div>
              <div style={{ background: 'rgba(0,0,0,0.3)', padding: '10px 14px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ fontSize: '.72rem', color: 'var(--muted)' }}>Official Email</div>
                <div style={{ fontSize: '.9rem', fontWeight: 700, color: 'var(--accent)' }}>mark.johnson@bank.com</div>
              </div>
            </div>
          </div>

          {app.status === 'DOCUMENT_REVIEW_PENDING' && (
            <div className="card p-6" style={{ background: '#f59e0b10', borderColor: '#f59e0b', borderWidth: 1.5 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                <AlertTriangle color="#d97706" size={24} />
                <div>
                  <h4 style={{ margin: 0, color: '#d97706' }}>Action Required: Submit Verification Documents</h4>
                  <p style={{ margin: '4px 0 0', fontSize: '.88rem', color: 'var(--muted)' }}>
                    Upload your KYC & income documents on the <strong>My Documents</strong> page,
                    then enter their Document IDs below to advance your application.
                  </p>
                </div>
              </div>
              {docUploadMsg && <div style={{ marginBottom: 12, fontWeight: 600, fontSize: '.9rem' }}>{docUploadMsg}</div>}
              <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                <button className="btn btn-ghost" onClick={() => navigate('/documents')}>
                  <UploadCloud size={15} /> Go to My Documents
                </button>
                <input
                  className="form-input"
                  style={{ flex: 1, minWidth: 220 }}
                  value={uploadDocIds}
                  onChange={(e) => setUploadDocIds(e.target.value)}
                  placeholder="e.g. DOC-1024, DOC-1025"
                />
                <button className="btn btn-primary" onClick={doUploadDocuments} disabled={uploadingDoc || !uploadDocIds.trim()}>
                  <UploadCloud size={16} style={{ marginRight: 6 }} />
                  {uploadingDoc ? 'Submitting…' : 'Submit Document IDs'}
                </button>
              </div>
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div className="card p-6">
              <div className="card-header-title" style={{ marginBottom: 16 }}>Application Summary</div>
              <div className="detail-grid">
                {[
                  ['Application ID', app.applicationId],
                  ['Customer ID', app.customerId],
                  ['Scheme', `${app.schemeName || ''} (${app.schemeId})`],
                  ['Employment', app.employmentType],
                  ['Monthly Income', fmt(app.monthlyIncome)],
                  ['Existing Liabilities', fmt(app.existingLiabilities)],
                  ['Decision Remarks', app.decisionRemarks || '—'],
                ].map(([l, v]) => (
                  <div key={l} className="detail-field">
                    <div className="detail-field-label">{l}</div>
                    <div className="detail-field-value" style={{ fontSize: '.85rem' }}>{v ?? '—'}</div>
                  </div>
                ))}
              </div>
            </div>

            <div className="card p-6">
              <div className="card-header-title" style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>My Documents ({customerDocs.length})</span>
                <button className="btn btn-ghost" style={{ fontSize: '.75rem', padding: '3px 8px' }} onClick={() => navigate('/documents')}>
                  <Search size={12} /> Manage →
                </button>
              </div>
              {customerDocs.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {customerDocs.map((d) => (
                    <div key={d.documentId || d.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', borderRadius: 6, background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border)' }}>
                      <div>
                        <div style={{ fontSize: '.82rem', fontWeight: 600 }}>
                          ID: {d.documentId || d.id} — {d.documentName || d.originalFileName || d.documentType}
                        </div>
                        <div style={{ fontSize: '.72rem', color: 'var(--muted)' }}>Type: {d.documentType}</div>
                      </div>
                      <span className={`badge ${d.status === 'VERIFIED' || d.status === 'APPROVED' ? 'badge-approved' : d.status === 'REJECTED' || d.status === 'ACTION_REQUIRED' ? 'badge-rejected' : 'badge-under-review'}`} style={{ fontSize: '.7rem' }}>
                        {d.status || 'UPLOADED'}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{ color: 'var(--muted)', fontSize: '.82rem' }}>No documents uploaded yet.</div>
              )}
            </div>
          </div>
        </div>
      )}

      {tab === 'audit' && (
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 16 }}>Progress Trail</div>
          {logs.length === 0 ? (
            <div className="empty">No progress events yet.</div>
          ) : (
            <ul className="timeline">
              {logs.map((l, i) => (
                <li key={i} className="timeline-item">
                  <div className="timeline-dot" />
                  <div style={{ fontWeight: 600, fontSize: '.88rem' }}>
                    {l.previousStatus ? <>{l.previousStatus} <span style={{ color: 'var(--muted)' }}>→</span> {l.newStatus}</> : l.newStatus}
                  </div>
                  <div className="timeline-meta">{new Date(l.timestamp).toLocaleString()}</div>
                  {l.comments && <div className="timeline-comment">{l.comments}</div>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

    </div>
  );
}
