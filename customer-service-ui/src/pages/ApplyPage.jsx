import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { FileText, Loader2, FolderUp, CheckCircle, X, AlertCircle } from 'lucide-react';
import {
  fetchSchemes, applyLoan, resolveLoanCustomer, ensureLoanCustomer,
  fetchDocumentTypes, uploadDocument, notifyDocumentUploaded, deleteDocumentById,
} from '../services/loanApi';
import { useSession } from '../lib/session';
import { useNotifications } from '../context/NotificationContext';

const EMP_TYPES = ['SALARIED', 'SELF_EMPLOYED', 'BUSINESS', 'STUDENT'];

export default function ApplyPage() {
  const { session, update } = useSession();
  const { addNotification } = useNotifications();
  const navigate = useNavigate();
  const location = useLocation();

  const [schemes, setSchemes] = useState([]);
  const [docTypes, setDocTypes] = useState([]);
  const [linking, setLinking] = useState(true);
  const [loanCustomerId, setLoanCustomerId] = useState(session?.loanCustomerId || '');

  const [form, setForm] = useState({
    monthlyIncome: 75000,
    existingLiabilities: 5000,
    employmentType: 'SALARIED',
    schemeId: location.state?.schemeId || 'SCHEME-PL-01',
    loanAmount: 200000,
    tenureMonths: 24,
    customerName: session?.name || '',
    customerPhone: session?.phoneNumber || '',
  });

  // Documents uploaded for THIS application
  const [pendingType, setPendingType] = useState('IDENTITY_PROOF');
  const [pendingFile, setPendingFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [docs, setDocs] = useState([]); // { id, name, type }
  const [docError, setDocError] = useState('');

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchSchemes().then((d) => setSchemes(Array.isArray(d) ? d : []));
    fetchDocumentTypes().then((t) => {
      if (Array.isArray(t) && t.length) {
        setDocTypes(t);
        setPendingType(t[0].typeCode || t[0].code || 'IDENTITY_PROOF');
      }
    });
  }, []);

  // Make sure this customer has a loan-service record BEFORE they upload docs /
  // apply, so everything is filed under one customer id the officer console shares.
  useEffect(() => {
    let alive = true;
    (async () => {
      setLinking(true);
      if (!session?.email) { setLinking(false); return; }
      let cid = session.loanCustomerId;
      if (!cid) {
        cid = await ensureLoanCustomer({
          email: session.email,
          fullName: session.name || form.customerName,
          mobileNumber: session.phoneNumber || form.customerPhone,
          onboardingStatus: session.onboardingStatus,
          externalRef: session.customerServiceId,
          incomeDetails: Number(form.monthlyIncome) || null,
        });
        if (cid && alive) update({ loanCustomerId: cid });
      }
      if (alive) { setLoanCustomerId(cid || ''); setLinking(false); }
    })();
    return () => { alive = false; };
    // eslint-disable-next-line
  }, [session?.email]);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const addDocument = async () => {
    if (!pendingFile) { setDocError('Choose a file first.'); return; }
    const cid = loanCustomerId || session?.loanCustomerId || session?.email;
    if (!cid) { setDocError('Still linking your profile — try again in a moment.'); return; }
    setUploading(true);
    setDocError('');
    const fd = new FormData();
    fd.append('customerId', cid);
    fd.append('documentType', pendingType);
    fd.append('docType', pendingType);
    fd.append('documentName', pendingFile.name);
    fd.append('file', pendingFile);
    try {
      const res = await uploadDocument(fd);
      const id = res.documentId || res.id;
      setDocs((d) => [...d, { id, name: pendingFile.name, type: pendingType }]);
      setPendingFile(null);
      const input = document.getElementById('apply-doc-file');
      if (input) input.value = '';
    } catch (e) {
      setDocError(e.message || 'Upload failed.');
    } finally {
      setUploading(false);
    }
  };

  const removeDocument = async (id) => {
    setDocs((d) => d.filter((x) => x.id !== id));
    try { await deleteDocumentById(id); } catch { /* best effort */ }
  };

  const submit = async () => {
    setLoading(true);
    setResult(null);
    setError('');
    try {
      const documentIds = docs.map((d) => String(d.id));
      const body = {
        customerId: loanCustomerId || session?.loanCustomerId || undefined,
        customerName: form.customerName,
        customerEmail: session.email,
        customerPhone: form.customerPhone,
        monthlyIncome: parseFloat(form.monthlyIncome),
        existingLiabilities: parseFloat(form.existingLiabilities || 0),
        employmentType: form.employmentType,
        schemeId: form.schemeId,
        loanAmount: parseFloat(form.loanAmount),
        tenureMonths: parseInt(form.tenureMonths),
        documentIds: documentIds.length > 0 ? documentIds : [],
      };
      let res = await applyLoan(body);
      if (!res.applicationId) {
        setError(res.message || JSON.stringify(res));
        return;
      }

      // If documents were uploaded in this form, link them to the application
      if (documentIds.length > 0) {
        try {
          const advanced = await notifyDocumentUploaded(res.applicationId, {
            documentIds,
            customerId: body.customerId || res.customerId,
          });
          if (advanced?.status) res = { ...res, ...advanced };
        } catch (e) {
          console.warn('document-uploaded callback note:', e);
        }
      }

      setResult(res);
      const loanCust = await resolveLoanCustomer(session.email);
      if (loanCust?.customerCode) update({ loanCustomerId: loanCust.customerCode });
      else if (res.customerId) update({ loanCustomerId: res.customerId });

      // Trigger real-time customer alert popup and chime
      const mgrName = res.assignedManagerName || (res.assignedManager === 'markj' ? 'Mark Johnson' : res.assignedManager || 'Dedicated Manager');
      const mgrPhone = res.assignedManagerPhone || '+1 (555) 019-2834';
      const mgrEmail = res.assignedManagerEmail || 'manager@bank.com';

      if (addNotification) {
        addNotification({
          id: `apply-mgr-${res.applicationId}`,
          title: `🎉 Case Assigned: ${mgrName}`,
          message: `Application ${res.applicationId} registered! Dedicated Manager ${mgrName} (📞 ${mgrPhone}, 📧 ${mgrEmail}) has been assigned to your loan case.`,
          type: 'MANAGER_ASSIGNED',
          link: `/applications/${res.applicationId}`,
        });
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const statusColor = (s) => (s === 'APPROVED' ? 'var(--green)' : s === 'REJECTED' ? 'var(--red)' : 'var(--yellow)');

  const mgrName = result?.assignedManagerName || (result?.assignedManager === 'markj' ? 'Mark Johnson' : result?.assignedManager || 'Dedicated Officer');
  const mgrPhone = result?.assignedManagerPhone || '+1 (555) 019-2834';
  const mgrEmail = result?.assignedManagerEmail || 'manager@bank.com';

  return (
    <div className="page">
      <div className="info-box">
        Submitting this form registers your loan application and assigns a dedicated Credit Manager to your case.
        Uploading KYC &amp; income documents is optional during application and can also be uploaded later under <strong>My Documents</strong>.
      </div>

      {error && <div className="error-box">{error}</div>}

      {result && (
        <div style={{ marginBottom: 24, padding: 20, borderRadius: 14, background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.12) 0%, rgba(13, 20, 44, 0.95) 100%)', border: '1.5px solid var(--green)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
            <div style={{ padding: 10, borderRadius: 10, background: 'rgba(16, 185, 129, 0.2)', color: 'var(--green)' }}>
              <CheckCircle size={26} />
            </div>
            <div>
              <h3 style={{ margin: 0, color: '#fff', fontSize: '1.15rem', fontWeight: 700 }}>
                🎉 Loan Application Submitted Successfully!
              </h3>
              <p style={{ margin: '4px 0 0', fontSize: '.86rem', color: 'var(--text-muted)' }}>
                Application ID: <strong style={{ color: '#fff' }}>{result.applicationId}</strong> · Customer ID: <strong className="font-mono" style={{ color: 'var(--accent)' }}>{result.customerId}</strong> · Status: <span style={{ color: statusColor(result.status), fontWeight: 700 }}>{result.status}</span>
              </p>
            </div>
          </div>

          {/* ASSIGNED LOAN MANAGER POPUP CARD */}
          <div style={{ background: 'rgba(0, 210, 255, 0.08)', border: '1px solid rgba(0, 210, 255, 0.35)', borderRadius: 12, padding: '16px 18px', marginBottom: 16 }}>
            <div style={{ fontSize: '.92rem', fontWeight: 700, color: 'var(--accent)', marginBottom: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
              👨‍💼 Dedicated Loan Manager Assigned
            </div>
            <div style={{ fontSize: '.84rem', color: 'var(--text)', lineHeight: 1.5 }}>
              A dedicated Relationship &amp; Underwriting Manager has been assigned to assist and review your loan application:
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, marginTop: 12 }}>
              <div style={{ background: 'rgba(0,0,0,0.35)', padding: '10px 14px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ fontSize: '.72rem', color: 'var(--muted)' }}>Manager Name</div>
                <div style={{ fontSize: '.9rem', fontWeight: 700, color: '#fff' }}>{mgrName} ({result.assignedManager})</div>
              </div>
              <div style={{ background: 'rgba(0,0,0,0.35)', padding: '10px 14px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ fontSize: '.72rem', color: 'var(--muted)' }}>Contact Mobile</div>
                <div style={{ fontSize: '.9rem', fontWeight: 700, color: 'var(--green)' }}>{mgrPhone}</div>
              </div>
              <div style={{ background: 'rgba(0,0,0,0.35)', padding: '10px 14px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.06)' }}>
                <div style={{ fontSize: '.72rem', color: 'var(--muted)' }}>Official Email</div>
                <div style={{ fontSize: '.9rem', fontWeight: 700, color: 'var(--accent)' }}>{mgrEmail}</div>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <button className="btn btn-primary" onClick={() => navigate(`/applications/${result.applicationId}`)}>
              View Application Details &amp; Progress →
            </button>
            <button className="btn btn-ghost" onClick={() => navigate('/documents')}>
              Upload Supporting Documents →
            </button>
          </div>
        </div>
      )}

      <div className="card p-6">
        <div className="card-header-title" style={{ marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
          <FileText size={18} /> Loan Application Form
        </div>

        <div style={{ marginBottom: 20, fontWeight: 600, color: 'var(--muted)', fontSize: '.8rem', textTransform: 'uppercase', letterSpacing: '.5px' }}>
          Your Details
        </div>
        <div className="form-grid" style={{ marginBottom: 24 }}>
          <div className="form-group">
            <label className="form-label">Email (your login)</label>
            <input className="form-input" value={session?.email || ''} readOnly style={{ opacity: 0.8, background: 'rgba(255,255,255,0.04)' }} />
          </div>
          <div className="form-group">
            <label className="form-label">Customer ID</label>
            <input className="form-input" value={linking ? 'Linking…' : (loanCustomerId || '(assigned on submit)')} readOnly style={{ opacity: 0.8, background: 'rgba(255,255,255,0.04)' }} />
          </div>
          <div className="form-group">
            <label className="form-label">Full Name *</label>
            <input className="form-input" value={form.customerName} onChange={set('customerName')} placeholder="e.g. Rahul Sharma" required />
          </div>
          <div className="form-group">
            <label className="form-label">Phone *</label>
            <input className="form-input" value={form.customerPhone} onChange={set('customerPhone')} placeholder="e.g. +91 9876543210" required />
          </div>
          <div className="form-group">
            <label className="form-label">Monthly Income (₹) *</label>
            <input className="form-input" type="number" value={form.monthlyIncome} onChange={set('monthlyIncome')} required />
          </div>
          <div className="form-group">
            <label className="form-label">Existing Liabilities (₹)</label>
            <input className="form-input" type="number" value={form.existingLiabilities} onChange={set('existingLiabilities')} />
          </div>
          <div className="form-group">
            <label className="form-label">Employment Type *</label>
            <select className="form-select" value={form.employmentType} onChange={set('employmentType')}>
              {EMP_TYPES.map((t) => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
            </select>
          </div>
        </div>

        <div style={{ marginBottom: 20, fontWeight: 600, color: 'var(--muted)', fontSize: '.8rem', textTransform: 'uppercase', letterSpacing: '.5px' }}>
          Loan Details
        </div>
        <div className="form-grid" style={{ marginBottom: 24 }}>
          <div className="form-group">
            <label className="form-label">Loan Scheme *</label>
            <select className="form-select" value={form.schemeId} onChange={set('schemeId')}>
              {schemes.map((s) => (
                <option key={s.schemeId} value={s.schemeId}>
                  {s.schemeName} ({s.schemeId}) — {s.baseInterestRate}% APR
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Loan Amount (₹) *</label>
            <input className="form-input" type="number" value={form.loanAmount} onChange={set('loanAmount')} required />
          </div>
          <div className="form-group">
            <label className="form-label">Tenure (Months) *</label>
            <input className="form-input" type="number" value={form.tenureMonths} onChange={set('tenureMonths')} required />
          </div>
        </div>

        <div style={{ marginBottom: 12, fontWeight: 600, color: 'var(--muted)', fontSize: '.8rem', textTransform: 'uppercase', letterSpacing: '.5px' }}>
          Supporting Documents *
        </div>
        <div style={{ border: '1px solid var(--border)', borderRadius: 10, padding: 16, marginBottom: 24, background: 'rgba(255,255,255,0.02)' }}>
          {docError && (
            <div className="error-box" style={{ margin: '0 0 12px', padding: '8px 12px', fontSize: '.8rem', display: 'flex', gap: 6, alignItems: 'center' }}>
              <AlertCircle size={14} /> {docError}
            </div>
          )}

          {docs.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 14 }}>
              {docs.map((d) => (
                <div key={d.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', borderRadius: 6, background: 'rgba(46,204,113,0.08)', border: '1px solid rgba(46,204,113,0.25)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '.82rem' }}>
                    <CheckCircle size={14} color="var(--green)" />
                    <span className="font-mono" style={{ color: 'var(--accent)' }}>{d.id}</span>
                    <span style={{ color: 'var(--muted)' }}>· {d.type} · {d.name}</span>
                  </div>
                  <button className="btn btn-ghost" style={{ padding: '2px 8px' }} onClick={() => removeDocument(d.id)}><X size={13} /></button>
                </div>
              ))}
            </div>
          )}

          <div className="form-grid" style={{ gridTemplateColumns: '1fr 1fr auto', alignItems: 'end', gap: 12 }}>
            <div className="form-group">
              <label className="form-label">Document Type</label>
              <select className="form-select" value={pendingType} onChange={(e) => setPendingType(e.target.value)}>
                {docTypes.length ? docTypes.map((t) => (
                  <option key={t.typeCode || t.code} value={t.typeCode || t.code}>
                    {t.categoryName || t.description || t.typeCode}
                  </option>
                )) : (
                  <>
                    <option value="IDENTITY_PROOF">Identity Proof</option>
                    <option value="INCOME_PROOF">Income Proof</option>
                    <option value="ADDRESS_PROOF">Address Proof</option>
                    <option value="BANK_STATEMENT">Bank Statement</option>
                  </>
                )}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">File (PDF / JPG / PNG)</label>
              <input id="apply-doc-file" className="form-input" type="file" accept=".pdf,.jpg,.jpeg,.png,.webp"
                onChange={(e) => setPendingFile(e.target.files?.[0] || null)} />
            </div>
            <button className="btn btn-ghost" onClick={addDocument} disabled={uploading || !pendingFile}>
              {uploading ? <><Loader2 size={14} className="spin" /> Uploading…</> : <><FolderUp size={14} /> Add</>}
            </button>
          </div>
        </div>

        <button className="btn btn-primary" onClick={submit} disabled={loading || linking || !form.customerName || !session?.email || docs.length === 0}>
          {loading ? <><Loader2 size={16} className="spin" /> Submitting Application…</> : '🚀 Submit Application'}
        </button>
        {docs.length === 0 && <div style={{ fontSize: '.75rem', color: 'var(--muted)', marginTop: 8 }}>Upload at least one supporting document to enable submission.</div>}
      </div>
    </div>
  );
}
