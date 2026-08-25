import React from 'react';
import { MoreHorizontal } from 'lucide-react';

const ApplicationTable = ({ applications, loading }) => {
  const getStatusBadge = (status) => {
    switch (status) {
      case 'APPROVED':
        return <span className="status-badge status-approved">Approved</span>;
      case 'REJECTED':
        return <span className="status-badge status-rejected">Rejected</span>;
      case 'MANUAL_REVIEW_REQUIRED':
        return <span className="status-badge status-review">Manual Review</span>;
      default:
        return <span className="status-badge" style={{background: 'rgba(255,255,255,0.1)'}}>{status}</span>;
    }
  };

  return (
    <div className="glass-panel table-container">
      <div className="table-header">
        <span>RECENT LOAN APPLICATIONS</span>
      </div>
      
      {loading ? (
        <div className="empty-state">Loading applications...</div>
      ) : applications.length === 0 ? (
        <div className="empty-state">No applications found. Submit one via Postman to see it here!</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Application ID</th>
              <th>Applicant Name</th>
              <th>Scheme</th>
              <th>Amount ($)</th>
              <th>Date</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {applications.slice(0, 10).map((app) => (
              <tr key={app.id}>
                <td>{app.id}</td>
                <td>{app.customerName}</td>
                <td>{app.schemeId}</td>
                <td>{new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(app.loanAmount)}</td>
                <td>{new Date(app.createdAt || Date.now()).toLocaleDateString()}</td>
                <td>{getStatusBadge(app.status)}</td>
                <td>
                  <MoreHorizontal size={18} style={{ cursor: 'pointer', color: 'var(--text-secondary)' }} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default ApplicationTable;
