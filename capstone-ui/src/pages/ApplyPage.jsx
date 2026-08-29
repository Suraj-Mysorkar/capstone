import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchSchemes, fetchCustomers, applyLoan } from '../services/api';
import { FileText, Users, UserPlus, UserCheck, Loader2 } from 'lucide-react';

const EMP_TYPES = ['SALARIED', 'SELF_EMPLOYED', 'BUSINESS', 'STUDENT'];

const INITIAL_FORM = {
  selectedCustomer: 'new',
  customerId: '',
  customerName: '',
  customerEmail: '',
  customerPhone: '',
  monthlyIncome: 75000,
  existingLiabilities: 5000,
  employmentType: 'SALARIED',
  schemeId: 'SCHEME-PL-01',
  loanAmount: 200000,
  tenureMonths: 24,
  documentIds: '',
};

const SCENARIOS = [
  {
    label: '⚡ Low Risk + Docs Provided (Auto-Approval)',
    data: {
      selectedCustomer: 'new',
      customerName: 'Alice Johnson',
      customerEmail: 'alice.johnson@example.com',
      customerPhone: '+14155552671',
      monthlyIncome: 180000,
      existingLiabilities: 3000,
      employmentType: 'SALARIED',
      schemeId: 'SCHEME-PL-01',
      loanAmount: 150000,
      tenureMonths: 24,
      documentIds: 'DOC-KYC-1001, DOC-INCOME-1001'
    },
  },
  {
    label: '📄 Low Risk + Missing Docs (Awaiting Documents)',
    data: {
      selectedCustomer: 'new',
      customerName: 'David Miller',
      customerEmail: 'david.miller@example.com',
      customerPhone: '+14155553344',
      monthlyIncome: 150000,
      existingLiabilities: 2000,
      employmentType: 'SALARIED',
      schemeId: 'SCHEME-PL-01',
      loanAmount: 100000,
      tenureMonths: 12,
      documentIds: ''
    },
  },
  {
    label: '👔 Medium Risk + Docs Provided (Underwriter Review)',
    data: {
      selectedCustomer: 'new',
      customerName: 'Elena Rostova',
      customerEmail: 'elena.rostova@example.com',
      customerPhone: '+14155557766',
      monthlyIncome: 75000,
      existingLiabilities: 15000,
      employmentType: 'SELF_EMPLOYED',
      schemeId: 'SCHEME-PL-01',
      loanAmount: 400000,
      tenureMonths: 24,
      documentIds: 'DOC-GST-3003'
    },
  },
  {
    label: '⚠️ Medium Risk + Missing Docs (Awaiting Documents)',
    data: {
      selectedCustomer: 'new',
      customerName: 'Carlos Gomez',
      customerEmail: 'carlos.gomez@example.com',
      customerPhone: '+14155558899',
      monthlyIncome: 65000,
      existingLiabilities: 12000,
      employmentType: 'SELF_EMPLOYED',
      schemeId: 'SCHEME-PL-01',
      loanAmount: 350000,
      tenureMonths: 24,
      documentIds: ''
    },
  },
  {
    label: '❌ High Risk (Direct Auto-Rejection)',
    data: {
      selectedCustomer: 'new',
      customerName: 'Bob Overleveraged',
      customerEmail: 'bob.debt@example.com',
      customerPhone: '+14155559988',
      monthlyIncome: 30000,
      existingLiabilities: 25000,
      employmentType: 'STUDENT',
      schemeId: 'SCHEME-PL-01',
      loanAmount: 800000,
      tenureMonths: 60,
      documentIds: ''
    },
  },
];

