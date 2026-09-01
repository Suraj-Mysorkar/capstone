import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, LogIn, LogOut } from 'lucide-react';

const titles = {
  '/':             'Dashboard',
  '/schemes':      'Loan Schemes',
  '/emi':          'EMI Calculator',
  '/documents':    'Document Upload & Review',
  '/apply':        'Apply for Loan',
  '/applications': 'Applications',
  '/login':        'Employee Portal & Authentication',
};

export default function Header() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { currentUser, isAuthenticated, logout } = useAuth();

  const base = '/' + pathname.split('/')[1];
  const title = titles[base] || 'Dashboard';

  const getInitials = (name) => {
    if (!name) return 'EP';
    const parts = name.trim().split(' ');
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.substring(0, 2).toUpperCase();
  };

  return (
    <header className="header">
      <div className="header-title">{title}</div>
      <div className="header-right" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span className="badge-server">Cloud Live · Azure APIM</span>

        {isAuthenticated && currentUser ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              className="avatar"
              style={{
                background: 'linear-gradient(135deg, #00d2ff 0%, #9d4edd 100%)',
                cursor: 'pointer'
              }}
              onClick={() => navigate('/login')}
              title="View Employee Profile"
            >
              <div className="avatar-initials">{getInitials(currentUser.name || currentUser.username)}</div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span className="user-name" style={{ fontSize: '0.85rem', lineHeight: 1.2 }}>
                {currentUser.name || currentUser.username}
              </span>
              <span style={{ fontSize: '0.7rem', color: 'var(--accent)', fontWeight: 600 }}>
                {currentUser.role ? 'Underwriter' : 'Employee'}
              </span>
            </div>

            <button
              className="btn btn-ghost"
              style={{ padding: '4px 8px', fontSize: '0.75rem', color: 'var(--muted)' }}
              onClick={logout}
              title="Sign Out"
            >
              <LogOut size={14} />
            </button>
          </div>
        ) : (
          <button
            className="btn btn-primary"
            style={{ padding: '5px 12px', fontSize: '0.78rem', display: 'flex', alignItems: 'center', gap: 5 }}
            onClick={() => navigate('/login')}
          >
            <LogIn size={13} /> Employee Login
          </button>
        )}
      </div>
    </header>
  );
}
