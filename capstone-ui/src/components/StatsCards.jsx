import React from 'react';
import { BarChart2, DollarSign, CheckCircle2 } from 'lucide-react';

const StatsCards = ({ applications }) => {
  const totalApps = applications.length;
  
  // Calculate total requested amount
  const totalAmount = applications.reduce((sum, app) => sum + (app.loanAmount || 0), 0);
  const formattedAmount = `$${(totalAmount / 1000000).toFixed(1)}M`;
  
  // Calculate approval rate
  const approved = applications.filter(app => app.status === 'APPROVED').length;
  const approvalRate = totalApps > 0 ? ((approved / totalApps) * 100).toFixed(1) + '%' : '0%';

  return (
    <div className="stats-grid">
      <div className="glass-panel stat-card">
        <div className="stat-info">
          <span className="stat-title">Active Apps</span>
          <span className="stat-value">{totalApps}</span>
        </div>
        <div className="stat-icon">
          <BarChart2 size={24} />
        </div>
      </div>
      
      <div className="glass-panel stat-card">
        <div className="stat-info">
          <span className="stat-title">Total Requested</span>
          <span className="stat-value">{formattedAmount}</span>
        </div>
        <div className="stat-icon">
          <DollarSign size={24} />
        </div>
      </div>
      
      <div className="glass-panel stat-card">
        <div className="stat-info">
          <span className="stat-title">Approval Rate</span>
          <span className="stat-value">{approvalRate}</span>
        </div>
        <div className="stat-icon">
          <CheckCircle2 size={24} />
        </div>
      </div>
    </div>
  );
};

export default StatsCards;
