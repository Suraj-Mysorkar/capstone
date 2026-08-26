import React, { useEffect, useState } from 'react';
import { fetchSchemes, calculateEmi } from '../services/api';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Calculator } from 'lucide-react';

function fmt(n) {
  return new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:0 }).format(n);
}

export default function EmiPage() {
  const [schemes, setSchemes] = useState([]);
  const [form, setForm] = useState({ loanAmount: 300000, tenureMonths: 36, schemeId: 'SCHEME-PL-01', interestRate: '' });
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { fetchSchemes().then(d => setSchemes(Array.isArray(d) ? d : [])); }, []);

  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }));

  const submit = async () => {
    setLoading(true); setError(''); setResult(null);
    try {
      const body = {
        loanAmount: parseFloat(form.loanAmount),
        tenureMonths: parseInt(form.tenureMonths),
        schemeId: form.schemeId || null,
        interestRate: form.interestRate ? parseFloat(form.interestRate) : null,
      };
      const res = await calculateEmi(body);
      if (res.monthlyEMI) setResult(res);
      else setError(res.message || JSON.stringify(res));
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };

  return (
    <div className="page">
      <div className="card p-6" style={{ marginBottom: 24 }}>
        <div className="card-header-title" style={{ marginBottom: 20, display:'flex', alignItems:'center', gap:8 }}>
          <Calculator size={18} /> Calculate EMI
        </div>

        <div className="form-grid">
          <div className="form-group">
            <label className="form-label">Loan Amount (₹)</label>
            <input className="form-input" type="number" value={form.loanAmount} onChange={set('loanAmount')} />
          </div>
          <div className="form-group">
            <label className="form-label">Tenure (Months)</label>
            <input className="form-input" type="number" value={form.tenureMonths} onChange={set('tenureMonths')} />
          </div>
          <div className="form-group">
            <label className="form-label">Loan Scheme</label>
            <select className="form-select" value={form.schemeId} onChange={set('schemeId')}>
              <option value="">— Select Scheme (or enter rate manually) —</option>
              {schemes.map(s => (
                <option key={s.schemeId} value={s.schemeId}>{s.schemeId} — {s.baseInterestRate ?? s.interestRate}% p.a.</option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Custom Interest Rate % (optional, overrides scheme)</label>
            <input className="form-input" type="number" step="0.01" value={form.interestRate} onChange={set('interestRate')} placeholder="e.g. 10.5" />
          </div>
        </div>

        <div style={{ marginTop: 20 }}>
          <button className="btn btn-primary" onClick={submit} disabled={loading}>
            {loading ? 'Calculating…' : 'Calculate EMI'}
          </button>
        </div>
      </div>

      {error && <div className="error-box">{error}</div>}

      {result && (
        <>
          <div className="card p-6" style={{ marginBottom: 24 }}>
            <div className="card-header-title" style={{ marginBottom: 16 }}>EMI Breakdown</div>
            <div className="emi-result">
              {[
                ['Monthly EMI',    fmt(result.monthlyEMI)],
                ['Principal',      fmt(result.principalAmount)],
                ['Interest Rate',  result.annualInterestRate + '% p.a.'],
                ['Tenure',         result.tenureMonths + ' months'],
                ['Total Interest', fmt(result.totalInterestPayable)],
                ['Total Payment',  fmt(result.totalAmountPayable)],
              ].map(([l, v]) => (
                <div key={l} className="emi-box">
                  <div className="emi-box-label">{l}</div>
                  <div className="emi-box-value" style={{ fontSize:'1.1rem' }}>{v}</div>
                </div>
              ))}
            </div>
          </div>

          {result.amortizationSchedule?.length > 0 && (
            <div className="card p-6">
              <div className="card-header-title" style={{ marginBottom: 16 }}>Amortization Schedule</div>
              <div style={{ marginBottom:20, height:200 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={result.amortizationSchedule.slice(0,36)}>
                    <defs>
                      <linearGradient id="gp" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#2ecc71" stopOpacity={0.3}/>
                        <stop offset="95%" stopColor="#2ecc71" stopOpacity={0}/>
                      </linearGradient>
                      <linearGradient id="gi" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#e74c3c" stopOpacity={0.3}/>
                        <stop offset="95%" stopColor="#e74c3c" stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,.05)" vertical={false}/>
                    <XAxis dataKey="monthNumber" tick={{ fill:'#7b7f9e', fontSize:11 }} stroke="transparent"/>
                    <YAxis tick={{ fill:'#7b7f9e', fontSize:11 }} stroke="transparent"/>
                    <Tooltip contentStyle={{ background:'#13182e', border:'1px solid rgba(255,255,255,.1)', borderRadius:8 }} formatter={v => fmt(v)}/>
                    <Area type="monotone" dataKey="principalPaid" stroke="#2ecc71" strokeWidth={2} fill="url(#gp)" name="Principal"/>
                    <Area type="monotone" dataKey="interestPaid"  stroke="#e74c3c" strokeWidth={2} fill="url(#gi)" name="Interest"/>
                  </AreaChart>
                </ResponsiveContainer>
              </div>
              <div className="amort-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Month</th><th>EMI</th><th>Principal</th><th>Interest</th><th>Balance</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.amortizationSchedule.map(r => (
                      <tr key={r.month}>
                        <td>{r.monthNumber}</td>
                        <td>{fmt(r.emiAmount)}</td>
                        <td style={{ color:'var(--green)' }}>{fmt(r.principalPaid)}</td>
                        <td style={{ color:'var(--red)' }}>{fmt(r.interestPaid)}</td>
                        <td>{fmt(r.endingBalance)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
