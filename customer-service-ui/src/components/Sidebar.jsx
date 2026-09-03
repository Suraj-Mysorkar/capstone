import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard, Calculator, FolderUp, FileText,
  CheckCircle, Settings, HelpCircle, Landmark, LogOut,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const links = [
  { to: '/', icon: LayoutDashboard, label: 'Home' },
  { to: '/emi', icon: Calculator, label: 'EMI Calculator' },
  { to: '/documents', icon: FolderUp, label: 'My Documents' },
  { to: '/apply', icon: FileText, label: 'Apply for Loan' },
  { to: '/applications', icon: CheckCircle, label: 'My Applications' },
];

export default function Sidebar() {
  const { currentUser, logout } = useAuth();
  const navigate = useNavigate();
  const signOut = () => { logout(); navigate('/login'); };

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <Landmark size={24} />
        Everyday Bank
      </div>

      {links.map(({ to, icon: Icon, label }) => (
        <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
          <Icon size={18} />
          {label}
        </NavLink>
      ))}

      <div className="nav-divider" />

      <NavLink to="/settings" className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
        <Settings size={18} /> Settings
      </NavLink>
      <div className="nav-link" style={{ cursor: 'default' }}>
        <HelpCircle size={18} /> Help
      </div>

      <div className="nav-spacer" />

      {currentUser && (
        <>
          <div className="nav-divider" />
          <div style={{ padding: '0 14px 8px', fontSize: '.72rem', color: 'var(--muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {currentUser.name || currentUser.email}
          </div>
          <button className="nav-link" style={{ background: 'none', border: 'none', width: '100%', cursor: 'pointer' }} onClick={signOut}>
            <LogOut size={18} /> Sign out
          </button>
        </>
      )}
    </aside>
  );
}
