import React from 'react';
import { Check, Circle, Clock3, X } from 'lucide-react';
import './WorkflowDiagram.css';

const steps = [
  { id: 'SUBMITTED', label: 'Submitted', note: 'Application received' },
  { id: 'VALIDATING', label: 'Validation', note: 'Checking eligibility' },
  { id: 'CREDIT_ASSESSMENT', label: 'Credit assessment', note: 'Risk score calculated' },
  { id: 'MANUAL_REVIEW_REQUIRED', label: 'Manual review', note: 'Manager decision required' },
  { id: 'APPROVED', label: 'Approved', note: 'Loan cleared' },
  { id: 'REJECTED', label: 'Rejected', note: 'Application declined' },
];

export default function WorkflowDiagram({ currentStatus, onDecision }) {
  const terminalStatus = currentStatus === 'APPROVED' || currentStatus === 'REJECTED';
  const currentIndex = steps.findIndex(step => step.id === currentStatus);
  const isFinished = index => terminalStatus || (currentIndex >= 0 && index < currentIndex);
  const iconFor = (step, index) => {
    if (step.id === 'REJECTED') return <X size={16} />;
    if (step.id === 'APPROVED' || isFinished(index)) return <Check size={16} />;
    if (step.id === currentStatus) return <Clock3 size={16} />;
    return <Circle size={14} />;
  };

  return (
    <div className={`workflow-diagram ${terminalStatus ? 'is-terminal' : ''}`}>
      {steps.map((step, idx) => (
        <React.Fragment key={step.id}>
          <div className={`workflow-step ${step.id === currentStatus ? 'active' : ''} ${isFinished(idx) ? 'complete' : ''} ${step.id === 'REJECTED' && currentStatus === 'REJECTED' ? 'rejected' : ''}`}>
            <div className="workflow-step-icon">{iconFor(step, idx)}</div>
            <div>
              <div className="workflow-step-label">{step.label}</div>
              <div className="workflow-step-note">{step.note}</div>
            </div>
          </div>
          {idx < steps.length - 1 && <div className={`workflow-connector ${isFinished(idx) ? 'complete' : ''}`} />}
        </React.Fragment>
      ))}
      {currentStatus === 'MANUAL_REVIEW_REQUIRED' && onDecision && (
        <div className="workflow-actions">
          <button className="btn btn-primary" onClick={() => onDecision('APPROVE')}>Approve application</button>
          <button className="btn btn-danger" onClick={() => onDecision('REJECT')}>Reject application</button>
        </div>
      )}
    </div>
  );
}
