import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard, BookOpen, Calculator, FolderUp,
  FileText, CheckCircle, Settings, HelpCircle, Activity
} from 'lucide-react';

const links = [
  { to: '/',            icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/schemes',     icon: BookOpen,         label: 'Loan Schemes' },
  { to: '/emi',         icon: Calculator,       label: 'EMI Calculator' },
  { to: '/documents',   icon: FolderUp,         label: 'Documents' },
  { to: '/apply',       icon: FileText,         label: 'Apply for Loan' },
  { to: '/applications',icon: CheckCircle,      label: 'Applications' },
];

export default function Sidebar() {
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
