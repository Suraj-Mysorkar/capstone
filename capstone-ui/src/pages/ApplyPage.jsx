import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchSchemes, applyLoan } from '../services/api';
import { FileText } from 'lucide-react';

const EMP_TYPES = ['SALARIED','SELF_EMPLOYED','BUSINESS','STUDENT'];

const DEFAULTS = {
  customerId: 'CUST-1001',
  customerName: 'Alice Johnson',
  customerEmail: 'alice.johnson@example.com',
  customerPhone: '+14155552671',
  monthlyIncome: 180000,
  existingLiabilities: 3000,
  employmentType: 'SALARIED',
  schemeId: 'SCHEME-PL-01',
  loanAmount: 150000,
  tenureMonths: 24,
  documentIds: 'DOC-KYC-1001, DOC-INCOME-1001',
};

const SCENARIOS = [
  {
    label: '⚡ Low Risk + Docs Provided (Auto-Approval)',
    data: { customerId: 'CUST-1001', customerName: 'Alice Johnson', customerEmail: 'alice.johnson@example.com', customerPhone: '+14155552671', monthlyIncome: 180000, existingLiabilities: 3000, employmentType: 'SALARIED', schemeId: 'SCHEME-PL-01', loanAmount: 150000, tenureMonths: 24, documentIds: 'DOC-KYC-1001, DOC-INCOME-1001' },
  },
  {
    label: '📄 Low Risk + Missing Docs (Awaiting Documents)',
    data: { customerId: 'CUST-1002', customerName: 'David Miller', customerEmail: 'david.miller@example.com', customerPhone: '+14155553344', monthlyIncome: 150000, existingLiabilities: 2000, employmentType: 'SALARIED', schemeId: 'SCHEME-PL-01', loanAmount: 100000, tenureMonths: 12, documentIds: '' },
  },
  {
    label: '👔 Medium Risk + Docs Provided (Manual Review)',
    data: { customerId: 'CUST-3003', customerName: 'Elena Rostova', customerEmail: 'elena.rostova@example.com', customerPhone: '+14155557766', monthlyIncome: 75000, existingLiabilities: 15000, employmentType: 'SELF_EMPLOYED', schemeId: 'SCHEME-PL-01', loanAmount: 400000, tenureMonths: 24, documentIds: 'DOC-GST-3003' },
  },
  {
    label: '⚠️ Medium Risk + Missing Docs (Awaiting Documents)',
    data: { customerId: 'CUST-3004', customerName: 'Carlos Gomez', customerEmail: 'carlos.gomez@example.com', customerPhone: '+14155558899', monthlyIncome: 65000, existingLiabilities: 12000, employmentType: 'SELF_EMPLOYED', schemeId: 'SCHEME-PL-01', loanAmount: 350000, tenureMonths: 24, documentIds: '' },
  },
  {
    label: '❌ High Risk (Direct Auto-Rejection)',
    data: { customerId: 'CUST-2002', customerName: 'Bob Overleveraged', customerEmail: 'bob.debt@example.com', customerPhone: '+14155559988', monthlyIncome: 30000, existingLiabilities: 25000, employmentType: 'STUDENT', schemeId: 'SCHEME-PL-01', loanAmount: 800000, tenureMonths: 60, documentIds: '' },
  },
];

