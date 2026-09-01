import React from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LogOut, ShieldCheck } from 'lucide-react';

const titles = {
  '/':             'Dashboard',
  '/dashboard':    'Dashboard',
  '/schemes':      'Loan Schemes',
  '/emi':          'EMI Calculator',
  '/documents':    'Document Upload & Review',
  '/apply':        'Apply for Loan',
  '/applications': 'Applications Queue',
};

export default function Header() {
  const { pathname } = useLocation();
  const { currentUser, logout } = useAuth();

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
        <span className="badge-server">Cloud Live · Azure</span>

        {currentUser && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              className="avatar"
              style={{
                background: 'linear-gradient(135deg, #00d2ff 0%, #3a7bd5 100%)'
              }}
            >
              <div className="avatar-initials">{getInitials(currentUser.name || currentUser.username)}</div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <span className="user-name" style={{ fontSize: '0.85rem', lineHeight: 1.2 }}>
                {currentUser.name || currentUser.username}
              </span>
              <span style={{ fontSize: '0.7rem', color: 'var(--accent)', fontWeight: 600 }}>
                {currentUser.role || 'Underwriter'}
              </span>
            </div>

            <button
              className="btn btn-ghost"
              style={{ padding: '5px 8px', fontSize: '0.75rem', color: 'var(--muted)', marginLeft: 4 }}
              onClick={logout}
              title="Sign Out"
            >
              <LogOut size={14} />
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
