import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard, BookOpen, Calculator, FolderUp,
  FileText, CheckCircle, Settings, HelpCircle, Activity,
  ShieldCheck, LogIn
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const links = [
  { to: '/',             icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/schemes',      icon: BookOpen,         label: 'Loan Schemes' },
  { to: '/emi',          icon: Calculator,       label: 'EMI Calculator' },
  { to: '/documents',    icon: FolderUp,         label: 'Documents' },
  { to: '/apply',        icon: FileText,         label: 'Apply for Loan' },
  { to: '/applications', icon: CheckCircle,      label: 'Applications' },
  { to: '/login',        icon: ShieldCheck,      label: 'Employee Portal' },
];

export default function Sidebar() {
  const { currentUser, isAuthenticated } = useAuth();

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
          end={to === '/'}
          className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
        >
          <Icon size={18} />
          {label}
          {to === '/login' && isAuthenticated && (
            <span
              style={{
                marginLeft: 'auto',
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: '#10b981',
                boxShadow: '0 0 8px #10b981'
              }}
            />
          )}
        </NavLink>
      ))}

      <div className="nav-divider" />
      <div className="nav-link" style={{ cursor: 'default' }}>
        <Settings size={18} /> Settings
      </div>
      <div className="nav-link" style={{ cursor: 'default' }}>
        <HelpCircle size={18} /> Help
      </div>
    </aside>
  );
}
