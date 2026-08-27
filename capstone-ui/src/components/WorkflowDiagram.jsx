import React from 'react';
import {
  CheckCircle2, Clock, XCircle, FileCheck, FileWarning,
  UserCheck, ArrowDown, ArrowRight, ShieldCheck, Zap, UploadCloud
} from 'lucide-react';
import './WorkflowDiagram.css';

// ── Helper components ────────────────────────────────────────────────────────

function FlowArrow({ direction = 'down' }) {
  return (
    <div className={`wf-arrow wf-arrow-${direction}`}>
      {direction === 'down' ? <ArrowDown size={18} /> : <ArrowRight size={18} />}
    </div>
  );
}

function StepNode({ icon, title, desc, state = 'idle', badge = null }) {
  return (
    <div className={`wf-node wf-node-${state}`}>
      <div className={`wf-node-icon wf-icon-${state}`}>{icon}</div>
      <div className="wf-node-body">
        {badge && <div className={`wf-badge wf-badge-${badge.color}`}>{badge.text}</div>}
        <div className="wf-node-title">{title}</div>
        <div className="wf-node-desc">{desc}</div>
      </div>
    </div>
  );
}

function GatewayDiamond({ title, desc, riskScore }) {
  return (
    <div className="wf-gateway">
      <div className="wf-gateway-badge">⬡ DECISION GATEWAY</div>
      <div className="wf-gateway-title">{title}</div>
      <div className="wf-gateway-desc">{desc}</div>
      {riskScore != null && (
        <div className="wf-gateway-score">
          Evaluated Score: <strong>{riskScore}/100</strong>
          {' — '}
          <span className={riskScore <= 30 ? 'score-low' : riskScore >= 70 ? 'score-high' : 'score-med'}>
            {riskScore <= 30 ? 'LOW RISK' : riskScore >= 70 ? 'HIGH RISK' : 'MODERATE RISK'}
          </span>
        </div>
      )}
    </div>
  );
}

function BranchLabel({ text, color }) {
  return <div className={`wf-branch-label wf-branch-label-${color}`}>{text}</div>;
}

function LevelBadge({ level, total, color }) {
  return (
    <div className={`wf-level-badge wf-level-${color}`}>
      LEVEL {level} of {total}
    </div>
  );
}

function OutcomeNode({ icon, title, desc, color }) {
  return (
    <div className={`wf-outcome wf-outcome-${color}`}>
      <div className={`wf-outcome-icon wf-icon-${color}`}>{icon}</div>
      <div className="wf-node-title">{title}</div>
      <div className="wf-node-desc">{desc}</div>
    </div>
  );
}

// ── Main WorkflowDiagram Component ──────────────────────────────────────────

