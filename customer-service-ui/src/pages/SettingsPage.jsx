import React, { useState } from 'react';
import { KeyRound, Plug, CheckCircle2, XCircle } from 'lucide-react';
import {
  apiBase, apiBaseSource, setApiBase, getToken, setToken, ping,
} from '../services/api';
import {
  loanBase, loanBaseSource, setLoanBase,
  docBase, docBaseSource, setDocBase,
} from '../services/loanApi';

function UrlRow({ label, value, source, onChange }) {
  const [v, setV] = useState(value);
  const [saved, setSaved] = useState(false);
  return (
    <div className="form-group" style={{ marginBottom: 14 }}>
      <label className="form-label">
        {label} <span className="text-muted">— from {source}</span>
      </label>
      <div className="flex-row" style={{ gap: 8 }}>
        <input className="form-input" style={{ flex: 1 }} value={v} onChange={(e) => setV(e.target.value)} />
        <button
          className="btn btn-primary"
          onClick={() => { onChange(v); setSaved(true); setTimeout(() => setSaved(false), 2000); }}
        >
          Save
        </button>
        <button className="btn btn-ghost" onClick={() => { onChange(''); setV(''); }}>Reset</button>
      </div>
      {saved && <span style={{ fontSize: '.75rem', color: 'var(--green)' }}>Saved to this browser.</span>}
    </div>
  );
}

export default function SettingsPage() {
  const [token, setTokenValue] = useState(getToken());
  const [tokenSaved, setTokenSaved] = useState(false);
  const [test, setTest] = useState(null);
  const [testMsg, setTestMsg] = useState('');

  const saveToken = () => {
    setToken(token);
    setTokenSaved(true);
    setTimeout(() => setTokenSaved(false), 2500);
  };

  const runPing = async () => {
    setTest(null); setTestMsg('');
    try {
      const res = await ping();
      setTest('ok');
      setTestMsg(`${res.service} · ${res.status}`);
    } catch (e) {
      setTest('fail');
      setTestMsg(e.message);
    }
  };

  return (
    <div className="page">
      <div className="card p-6">
        <div className="card-header-title" style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Plug size={18} /> Service Endpoints
        </div>
        <div className="text-muted" style={{ marginBottom: 16 }}>
          Point the portal at the running backend services. Stored in this browser
          only; build-time env vars (<span className="font-mono">VITE_CUSTOMER_API_URL</span>,{' '}
          <span className="font-mono">VITE_LOAN_API_URL</span>,{' '}
          <span className="font-mono">VITE_DOC_API_URL</span>) are used otherwise.
        </div>

        <UrlRow label="customer-service (registration & onboarding)" value={apiBase()} source={apiBaseSource()} onChange={(v) => { setApiBase(v); }} />
        <UrlRow label="loan-service (schemes, EMI, apply, applications)" value={loanBase()} source={loanBaseSource()} onChange={(v) => { setLoanBase(v); }} />
        <UrlRow label="document-service (uploads & document status)" value={docBase()} source={docBaseSource()} onChange={(v) => { setDocBase(v); }} />

        <div className="mt-4 flex-row">
          <button className="btn btn-ghost" onClick={runPing}>Test customer-service /ping</button>
          {test === 'ok' && <span className="badge-server" style={{ color: 'var(--green)' }}><CheckCircle2 size={14} /> {testMsg}</span>}
          {test === 'fail' && <span className="badge-server" style={{ color: 'var(--red)' }}><XCircle size={14} /> {testMsg}</span>}
        </div>
      </div>

      <div className="card p-6 mt-4">
        <div className="card-header-title" style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
          <KeyRound size={18} /> Bearer Token (customer-service only)
        </div>
        <div className="text-muted" style={{ marginBottom: 14 }}>
          Only needed if customer-service runs with Entra ID auth (not the{' '}
          <span className="font-mono">local</span> profile). Paste an access token —
          stored in this browser's <span className="font-mono">localStorage</span>.
        </div>
        {tokenSaved && <div className="success-box">Token saved to this browser.</div>}
        <div className="form-group">
          <label className="form-label">Access token (JWT)</label>
          <textarea
            className="form-input"
            style={{ minHeight: 100, resize: 'vertical', fontFamily: 'monospace', fontSize: '.75rem' }}
            value={token}
            onChange={(e) => setTokenValue(e.target.value)}
            placeholder="eyJ0eXAiOiJKV1QiLCJhbGci…"
          />
        </div>
        <div className="mt-4 flex-row">
          <button className="btn btn-primary" onClick={saveToken}>Save Token</button>
          <button className="btn btn-ghost" onClick={() => { setTokenValue(''); setToken(''); }}>Clear</button>
        </div>
      </div>
    </div>
  );
}