export default function ApplyPage() {
  const [schemes, setSchemes] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [form, setForm] = useState(INITIAL_FORM);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    fetchSchemes().then(d => setSchemes(Array.isArray(d) ? d : []));
    fetchCustomers().then(d => {
      const list = Array.isArray(d) ? d : [];
      setCustomers(list);
      if (list.length > 0) {
        const first = list[0];
        setForm(f => ({
          ...f,
          selectedCustomer: String(first.customerId),
          customerId: first.customerCode || `CUST-${first.customerId}`,
          customerName: first.fullName || '',
          customerEmail: first.email || '',
          customerPhone: first.mobileNumber || '',
          monthlyIncome: first.incomeDetails || 75000,
          employmentType: first.employmentDetails && EMP_TYPES.includes(first.employmentDetails) ? first.employmentDetails : 'SALARIED',
        }));
      }
    });
  }, []);

  const handleCustomerChange = (e) => {
    const val = e.target.value;
    if (val === 'new') {
      setForm(f => ({
        ...f,
        selectedCustomer: 'new',
        customerId: '',
        customerName: '',
        customerEmail: '',
        customerPhone: '',
        monthlyIncome: 50000,
        employmentType: 'SALARIED',
      }));
    } else {
      const cust = customers.find(c => String(c.customerId) === val);
      if (cust) {
        setForm(f => ({
          ...f,
          selectedCustomer: val,
          customerId: cust.customerCode || `CUST-${cust.customerId}`,
          customerName: cust.fullName || '',
          customerEmail: cust.email || '',
          customerPhone: cust.mobileNumber || '',
          monthlyIncome: cust.incomeDetails || 75000,
          employmentType: cust.employmentDetails && EMP_TYPES.includes(cust.employmentDetails) ? cust.employmentDetails : 'SALARIED',
        }));
      }
    }
  };

  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }));

  const loadScenario = (s) => {
    setForm(f => ({
      ...f,
      ...s.data,
    }));
  };

  const submit = async () => {
    setLoading(true);
    setResult(null);
    setError('');
    try {
      const body = {
        customerId: form.customerId || undefined,
        customerName: form.customerName,
        customerEmail: form.customerEmail,
        customerPhone: form.customerPhone,
        monthlyIncome: parseFloat(form.monthlyIncome),
        existingLiabilities: parseFloat(form.existingLiabilities || 0),
        employmentType: form.employmentType,
        schemeId: form.schemeId,
        loanAmount: parseFloat(form.loanAmount),
        tenureMonths: parseInt(form.tenureMonths),
        documentIds: form.documentIds ? form.documentIds.split(',').map(s => s.trim()).filter(Boolean) : [],
      };
      const res = await applyLoan(body);
      if (res.id || res.applicationId) {
        setResult(res);
        // Refresh customer list to include newly created customer
        fetchCustomers().then(d => setCustomers(Array.isArray(d) ? d : []));
      } else {
        setError(res.message || JSON.stringify(res));
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const statusColor = s => s === 'APPROVED' ? 'var(--green)' : s === 'REJECTED' ? 'var(--red)' : 'var(--yellow)';

  return (
    <div className="page">
      {/* Quick Scenarios */}
      <div className="card p-6" style={{ marginBottom: 24 }}>
        <div className="card-header-title" style={{ marginBottom: 14 }}>Quick Demo Scenarios</div>
        <div className="flex-row" style={{ flexWrap: 'wrap', gap: 10 }}>
          {SCENARIOS.map(s => (
            <button key={s.label} className="btn btn-ghost" onClick={() => loadScenario(s)}>{s.label}</button>
          ))}
        </div>
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
              ['Decision Remarks', result.decisionRemarks],
              ['Orchestration ID', result.orchestrationInstanceId || '—'],
            ].map(([l, v]) => (
              <div key={l} className="detail-field">
                <div className="detail-field-label">{l}</div>
                <div className="detail-field-value" style={{ fontSize: '.82rem' }}>{v}</div>
              </div>
            ))}
          </div>
          <div className="mt-4">
            <button className="btn btn-primary" onClick={() => navigate(`/applications/${result.applicationId}`)}>
              View Full Details & Workflow →
            </button>
          </div>
        </div>
      )}

      {/* Application Form */}
      <div className="card p-6">
        <div className="card-header-title" style={{ marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
          <FileText size={18} /> Loan Application Form
        </div>

        {/* Customer Selector from Database */}
        <div style={{ marginBottom: 20, padding: '16px', background: 'rgba(255,255,255,0.03)', borderRadius: 10, border: '1px solid rgba(255,255,255,0.08)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, color: 'var(--accent)', fontWeight: 600, fontSize: '.85rem' }}>
            <Users size={16} /> Select Applicant from Database (Customers Table)
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <select
              className="form-select"
              value={form.selectedCustomer}
              onChange={handleCustomerChange}
              style={{ background: '#13182e', borderColor: 'rgba(0, 210, 255, 0.4)' }}
            >
              <option value="new">➕ Register / Enter New Customer</option>
              {customers.map(c => (
                <option key={c.customerId} value={String(c.customerId)}>
                  {c.customerCode || `CUST-${c.customerId}`} — {c.fullName} ({c.email}) [Status: {c.onboardingStatus}]
                </option>
              ))}
            </select>
          </div>
          <div style={{ fontSize: '.75rem', color: 'var(--muted)', marginTop: 6 }}>
            {form.selectedCustomer === 'new'
              ? '✨ Enter applicant details below. A new customer profile will be automatically saved in the database Customers table.'
              : `✅ Using existing database profile linked to ${form.customerId}.`}
          </div>
        </div>

        <div style={{ marginBottom: 20, fontWeight: 600, color: 'var(--muted)', fontSize: '.8rem', textTransform: 'uppercase', letterSpacing: '.5px' }}>
          Customer Details
        </div>
        <div className="form-grid" style={{ marginBottom: 24 }}>
          <div className="form-group">
            <label className="form-label">Customer ID (Auto-assigned)</label>
            <input
              className="form-input"
              value={form.customerId || '(Auto-assigned upon submission)'}
              readOnly
              style={{ opacity: 0.8, background: 'rgba(255,255,255,0.04)' }}
            />
          </div>
          <div className="form-group">
            <label className="form-label">Full Name *</label>
            <input
              className="form-input"
              value={form.customerName}
              onChange={set('customerName')}
              placeholder="e.g. Rahul Sharma"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Email *</label>
            <input
              className="form-input"
              type="email"
              value={form.customerEmail}
              onChange={set('customerEmail')}
              placeholder="e.g. rahul@example.com"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Phone *</label>
            <input
              className="form-input"
              value={form.customerPhone}
              onChange={set('customerPhone')}
              placeholder="e.g. +91 9876543210"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Monthly Income (₹) *</label>
            <input
              className="form-input"
              type="number"
              value={form.monthlyIncome}
              onChange={set('monthlyIncome')}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Existing Liabilities (₹)</label>
            <input
              className="form-input"
              type="number"
              value={form.existingLiabilities}
              onChange={set('existingLiabilities')}
            />
          </div>
          <div className="form-group">
            <label className="form-label">Employment Type *</label>
            <select className="form-select" value={form.employmentType} onChange={set('employmentType')}>
              {EMP_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
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
              {schemes.map(s => (
                <option key={s.schemeId} value={s.schemeId}>
                  {s.schemeName} ({s.schemeId}) — {s.baseInterestRate}% APR
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Loan Amount (₹) *</label>
            <input
              className="form-input"
              type="number"
              value={form.loanAmount}
              onChange={set('loanAmount')}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Tenure (Months) *</label>
            <input
              className="form-input"
              type="number"
              value={form.tenureMonths}
              onChange={set('tenureMonths')}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Document IDs (comma-separated, optional)</label>
            <input
              className="form-input"
              value={form.documentIds}
              onChange={set('documentIds')}
              placeholder="DOC-KYC-1001, DOC-INCOME-1001"
            />
          </div>
        </div>

        <button className="btn btn-primary" onClick={submit} disabled={loading || !form.customerName || !form.customerEmail}>
          {loading ? (
            <><Loader2 size={16} className="wf-spin" /> Submitting Application...</>
          ) : (
            '🚀 Submit Application'
          )}
        </button>
      </div>
    </div>
  );
}
