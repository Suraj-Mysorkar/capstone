import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { FileText, Loader2, FolderUp, CheckCircle, X, AlertCircle } from 'lucide-react';
import {
  fetchSchemes, applyLoan, resolveLoanCustomer, ensureLoanCustomer,
  fetchDocumentTypes, uploadDocument, notifyDocumentUploaded, deleteDocumentById,
} from '../services/loanApi';
import { useSession } from '../lib/session';

const EMP_TYPES = ['SALARIED', 'SELF_EMPLOYED', 'BUSINESS', 'STUDENT'];

export default function ApplyPage() {
  const { session, update } = useSession();
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
    if (docs.length === 0) { setError('Please upload at least one supporting document.'); return; }
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
        documentIds,
      };
      let res = await applyLoan(body);
      if (!res.applicationId) {
        setError(res.message || JSON.stringify(res));
        return;
      }

      // Link the uploaded documents to the new application and push the workflow
      // past the document-review gate (same callback the officer console relies on).
      try {
        const advanced = await notifyDocumentUploaded(res.applicationId, {
          documentIds,
          customerId: body.customerId || res.customerId,
        });
        if (advanced?.status) res = { ...res, ...advanced };
      } catch (e) {
        console.warn('document-uploaded callback failed (non-fatal):', e);
      }

      setResult(res);
      const loanCust = await resolveLoanCustomer(session.email);
      if (loanCust?.customerCode) update({ loanCustomerId: loanCust.customerCode });
      else if (res.customerId) update({ loanCustomerId: res.customerId });
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const statusColor = (s) => (s === 'APPROVED' ? 'var(--green)' : s === 'REJECTED' ? 'var(--red)' : 'var(--yellow)');

  return (
    <div className="page">
      <div className="info-box">
        Submitting this form runs the bank's automated credit workflow. Upload your
        KYC &amp; income documents below — they are filed under your customer id
        {loanCustomerId ? <> (<span className="font-mono">{loanCustomerId}</span>)</> : null} and
        submitted with the application. Track progress under <strong>My Applications</strong>.
      </div>

      {error && <div className="error-box">{error}</div>}

      {result && (
        <div className="card p-6" style={{ marginBottom: 24, borderColor: statusColor(result.status), borderWidth: 1.5 }}>
          <div className="card-header-title" style={{ color: statusColor(result.status), marginBottom: 16 }}>
            Application Submitted — {result.status}
          </div>
          <div className="detail-grid">
            {[
              ['Application ID', result.applicationId],
              ['Customer ID', result.customerId || '—'],
              ['Status', result.status],
              ['Risk Score', result.riskScore != null ? `${result.riskScore}/100` : '—'],
              ['DTI Ratio', result.dtiRatio != null ? `${result.dtiRatio}%` : '—'],
              ['Calculated EMI', result.calculatedEMI != null ? `₹${result.calculatedEMI.toLocaleString('en-IN')}` : '—'],
              ['Documents', docs.map((d) => d.id).join(', ') || '—'],
              ['Decision Remarks', result.decisionRemarks],
            ].map(([l, v]) => (
              <div key={l} className="detail-field">
                <div className="detail-field-label">{l}</div>
                <div className="detail-field-value" style={{ fontSize: '.82rem' }}>{v}</div>
              </div>
            ))}
          </div>
          <div className="mt-4">
            <button className="btn btn-primary" onClick={() => navigate(`/applications/${result.applicationId}`)}>
              View Status &amp; Next Steps →
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
