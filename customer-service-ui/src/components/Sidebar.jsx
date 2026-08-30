import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard, Users, UserPlus, Search, Settings, HelpCircle, UserCog,
} from 'lucide-react';

const links = [
  { to: '/',          icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/customers', icon: Users,           label: 'Customers' },
  { to: '/register',  icon: UserPlus,        label: 'Register' },
  { to: '/lookup',    icon: Search,          label: 'Lookup' },
];

export default function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <UserCog size={26} />
        Customer Console
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

      <NavLink
        to="/settings"
        className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
      >
        <Settings size={18} /> Settings
      </NavLink>
      <div className="nav-link" style={{ cursor: 'default' }}>
        <HelpCircle size={18} /> Help
      </div>
    </aside>
  );
}
