import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { User, Lock, Eye, EyeOff, LogIn, AlertCircle, Loader2, Landmark } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { resolveLoanCustomer } from '../services/loanApi';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, login, updateUser, loading, authError, setAuthError } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  useEffect(() => {
    if (isAuthenticated) {
      navigate(location.state?.from?.pathname || '/', { replace: true });
    }
  }, [isAuthenticated, navigate, location]);

  const submit = async (e) => {
    if (e) e.preventDefault();
    if (!username.trim() || !password) {
      setAuthError('Enter your email and password.');
      return;
    }
    try {
      const user = await login(username, password);
      // Link to the shared loan Customers table if a record already exists.
      try {
        const loanCust = await resolveLoanCustomer(user.email);
        if (loanCust?.customerCode) updateUser({ loanCustomerId: loanCust.customerCode });
      } catch { /* offline — linked later on apply */ }
      navigate(location.state?.from?.pathname || '/', { replace: true });
    } catch {
      /* authError shown below */
    }
  };

  return (
    <div className="card auth-card">
      <div className="auth-brand">
        <span className="auth-brand-mark"><Landmark size={26} /></span>
        <div>
          <h2>Everyday Bank</h2>
          <p>Personal loan self-service</p>
        </div>
      </div>

      {authError && (
        <div className="error-box" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 18 }}>
          <AlertCircle size={16} style={{ flexShrink: 0 }} /> <span>{authError}</span>
        </div>
      )}

      <form onSubmit={submit}>
        <div className="form-group" style={{ marginBottom: 16 }}>
          <label className="form-label"><User size={13} /> Email</label>
          <input
            className="form-input"
            type="email"
            value={username}
            autoFocus
            onChange={(e) => setUsername(e.target.value)}
            placeholder="you@example.com"
          />
        </div>

        <div className="form-group" style={{ marginBottom: 22 }}>
          <label className="form-label"><Lock size={13} /> Password</label>
          <div style={{ position: 'relative' }}>
            <input
              className="form-input"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Your password"
              style={{ paddingRight: 40 }}
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: 'var(--muted)', cursor: 'pointer', padding: 0 }}
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
        </div>

        <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center' }} disabled={loading}>
          {loading ? <><Loader2 size={16} className="spin" /> Signing in…</> : <><LogIn size={16} /> Sign In</>}
        </button>
      </form>

      <div className="text-muted" style={{ marginTop: 18, fontSize: '.82rem', textAlign: 'center' }}>
        New customer? <Link to="/register" style={{ color: 'var(--accent)', fontWeight: 600 }}>Create an account →</Link>
      </div>
    </div>
  );
}
