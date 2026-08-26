import React, { useEffect, useState } from 'react';
import { fetchSchemes } from '../services/api';

const TYPE_ICONS = {
  PERSONAL_LOAN:  '👤',
  HOME_LOAN:      '🏡',
  VEHICLE_LOAN:   '🚗',
  EDUCATION_LOAN: '🎓',
};

const TYPE_LABEL = {
  PERSONAL_LOAN:  'Personal Loan',
  HOME_LOAN:      'Home Loan',
  VEHICLE_LOAN:   'Vehicle Loan',
  EDUCATION_LOAN: 'Education Loan',
};

function fmt(n) {
  if (n >= 10000000) return '₹' + (n / 10000000).toFixed(1) + ' Cr';
  if (n >= 100000)   return '₹' + (n / 100000).toFixed(1) + ' L';
  return '₹' + n.toLocaleString('en-IN');
}

export default function SchemesPage() {
  const [schemes, setSchemes] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchSchemes()
      .then(d => setSchemes(Array.isArray(d) ? d : []))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="page">
      <div className="info-box">
        These are the active loan products currently offered. Click a card to see full details.
      </div>

      {loading ? <div className="spinner" /> : (
        <div className="scheme-grid">
          {schemes.map(s => (
            <div key={s.schemeId} className="card scheme-card">
              <div className="flex-row">
                <span style={{ fontSize: '1.8rem' }}>{TYPE_ICONS[s.loanType] || '💳'}</span>
                <span className="scheme-type-pill">{TYPE_LABEL[s.loanType] || s.loanType}</span>
              </div>
              <div className="scheme-title">{s.schemeName || s.schemeId}</div>

              <div className="scheme-interest">{s.baseInterestRate?.toFixed(2) ?? s.interestRate}%
                <span style={{ fontSize:'.75rem', color:'var(--muted)', fontWeight:400 }}> p.a.</span>
              </div>

              <div style={{ display:'flex', flexDirection:'column', gap:8, marginTop:4 }}>
                <div className="scheme-row">
                  <span className="scheme-row-label">Scheme ID</span>
                  <span className="font-mono" style={{ fontSize:'.78rem', color:'var(--accent)' }}>{s.schemeId}</span>
                </div>
                <div className="scheme-row">
                  <span className="scheme-row-label">Amount Range</span>
                  <span>{fmt(s.minAmount)} – {fmt(s.maxAmount)}</span>
                </div>
                <div className="scheme-row">
                  <span className="scheme-row-label">Tenure</span>
                  <span>{s.minTenureMonths} – {s.maxTenureMonths} months</span>
                </div>
                <div className="scheme-row">
                  <span className="scheme-row-label">Status</span>
                  <span className={`badge ${s.isActive ? 'badge-approved' : 'badge-rejected'}`}>
                    {s.isActive ? 'Active' : 'Inactive'}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
