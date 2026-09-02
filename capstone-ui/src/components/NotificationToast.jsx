import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, X, ArrowRight, FileText, UserCheck, CheckCircle2, ShieldAlert } from 'lucide-react';
import { useNotifications } from '../context/NotificationContext';

export function NotificationToastContainer() {
  const { toasts, dismissToast } = useNotifications();

  if (!toasts || toasts.length === 0) return null;

  return (
    <div
      style={{
        position: 'fixed',
        top: 20,
        right: 24,
        zIndex: 99999,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        maxWidth: 420,
        width: 'calc(100vw - 48px)',
        pointerEvents: 'none',
      }}
    >
      {toasts.map(toast => (
        <ToastItem key={toast.toastId} toast={toast} onDismiss={() => dismissToast(toast.toastId)} />
      ))}
    </div>
  );
}

function ToastItem({ toast, onDismiss }) {
  const navigate = useNavigate();

  useEffect(() => {
    const timer = setTimeout(() => {
      onDismiss();
    }, 7000);
    return () => clearTimeout(timer);
  }, [onDismiss]);

  const getIcon = () => {
    switch (toast.eventType) {
      case 'DOCUMENT_UPLOADED':
        return <FileText size={20} color="var(--accent)" />;
      case 'NEW_CASE_ASSIGNED':
        return <UserCheck size={20} color="var(--green)" />;
      case 'DECISION_RECORDED':
        return <CheckCircle2 size={20} color="var(--purple)" />;
      default:
        return <Bell size={20} color="var(--yellow)" />;
    }
  };

  const getBorderColor = () => {
    switch (toast.eventType) {
      case 'DOCUMENT_UPLOADED':
        return 'rgba(0, 210, 255, 0.6)';
      case 'NEW_CASE_ASSIGNED':
        return 'rgba(0, 230, 118, 0.6)';
      case 'DECISION_RECORDED':
        return 'rgba(168, 85, 247, 0.6)';
      default:
        return 'rgba(255, 179, 0, 0.6)';
    }
  };

  const handleAction = () => {
    onDismiss();
    if (toast.applicationId) {
      navigate(`/applications/${toast.applicationId}`);
    } else if (toast.customerId) {
      navigate(`/documents`);
    } else {
      navigate(`/applications`);
    }
  };

  return (
    <div
      style={{
        pointerEvents: 'auto',
        background: 'linear-gradient(135deg, rgba(19, 24, 46, 0.96) 0%, rgba(13, 17, 33, 0.98) 100%)',
        backdropFilter: 'blur(16px)',
        border: `1.5px solid ${getBorderColor()}`,
        borderRadius: 14,
        padding: '16px 18px',
        boxShadow: '0 16px 36px rgba(0, 0, 0, 0.65), 0 0 20px rgba(0, 210, 255, 0.15)',
        animation: 'slideInRight 0.35s cubic-bezier(0.16, 1, 0.3, 1)',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Top Banner */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{
              padding: 8,
              borderRadius: 10,
              background: 'rgba(255,255,255,0.06)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {getIcon()}
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '.9rem', color: '#fff' }}>
              {toast.title || 'Live Activity Notification'}
            </div>
            <div style={{ fontSize: '.74rem', color: 'var(--muted)' }}>
              🔔 Assigned Employee Notification
            </div>
          </div>
        </div>
        <button
          onClick={onDismiss}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--muted)',
            cursor: 'pointer',
            padding: 4,
            borderRadius: 6,
            display: 'flex',
            alignItems: 'center',
          }}
          title="Close notification"
        >
          <X size={16} />
        </button>
      </div>

      {/* Message Body */}
      <div style={{ fontSize: '.84rem', color: 'rgba(255,255,255,0.9)', lineHeight: 1.45 }}>
        {toast.message}
      </div>

      {/* Customer / Case Metadata Pill */}
      {(toast.customerName || toast.customerId || toast.applicationId) && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            background: 'rgba(255,255,255,0.04)',
            padding: '6px 12px',
            borderRadius: 8,
            fontSize: '.78rem',
            color: 'var(--accent)',
            flexWrap: 'wrap',
          }}
        >
          {toast.customerId && <span><strong>Cust ID:</strong> {toast.customerId}</span>}
          {toast.customerName && <span><strong>Name:</strong> {toast.customerName}</span>}
          {toast.applicationId && <span><strong>App:</strong> {toast.applicationId}</span>}
        </div>
      )}

      {/* Quick Action Button */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 2 }}>
        <button
          onClick={handleAction}
          className="btn btn-primary"
          style={{
            fontSize: '.78rem',
            padding: '5px 12px',
            height: 'auto',
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <span>View Details</span>
          <ArrowRight size={13} />
        </button>
      </div>

      {/* Auto-dismiss progress bar */}
      <div
        style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          height: 3,
          background: 'rgba(255,255,255,0.1)',
        }}
      >
        <div
          style={{
            height: '100%',
            background: getBorderColor(),
            animation: 'toastProgress 7s linear forwards',
          }}
        />
      </div>
    </div>
  );
}
