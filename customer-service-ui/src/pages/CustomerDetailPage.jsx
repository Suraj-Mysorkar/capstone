import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Pencil, Trash2, ArrowRight } from 'lucide-react';
import {
  getCustomer, updateCustomer, updateOnboardingStatus, deleteCustomer,
} from '../services/api';
import {
  ALLOWED_TRANSITIONS, HAPPY_PATH, prettyStatus, statusBadgeClass,
} from '../lib/onboarding';

const EDITABLE = [
  ['firstName', 'First name'], ['lastName', 'Last name'], ['email', 'Email'],
  ['phoneNumber', 'Phone'], ['addressLine1', 'Address line 1'], ['addressLine2', 'Address line 2'],
  ['city', 'City'], ['state', 'State'], ['postalCode', 'Postal code'], ['countryCode', 'Country'],
];

function LifecycleStrip({ current }) {
  const currentIdx = HAPPY_PATH.indexOf(current);
  return (
    <div className="lifecycle">
      {HAPPY_PATH.map((s, i) => {
        const state = current === s ? 'current'
          : currentIdx > -1 && i < currentIdx ? 'done'
          : 'todo';
        return (
          <React.Fragment key={s}>
            <div className={`lifecycle-node ${state}`}>{prettyStatus(s)}</div>
            {i < HAPPY_PATH.length - 1 && <div className="lifecycle-arrow">→</div>}
          </React.Fragment>
        );
      })}
      {(current === 'KYC_REJECTED' || current === 'SUSPENDED') && (
        <>
          <div className="lifecycle-arrow">·</div>
          <div className="lifecycle-node current" style={{ borderColor: 'var(--red)', color: 'var(--red)' }}>
            {prettyStatus(current)}
          </div>
        </>
      )}
    </div>
  );
}

