import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Search } from 'lucide-react';
import { getCustomer, getCustomerByEmail } from '../services/api';
import { prettyStatus, statusBadgeClass } from '../lib/onboarding';

export default function LookupPage() {
  const [mode, setMode] = useState('id'); // id | email
  const [value, setValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const run = async () => {
    if (!value.trim()) { setError('Enter a value to search.'); return; }
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const c = mode === 'id'
        ? await getCustomer(value.trim())
        : await getCustomerByEmail(value.trim());
      setResult(c);
    } catch (e) {
      setError(e.status === 404 ? 'No customer found.'
        : e.status === 401 ? 'Not authorized — set a bearer token on the Settings page.'
        : e.message);
    }
    setLoading(false);
  };

  return (
    <div className="page">
      <div className="tabs">
        <button className={`tab-btn${mode === 'id' ? ' active' : ''}`} onClick={() => { setMode('id'); setResult(null); setError(''); }}>
          By ID
        </button>
        <button className={`tab-btn${mode === 'email' ? ' active' : ''}`} onClick={() => { setMode('email'); setResult(null); setError(''); }}>
          By Email
        </button>
      </div>

      <div className="card p-6">
        {error && <div className="error-box">{error}</div>}

        <div className="flex-row" style={{ alignItems: 'flex-end', gap: 12 }}>
          <div className="form-group" style={{ flex: 1 }}>
            <label className="form-label">{mode === 'id' ? 'Customer ID (UUID)' : 'Email address'}</label>
            <input
              className="form-input"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && run()}
              placeholder={mode === 'id' ? '3fa85f64-5717-4562-b3fc-2c963f66afa6' : 'name@example.com'}
            />
          </div>
          <button className="btn btn-primary" onClick={run} disabled={loading}>
            <Search size={15} /> {loading ? 'Searching…' : 'Search'}
          </button>
        </div>

        {result && (
          <>
            <div className="flex-row mt-4" style={{ justifyContent: 'space-between' }}>
              <div style={{ fontWeight: 700, fontSize: '1.05rem' }}>
                {result.firstName} {result.lastName}
              </div>
              <span className={`badge ${statusBadgeClass(result.onboardingStatus)}`}>
                {prettyStatus(result.onboardingStatus)}
              </span>
            </div>
            <div className="detail-grid mt-4">
              {[
                ['ID', result.id],
                ['Email', result.email],
                ['Phone', result.phoneNumber || '—'],
                ['City', result.city || '—'],
                ['State', result.state || '—'],
                ['Country', result.countryCode || '—'],
                ['Created', result.createdAt ? new Date(result.createdAt).toLocaleString() : '—'],
                ['Updated', result.updatedAt ? new Date(result.updatedAt).toLocaleString() : '—'],
              ].map(([l, v]) => (
                <div key={l} className="detail-field">
                  <div className="detail-field-label">{l}</div>
                  <div className="detail-field-value" style={{ fontSize: '.85rem' }}>{v}</div>
                </div>
              ))}
            </div>
            <div className="mt-4">
              <Link to={`/customers/${result.id}`} className="btn btn-primary">
                Open full profile →
              </Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
