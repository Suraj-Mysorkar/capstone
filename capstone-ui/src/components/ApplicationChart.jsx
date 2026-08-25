import React from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

const data = [
  { name: 'Jul', submitted: 400, approved: 240, funded: 200 },
  { name: 'Aug', submitted: 800, approved: 600, funded: 420 },
  { name: 'Sep', submitted: 1200, approved: 900, funded: 650 },
  { name: 'Oct', submitted: 900, approved: 750, funded: 500 },
  { name: 'Nov', submitted: 1450, approved: 1200, funded: 950 },
  { name: 'Dec', submitted: 1800, approved: 1450, funded: 1100 },
];

const ApplicationChart = () => {
  return (
    <div className="glass-panel chart-container">
      <div className="chart-header">
        <span className="chart-title">Loan Application Progress (Q3-Q4)</span>
      </div>
      
      <div style={{ width: '100%', height: 300 }}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart
            data={data}
            margin={{ top: 10, right: 30, left: 0, bottom: 0 }}
          >
            <defs>
              <linearGradient id="colorSubmitted" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#00d2ff" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="#00d2ff" stopOpacity={0}/>
              </linearGradient>
              <linearGradient id="colorApproved" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#9d4edd" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="#9d4edd" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <XAxis dataKey="name" stroke="#9da0b5" tick={{ fill: '#9da0b5' }} />
            <YAxis stroke="#9da0b5" tick={{ fill: '#9da0b5' }} />
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
            <Tooltip 
              contentStyle={{ 
                backgroundColor: 'rgba(22, 25, 43, 0.9)', 
                borderColor: 'rgba(255,255,255,0.1)',
                borderRadius: '8px'
              }} 
            />
            <Area type="monotone" dataKey="submitted" stroke="#00d2ff" strokeWidth={3} fillOpacity={1} fill="url(#colorSubmitted)" />
            <Area type="monotone" dataKey="approved" stroke="#9d4edd" strokeWidth={3} fillOpacity={1} fill="url(#colorApproved)" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

export default ApplicationChart;
