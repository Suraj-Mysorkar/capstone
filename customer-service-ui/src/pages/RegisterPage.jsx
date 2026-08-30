import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { UserPlus, Wand2 } from 'lucide-react';
import { registerCustomer } from '../services/api';
import { ISO_COUNTRIES } from '../lib/onboarding';

const EMPTY = {
  firstName: '', lastName: '', email: '', phoneNumber: '',
  addressLine1: '', addressLine2: '', city: '', state: '',
  postalCode: '', countryCode: 'US',
};

const SAMPLES = [
  {
    label: '🇺🇸 Jane Doe (Springfield, IL)',
    data: {
      firstName: 'Jane', lastName: 'Doe', email: `jane.doe.${Date.now()}@example.com`,
      phoneNumber: '+14155552671', addressLine1: '123 Main St', addressLine2: '',
      city: 'Springfield', state: 'IL', postalCode: '62701', countryCode: 'US',
    },
  },
  {
    label: '🇮🇳 Rohan Mehta (Bengaluru)',
    data: {
      firstName: 'Rohan', lastName: 'Mehta', email: `rohan.mehta.${Date.now()}@example.com`,
      phoneNumber: '+919812345678', addressLine1: '42 MG Road', addressLine2: 'Apt 5B',
      city: 'Bengaluru', state: 'KA', postalCode: '560001', countryCode: 'IN',
    },
  },
];

const FIELDS = [
  ['firstName', 'First name *', 'text'],
  ['lastName', 'Last name *', 'text'],
  ['email', 'Email *', 'email'],
  ['phoneNumber', 'Phone number', 'text'],
  ['addressLine1', 'Address line 1', 'text'],
  ['addressLine2', 'Address line 2', 'text'],
  ['city', 'City', 'text'],
  ['state', 'State', 'text'],
  ['postalCode', 'Postal code', 'text'],
];

export default function RegisterPage() {
  const [form, setForm] = useState(EMPTY);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async () => {
    setSubmitting(true);
    setError('');
    setFieldErrors({});
    setResult(null);
    try {
      const payload = Object.fromEntries(
        Object.entries(form).map(([k, v]) => [k, v === '' ? null : v]),
      );
      const created = await registerCustomer(payload);
      setResult(created);
      setForm(EMPTY);
    } catch (e) {
      if (e.status === 400 && e.body?.fieldErrors) setFieldErrors(e.body.fieldErrors);
      setError(
        e.status === 401 ? 'Not authorized — set a bearer token on the Settings page.'
          : e.status === 403 ? 'Forbidden — your token lacks the customers.write scope.'
          : e.status === 409 ? 'A customer with that email already exists.'
          : e.message,
      );
    }
    setSubmitting(false);
  };

  return (
    <div className="page">
      <div className="filter-bar">
        {SAMPLES.map((s) => (
          <button key={s.label} className="filter-btn" onClick={() => { setForm({ ...EMPTY, ...s.data }); setResult(null); setError(''); }}>
            <Wand2 size={13} style={{ marginRight: 6, verticalAlign: '-2px' }} />
            {s.label}
          </button>
        ))}
      </div>

      {error && <div className="error-box">{error}</div>}
      {result && (
        <div className="success-box">
          ✅ Customer registered — status <strong>{result.onboardingStatus}</strong>.<br />
          ID: <span className="font-mono">{result.id}</span>{' '}
          <Link to={`/customers/${result.id}`} style={{ color: 'var(--green)', textDecoration: 'underline' }}>
            open profile →
          </Link>
        </div>
      )}

      <div className="card p-6">
        <div className="card-header-title" style={{ marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
          <UserPlus size={18} /> New Customer Profile
        </div>

        <div className="form-grid">
          {FIELDS.map(([k, label, type]) => (
            <div className="form-group" key={k}>
              <label className="form-label">{label}</label>
              <input
                className="form-input"
                type={type}
                value={form[k]}
                onChange={set(k)}
                placeholder={k === 'email' ? 'name@example.com' : ''}
              />
              {fieldErrors[k] && (
                <span style={{ fontSize: '.72rem', color: 'var(--red)' }}>{fieldErrors[k]}</span>
              )}
            </div>
          ))}

          <div className="form-group">
            <label className="form-label">Country code</label>
            <select className="form-select" value={form.countryCode} onChange={set('countryCode')}>
              {ISO_COUNTRIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
            {fieldErrors.countryCode && (
              <span style={{ fontSize: '.72rem', color: 'var(--red)' }}>{fieldErrors.countryCode}</span>
            )}
          </div>
        </div>

        <div className="mt-4 flex-row">
          <button className="btn btn-primary" onClick={submit} disabled={submitting}>
            {submitting ? 'Registering…' : 'Register Customer'}
          </button>
          <button className="btn btn-ghost" onClick={() => { setForm(EMPTY); setError(''); setResult(null); setFieldErrors({}); }}>
            Clear
          </button>
        </div>
      </div>
    </div>
  );
}
