import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, PieChart, Pie, Cell, Legend
} from 'recharts';
import { fetchApplications } from '../services/api';
import { BarChart2, DollarSign, CheckCircle2, AlertCircle, Clock, TrendingUp } from 'lucide-react';

function statusBadge(status) {
  if (status === 'APPROVED')              return <span className="badge badge-approved">Approved</span>;
  if (status === 'REJECTED')              return <span className="badge badge-rejected">Rejected</span>;
  if (status === 'MANUAL_REVIEW_REQUIRED') return <span className="badge badge-review">Manual Review</span>;
  return <span className="badge badge-default">{status}</span>;
}

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);
}

const COLORS = ['#2ecc71', '#e74c3c', '#f1c40f', '#00d2ff'];
const MONTH_NAMES = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

export default function Dashboard() {
  const [apps, setApps] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const load = async () => {
    const data = await fetchApplications();
    setApps(Array.isArray(data) ? data : []);
    setLoading(false);
  };

  useEffect(() => {
    load();
    const t = setInterval(load, 10000);
    return () => clearInterval(t);
  }, []);

  const approved = apps.filter(a => a.status === 'APPROVED').length;
  const rejected = apps.filter(a => a.status === 'REJECTED').length;
  const review   = apps.filter(a => a.status === 'MANUAL_REVIEW_REQUIRED').length;
  const total    = apps.length;
  const totalAmt = apps.reduce((s, a) => s + (a.loanAmount || 0), 0);
  const rate     = total ? ((approved / total) * 100).toFixed(1) + '%' : '—';

  // Build trend data by month
  const trendMap = {};
  apps.forEach(a => {
    const d = new Date(a.createdAt || Date.now());
    const key = MONTH_NAMES[d.getMonth()];
    if (!trendMap[key]) trendMap[key] = { name: key, submitted: 0, approved: 0 };
    trendMap[key].submitted++;
    if (a.status === 'APPROVED') trendMap[key].approved++;
  });
  const trend = Object.values(trendMap).slice(-6);

  // Pie breakdown
  const pie = [
    { name: 'Approved', value: approved },
    { name: 'Rejected', value: rejected },
    { name: 'Review',   value: review   },
    { name: 'Other',    value: total - approved - rejected - review },
  ].filter(p => p.value > 0);

  return (
    <div className="page">
      {/* Stats */}
      <div className="stats-grid">
        {[
          { label: 'Total Applications', value: total,    icon: BarChart2,    color: '#00d2ff' },
          { label: 'Total Requested',    value: fmt(totalAmt), icon: DollarSign, color: '#9d4edd' },
          { label: 'Approved',           value: approved, icon: CheckCircle2, color: '#2ecc71' },
          { label: 'Rejected',           value: rejected, icon: AlertCircle,  color: '#e74c3c' },
          { label: 'Pending Review',     value: review,   icon: Clock,        color: '#f1c40f' },
          { label: 'Approval Rate',      value: rate,     icon: TrendingUp,   color: '#00d2ff' },
        ].map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="card stat-card">
            <div>
              <div className="stat-label">{label}</div>
              <div className="stat-value" style={{ fontSize: '1.6rem' }}>{value}</div>
            </div>
            <div className="stat-icon" style={{ background: color + '18', color }}>
              <Icon size={20} />
            </div>
          </div>
        ))}
      </div>

      {/* Charts row */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 20, marginBottom: 24 }}>
        {/* Area chart */}
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 20 }}>Application Trend</div>
          {trend.length === 0 ? (
            <div className="empty">Submit applications to see trend data</div>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={trend}>
                <defs>
                  <linearGradient id="gs" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#00d2ff" stopOpacity={0.25}/>
                    <stop offset="95%" stopColor="#00d2ff" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="ga" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#9d4edd" stopOpacity={0.25}/>
                    <stop offset="95%" stopColor="#9d4edd" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,.05)" vertical={false}/>
                <XAxis dataKey="name" tick={{ fill:'#7b7f9e', fontSize:12 }} stroke="transparent"/>
                <YAxis tick={{ fill:'#7b7f9e', fontSize:12 }} stroke="transparent"/>
                <Tooltip contentStyle={{ background:'#13182e', border:'1px solid rgba(255,255,255,.1)', borderRadius:8 }}/>
                <Area type="monotone" dataKey="submitted" stroke="#00d2ff" strokeWidth={2} fill="url(#gs)" name="Submitted"/>
                <Area type="monotone" dataKey="approved"  stroke="#9d4edd" strokeWidth={2} fill="url(#ga)" name="Approved"/>
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Pie chart */}
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 20 }}>Status Breakdown</div>
          {pie.length === 0 ? (
            <div className="empty">No data yet</div>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={pie} cx="50%" cy="45%" innerRadius={55} outerRadius={80} dataKey="value" paddingAngle={3}>
                  {pie.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]}/>)}
                </Pie>
                <Legend iconType="circle" wrapperStyle={{ fontSize: 12, color: '#7b7f9e' }}/>
                <Tooltip contentStyle={{ background:'#13182e', border:'1px solid rgba(255,255,255,.1)', borderRadius:8 }}/>
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Recent applications */}
      <div className="card">
        <div className="card-header">
          <div className="card-header-title">Recent Applications</div>
        </div>
        {loading ? <div className="spinner" /> : apps.length === 0 ? (
          <div className="empty">No applications yet. <a onClick={() => navigate('/apply')} style={{ color:'var(--accent)', cursor:'pointer' }}>Apply now →</a></div>
        ) : (
          <div className="table-wrap p-4">
            <table>
              <thead>
                <tr>
                  <th>Application ID</th><th>Customer</th><th>Scheme</th>
                  <th>Amount</th><th>Risk</th><th>Status</th>
                </tr>
              </thead>
              <tbody>
                {apps.slice(0, 8).map(a => (
                  <tr key={a.id} onClick={() => navigate(`/applications/${a.id}`)}>
                    <td className="font-mono" style={{ fontSize: '.78rem', color:'var(--accent)' }}>{a.id}</td>
                    <td>
                      <div style={{ fontWeight:600 }}>{a.customerName}</div>
                      <div className="text-muted">{a.customerEmail}</div>
                    </td>
                    <td>{a.schemeId}</td>
                    <td>{fmt(a.loanAmount)}</td>
                    <td>
                      <span style={{
                        color: a.riskScore <= 30 ? 'var(--green)' : a.riskScore >= 70 ? 'var(--red)' : 'var(--yellow)',
                        fontWeight: 700
                      }}>{a.riskScore ?? '—'}</span>
                    </td>
                    <td>{statusBadge(a.status)}</td>
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
