import React from 'react';
import { useLocation } from 'react-router-dom';

const titles = {
  '/':             'Dashboard',
  '/schemes':      'Loan Schemes',
  '/emi':          'EMI Calculator',
  '/documents':    'Document Upload',
  '/apply':        'Apply for Loan',
  '/applications': 'Applications',
};

export default function Header() {
  const { pathname } = useLocation();
  const base = '/' + pathname.split('/')[1];
  const title = titles[base] || 'Dashboard';

  return (
    <header className="header">
      <div className="header-title">{title}</div>
      <div className="header-right">
        <span className="badge-server">Cloud Live · Azure</span>
        <div className="avatar">
          <div className="avatar-initials">AK</div>
        </div>
        <span className="user-name">Arpit Kumar</span>
      </div>
    </header>
  );
}
