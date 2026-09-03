import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, PieChart, Pie, Cell, Legend
} from 'recharts';
import { fetchApplications, fetchSchemes } from '../services/api';
import {
  BarChart2, DollarSign, CheckCircle2, AlertCircle, Clock,
  TrendingUp, ShieldAlert, Layers
} from 'lucide-react';

function statusBadge(status) {
  if (status === 'APPROVED') return <span className="badge badge-approved">Approved</span>;
  if (status === 'REJECTED') return <span className="badge badge-rejected">Rejected</span>;
  if (status === 'MANUAL_REVIEW_REQUIRED') return <span className="badge badge-review">Manual Review</span>;
  if (status === 'DOCUMENT_REVIEW_PENDING') return <span className="badge badge-warning" style={{ background: '#f59e0b20', color: '#d97706', border: '1px solid #d97706' }}>Awaiting Docs</span>;
  return <span className="badge badge-default">{status}</span>;
}

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n);
}

function fmtCompact(n) {
  if (n >= 10000000) return '₹' + (n / 10000000).toFixed(2) + ' Cr';
  if (n >= 100000) return '₹' + (n / 100000).toFixed(1) + ' Lakh';
  if (n >= 1000) return '₹' + (n / 1000).toFixed(0) + 'k';
  return '₹' + n;
}

const PIE_COLORS = ['#2ecc71', '#e74c3c', '#f1c40f', '#00d2ff', '#9d4edd'];

