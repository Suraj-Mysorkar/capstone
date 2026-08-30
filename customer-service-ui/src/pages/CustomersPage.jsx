import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import { listCustomers } from '../services/api';
import { ONBOARDING_STATUSES, prettyStatus, statusBadgeClass } from '../lib/onboarding';

const PAGE_SIZE = 15;

export default function CustomersPage() {
  const [data, setData] = useState({ content: [], totalElements: 0, totalPages: 0, number: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await listCustomers({
        status: filter === 'ALL' ? undefined : filter,
        page,
        size: PAGE_SIZE,
        sort: 'createdAt,desc',
      });
      setData(res ?? { content: [] });
    } catch (e) {
      setError(e.status === 401
        ? 'Not authorized — set a bearer token on the Settings page.'
        : e.message);
      setData({ content: [], totalElements: 0, totalPages: 0, number: 0 });
    }
    setLoading(false);
  };

  useEffect(() => { load(); }, [filter, page]);

  const rows = data.content ?? [];
  const totalPages = data.totalPages ?? 0;

  return (
    <div className="page">
      <div className="filter-bar">
        {['ALL', ...ONBOARDING_STATUSES].map((s) => (
          <button
            key={s}
            className={`filter-btn${filter === s ? ' active' : ''}`}
            onClick={() => { setPage(0); setFilter(s); }}
          >
            {s === 'ALL' ? 'All' : prettyStatus(s)}
          </button>
        ))}
        <button
          className="btn btn-ghost"
          style={{ marginLeft: 'auto', padding: '7px 16px', fontSize: '.8rem' }}
          onClick={load}
        >
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {error && <div className="error-box">{error}</div>}

      <div className="card">
        {loading ? <div className="spinner" /> : rows.length === 0 ? (
          <div className="empty">No customers match this filter.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th><th>Email</th><th>Phone</th><th>City</th>
                  <th>Country</th><th>Status</th><th>Created</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((c) => (
                  <tr key={c.id} onClick={() => navigate(`/customers/${c.id}`)}>
                    <td style={{ fontWeight: 600 }}>{c.firstName} {c.lastName}</td>
                    <td className="text-muted">{c.email}</td>
                    <td className="text-muted">{c.phoneNumber || '—'}</td>
                    <td className="text-muted">{c.city || '—'}</td>
                    <td className="text-muted">{c.countryCode || '—'}</td>
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

      {totalPages > 1 && (
        <div className="pager">
          <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            <ChevronLeft size={15} /> Prev
          </button>
          <span className="text-muted">
            Page {page + 1} of {totalPages} · {data.totalElements} total
          </span>
          <button
            className="btn btn-ghost"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next <ChevronRight size={15} />
          </button>
        </div>
      )}
    </div>
  );
}