export default function WorkflowDiagram({
  currentStatus,
  riskScore,
  decisionRemarks,
  onDecision,
  onUploadDocs
}) {
  const isDocPending    = currentStatus === 'DOCUMENT_REVIEW_PENDING';
  const isManualReview  = currentStatus === 'MANUAL_REVIEW_REQUIRED';
  const isApproved      = currentStatus === 'APPROVED';
  const isRejected      = currentStatus === 'REJECTED';
  const isEarlyStage    = !isDocPending && !isManualReview && !isApproved && !isRejected;

  // Which risk branch is this app on?
  const isHighRisk = riskScore != null ? riskScore >= 70 : isRejected;
  const isLowRisk  = riskScore != null ? riskScore <= 30 : false;
  const isMedRisk  = riskScore != null ? (riskScore > 30 && riskScore < 70) : isManualReview;

  // Early stages state
  const submittedState       = 'done';
  const creditAssessState    = isEarlyStage ? 'active' : 'done';

  return (
    <div className="wf-container">

      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="wf-header">
        <div className="wf-header-left">
          <div className="wf-header-title">Loan Processing Workflow</div>
          <div className="wf-header-sub">
            Azure Durable Function Orchestration — Multi-Level Approval Pipeline
          </div>
        </div>
        <div className="wf-header-right">
          <div className={`wf-status-chip wf-chip-${currentStatus?.toLowerCase()}`}>
            {currentStatus?.replace(/_/g, ' ')}
          </div>
          {riskScore != null && (
            <div className="wf-risk-chip">
              Risk Score: <strong>{riskScore}/100</strong>
            </div>
          )}
        </div>
      </div>

      {/* ── PHASE 1: Intake & Credit Assessment (Linear, same for all) ─── */}
      <div className="wf-phase">
        <div className="wf-phase-label">PHASE 1 — INTAKE & AUTOMATED CREDIT ASSESSMENT</div>
        <div className="wf-linear-row">
          <StepNode
            state={submittedState}
            icon={<CheckCircle2 size={18} />}
            title="Application Submitted"
            desc="Applicant data & scheme parameters ingested"
          />
          <FlowArrow direction="right" />
          <StepNode
            state={submittedState}
            icon={<ShieldCheck size={18} />}
            title="Eligibility Validation"
            desc="Loan limits, tenure bounds, schema check"
          />
          <FlowArrow direction="right" />
          <StepNode
            state={creditAssessState}
            icon={<Zap size={18} />}
            title="Credit Risk Scoring"
            desc="DTI ratio, income multiplier, employment factor"
          />
        </div>
      </div>

      {/* ── DECISION GATEWAY ────────────────────────────────────────────── */}
      <FlowArrow direction="down" />

      <GatewayDiamond
        title="Automated Credit Risk Decision Gateway"
        desc="Classifies application into risk tier and routes to the correct approval pipeline"
        riskScore={riskScore}
      />

      {/* ── PHASE 2: Three parallel branches ───────────────────────────── */}
      <FlowArrow direction="down" />

      <div className="wf-branches-grid">

        {/* ════ BRANCH A: LOW RISK ════════════════════════════════════════ */}
        <div className={`wf-branch ${isLowRisk ? 'wf-branch-active' : 'wf-branch-dim'} wf-branch-green`}>
          <BranchLabel text="BRANCH A — LOW RISK (Score ≤ 30)" color="green" />
          <div className="wf-branch-subtitle">Single-Level Approval — Document Review Required</div>

          {/* Level 1: Document Review */}
          <div className="wf-level-card wf-level-card-amber">
            <LevelBadge level={1} total={1} color="amber" />
            <div className="wf-level-title">
              <FileCheck size={16} /> Document Review
            </div>
            <div className="wf-level-desc">
              Customer must submit <strong>KYC documents</strong> and <strong>Income proofs</strong>.
              Mandatory for all applications — no exceptions.
            </div>

            {isDocPending && isLowRisk && (
              <div className="wf-action-card wf-action-amber">
                <FileWarning size={15} /> Documents pending! Submit to trigger auto-approval.
                {onUploadDocs && (
                  <button className="wf-btn wf-btn-amber" onClick={onUploadDocs}>
                    <UploadCloud size={14} /> Submit Documents
                  </button>
                )}
              </div>
            )}
          </div>

          <FlowArrow direction="down" />

          {/* Auto-Approval outcome */}
          <OutcomeNode
            color={isApproved && isLowRisk ? 'green-active' : 'green'}
            icon={<CheckCircle2 size={20} />}
            title="AUTO-APPROVED"
            desc="Credit Engine auto-approves immediately after document verification. No human review needed."
          />
        </div>

        {/* ════ BRANCH B: MEDIUM RISK ════════════════════════════════════ */}
        <div className={`wf-branch ${isMedRisk ? 'wf-branch-active' : 'wf-branch-dim'} wf-branch-yellow`}>
          <BranchLabel text="BRANCH B — MODERATE RISK (31–69)" color="yellow" />
          <div className="wf-branch-subtitle">Two-Level Approval — Documents + Underwriter Review</div>

          {/* Level 1: Document Review */}
          <div className={`wf-level-card wf-level-card-amber ${isDocPending && isMedRisk ? 'wf-level-card-active' : ''}`}>
            <LevelBadge level={1} total={2} color="amber" />
            <div className="wf-level-title">
              <FileCheck size={16} /> Level 1: Document Review
            </div>
            <div className="wf-level-desc">
              Customer must submit <strong>KYC + Income documents</strong> for verification.
              Mandatory prerequisite before manager can review.
            </div>

            {isDocPending && isMedRisk && (
              <div className="wf-action-card wf-action-amber">
                <FileWarning size={15} /> Awaiting mandatory documents to proceed to Level 2.
                {onUploadDocs && (
                  <button className="wf-btn wf-btn-amber" onClick={onUploadDocs}>
                    <UploadCloud size={14} /> Submit Documents → Escalate to Manager
                  </button>
                )}
              </div>
            )}
          </div>

          <FlowArrow direction="down" />

          {/* Level 2: Underwriter Review */}
          <div className={`wf-level-card wf-level-card-blue ${isManualReview ? 'wf-level-card-active' : ''}`}>
            <LevelBadge level={2} total={2} color="blue" />
            <div className="wf-level-title">
              <UserCheck size={16} /> Level 2: Underwriter Review
            </div>
            <div className="wf-level-desc">
              Operations Manager reviews <strong>loan amount</strong>, <strong>tenure</strong>,
              {' '}<strong>income adequacy</strong>, and overall risk profile before final decision.
            </div>

            {isManualReview && onDecision && (
              <div className="wf-action-card wf-action-blue">
                <UserCheck size={15} /> Awaiting Operations Manager decision on loan terms.
                <div className="wf-action-buttons">
                  <button className="wf-btn wf-btn-green" onClick={() => onDecision('APPROVE')}>
                    ✓ Approve Loan
                  </button>
                  <button className="wf-btn wf-btn-red" onClick={() => onDecision('REJECT')}>
                    ✕ Reject Loan
                  </button>
                </div>
              </div>
            )}
          </div>

          <FlowArrow direction="down" />

          {/* Manual Review outcome */}
          <div className="wf-outcome-row">
            <OutcomeNode
              color={isApproved && isMedRisk ? 'green-active' : 'green'}
              icon={<CheckCircle2 size={18} />}
              title="APPROVED"
              desc="Manager approves loan terms"
            />
            <div className="wf-outcome-or">OR</div>
            <OutcomeNode
              color={isRejected && isMedRisk ? 'red-active' : 'red'}
              icon={<XCircle size={18} />}
              title="REJECTED"
              desc="Manager rejects on merit"
            />
          </div>
        </div>

        {/* ════ BRANCH C: HIGH RISK ═══════════════════════════════════════ */}
        <div className={`wf-branch ${isHighRisk ? 'wf-branch-active' : 'wf-branch-dim'} wf-branch-red`}>
          <BranchLabel text="BRANCH C — HIGH RISK (Score ≥ 70)" color="red" />
          <div className="wf-branch-subtitle">Auto-Rejected — No Further Review</div>

          <div className="wf-level-card wf-level-card-red">
            <div className="wf-level-title">
              <XCircle size={16} color="var(--red)" /> Credit Engine Decision
            </div>
            <div className="wf-level-desc">
              Application automatically declined due to <strong>excessive DTI</strong> or
              {' '}<strong>high debt leverage</strong>. Document submission has no effect.
            </div>
          </div>

          <FlowArrow direction="down" />

          <OutcomeNode
            color={isRejected && isHighRisk ? 'red-active' : 'red'}
            icon={<XCircle size={20} />}
            title="AUTO-REJECTED"
            desc="Declined by Credit Engine — no human intervention."
          />
        </div>
      </div>

      {/* ── Decision Remarks Footer ─────────────────────────────────────── */}
      {decisionRemarks && (
        <div className="wf-remarks-card">
          <div className="wf-remarks-label">📋 Engine Decision Remarks</div>
          <div className="wf-remarks-text">{decisionRemarks}</div>
        </div>
      )}
    </div>
  );
}
