import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard, BookOpen, Calculator, FolderUp,
  FileText, CheckCircle, Settings, HelpCircle, Activity,
  LogOut, UserCheck
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const links = [
  { to: '/dashboard',    icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/schemes',      icon: BookOpen,         label: 'Loan Schemes' },
  { to: '/emi',          icon: Calculator,       label: 'EMI Calculator' },
  { to: '/documents',    icon: FolderUp,         label: 'Documents' },
  { to: '/apply',        icon: FileText,         label: 'Apply for Loan' },
  { to: '/applications', icon: CheckCircle,      label: 'Applications' },
];

export default function Sidebar() {
  const { currentUser, logout } = useAuth();

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <Activity size={26} />
        Capstone
      </div>

      {links.map(({ to, icon: Icon, label }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
        >
          <Icon size={18} />
          {label}
        </NavLink>
      ))}

      <div className="nav-divider" />
      <div className="nav-link" style={{ cursor: 'default' }}>
        <Settings size={18} /> Settings
      </div>
      <div className="nav-link" style={{ cursor: 'default' }}>
        <HelpCircle size={18} /> Help
      </div>

      {currentUser && (
        <div style={{ marginTop: 'auto', paddingTop: 16, borderTop: '1px solid var(--border)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 12px', borderRadius: 6, background: 'rgba(255,255,255,0.02)', marginBottom: 8 }}>
            <UserCheck size={16} color="var(--accent)" />
            <div style={{ overflow: 'hidden' }}>
              <div style={{ fontSize: '0.78rem', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {currentUser.name || currentUser.username}
              </div>
              <div style={{ fontSize: '0.68rem', color: 'var(--muted)' }}>
                {currentUser.username}
              </div>
            </div>
          </div>

          <button
            className="btn btn-ghost"
            style={{ width: '100%', fontSize: '0.78rem', padding: '6px 10px', justifyContent: 'flex-start', color: '#ef4444' }}
            onClick={logout}
          >
            <LogOut size={14} /> Sign Out
          </button>
        </div>
      )}
    </aside>
  );
}