export default function ApplyPage() {
  const [schemes, setSchemes] = useState([]);
  const [form, setForm]       = useState(DEFAULTS);
  const [loading, setLoading] = useState(false);
  const [result, setResult]   = useState(null);
  const [error, setError]     = useState('');
  const navigate = useNavigate();

  useEffect(() => { fetchSchemes().then(d => setSchemes(Array.isArray(d) ? d : [])); }, []);

  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }));
  const load = s => setForm({ ...s.data });

  const submit = async () => {
    setLoading(true); setResult(null); setError('');
    try {
      const body = {
        ...form,
        monthlyIncome:       parseFloat(form.monthlyIncome),
        existingLiabilities: parseFloat(form.existingLiabilities),
        loanAmount:          parseFloat(form.loanAmount),
        tenureMonths:        parseInt(form.tenureMonths),
        documentIds:         form.documentIds ? form.documentIds.split(',').map(s=>s.trim()).filter(Boolean) : [],
      };
      const res = await applyLoan(body);
      if (res.id || res.applicationId) setResult(res);
      else setError(res.message || JSON.stringify(res));
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const statusColor = s => s === 'APPROVED' ? 'var(--green)' : s === 'REJECTED' ? 'var(--red)' : 'var(--yellow)';

  return (
    <div className="page">
      {/* Quick Scenarios */}
      <div className="card p-6" style={{ marginBottom:24 }}>
        <div className="card-header-title" style={{ marginBottom:14 }}>Quick Demo Scenarios</div>
        <div className="flex-row" style={{ flexWrap:'wrap', gap:10 }}>
          {SCENARIOS.map(s => (
            <button key={s.label} className="btn btn-ghost" onClick={() => load(s)}>{s.label}</button>
          ))}
        </div>
      </div>

      {error  && <div className="error-box">{error}</div>}

      {result && (
        <div className="card p-6" style={{ marginBottom:24, borderColor: statusColor(result.status), borderWidth:1.5 }}>
          <div className="card-header-title" style={{ color: statusColor(result.status), marginBottom:16 }}>
            Application Submitted — {result.status}
          </div>
          <div className="detail-grid">
            {[
              ['Application ID',    result.applicationId],
              ['Status',           result.status],
              ['Risk Score',       result.riskScore ?? '—'],
              ['Decision Remarks', result.decisionRemarks],
              ['Orchestration ID', result.orchestrationInstanceId || '—'],
            ].map(([l,v]) => (
              <div key={l} className="detail-field">
                <div className="detail-field-label">{l}</div>
                <div className="detail-field-value" style={{ fontSize:'.82rem' }}>{v}</div>
              </div>
            ))}
          </div>
          <div className="mt-4">
            <button className="btn btn-primary" onClick={() => navigate(`/applications/${result.applicationId}`)}>
              View Full Details →
            </button>
          </div>
        </div>
      )}

      {/* Application Form */}
      <div className="card p-6">
        <div className="card-header-title" style={{ marginBottom:20, display:'flex', alignItems:'center', gap:8 }}>
          <FileText size={18}/> Loan Application Form
        </div>

        <div style={{ marginBottom:20, fontWeight:600, color:'var(--muted)', fontSize:'.8rem', textTransform:'uppercase', letterSpacing:'.5px' }}>Customer Details</div>
        <div className="form-grid" style={{ marginBottom:24 }}>
          <div className="form-group">
            <label className="form-label">Customer ID *</label>
            <input className="form-input" value={form.customerId} onChange={set('customerId')}/>
          </div>
          <div className="form-group">
            <label className="form-label">Full Name *</label>
            <input className="form-input" value={form.customerName} onChange={set('customerName')}/>
          </div>
          <div className="form-group">
            <label className="form-label">Email *</label>
            <input className="form-input" type="email" value={form.customerEmail} onChange={set('customerEmail')}/>
          </div>
          <div className="form-group">
            <label className="form-label">Phone *</label>
            <input className="form-input" value={form.customerPhone} onChange={set('customerPhone')}/>
          </div>
          <div className="form-group">
            <label className="form-label">Monthly Income (₹) *</label>
            <input className="form-input" type="number" value={form.monthlyIncome} onChange={set('monthlyIncome')}/>
          </div>
          <div className="form-group">
            <label className="form-label">Existing Liabilities (₹)</label>
            <input className="form-input" type="number" value={form.existingLiabilities} onChange={set('existingLiabilities')}/>
          </div>
          <div className="form-group">
            <label className="form-label">Employment Type *</label>
            <select className="form-select" value={form.employmentType} onChange={set('employmentType')}>
              {EMP_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g,' ')}</option>)}
            </select>
          </div>
        </div>

        <div style={{ marginBottom:20, fontWeight:600, color:'var(--muted)', fontSize:'.8rem', textTransform:'uppercase', letterSpacing:'.5px' }}>Loan Details</div>
        <div className="form-grid" style={{ marginBottom:24 }}>
          <div className="form-group">
            <label className="form-label">Loan Scheme *</label>
            <select className="form-select" value={form.schemeId} onChange={set('schemeId')}>
              {schemes.map(s => <option key={s.schemeId} value={s.schemeId}>{s.schemeId}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Loan Amount (₹) *</label>
            <input className="form-input" type="number" value={form.loanAmount} onChange={set('loanAmount')}/>
          </div>
          <div className="form-group">
            <label className="form-label">Tenure (Months) *</label>
            <input className="form-input" type="number" value={form.tenureMonths} onChange={set('tenureMonths')}/>
          </div>
          <div className="form-group">
            <label className="form-label">Document IDs (comma-separated, optional)</label>
            <input className="form-input" value={form.documentIds} onChange={set('documentIds')} placeholder="DOC-xxx, DOC-yyy"/>
          </div>
        </div>

        <button className="btn btn-primary" onClick={submit} disabled={loading}>
          {loading ? 'Submitting…' : '🚀 Submit Application'}
        </button>
      </div>
    </div>
  );
}
