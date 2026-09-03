import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  PieChart, Pie, Cell, Legend, Tooltip, ResponsiveContainer,
} from 'recharts';
import {
  FileText, CheckCircle2, Clock, ShieldAlert, FolderUp, Calculator, RefreshCw, ArrowRight,
} from 'lucide-react';
import { fetchApplications, fetchCustomerDocuments, fetchSchemes } from '../services/loanApi';
import { getCustomerByEmail } from '../services/api';
import { useSession, docCustomerId } from '../lib/session';
import { HAPPY_PATH, prettyStatus } from '../lib/onboarding';

const PIE_COLORS = ['#10b981', '#f59e0b', '#f97316', '#ef4444', '#6366f1'];

const TYPE_ICONS = { PERSONAL_LOAN: '👤', HOME_LOAN: '🏡', VEHICLE_LOAN: '🚗', EDUCATION_LOAN: '🎓' };
const TYPE_LABEL = { PERSONAL_LOAN: 'Personal Loan', HOME_LOAN: 'Home Loan', VEHICLE_LOAN: 'Vehicle Loan', EDUCATION_LOAN: 'Education Loan' };

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n || 0);
}
function fmtShort(n) {
  if (n >= 10000000) return '₹' + (n / 10000000).toFixed(1) + ' Cr';
  if (n >= 100000) return '₹' + (n / 100000).toFixed(1) + ' L';
  return '₹' + Number(n || 0).toLocaleString('en-IN');
}

function statusBadge(s) {
  if (s === 'APPROVED') return <span className="badge badge-approved">Approved</span>;
  if (s === 'REJECTED') return <span className="badge badge-rejected">Rejected</span>;
  if (s === 'MANUAL_REVIEW_REQUIRED') return <span className="badge badge-review">Under Review</span>;
  if (s === 'DOCUMENTS_SUBMITTED') return <span className="badge" style={{ background: 'rgba(0, 210, 255, 0.15)', color: 'var(--accent)', border: '1px solid var(--accent)' }}>Documents Submitted</span>;
  if (s === 'DOCUMENT_REVIEW_PENDING') return <span className="badge badge-warning">Awaiting Docs</span>;
  return <span className="badge badge-default">{(s || '').replace(/_/g, ' ')}</span>;
}

function LifecycleStrip({ current }) {
  const idx = HAPPY_PATH.indexOf(current);
  return (
    <div className="lifecycle">
      {HAPPY_PATH.map((s, i) => {
        const state = current === s ? 'current' : idx > -1 && i < idx ? 'done' : 'todo';
        return (
          <React.Fragment key={s}>
            <div className={`lifecycle-node ${state}`}>{prettyStatus(s)}</div>
            {i < HAPPY_PATH.length - 1 && <div className="lifecycle-arrow">→</div>}
          </React.Fragment>
        );
      })}
    </div>
  );
}

