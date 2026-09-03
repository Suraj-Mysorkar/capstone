import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { UserPlus, Wand2, Loader2, Landmark } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { registerLoanCustomer } from '../services/loanApi';
import { ISO_COUNTRIES } from '../lib/onboarding';

const EMPTY = {
  firstName: '', lastName: '', email: '', phoneNumber: '',
  addressLine1: '', addressLine2: '', city: '', state: '',
  postalCode: '', countryCode: 'US', password: '', confirmPassword: '',
};

const SAMPLES = [
  {
    label: '🇺🇸 Jane Doe (Springfield, IL)',
    data: {
      firstName: 'Jane', lastName: 'Doe', email: `jane.doe.${Date.now()}@example.com`,
      phoneNumber: '+14155552671', addressLine1: '123 Main St',
      city: 'Springfield', state: 'IL', postalCode: '62701', countryCode: 'US',
      password: 'password1', confirmPassword: 'password1',
    },
  },
  {
    label: '🇮🇳 Rohan Mehta (Bengaluru)',
    data: {
      firstName: 'Rohan', lastName: 'Mehta', email: `rohan.mehta.${Date.now()}@example.com`,
      phoneNumber: '+919812345678', addressLine1: '42 MG Road', addressLine2: 'Apt 5B',
      city: 'Bengaluru', state: 'KA', postalCode: '560001', countryCode: 'IN',
      password: 'password1', confirmPassword: 'password1',
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
  const { register, updateUser } = useAuth();
  const [form, setForm] = useState(EMPTY);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const navigate = useNavigate();

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async () => {
    setError('');
    setFieldErrors({});

    if (!form.firstName || !form.lastName || !form.email) {
      setError('First name, last name and email are required.');
      return;
    }
    if (form.password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }
    if (form.password !== form.confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email.trim(),
        phoneNumber: form.phoneNumber || null,
        addressLine1: form.addressLine1 || null,
        addressLine2: form.addressLine2 || null,
        city: form.city || null,
        state: form.state || null,
        postalCode: form.postalCode || null,
        countryCode: form.countryCode,
        password: form.password,
      };
      const user = await register(payload); // creates account + signs in

      // Mirror into the shared loan Customers table so a loan officer can help
      // this customer in capstone-ui straight away.
      try {
        const linked = await registerLoanCustomer({
          fullName: `${form.firstName} ${form.lastName}`.trim(),
          email: user.email,
          mobileNumber: form.phoneNumber || null,
          address: [form.addressLine1, form.city, form.state, form.countryCode].filter(Boolean).join(', ') || null,
          onboardingStatus: user.onboardingStatus || 'REGISTERED',
          externalRef: user.customerServiceId || null,
        });
        if (linked.customerCode) updateUser({ loanCustomerId: linked.customerCode });
      } catch (syncErr) {
        console.warn('loan-service customer sync failed (non-fatal):', syncErr);
      }

      navigate('/', { replace: true });
    } catch (e) {
      if (e.status === 400 && e.body?.fieldErrors) setFieldErrors(e.body.fieldErrors);
      setError(
        e.status === 409 ? 'An account with that email already exists — sign in instead.' : (e.message || 'Registration failed.'),
      );
      setSubmitting(false);
    }
  };

  return (
    <div className="card p-6">
      <div className="auth-brand" style={{ marginBottom: 18 }}>
        <span className="auth-brand-mark"><Landmark size={24} /></span>
        <div>
          <h2>Create your account</h2>
          <p>One profile for self-service or manager-assisted loans</p>
        </div>
      </div>

      <div className="filter-bar">
        {SAMPLES.map((s) => (
          <button key={s.label} className="filter-btn" onClick={() => { setForm({ ...EMPTY, ...s.data }); setError(''); }}>
            <Wand2 size={13} style={{ marginRight: 6, verticalAlign: '-2px' }} />{s.label}
          </button>
        ))}
      </div>

      {error && <div className="error-box">{error}</div>}

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
            {fieldErrors[k] && <span style={{ fontSize: '.72rem', color: 'var(--red)' }}>{fieldErrors[k]}</span>}
          </div>
        ))}

        <div className="form-group">
          <label className="form-label">Country code</label>
          <select className="form-select" value={form.countryCode} onChange={set('countryCode')}>
            {ISO_COUNTRIES.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>

        <div className="form-group">
          <label className="form-label">Create password *</label>
          <input className="form-input" type="password" value={form.password} onChange={set('password')} placeholder="At least 6 characters" />
          {fieldErrors.password && <span style={{ fontSize: '.72rem', color: 'var(--red)' }}>{fieldErrors.password}</span>}
        </div>
        <div className="form-group">
          <label className="form-label">Confirm password *</label>
          <input className="form-input" type="password" value={form.confirmPassword} onChange={set('confirmPassword')} placeholder="Re-enter password" />
        </div>
      </div>

      <div className="mt-4 flex-row">
        <button className="btn btn-primary" onClick={submit} disabled={submitting}>
          {submitting ? <><Loader2 size={15} className="spin" /> Creating account…</> : <><UserPlus size={15} /> Create Account</>}
        </button>
        <button className="btn btn-ghost" onClick={() => { setForm(EMPTY); setError(''); setFieldErrors({}); }}>Clear</button>
      </div>

      <div className="text-muted" style={{ marginTop: 16, fontSize: '.82rem', textAlign: 'center' }}>
        Already have an account? <Link to="/login" style={{ color: 'var(--accent)', fontWeight: 600 }}>Sign in →</Link>
      </div>
    </div>
  );
}
