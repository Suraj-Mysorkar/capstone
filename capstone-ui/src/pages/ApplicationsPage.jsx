import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const API_BASE_URL = import.meta.env.VITE_LOAN_API_URL || 'https://team6-loan-service.azurewebsites.net/api/v1/loans';

const STATUSES = ['ALL', 'APPROVED', 'REJECTED', 'MANUAL_REVIEW_REQUIRED', 'DOCUMENT_REVIEW_PENDING', 'SUBMITTED', 'VALIDATING', 'CREDIT_ASSESSMENT'];

function statusBadge(s) {
  if (s === 'APPROVED') return <span className="badge badge-approved">Approved</span>;
  if (s === 'REJECTED') return <span className="badge badge-rejected">Rejected</span>;
  if (s === 'MANUAL_REVIEW_REQUIRED') return <span className="badge badge-review">Manual Review</span>;
  if (s === 'DOCUMENT_REVIEW_PENDING') return <span className="badge badge-warning" style={{ background: '#f59e0b20', color: '#d97706', border: '1px solid #d97706' }}>Awaiting Documents</span>;
  return <span className="badge badge-default">{s}</span>;
}

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);
}

// API service function
const fetchApplications = async (status) => {
  try {
    const url = status
      ? `${API_BASE_URL}/applications?status=${status}`
      : `${API_BASE_URL}/applications`;

    const response = await fetch(url);

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    console.log('API Response:', data);
    return data;
  } catch (error) {
    console.error('Error fetching applications:', error);
    throw error;
  }
};

export default function ApplicationsPage() {
  const [apps, setApps] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('ALL');
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    try {
      const data = await fetchApplications(filter !== 'ALL' ? filter : undefined);
      setApps(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('Error loading applications:', error);
      setApps([]);
    }
    setLoading(false);
  };

  useEffect(() => { load(); }, [filter]);

  return (
    <div className="page">
      <div className="filter-bar">
        {STATUSES.map(s => (
          <button
            key={s}
            className={`filter-btn${filter === s ? ' active' : ''}`}
            onClick={() => setFilter(s)}
          >
            {s === 'ALL' ? 'All' : s.replace(/_/g, ' ')}
          </button>
        ))}
        <button className="btn btn-ghost" style={{ marginLeft: 'auto', padding: '7px 16px', fontSize: '.8rem' }} onClick={load}>
          ↺ Refresh
        </button>
      </div>

      <div className="card">
        {loading ? <div className="spinner" /> : apps.length === 0 ? (
          <div className="empty">No applications found for this filter.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Application ID</th>
                  <th>Customer ID</th>
                  <th>Customer</th>
                  <th>Scheme</th>
                  <th>Loan Type</th>
                  <th>Amount</th>
                  <th>Tenure</th>
                  <th>Risk Score</th>
                  <th>Status</th>
                  <th>Applied</th>
                </tr>
              </thead>
              <tbody>
                {apps.map(a => (
                  <tr key={a.applicationId} onClick={() => navigate(`/applications/${a.applicationId}`)}>
                    <td className="font-mono" style={{ fontSize: '.75rem', color: 'var(--accent)' }}>
                      {a.applicationId}
                    </td>
                    <td className="font-mono" style={{ fontSize: '.75rem', color: 'var(--accent)' }}>
                      {a.customerId}
                    </td>
                    <td>
                      <div style={{ fontWeight: 600 }}>{a.customerName}</div>
                      <div className="text-muted">{a.customerEmail}</div>
                    </td>
                    <td className="font-mono" style={{ fontSize: '.8rem' }}>
                      {a.schemeId}
                      <div style={{ fontSize: '.7rem', color: 'var(--muted)' }}>{a.schemeName}</div>
                    </td>
                    <td className="text-muted" style={{ fontSize: '.8rem' }}>
                      {a.loanType?.replace(/_/g, ' ')}
                    </td>
                    <td>{fmt(a.loanAmount)}</td>
                    <td>{a.tenureMonths}m</td>
                    <td>
                      <span style={{
                        fontWeight: 700,
                        color: a.riskScore == null ? 'var(--muted)' :
                          a.riskScore <= 30 ? 'var(--green)' :
                            a.riskScore >= 70 ? 'var(--red)' : 'var(--yellow)'
                      }}>{a.riskScore ?? '—'}</span>
                      {a.dtiRatio && (
                        <div style={{ fontSize: '.6rem', color: 'var(--muted)' }}>
                          DTI: {a.dtiRatio.toFixed(1)}%
                        </div>
                      )}
                    </td>
                    <td>{statusBadge(a.status)}</td>
                    <td className="text-muted">
                      {new Date(a.createdAt).toLocaleDateString('en-IN', {
                        day: '2-digit',
                        month: '2-digit',
                        year: 'numeric'
                      })}
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