export default function Dashboard() {
  const { session, update } = useSession();
  const navigate = useNavigate();
  const [apps, setApps] = useState([]);
  const [docs, setDocs] = useState([]);
  const [schemes, setSchemes] = useState([]);
  const [schemesError, setSchemesError] = useState('');
  const [schemesLoading, setSchemesLoading] = useState(true);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    const email = (session?.email || '').toLowerCase();
    let mine = [];
    let fetchedDocs = [];

    try {
      const all = await fetchApplications();
      mine = (Array.isArray(all) ? all : []).filter(
        (a) => (a.customerEmail || '').toLowerCase() === email || (session?.loanCustomerId && a.customerId === session.loanCustomerId),
      );
      setApps(mine);
    } catch { setApps([]); }

    const cid = docCustomerId(session);
    if (cid) {
      try {
        const d = await fetchCustomerDocuments(cid);
        fetchedDocs = Array.isArray(d) ? d : [];
        setDocs(fetchedDocs);
      } catch { setDocs([]); }
    }

    // Dynamically calculate and advance Customer Onboarding Status
    let computedStatus = 'REGISTERED';
    const hasApprovedLoan = mine.some((a) => a.status === 'APPROVED');
    const hasVerifiedDocs = fetchedDocs.some((d) => d.status === 'VERIFIED' || d.status === 'APPROVED');
    const hasSubmittedDocs = mine.some((a) => a.status === 'DOCUMENTS_SUBMITTED') || fetchedDocs.length >= 3;
    const hasPendingDocs = fetchedDocs.length > 0 || mine.some((a) => a.status === 'DOCUMENT_REVIEW_PENDING' || a.status === 'MANUAL_REVIEW_REQUIRED');

    if (hasApprovedLoan) {
      computedStatus = 'ONBOARDING_COMPLETE';
    } else if (hasVerifiedDocs || mine.some((a) => a.status === 'MANUAL_REVIEW_REQUIRED')) {
      computedStatus = 'KYC_APPROVED';
    } else if (hasSubmittedDocs) {
      computedStatus = 'DOCUMENTS_SUBMITTED';
    } else if (hasPendingDocs || mine.length > 0) {
      computedStatus = 'DOCUMENTS_PENDING';
    }

    update({ onboardingStatus: computedStatus });

    try {
      const profile = await getCustomerByEmail(email);
      if (profile?.id) {
        update({ customerServiceId: profile.id });
      }
    } catch { /* no profile */ }

    setLoading(false);
  };

  const loadSchemes = () => {
    setSchemesLoading(true);
    setSchemesError('');
    fetchSchemes()
      .then((d) => setSchemes(Array.isArray(d) ? d : []))
      .catch((e) => {
        setSchemes([]);
        setSchemesError(
          `Couldn't load loan schemes — the loan service may be unreachable or not allowing this site (${e.message}).`,
        );
      })
      .finally(() => setSchemesLoading(false));
  };

  useEffect(() => { loadSchemes(); }, []);

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [session?.email, session?.loanCustomerId]);

  const approved = apps.filter((a) => a.status === 'APPROVED').length;
  const review = apps.filter((a) => a.status === 'MANUAL_REVIEW_REQUIRED').length;
  const docPending = apps.filter((a) => a.status === 'DOCUMENT_REVIEW_PENDING').length;
  const rejected = apps.filter((a) => a.status === 'REJECTED').length;

  const pie = [
    { name: 'Approved', value: approved },
    { name: 'Under Review', value: review },
    { name: 'Awaiting Docs', value: docPending },
    { name: 'Rejected', value: rejected },
    { name: 'In Progress', value: apps.length - approved - review - docPending - rejected },
  ].filter((p) => p.value > 0);

  return (
    <div className="page">
      <div className="card p-6" style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <div style={{ fontSize: '1.2rem', fontWeight: 800 }}>
            Welcome{session?.name ? `, ${session.name}` : ''} 👋
          </div>
          <div className="text-muted" style={{ marginTop: 4 }}>{session?.email}</div>
        </div>
        <button className="btn btn-ghost" onClick={load}><RefreshCw size={14} /> Refresh</button>
      </div>

      {session?.onboardingStatus && (
        <div className="card p-6" style={{ marginBottom: 20 }}>
          <div className="card-header-title" style={{ marginBottom: 16 }}>Your Onboarding Status</div>
          <LifecycleStrip current={session.onboardingStatus} />
        </div>
      )}

      <div className="stats-grid">
        {[
          { label: 'My Applications', value: apps.length, icon: FileText, color: '#6366f1' },
          { label: 'Approved', value: approved, icon: CheckCircle2, color: '#10b981' },
          { label: 'Under Review', value: review, icon: Clock, color: '#f59e0b' },
          { label: 'Awaiting Documents', value: docPending, icon: ShieldAlert, color: '#f97316' },
          { label: 'My Documents', value: docs.length, icon: FolderUp, color: '#0ea5e9' },
        ].map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="card stat-card">
            <div>
              <div className="stat-label">{label}</div>
              <div className="stat-value" style={{ fontSize: '1.55rem' }}>{loading ? '—' : value}</div>
            </div>
            <div className="stat-icon" style={{ background: color + '18', color }}><Icon size={20} /></div>
          </div>
        ))}
      </div>

      {/* ── Loan Schemes (centre of the homepage) ─────────────────────── */}
      <div className="card p-6" style={{ marginBottom: 24 }}>
        <div className="card-header" style={{ padding: 0, marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div className="card-header-title">Loan Schemes</div>
            <div className="text-muted" style={{ fontSize: '.8rem', marginTop: 2 }}>Pick a product to start an application</div>
          </div>
          <button className="btn btn-primary" onClick={() => navigate('/apply')}>Apply for a Loan <ArrowRight size={15} /></button>
        </div>

        {schemesLoading ? (
          <div className="empty">Loading loan products…</div>
        ) : schemesError ? (
          <div className="error-box" style={{ marginBottom: 0, display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <span style={{ flex: 1, minWidth: 240 }}>{schemesError}</span>
            <button className="btn btn-ghost" onClick={loadSchemes}><RefreshCw size={14} /> Retry</button>
          </div>
        ) : schemes.length === 0 ? (
          <div className="empty">No loan products are currently available.</div>
        ) : (
          <div className="scheme-grid">
            {schemes.map((s) => (
              <div
                key={s.schemeId}
                className="card scheme-card"
                onClick={() => navigate('/apply', { state: { schemeId: s.schemeId } })}
              >
                <div className="flex-row">
                  <span style={{ fontSize: '1.7rem' }}>{TYPE_ICONS[s.loanType] || '💳'}</span>
                  <span className="scheme-type-pill">{TYPE_LABEL[s.loanType] || s.loanType}</span>
                </div>
                <div className="scheme-title">{s.schemeName || s.schemeId}</div>
                <div className="scheme-interest">
                  {s.baseInterestRate?.toFixed(2) ?? s.interestRate}%
                  <span style={{ fontSize: '.72rem', color: 'var(--muted)', fontWeight: 400 }}> p.a.</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 2 }}>
                  <div className="scheme-row">
                    <span className="scheme-row-label">Amount</span>
                    <span>{fmtShort(s.minAmount)} – {fmtShort(s.maxAmount)}</span>
                  </div>
                  <div className="scheme-row">
                    <span className="scheme-row-label">Tenure</span>
                    <span>{s.minTenureMonths} – {s.maxTenureMonths} mo</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 20, marginBottom: 24 }}>
        <div className="card">
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div className="card-header-title">My Recent Applications</div>
            <button className="btn btn-ghost" style={{ fontSize: '.8rem' }} onClick={() => navigate('/applications')}>View All →</button>
          </div>
          {loading ? <div className="spinner" /> : apps.length === 0 ? (
            <div className="empty">
              No applications yet.{' '}
              <a onClick={() => navigate('/apply')} style={{ color: 'var(--accent)', cursor: 'pointer' }}>Apply now →</a>
            </div>
          ) : (
            <div className="table-wrap p-4">
              <table>
                <thead>
                  <tr><th>Application ID</th><th>Scheme</th><th>Amount</th><th>EMI</th><th>Assigned Manager</th><th>Status</th></tr>
                </thead>
                <tbody>
                  {apps.slice(0, 6).map((a) => (
                    <tr key={a.applicationId} onClick={() => navigate(`/applications/${a.applicationId}`)} style={{ cursor: 'pointer' }}>
                      <td className="font-mono" style={{ fontSize: '.78rem', color: 'var(--accent)', fontWeight: 600 }}>{a.applicationId}</td>
                      <td>{a.schemeName || a.schemeId}</td>
                      <td style={{ fontWeight: 600 }}>{fmt(a.loanAmount)}</td>
                      <td>{a.calculatedEMI != null ? fmt(a.calculatedEMI) : '—'}</td>
                      <td>
                        <div style={{ fontSize: '.8rem', fontWeight: 600, color: '#fff' }}>
                          {a.assignedManagerName || (a.assignedManager === 'markj' ? 'Mark Johnson' : a.assignedManager || 'Assigned Officer')}
                        </div>
                      </td>
                      <td>{statusBadge(a.status)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="card p-6">
          <div className="card-header-title" style={{ marginBottom: 4 }}>Application Status</div>
          <div style={{ fontSize: '.78rem', color: 'var(--muted)', marginBottom: 16 }}>Your loan pipeline</div>
          {pie.length === 0 ? (
            <div className="empty">Nothing to show yet</div>
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie data={pie} cx="50%" cy="45%" innerRadius={55} outerRadius={82} dataKey="value" paddingAngle={3}>
                  {pie.map((_, i) => <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />)}
                </Pie>
                <Legend iconType="circle" wrapperStyle={{ fontSize: 11, color: 'var(--muted)' }} />
                <Tooltip contentStyle={{ background: 'var(--panel-solid)', border: '1px solid var(--border)', borderRadius: 8 }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      <div className="card p-6">
        <div className="card-header-title" style={{ marginBottom: 16 }}>Quick Actions</div>
        <div className="flex-row" style={{ flexWrap: 'wrap', gap: 12 }}>
          <button className="btn btn-primary" onClick={() => navigate('/apply')}><FileText size={15} /> Apply for a Loan</button>
          <button className="btn btn-ghost" onClick={() => navigate('/emi')}><Calculator size={15} /> EMI Calculator</button>
          <button className="btn btn-ghost" onClick={() => navigate('/documents')}><FolderUp size={15} /> Upload Documents</button>
        </div>
      </div>
    </div>
  );
}
