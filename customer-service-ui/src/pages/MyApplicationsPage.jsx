import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCw } from 'lucide-react';
import { fetchApplications } from '../services/loanApi';
import { useSession } from '../lib/session';

const STATUSES = ['ALL', 'APPROVED', 'REJECTED', 'MANUAL_REVIEW_REQUIRED', 'DOCUMENTS_SUBMITTED', 'DOCUMENT_REVIEW_PENDING', 'SUBMITTED'];

function statusBadge(s) {
  if (s === 'APPROVED') return <span className="badge badge-approved">Approved</span>;
  if (s === 'REJECTED') return <span className="badge badge-rejected">Rejected</span>;
  if (s === 'MANUAL_REVIEW_REQUIRED') return <span className="badge badge-review">Under Review</span>;
  if (s === 'DOCUMENTS_SUBMITTED') return <span className="badge" style={{ background: 'rgba(0, 210, 255, 0.15)', color: 'var(--accent)', border: '1px solid var(--accent)' }}>Documents Submitted</span>;
  if (s === 'DOCUMENT_REVIEW_PENDING') return <span className="badge badge-warning">Awaiting Documents</span>;
  return <span className="badge badge-default">{(s || '').replace(/_/g, ' ')}</span>;
}

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n || 0);
}

export default function MyApplicationsPage() {
  const { session } = useSession();
  const [apps, setApps] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('ALL');
  const navigate = useNavigate();

  const mine = (list) => {
    const email = (session?.email || '').toLowerCase();
    const cid = session?.loanCustomerId;
    return list.filter(
      (a) =>
        (a.customerEmail || '').toLowerCase() === email ||
        (cid && a.customerId === cid),
    );
  };

  const load = async () => {
    setLoading(true);
    try {
      const data = await fetchApplications(filter !== 'ALL' ? filter : undefined);
      setApps(mine(Array.isArray(data) ? data : []));
    } catch (e) {
      setApps([]);
    }
    setLoading(false);
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [filter, session?.email, session?.loanCustomerId]);

  return (
    <div className="page">
      <div className="filter-bar">
        {STATUSES.map((s) => (
          <button key={s} className={`filter-btn${filter === s ? ' active' : ''}`} onClick={() => setFilter(s)}>
            {s === 'ALL' ? 'All' : s.replace(/_/g, ' ')}
          </button>
        ))}
        <button className="btn btn-ghost" style={{ marginLeft: 'auto', padding: '7px 16px', fontSize: '.8rem' }} onClick={load}>
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      <div className="card">
        {loading ? <div className="spinner" /> : apps.length === 0 ? (
          <div className="empty">
            No applications yet.{' '}
            <a onClick={() => navigate('/apply')} style={{ color: 'var(--accent)', cursor: 'pointer' }}>Apply now →</a>
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Application ID</th><th>Scheme</th><th>Loan Type</th>
                  <th>Amount</th><th>Tenure</th><th>EMI</th><th>Assigned Manager</th><th>Status</th><th>Applied</th>
                </tr>
              </thead>
              <tbody>
                {apps.map((a) => (
                  <tr key={a.applicationId} onClick={() => navigate(`/applications/${a.applicationId}`)}>
                    <td className="font-mono" style={{ fontSize: '.75rem', color: 'var(--accent)' }}>{a.applicationId}</td>
                    <td className="font-mono" style={{ fontSize: '.8rem' }}>
                      {a.schemeId}
                      <div style={{ fontSize: '.7rem', color: 'var(--muted)' }}>{a.schemeName}</div>
                    </td>
                    <td className="text-muted" style={{ fontSize: '.8rem' }}>{a.loanType?.replace(/_/g, ' ')}</td>
                    <td>{fmt(a.loanAmount)}</td>
                    <td>{a.tenureMonths}m</td>
                    <td>{a.calculatedEMI != null ? fmt(a.calculatedEMI) : '—'}</td>
                    <td>
                      <div style={{ fontSize: '.82rem', fontWeight: 600, color: '#fff' }}>
                        {a.assignedManagerName || (a.assignedManager === 'markj' ? 'Mark Johnson' : a.assignedManager || 'Assigned Officer')}
                      </div>
                      <div style={{ fontSize: '.7rem', color: 'var(--muted)' }}>
                        {a.assignedManagerPhone || a.assignedManagerEmail || ''}
                      </div>
                    </td>
                    <td>{statusBadge(a.status)}</td>
                    <td className="text-muted">
                      {a.createdAt ? new Date(a.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
