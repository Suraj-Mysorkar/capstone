import React, { useState } from 'react';
import { KeyRound, Plug, CheckCircle2, XCircle } from 'lucide-react';
import {
  apiBase, apiBaseSource, setApiBase, getToken, setToken, ping,
} from '../services/api';

export default function SettingsPage() {
  const [base, setBaseValue] = useState(apiBase());
  const [baseSaved, setBaseSaved] = useState(false);

  const [token, setTokenValue] = useState(getToken());
  const [tokenSaved, setTokenSaved] = useState(false);

  const [test, setTest] = useState(null); // null | 'ok' | 'fail'
  const [testMsg, setTestMsg] = useState('');

  const saveBase = () => {
    setApiBase(base);
    setBaseValue(apiBase());
    setBaseSaved(true);
    setTimeout(() => setBaseSaved(false), 2500);
  };

  const saveToken = () => {
    setToken(token);
    setTokenSaved(true);
    setTimeout(() => setTokenSaved(false), 2500);
  };

  const runPing = async () => {
    setTest(null);
    setTestMsg('');
    try {
      const res = await ping();
      setTest('ok');
      setTestMsg(`${res.service} · ${res.status} · ${res.timestamp}`);
    } catch (e) {
      setTest('fail');
      setTestMsg(e.message);
    }
  };

  return (
    <div className="page">
      {/* ── API connection ─────────────────────────────────── */}
      <div className="card p-6">
        <div className="card-header-title" style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Plug size={18} /> API Connection
        </div>
        <div className="text-muted" style={{ marginBottom: 14 }}>
          Point the console at a running customer-service. Set once here (stored in this
          browser) or bake it into the build with{' '}
          <span className="font-mono">VITE_CUSTOMER_API_URL</span>.
        </div>

        {baseSaved && <div className="success-box">API base URL saved to this browser.</div>}

        <div className="form-group">
          <label className="form-label">
            API base URL <span className="text-muted">— currently from {apiBaseSource()}</span>
          </label>
          <input
            className="form-input"
            value={base}
            onChange={(e) => setBaseValue(e.target.value)}
            placeholder="https://team6-customer-service.azurewebsites.net/api/customers"
          />
        </div>

        <div className="mt-4 flex-row">
          <button className="btn btn-primary" onClick={saveBase}>Save URL</button>
          <button className="btn btn-ghost" onClick={() => { setApiBase(''); setBaseValue(apiBase()); }}>
            Reset to default
          </button>
          <button className="btn btn-ghost" onClick={runPing}>Test /ping</button>
          {test === 'ok' && (
            <span className="badge-server" style={{ color: 'var(--green)' }}>
              <CheckCircle2 size={14} /> {testMsg}
            </span>
          )}
          {test === 'fail' && (
            <span className="badge-server" style={{ color: 'var(--red)' }}>
              <XCircle size={14} /> {testMsg}
            </span>
          )}
        </div>
      </div>

      {/* ── Bearer token ───────────────────────────────────── */}
      <div className="card p-6 mt-4">
        <div className="card-header-title" style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
          <KeyRound size={18} /> Bearer Token
        </div>
        <div className="text-muted" style={{ marginBottom: 14 }}>
          Every endpoint except <span className="font-mono">/ping</span> requires a Microsoft Entra ID
          JWT. Paste an access token — it is stored only in this browser's{' '}
          <span className="font-mono">localStorage</span> and sent as{' '}
          <span className="font-mono">Authorization: Bearer …</span>.
          (Not needed when the service runs with the <span className="font-mono">local</span> profile.)
        </div>
        {tokenSaved && <div className="success-box">Token saved to this browser.</div>}
        <div className="form-group">
          <label className="form-label">Access token (JWT)</label>
          <textarea
            className="form-input"
            style={{ minHeight: 120, resize: 'vertical', fontFamily: 'monospace', fontSize: '.75rem' }}
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
