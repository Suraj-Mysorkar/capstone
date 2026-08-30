import React, { useEffect, useState } from 'react';
import { useLocation, Link } from 'react-router-dom';
import { KeyRound } from 'lucide-react';
import { ping, getToken } from '../services/api';

const titles = {
  '/':          'Dashboard',
  '/customers': 'Customer Directory',
  '/register':  'Register Customer',
  '/lookup':    'Customer Lookup',
  '/settings':  'Settings',
};

export default function Header() {
  const { pathname } = useLocation();
  const base = '/' + pathname.split('/')[1];
  const title = pathname.startsWith('/customers/') ? 'Customer Detail' : (titles[base] || 'Dashboard');

  const [health, setHealth] = useState('checking'); // checking | up | down
  const hasToken = !!getToken();

  useEffect(() => {
    let alive = true;
    ping()
      .then(() => alive && setHealth('up'))
      .catch(() => alive && setHealth('down'));
    return () => { alive = false; };
  }, [pathname]);

  return (
    <header className="header">
      <div className="header-title">{title}</div>
      <div className="header-right">
        <span
          className="badge-server"
          style={{ color: health === 'up' ? 'var(--green)' : health === 'down' ? 'var(--red)' : 'var(--muted)' }}
        >
          {health === 'up' ? 'customer-service · UP' : health === 'down' ? 'customer-service · unreachable' : 'checking…'}
        </span>

        <Link
          to="/settings"
          className="badge-server"
          style={{ color: hasToken ? 'var(--accent)' : 'var(--muted)', textDecoration: 'none' }}
          title={hasToken ? 'Bearer token is set' : 'No bearer token — protected calls will 401'}
        >
          <KeyRound size={14} />
          {hasToken ? 'Token set' : 'No token'}
        </Link>

        <div className="avatar">CS</div>
      </div>
    </header>
  );
}
