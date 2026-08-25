import React, { useState } from 'react';
import './WorkflowDiagram.css';

// Define the workflow steps in order
const steps = [
  { id: 'APPLIED', label: 'Applied' },
  { id: 'UNDER_REVIEW', label: 'Under Review' },
  { id: 'APPROVED', label: 'Approved' },
  { id: 'REJECTED', label: 'Rejected' },
];

/**
 * Interactive workflow diagram.
 * Props:
 *   - status: current application status string (one of the step ids)
 *   - onStepClick: optional callback when a step is clicked (receives step id)
 */
export default function WorkflowDiagram({ currentStatus, onDecision }) {
  const [selected, setSelected] = useState(currentStatus);

  const handleClick = (stepId) => {
    setSelected(stepId);
    if (onDecision) onDecision(stepId);
  };

  return (
    <div className="workflow-diagram">
      {steps.map((step, idx) => (
        <React.Fragment key={step.id}>
          <button
            className={`workflow-step ${selected === step.id ? 'active' : ''}`}
            onClick={() => handleClick(step.id)}
          >
            {step.label}
          </button>
          {idx < steps.length - 1 && <div className="workflow-arrow">→</div>}
        </React.Fragment>
      ))}
    </div>
  );
}