export default function Dashboard() {
  const [apps, setApps] = useState([]);
  const [schemes, setSchemes] = useState([]);
  const [chartView, setChartView] = useState('scheme'); // 'scheme' | 'risk'
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const load = async () => {
    try {
      const [appsData, schemesData] = await Promise.all([
        fetchApplications(),
        fetchSchemes()
      ]);
      setApps(Array.isArray(appsData) ? appsData : []);
      setSchemes(Array.isArray(schemesData) ? schemesData : []);
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    const t = setInterval(load, 15000);
    const handleDataUpdate = () => {
      console.log('Real-time event: auto-refreshing dashboard metrics');
      load();
    };
    window.addEventListener('loan-data-updated', handleDataUpdate);
    return () => {
      clearInterval(t);
      window.removeEventListener('loan-data-updated', handleDataUpdate);
    };
  }, []);

  const approved = apps.filter(a => a.status === 'APPROVED').length;
  const rejected = apps.filter(a => a.status === 'REJECTED').length;
  const review = apps.filter(a => a.status === 'MANUAL_REVIEW_REQUIRED').length;
  const docPending = apps.filter(a => a.status === 'DOCUMENT_REVIEW_PENDING').length;
  const total = apps.length;
  const totalAmt = apps.reduce((s, a) => s + (a.loanAmount || 0), 0);
  const rate = total ? ((approved / total) * 100).toFixed(1) + '%' : '—';

  // ── Chart 1: Scheme Distribution ───────────────────────────────────────────
  const schemeNameMap = {
    'SCHEME-PL-01': 'Personal Loan',
    'SCHEME-HL-01': 'Home Loan',
    'SCHEME-VL-01': 'Auto Loan',
    'SCHEME-EL-01': 'Education Loan',
  };
  schemes.forEach(s => {
    if (s.schemeId) schemeNameMap[s.schemeId] = s.schemeName || s.schemeId;
  });

  const schemeAgg = {};
  apps.forEach(a => {
    const sId = a.schemeId || 'Other';
    const sName = schemeNameMap[sId] || sId;
    if (!schemeAgg[sId]) {
      schemeAgg[sId] = {
        name: sName,
        code: sId,
        applications: 0,
        totalAmount: 0,
        approvedAmount: 0,
      };
    }
    schemeAgg[sId].applications += 1;
    schemeAgg[sId].totalAmount += (a.loanAmount || 0);
    if (a.status === 'APPROVED') {
      schemeAgg[sId].approvedAmount += (a.loanAmount || 0);
    }
  });
  const schemeChartData = Object.values(schemeAgg);

  // ── Chart 2: Risk Profile Breakdown ────────────────────────────────────────
  const lowRiskApps = apps.filter(a => a.riskScore != null && a.riskScore <= 30);
  const medRiskApps = apps.filter(a => a.riskScore != null && a.riskScore > 30 && a.riskScore < 70);
  const highRiskApps = apps.filter(a => a.riskScore != null && a.riskScore >= 70);

  const riskChartData = [
    {
      category: 'Low Risk (0–30)',
      applications: lowRiskApps.length,
      totalAmount: lowRiskApps.reduce((s, a) => s + (a.loanAmount || 0), 0),
      avgDti: lowRiskApps.length ? (lowRiskApps.reduce((s, a) => s + (a.dtiRatio || 0), 0) / lowRiskApps.length).toFixed(1) : 0,
      color: '#2ecc71',
    },
    {
      category: 'Moderate (31–69)',
      applications: medRiskApps.length,
      totalAmount: medRiskApps.reduce((s, a) => s + (a.loanAmount || 0), 0),
      avgDti: medRiskApps.length ? (medRiskApps.reduce((s, a) => s + (a.dtiRatio || 0), 0) / medRiskApps.length).toFixed(1) : 0,
      color: '#f1c40f',
    },
    {
      category: 'High Risk (70–100)',
      applications: highRiskApps.length,
      totalAmount: highRiskApps.reduce((s, a) => s + (a.loanAmount || 0), 0),
      avgDti: highRiskApps.length ? (highRiskApps.reduce((s, a) => s + (a.dtiRatio || 0), 0) / highRiskApps.length).toFixed(1) : 0,
      color: '#e74c3c',
    },
  ];

  // ── Pie Breakdown ──────────────────────────────────────────────────────────
  const pie = [
    { name: 'Approved', value: approved },
    { name: 'Underwriter Review', value: review },
    { name: 'Awaiting Docs', value: docPending },
    { name: 'Rejected', value: rejected },
    { name: 'Other / In-Flight', value: total - approved - rejected - review - docPending },
  ].filter(p => p.value > 0);

  return (
    <div className="page">
      {/* Stats Cards */}
      <div className="stats-grid">
        {[
          { label: 'Total Applications', value: total, icon: BarChart2, color: '#00d2ff' },
          { label: 'Total Portfolio Volume', value: fmtCompact(totalAmt), icon: DollarSign, color: '#9d4edd' },
          { label: 'Approved Applications', value: approved, icon: CheckCircle2, color: '#2ecc71' },
          { label: 'Underwriter Review Queue', value: review, icon: Clock, color: '#f1c40f' },
          { label: 'Awaiting Documents', value: docPending, icon: ShieldAlert, color: '#ff9f43' },
          { label: 'Approval Rate', value: rate, icon: TrendingUp, color: '#00d2ff' },
        ].map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="card stat-card">
            <div>
              <div className="stat-label">{label}</div>
              <div className="stat-value" style={{ fontSize: '1.55rem' }}>{value}</div>
            </div>
            <div className="stat-icon" style={{ background: color + '18', color }}>
              <Icon size={20} />
            </div>
          </div>
        ))}
      </div>

      {/* Main Charts Row */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 20, marginBottom: 24 }}>
        
        {/* Loan Scheme & Risk Analytics Bar Chart */}
        <div className="card p-6">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18, flexWrap: 'wrap', gap: 10 }}>
            <div>
              <div className="card-header-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {chartView === 'scheme' ? <Layers size={18} color="var(--accent)" /> : <ShieldAlert size={18} color="#f1c40f" />}
                {chartView === 'scheme' ? 'Portfolio Volume by Loan Scheme' : 'Underwriting & Risk Band Distribution'}
              </div>
              <div style={{ fontSize: '.78rem', color: 'var(--muted)', marginTop: 2 }}>
                {chartView === 'scheme'
                  ? 'Real-time loan requested amount & application counts across product schemes'
                  : 'Applicant credit risk assessment breakdown (Low, Moderate, High risk buckets)'}
              </div>
            </div>

            {/* Toggle Buttons */}
            <div style={{ display: 'flex', background: 'rgba(255,255,255,0.06)', borderRadius: 8, padding: 3, gap: 4 }}>
              <button
                className="btn"
                style={{
                  padding: '5px 12px',
                  fontSize: '.75rem',
                  background: chartView === 'scheme' ? 'var(--accent)' : 'transparent',
                  color: chartView === 'scheme' ? '#000' : 'var(--text)',
                  fontWeight: 700,
                  borderRadius: 6
                }}
                onClick={() => setChartView('scheme')}
              >
                By Scheme
              </button>
              <button
                className="btn"
                style={{
                  padding: '5px 12px',
                  fontSize: '.75rem',
                  background: chartView === 'risk' ? 'var(--accent)' : 'transparent',
                  color: chartView === 'risk' ? '#000' : 'var(--text)',
                  fontWeight: 700,
                  borderRadius: 6
                }}
                onClick={() => setChartView('risk')}
              >
                By Risk Band
              </button>
            </div>
          </div>

          {apps.length === 0 ? (
            <div className="empty">Submit loan applications to populate portfolio charts</div>
          ) : chartView === 'scheme' ? (
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={schemeChartData} margin={{ top: 10, right: 20, left: 10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,.05)" vertical={false} />
                <XAxis dataKey="name" tick={{ fill: '#8e94b2', fontSize: 11 }} stroke="transparent" />
                <YAxis
                  yAxisId="left"
                  tickFormatter={fmtCompact}
                  tick={{ fill: '#8e94b2', fontSize: 11 }}
                  stroke="transparent"
                />
                <YAxis
                  yAxisId="right"
                  orientation="right"
                  tick={{ fill: '#8e94b2', fontSize: 11 }}
                  stroke="transparent"
                />
                <Tooltip
                  formatter={(value, name) => [
                    name === 'Total Requested' ? fmt(value) : value,
                    name
                  ]}
                  contentStyle={{
                    background: '#10162c',
                    border: '1px solid rgba(255,255,255,.15)',
                    borderRadius: 8,
                    boxShadow: '0 8px 24px rgba(0,0,0,0.5)'
                  }}
                />
                <Legend wrapperStyle={{ fontSize: 12, color: '#8e94b2', paddingTop: 10 }} />
                <Bar yAxisId="left" dataKey="totalAmount" name="Total Requested" fill="#00d2ff" radius={[4, 4, 0, 0]} />
                <Bar yAxisId="right" dataKey="applications" name="Applications Count" fill="#9d4edd" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={riskChartData} margin={{ top: 10, right: 20, left: 10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,.05)" vertical={false} />
                <XAxis dataKey="category" tick={{ fill: '#8e94b2', fontSize: 11 }} stroke="transparent" />
                <YAxis
                  yAxisId="left"
                  tickFormatter={fmtCompact}
                  tick={{ fill: '#8e94b2', fontSize: 11 }}
                  stroke="transparent"
                />
                <YAxis
                  yAxisId="right"
                  orientation="right"
                  tick={{ fill: '#8e94b2', fontSize: 11 }}
                  stroke="transparent"
                />
                <Tooltip
                  formatter={(value, name) => [
                    name === 'Portfolio Volume' ? fmt(value) : value,
                    name
                  ]}
                  contentStyle={{
                    background: '#10162c',
                    border: '1px solid rgba(255,255,255,.15)',
                    borderRadius: 8,
                    boxShadow: '0 8px 24px rgba(0,0,0,0.5)'
                  }}
                />
                <Legend wrapperStyle={{ fontSize: 12, color: '#8e94b2', paddingTop: 10 }} />
                <Bar yAxisId="left" dataKey="totalAmount" name="Portfolio Volume" fill="#00d2ff" radius={[4, 4, 0, 0]}>
                  {riskChartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Bar>
                <Bar yAxisId="right" dataKey="applications" name="Applications Count" fill="#9d4edd" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Status Distribution Donut Chart */}
        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 4 }}>Lifecycle Status Breakdown</div>
          <div style={{ fontSize: '.78rem', color: 'var(--muted)', marginBottom: 16 }}>
            Current pipeline distribution
          </div>
          {pie.length === 0 ? (
            <div className="empty">No applications found</div>
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie
                  data={pie}
                  cx="50%"
                  cy="45%"
                  innerRadius={55}
                  outerRadius={82}
                  dataKey="value"
                  paddingAngle={3}
                >
                  {pie.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Legend iconType="circle" wrapperStyle={{ fontSize: 11, color: '#8e94b2' }} />
                <Tooltip
                  contentStyle={{
                    background: '#10162c',
                    border: '1px solid rgba(255,255,255,.15)',
                    borderRadius: 8
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Recent Applications Table */}
      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="card-header-title">Recent Loan Applications</div>
          <button className="btn btn-ghost" style={{ fontSize: '.8rem' }} onClick={() => navigate('/applications')}>
            View All Applications →
          </button>
        </div>
        {loading ? (
          <div className="spinner" />
        ) : apps.length === 0 ? (
          <div className="empty">
            No applications yet.{' '}
            <a onClick={() => navigate('/apply')} style={{ color: 'var(--accent)', cursor: 'pointer' }}>
              Apply now →
            </a>
          </div>
        ) : (
          <div className="table-wrap p-4">
            <table>
              <thead>
                <tr>
                  <th>Application ID</th>
                  <th>Applicant</th>
                  <th>Scheme</th>
                  <th>Loan Amount</th>
                  <th>Risk Score</th>
                  <th>DTI Ratio</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {apps.slice(0, 8).map(a => {
                  const appId = a.applicationId || a.id;
                  return (
                    <tr key={appId} onClick={() => navigate(`/applications/${appId}`)} style={{ cursor: 'pointer' }}>
                      <td className="font-mono" style={{ fontSize: '.78rem', color: 'var(--accent)', fontWeight: 600 }}>
                        {appId}
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{a.customerName}</div>
                        <div className="text-muted" style={{ fontSize: '.75rem' }}>{a.customerEmail}</div>
                      </td>
                      <td>{schemeNameMap[a.schemeId] || a.schemeId}</td>
                      <td style={{ fontWeight: 600 }}>{fmt(a.loanAmount)}</td>
                      <td>
                        <span
                          style={{
                            color: a.riskScore <= 30 ? 'var(--green)' : a.riskScore >= 70 ? 'var(--red)' : 'var(--yellow)',
                            fontWeight: 700
                          }}
                        >
                          {a.riskScore != null ? `${a.riskScore}/100` : '—'}
                        </span>
                      </td>
                      <td style={{ color: 'var(--muted)', fontSize: '.82rem' }}>
                        {a.dtiRatio != null ? `${a.dtiRatio}%` : '—'}
                      </td>
                      <td>{statusBadge(a.status)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
