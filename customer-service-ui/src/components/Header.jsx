import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const titles = {
  '/': 'Home',
  '/emi': 'EMI Calculator',
  '/documents': 'My Documents',
  '/apply': 'Apply for Loan',
  '/applications': 'My Applications',
  '/settings': 'Settings',
};

export default function Header() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { currentUser, logout } = useAuth();
  const base = '/' + pathname.split('/')[1];
  const title = pathname.startsWith('/applications/') ? 'Application Detail' : (titles[base] || 'Everyday Bank');

  const initials = (currentUser?.name || currentUser?.email || '?')
    .split(/[\s@.]+/).filter(Boolean).slice(0, 2).map((s) => s[0]?.toUpperCase()).join('');

  return (
    <header className="header">
      <div className="header-title">{title}</div>
      <div className="header-right">
        <span className="badge-server">Online</span>
        {currentUser && (
          <>
            <div className="avatar"><div className="avatar-initials">{initials}</div></div>
            <span className="user-name" style={{ fontSize: '.82rem' }}>{currentUser.name || currentUser.email}</span>
            <button
              className="btn btn-ghost"
              style={{ padding: '5px 10px', fontSize: '.75rem' }}
              onClick={() => { logout(); navigate('/login'); }}
            >
              <LogOut size={13} /> Sign out
            </button>
          </>
        )}
      </div>
    </header>
  );
}