export default function CustomerDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [customer, setCustomer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({});
  const [saving, setSaving] = useState(false);

  const [nextStatus, setNextStatus] = useState('');
  const [reason, setReason] = useState('');
  const [transitioning, setTransitioning] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setCustomer(await getCustomer(id));
    } catch (e) {
      setError(e.status === 404 ? 'No customer with that id.'
        : e.status === 401 ? 'Not authorized — set a bearer token on the Settings page.'
        : e.message);
      setCustomer(null);
    }
    setLoading(false);
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const startEdit = () => {
    setDraft(Object.fromEntries(EDITABLE.map(([k]) => [k, customer[k] ?? ''])));
    setEditing(true);
    setNotice('');
  };

  const saveEdit = async () => {
    setSaving(true);
    setError('');
    try {
      const patch = Object.fromEntries(
        Object.entries(draft).filter(([, v]) => v !== '').map(([k, v]) => [k, v]),
      );
      setCustomer(await updateCustomer(id, patch));
      setEditing(false);
      setNotice('Profile updated.');
    } catch (e) {
      setError(e.status === 403 ? 'Forbidden — token lacks customers.write.'
        : e.status === 409 ? 'That email is already used by another customer.'
        : e.message);
    }
    setSaving(false);
  };

  const applyTransition = async () => {
    if (!nextStatus) return;
    setTransitioning(true);
    setError('');
    try {
      setCustomer(await updateOnboardingStatus(id, nextStatus, reason));
      setNotice(`Status moved to ${prettyStatus(nextStatus)}.`);
      setNextStatus('');
      setReason('');
    } catch (e) {
      setError(e.status === 422 ? `Illegal transition: ${e.message}`
        : e.status === 403 ? 'Forbidden — token lacks customers.write.'
        : e.message);
    }
    setTransitioning(false);
  };

  const remove = async () => {
    if (!window.confirm('Delete this customer profile? This cannot be undone.')) return;
    setError('');
    try {
      await deleteCustomer(id);
      navigate('/customers');
    } catch (e) {
      setError(e.status === 403
        ? 'Forbidden — deleting requires the customer_admin role.'
        : e.message);
    }
  };

  if (loading) return <div className="page"><div className="spinner" /></div>;

  if (!customer) {
    return (
      <div className="page">
        <Link to="/customers" className="btn btn-ghost" style={{ marginBottom: 16 }}>
          <ArrowLeft size={15} /> Back to directory
        </Link>
        <div className="error-box">{error}</div>
      </div>
    );
  }

  const allowedNext = ALLOWED_TRANSITIONS[customer.onboardingStatus] || [];

  return (
    <div className="page">
      <div className="flex-row" style={{ marginBottom: 20 }}>
        <Link to="/customers" className="btn btn-ghost">
          <ArrowLeft size={15} /> Directory
        </Link>
        <span className={`badge ${statusBadgeClass(customer.onboardingStatus)}`} style={{ marginLeft: 'auto' }}>
          {prettyStatus(customer.onboardingStatus)}
        </span>
      </div>

      {error && <div className="error-box">{error}</div>}
      {notice && <div className="success-box">{notice}</div>}

      <div className="card p-6">
        <div className="card-header-title" style={{ marginBottom: 6 }}>
          {customer.firstName} {customer.lastName}
        </div>
        <div className="text-muted font-mono" style={{ marginBottom: 20 }}>{customer.id}</div>

        <LifecycleStrip current={customer.onboardingStatus} />
      </div>

      {/* ── Profile ─────────────────────────────────────────── */}
      <div className="card p-6 mt-4">
        <div className="card-header" style={{ padding: 0, marginBottom: 18 }}>
          <div className="card-header-title">Profile</div>
          {!editing
            ? <button className="btn btn-ghost" onClick={startEdit}><Pencil size={14} /> Edit</button>
            : (
              <div className="flex-row">
                <button className="btn btn-primary" onClick={saveEdit} disabled={saving}>
                  {saving ? 'Saving…' : 'Save'}
                </button>
                <button className="btn btn-ghost" onClick={() => setEditing(false)}>Cancel</button>
              </div>
            )}
        </div>

        {!editing ? (
          <div className="detail-grid">
            {EDITABLE.map(([k, label]) => (
              <div className="detail-field" key={k}>
                <div className="detail-field-label">{label}</div>
                <div className="detail-field-value">{customer[k] || '—'}</div>
              </div>
            ))}
            <div className="detail-field">
              <div className="detail-field-label">Created</div>
              <div className="detail-field-value">
                {customer.createdAt ? new Date(customer.createdAt).toLocaleString() : '—'}
              </div>
            </div>
            <div className="detail-field">
              <div className="detail-field-label">Updated</div>
              <div className="detail-field-value">
                {customer.updatedAt ? new Date(customer.updatedAt).toLocaleString() : '—'}
              </div>
            </div>
          </div>
        ) : (
          <div className="form-grid">
            {EDITABLE.map(([k, label]) => (
              <div className="form-group" key={k}>
                <label className="form-label">{label}</label>
                <input
                  className="form-input"
                  value={draft[k] ?? ''}
                  onChange={(e) => setDraft((d) => ({ ...d, [k]: e.target.value }))}
                />
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Onboarding transition ───────────────────────────── */}
      <div className="card p-6 mt-4">
        <div className="card-header-title" style={{ marginBottom: 16 }}>Advance Onboarding Status</div>
        {allowedNext.length === 0 ? (
          <div className="text-muted">No transitions available from {prettyStatus(customer.onboardingStatus)}.</div>
        ) : (
          <>
            <div className="text-muted" style={{ marginBottom: 12 }}>
              Allowed from <strong>{prettyStatus(customer.onboardingStatus)}</strong>:{' '}
              {allowedNext.map(prettyStatus).join(', ')}
            </div>
            <div className="form-grid">
              <div className="form-group">
                <label className="form-label">Next status</label>
                <select className="form-select" value={nextStatus} onChange={(e) => setNextStatus(e.target.value)}>
                  <option value="">Select…</option>
                  {allowedNext.map((s) => <option key={s} value={s}>{prettyStatus(s)}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Reason (optional)</label>
                <input
                  className="form-input" value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="e.g. documents verified"
                />
              </div>
            </div>
            <div className="mt-4">
              <button className="btn btn-primary" onClick={applyTransition} disabled={!nextStatus || transitioning}>
                <ArrowRight size={15} /> {transitioning ? 'Applying…' : 'Apply Transition'}
              </button>
            </div>
          </>
        )}
      </div>

      {/* ── Danger zone ─────────────────────────────────────── */}
      <div className="card p-6 mt-4" style={{ borderColor: 'rgba(231,76,60,.25)' }}>
        <div className="card-header-title" style={{ marginBottom: 8 }}>Delete Customer</div>
        <div className="text-muted" style={{ marginBottom: 14 }}>
          Permanently removes the profile. Requires the <span className="font-mono">customer_admin</span> role.
        </div>
        <button className="btn btn-danger" onClick={remove}><Trash2 size={14} /> Delete Profile</button>
      </div>
    </div>
  );
}
