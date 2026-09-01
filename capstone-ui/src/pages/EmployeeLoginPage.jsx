import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  ShieldCheck,
  User,
  Lock,
  Eye,
  EyeOff,
  LogIn,
  AlertCircle,
  RefreshCw,
  Activity
} from 'lucide-react';

export default function EmployeeLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, login, loading, authError, setAuthError } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  // If already authenticated, redirect to dashboard or intended destination
  useEffect(() => {
    if (isAuthenticated) {
      const from = location.state?.from?.pathname || '/dashboard';
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, navigate, location]);

  const handleLogin = async (e) => {
    if (e) e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setAuthError('Please enter both Employee ID and password.');
      return;
    }

    try {
      await login(username.trim(), password.trim());
      const from = location.state?.from?.pathname || '/dashboard';
      navigate(from, { replace: true });
    } catch (err) {
      // Error handled by AuthContext
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        width: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at center, #0d1527 0%, #030712 100%)',
        padding: '24px'
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 440,
          background: 'rgba(15, 23, 42, 0.85)',
          backdropFilter: 'blur(16px)',
          border: '1px solid rgba(255, 255, 255, 0.1)',
          borderRadius: 14,
          padding: '36px 32px',
          boxShadow: '0 20px 48px rgba(0, 0, 0, 0.6)'
        }}
      >
        {/* Brand Logo & Title */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 52,
              height: 52,
              borderRadius: 14,
              background: 'linear-gradient(135deg, #00d2ff 0%, #3a7bd5 100%)',
              color: '#ffffff',
              marginBottom: 16,
              boxShadow: '0 8px 24px rgba(0, 210, 255, 0.3)'
            }}
          >
            <Activity size={28} />
          </div>

          <h2 style={{ fontSize: '1.5rem', fontWeight: 800, margin: '0 0 6px', color: '#ffffff' }}>
            Digital Lending Portal
          </h2>
          <p style={{ color: 'var(--muted)', fontSize: '0.85rem', margin: 0 }}>
            Internal Employee & Underwriting Access
          </p>
        </div>

        {/* Error Alert */}
        {authError && (
          <div
            className="error-box"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              marginBottom: 20,
              padding: '10px 14px',
              fontSize: '0.82rem',
              borderRadius: 8
            }}
          >
            <AlertCircle size={16} style={{ flexShrink: 0 }} />
            <div>{authError}</div>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleLogin}>
          <div className="form-group" style={{ marginBottom: 16 }}>
            <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.8rem' }}>
              <User size={13} color="var(--accent)" /> Employee ID / Username *
            </label>
            <input
              className="form-input"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Enter your employee ID"
              autoFocus
              required
              style={{ padding: '10px 14px', fontSize: '0.88rem' }}
            />
          </div>

          <div className="form-group" style={{ marginBottom: 22 }}>
            <label className="form-label" style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.8rem' }}>
              <Lock size={13} color="var(--accent)" /> Password *
            </label>
            <div style={{ position: 'relative' }}>
              <input
                type={showPassword ? 'text' : 'password'}
                className="form-input"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter password"
                required
                style={{ padding: '10px 40px 10px 14px', fontSize: '0.88rem' }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute',
                  right: 12,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  color: 'var(--muted)',
                  cursor: 'pointer',
                  padding: 0
                }}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            className="btn btn-primary"
            style={{
              width: '100%',
              justifyContent: 'center',
              padding: '11px',
              fontSize: '0.92rem',
              fontWeight: 700
            }}
            disabled={loading}
          >
            {loading ? (
              <>
                <RefreshCw size={16} className="spin" /> Authenticating…
              </>
            ) : (
              <>
                <LogIn size={16} /> Sign In
              </>
            )}
          </button>
        </form>

        {/* Security Footer */}
        <div style={{ marginTop: 24, textAlign: 'center', borderTop: '1px solid rgba(255, 255, 255, 0.06)', paddingTop: 16 }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--muted)', fontSize: '0.75rem' }}>
            <ShieldCheck size={14} color="var(--accent)" /> Secure Enterprise Authentication • Azure APIM
          </div>
        </div>
      </div>
    </div>
  );
}
