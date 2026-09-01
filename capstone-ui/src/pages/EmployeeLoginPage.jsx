import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  ShieldCheck,
  User,
  Lock,
  Eye,
  EyeOff,
  LogIn,
  LogOut,
  CheckCircle2,
  AlertCircle,
  Sparkles,
  Server,
  Layers,
  ArrowRight,
  RefreshCw,
  KeyRound
} from 'lucide-react';

export default function EmployeeLoginPage() {
  const navigate = useNavigate();
  const { currentUser, isAuthenticated, login, logout, loading, authError, setAuthError } = useAuth();

  const [username, setUsername] = useState('markj');
  const [password, setPassword] = useState('Temp1234');
  const [showPassword, setShowPassword] = useState(false);
  const [successMessage, setSuccessMessage] = useState('');

  const handleLogin = async (e) => {
    if (e) e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setAuthError('Please enter both username and password.');
      return;
    }

    setSuccessMessage('');
    try {
      const user = await login(username.trim(), password.trim());
      setSuccessMessage(`Welcome back, ${user.name || user.username}! Authentication successful.`);
    } catch (err) {
      // Error state is handled by AuthContext
    }
  };

  const handleFillDemo = (u = 'markj', p = 'Temp1234') => {
    setUsername(u);
    setPassword(p);
    setAuthError('');
    setSuccessMessage('');
  };

  return (
    <div className="page" style={{ maxWidth: 960, margin: '20px auto', padding: '20px' }}>
      {/* Header Banner */}
      <div style={{ textAlign: 'center', marginBottom: 28 }}>
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '6px 16px',
            borderRadius: 20,
            background: 'rgba(0, 210, 255, 0.1)',
            border: '1px solid rgba(0, 210, 255, 0.3)',
            color: 'var(--accent)',
            fontSize: '0.82rem',
            fontWeight: 600,
            marginBottom: 12
          }}
        >
          <Server size={14} /> Azure API Management Gateway • /auth/internal/login
        </div>
        <h2 style={{ fontSize: '1.8rem', fontWeight: 800, margin: '0 0 6px' }}>
          Employee & Underwriter Portal
        </h2>
        <p style={{ color: 'var(--muted)', fontSize: '0.9rem', maxWidth: 600, margin: '0 auto' }}>
          Secure authentication for Bank Officers, Underwriters, and Operations Managers to manage loan queues, verify proofs, and submit decisions.
        </p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: isAuthenticated ? '1fr' : '1fr 340px', gap: 24, alignItems: 'start' }}>
        {/* Main Authentication Card */}
        <div className="card p-6" style={{ border: '1px solid var(--border)', background: '#090d1f' }}>
          {isAuthenticated && currentUser ? (
            /* Logged In State */
            <div>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '16px',
                  borderRadius: 8,
                  background: 'rgba(16, 185, 129, 0.1)',
                  border: '1px solid var(--green)',
                  marginBottom: 24
                }}
              >
                <CheckCircle2 size={24} color="var(--green)" />
                <div>
                  <div style={{ fontWeight: 700, fontSize: '1rem', color: '#10b981' }}>
                    Authenticated as {currentUser.name || currentUser.username}
                  </div>
                  <div style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 2 }}>
                    Session active via Azure API Management Gateway.
                  </div>
                </div>
              </div>

              <div className="detail-grid" style={{ marginBottom: 24 }}>
                {[
                  ['Username', currentUser.username],
                  ['Full Name', currentUser.name || currentUser.username],
                  ['Assigned Role', currentUser.role || 'Senior Underwriter / Operations Manager'],
                  ['Session Token', currentUser.token ? `${String(currentUser.token).substring(0, 24)}...` : 'Bearer Token Active'],
                  ['Login Timestamp', new Date(currentUser.loginTime || Date.now()).toLocaleString()],
                ].map(([label, val]) => (
                  <div key={label} className="detail-field">
                    <div className="detail-field-label">{label}</div>
                    <div className="detail-field-value font-mono" style={{ fontSize: '0.85rem' }}>
                      {val}
                    </div>
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <button
                  className="btn btn-primary"
                  style={{ padding: '10px 20px', fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: 8 }}
                  onClick={() => navigate('/applications')}
                >
                  <Layers size={16} /> Open Loan Underwriting Queue <ArrowRight size={16} />
                </button>

                <button
                  className="btn btn-ghost"
                  style={{ padding: '10px 18px', fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: 8 }}
                  onClick={() => navigate('/documents')}
                >
                  <ShieldCheck size={16} /> Document Review Portal
                </button>

                <button
                  className="btn"
                  style={{
                    padding: '10px 18px',
                    fontSize: '0.9rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    background: 'rgba(239, 68, 68, 0.15)',
                    color: '#ef4444',
                    border: '1px solid rgba(239, 68, 68, 0.3)',
                    marginLeft: 'auto'
                  }}
                  onClick={logout}
                >
                  <LogOut size={16} /> Sign Out
                </button>
              </div>
            </div>
          ) : (
            /* Login Form */
            <form onSubmit={handleLogin}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700, fontSize: '1.05rem', marginBottom: 16 }}>
                <ShieldCheck size={20} color="var(--accent)" /> Internal Employee Sign-In
              </div>

              {authError && (
                <div
                  className="error-box"
                  style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16, padding: '10px 14px', fontSize: '0.85rem' }}
                >
                  <AlertCircle size={16} /> {authError}
                </div>
              )}

              {successMessage && (
                <div
                  className="success-box"
                  style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16, padding: '10px 14px', fontSize: '0.85rem' }}
                >
                  <CheckCircle2 size={16} color="var(--green)" /> {successMessage}
                </div>
              )}

              <div className="form-group">
                <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <User size={14} color="var(--accent)" /> Username / Employee ID *
                </label>
                <input
                  className="form-input"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="e.g. markj"
                  required
                />
              </div>

              <div className="form-group" style={{ position: 'relative' }}>
                <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <Lock size={14} color="var(--accent)" /> Password *
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className="form-input"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter employee password"
                    style={{ paddingRight: 40 }}
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    style={{
                      position: 'absolute',
                      right: 10,
                      top: '50%',
                      transform: 'translateY(-50%)',
                      background: 'none',
                      border: 'none',
                      color: 'var(--muted)',
                      cursor: 'pointer'
                    }}
                  >
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div style={{ marginTop: 24 }}>
                <button
                  type="submit"
                  className="btn btn-primary"
                  style={{
                    width: '100%',
                    justifyContent: 'center',
                    padding: '12px',
                    fontSize: '0.95rem',
                    fontWeight: 700
                  }}
                  disabled={loading}
                >
                  {loading ? (
                    <>
                      <RefreshCw size={16} className="spin" /> Authenticating with Azure APIM…
                    </>
                  ) : (
                    <>
                      <LogIn size={16} /> Sign In to Portal
                    </>
                  )}
                </button>
              </div>
            </form>
          )}
        </div>

        {/* Quick Help & Demo Credentials Sidebar */}
        {!isAuthenticated && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="card p-5" style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700, fontSize: '0.9rem', marginBottom: 10, color: 'var(--accent)' }}>
                <KeyRound size={16} /> Employee Credentials
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--muted)', marginBottom: 12 }}>
                Click below to prefill test credentials provided for the internal API:
              </div>

              <div
                style={{
                  background: 'rgba(0,0,0,0.3)',
                  padding: '10px 12px',
                  borderRadius: 6,
                  border: '1px solid var(--border)',
                  fontSize: '0.82rem',
                  fontFamily: 'monospace',
                  marginBottom: 12
                }}
              >
                <div>
                  <span style={{ color: 'var(--muted)' }}>Username: </span>
                  <strong style={{ color: 'var(--accent)' }}>markj</strong>
                </div>
                <div style={{ marginTop: 4 }}>
                  <span style={{ color: 'var(--muted)' }}>Password: </span>
                  <strong style={{ color: 'var(--accent)' }}>Temp1234</strong>
                </div>
              </div>

              <button
                type="button"
                className="btn btn-ghost"
                style={{ width: '100%', fontSize: '0.8rem', padding: '6px 10px', justifyContent: 'center' }}
                onClick={() => handleFillDemo('markj', 'Temp1234')}
              >
                <Sparkles size={14} /> Fill Demo Credentials
              </button>
            </div>

            <div className="card p-5" style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border)' }}>
              <div style={{ fontWeight: 700, fontSize: '0.85rem', marginBottom: 8 }}>
                Gateway Details
              </div>
              <div style={{ fontSize: '0.78rem', color: 'var(--muted)', lineHeight: 1.5 }}>
                • <strong>Service:</strong> Azure API Management (APIM)<br />
                • <strong>Route:</strong> <code>/auth/internal/login</code><br />
                • <strong>Protocol:</strong> HTTPS REST JSON
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
