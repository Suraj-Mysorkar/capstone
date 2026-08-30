import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
} from 'recharts';
import { Users, UserCheck, ShieldAlert, Clock, RefreshCw } from 'lucide-react';
import { listCustomers, getToken } from '../services/api';
import { ONBOARDING_STATUSES, prettyStatus, statusBadgeClass } from '../lib/onboarding';

const BAR_COLORS = {
  REGISTERED: '#00d2ff',
  DOCUMENTS_PENDING: '#f1c40f',
  DOCUMENTS_SUBMITTED: '#f1c40f',
  KYC_IN_REVIEW: '#9d4edd',
  KYC_APPROVED: '#2ecc71',
  KYC_REJECTED: '#e74c3c',
  ONBOARDING_COMPLETE: '#2ecc71',
  SUSPENDED: '#e74c3c',
};

function StatCard({ label, value, icon: Icon }) {
  return (
    <div className="card stat-card">
      <div>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
      </div>
      <div className="stat-icon"><Icon size={22} /></div>
    </div>
  );
}

export default function Dashboard() {
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      // Pull a wide page for the overview; the directory page does real pagination.
      const page = await listCustomers({ size: 200, sort: 'createdAt,desc' });
      setCustomers(page?.content ?? []);
    } catch (e) {
      setError(e.status === 401
        ? 'Not authorized — set a bearer token on the Settings page.'
        : e.message);
      setCustomers([]);
    }
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const counts = ONBOARDING_STATUSES.map((s) => ({
    status: s,
    label: prettyStatus(s),
    count: customers.filter((c) => c.onboardingStatus === s).length,
  }));

  const total = customers.length;
  const complete = counts.find((c) => c.status === 'ONBOARDING_COMPLETE')?.count ?? 0;
  const inReview = counts.find((c) => c.status === 'KYC_IN_REVIEW')?.count ?? 0;
  const suspended = counts.find((c) => c.status === 'SUSPENDED')?.count ?? 0;

  return (
    <div className="page">
      {!getToken() && (
        <div className="info-box">
          No bearer token set — the customer-service API rejects unauthenticated calls.
          Add an Entra ID JWT on the <strong>Settings</strong> page to load data.
        </div>
      )}
      {error && <div className="error-box">{error}</div>}

      <div className="stats-grid">
        <StatCard label="Total Customers" value={loading ? '—' : total} icon={Users} />
        <StatCard label="Onboarding Complete" value={loading ? '—' : complete} icon={UserCheck} />
        <StatCard label="KYC In Review" value={loading ? '—' : inReview} icon={Clock} />
        <StatCard label="Suspended" value={loading ? '—' : suspended} icon={ShieldAlert} />
      </div>

      <div className="card p-6 mt-4">
        <div className="card-header-title" style={{ marginBottom: 16 }}>
          Onboarding Status Distribution
        </div>
        {loading ? <div className="spinner" /> : (
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={counts} margin={{ top: 8, right: 8, left: -18, bottom: 40 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,.06)" />
              <XAxis
                dataKey="label" tick={{ fill: '#7b7f9e', fontSize: 11 }}
                angle={-25} textAnchor="end" interval={0} height={60}
              />
              <YAxis allowDecimals={false} tick={{ fill: '#7b7f9e', fontSize: 11 }} />
              <Tooltip
                contentStyle={{ background: '#0d1225', border: '1px solid rgba(255,255,255,.1)', borderRadius: 8 }}
                labelStyle={{ color: '#eef0f7' }}
              />
              <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                {counts.map((c) => <Cell key={c.status} fill={BAR_COLORS[c.status] || '#7b7f9e'} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="card mt-4">
        <div className="card-header">
          <div className="card-header-title">Recent Customers</div>
          <button className="btn btn-ghost" style={{ padding: '7px 16px', fontSize: '.8rem' }} onClick={load}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
        {loading ? <div className="spinner" /> : customers.length === 0 ? (
          <div className="empty">No customers yet.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th><th>Email</th><th>City</th><th>Status</th><th>Created</th>
                </tr>
              </thead>
              <tbody>
                {customers.slice(0, 8).map((c) => (
                  <tr key={c.id} onClick={() => navigate(`/customers/${c.id}`)}>
                    <td style={{ fontWeight: 600 }}>{c.firstName} {c.lastName}</td>
                    <td className="text-muted">{c.email}</td>
                    <td className="text-muted">{c.city || '—'}</td>
                    <td>
                      <span className={`badge ${statusBadgeClass(c.onboardingStatus)}`}>
                        {prettyStatus(c.onboardingStatus)}
                      </span>
                    </td>
                    <td className="text-muted">
                      {c.createdAt ? new Date(c.createdAt).toLocaleDateString() : '—'}
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
