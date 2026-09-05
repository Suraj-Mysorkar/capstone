import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useAuth } from './AuthContext';
import { fetchApplications, fetchCustomerDocuments } from '../services/loanApi';

const NotificationContext = createContext(null);

const getSeenAlerts = (userKey = 'default') => {
  try {
    return new Set(JSON.parse(localStorage.getItem(`customer_seen_alerts_${userKey}`) || '[]'));
  } catch (e) {
    return new Set();
  }
};

const recordSeenAlert = (id, userKey = 'default') => {
  try {
    const current = Array.from(getSeenAlerts(userKey));
    if (!current.includes(id)) {
      current.push(id);
      localStorage.setItem(`customer_seen_alerts_${userKey}`, JSON.stringify(current.slice(-150)));
    }
  } catch (e) {}
};

export function NotificationProvider({ children }) {
  const { currentUser } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [toast, setToast] = useState(null);

  const userKey = (currentUser?.email || currentUser?.username || 'default').toLowerCase();

  const playChime = useCallback(() => {
    try {
      const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const now = audioCtx.currentTime;
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(587.33, now); // D5
      osc.frequency.setValueAtTime(880.0, now + 0.12); // A5
      osc.frequency.setValueAtTime(1174.66, now + 0.24); // D6

      gain.gain.setValueAtTime(0.18, now);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.85);

      osc.connect(gain);
      gain.connect(audioCtx.destination);

      osc.start(now);
      osc.stop(now + 0.85);
    } catch (e) {
      // Audio autoplay policy fallback
    }
  }, []);

  const addNotification = useCallback(
    (notif) => {
      const id = notif.id || `notif-${Date.now()}-${Math.random()}`;
      const item = { ...notif, id, timestamp: new Date().toISOString(), read: false };
      const seen = getSeenAlerts(userKey);
      if (!seen.has(id)) {
        recordSeenAlert(id, userKey);
        setToast(item);
        playChime();
        setTimeout(() => setToast((curr) => (curr?.id === id ? null : curr)), 7000);
      }
      setNotifications((prev) => [item, ...prev]);
    },
    [playChime, userKey]
  );

  // Sync application & manager events for the current user
  const syncNotifications = useCallback(async () => {
    if (!currentUser?.email) return;
    const email = currentUser.email.toLowerCase();
    try {
      const apps = await fetchApplications();
      const myApps = (Array.isArray(apps) ? apps : []).filter(
        (a) => (a.customerEmail || '').toLowerCase() === email || (currentUser.loanCustomerId && a.customerId === currentUser.loanCustomerId)
      );

      const generated = [];

      // If user has no loan applications yet, show active Welcome alert
      if (myApps.length === 0) {
        generated.push({
          id: `welcome-${email}`,
          title: `Welcome to Digital Banking! 🎉`,
          message: `Your customer account is active. Explore flexible loan schemes, calculate EMIs, and submit your loan application online anytime.`,
          type: 'WELCOME',
          timestamp: currentUser.loginTime || new Date().toISOString(),
          read: false,
          link: `/schemes`,
        });
      }

      myApps.forEach((app) => {
        const mgrName = app.assignedManagerName || (app.assignedManager === 'markj' ? 'Mark Johnson' : app.assignedManager || 'Assigned Officer');
        const mgrPhone = app.assignedManagerPhone || '+1 (555) 019-2834';
        const mgrEmail = app.assignedManagerEmail || 'manager@bank.com';

        // 1. Application Submitted notification
        generated.push({
          id: `app-submitted-${app.applicationId}`,
          title: `Loan Application: ${app.applicationId}`,
          message: `Application for ₹${Number(app.loanAmount || 0).toLocaleString('en-IN')} (${app.schemeName || 'Loan'}) was received. Assigned to ${mgrName}.`,
          type: 'SUBMITTED',
          timestamp: app.createdAt || new Date().toISOString(),
          read: false,
          link: `/applications/${app.applicationId}`,
          meta: { applicationId: app.applicationId }
        });

        // 2. Manager Assignment notification
        generated.push({
          id: `mgr-assign-${app.applicationId}`,
          title: `Loan Manager Assigned: ${mgrName}`,
          message: `Case ${app.applicationId} is assigned to ${mgrName} (📞 ${mgrPhone}, 📧 ${mgrEmail}).`,
          type: 'MANAGER_ASSIGNED',
          timestamp: app.createdAt || new Date().toISOString(),
          read: false,
          link: `/applications/${app.applicationId}`,
          meta: { managerName: mgrName, managerPhone: mgrPhone, managerEmail: mgrEmail, applicationId: app.applicationId }
        });

        // 3. Action Required / Document Request notification
        if (app.status === 'DOCUMENT_REVIEW_PENDING' || app.status === 'MANUAL_REVIEW_REQUIRED') {
          generated.push({
            id: `doc-req-${app.applicationId}`,
            title: `Action Required: Documents Needed (${app.applicationId})`,
            message: `${mgrName} has requested verification documents (KYC, Income Proof, Bank Statement) for your ₹${Number(app.loanAmount || 0).toLocaleString('en-IN')} loan.`,
            type: 'DOC_REQUEST',
            timestamp: app.updatedAt || app.createdAt || new Date().toISOString(),
            read: false,
            link: `/documents`,
            meta: { applicationId: app.applicationId }
          });
        }

        // 4. Approval notification
        if (app.status === 'APPROVED') {
          generated.push({
            id: `app-approved-${app.applicationId}`,
            title: `🎉 Loan Approved: ${app.applicationId}`,
            message: `Congratulations! Your loan application for ₹${Number(app.loanAmount || 0).toLocaleString('en-IN')} has been approved by ${mgrName}.`,
            type: 'STATUS_UPDATE',
            timestamp: app.updatedAt || new Date().toISOString(),
            read: false,
            link: `/applications/${app.applicationId}`,
            meta: { status: 'APPROVED' }
          });
        }

        // 5. Rejection notification
        if (app.status === 'REJECTED') {
          generated.push({
            id: `app-rejected-${app.applicationId}`,
            title: `Update on Loan Application (${app.applicationId})`,
            message: app.decisionRemarks || `Your loan application was reviewed and could not be approved at this time.`,
            type: 'STATUS_UPDATE',
            timestamp: app.updatedAt || new Date().toISOString(),
            read: false,
            link: `/applications/${app.applicationId}`,
            meta: { status: 'REJECTED' }
          });
        }
      });

      // 4. Document Review (Approved / Rejected) notifications
      const cid = currentUser.loanCustomerId || currentUser.customerServiceId || currentUser.userId;
      if (cid) {
        try {
          const docs = await fetchCustomerDocuments(cid);
          (Array.isArray(docs) ? docs : []).forEach((doc) => {
            const docId = doc.documentId || doc.id;
            const docName = doc.documentName || doc.originalFileName || doc.documentType || `DOC-${docId}`;
            const st = (doc.status || '').toUpperCase();

            if (st === 'VERIFIED' || st === 'APPROVED') {
              generated.push({
                id: `doc-approved-${docId}-${st}`,
                title: `✅ Document Approved: DOC-${docId}`,
                message: `Your document "${docName}" has been verified and approved by the Underwriting team.`,
                type: 'DOC_APPROVED',
                timestamp: doc.updatedAt || doc.createdAt || new Date().toISOString(),
                read: false,
                link: `/documents`,
                meta: { documentId: docId, status: st }
              });
            } else if (st === 'REJECTED' || st === 'ACTION_REQUIRED' || st === 'FAILED') {
              generated.push({
                id: `doc-rejected-${docId}-${st}`,
                title: `❌ Document Rejected: DOC-${docId}`,
                message: `Your document "${docName}" was rejected. ${doc.remarks ? 'Reason: ' + doc.remarks : 'Please re-upload a clear image/copy.'}`,
                type: 'DOC_REJECTED',
                timestamp: doc.updatedAt || doc.createdAt || new Date().toISOString(),
                read: false,
                link: `/documents`,
                meta: { documentId: docId, status: st, remarks: doc.remarks }
              });
            }
          });
        } catch (docErr) {
          console.warn('Sync customer doc status error:', docErr);
        }
      }

      setNotifications((prev) => {
        const existingIds = new Set(prev.map((p) => p.id));
        const newOnes = generated.filter((g) => !existingIds.has(g.id));

        const seenAlerts = getSeenAlerts();
        const unseenNewOnes = newOnes.filter((g) => !seenAlerts.has(g.id));

        if (unseenNewOnes.length > 0) {
          // Play chime and show toast ONLY for truly unseen new events (never on page reload!)
          const critical = unseenNewOnes.find(n => n.type === 'DOC_REJECTED' || n.type === 'DOC_APPROVED') || unseenNewOnes[0];
          if (critical) {
            unseenNewOnes.forEach(n => recordSeenAlert(n.id));
            setToast(critical);
            playChime();
            setTimeout(() => setToast((curr) => (curr?.id === critical.id ? null : curr)), 7000);
          }
        }
        return [...newOnes, ...prev];
      });
    } catch (e) {
      console.warn('Sync customer notifications error:', e);
    }
  }, [currentUser, playChime]);

  useEffect(() => {
    if (currentUser?.email) {
      syncNotifications();
      const interval = setInterval(syncNotifications, 15000);
      return () => clearInterval(interval);
    }
  }, [currentUser, syncNotifications]);

  const markAllAsRead = useCallback(() => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
  }, []);

  const markAsRead = useCallback((id) => {
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, read: true } : n)));
  }, []);

  const unreadCount = notifications.filter((n) => !n.read).length;

  return (
    <NotificationContext.Provider
      value={{
        notifications,
        unreadCount,
        addNotification,
        markAllAsRead,
        markAsRead,
        syncNotifications,
      }}
    >
      {children}
      {toast && (
        <div
          style={{
            position: 'fixed',
            top: 24,
            right: 24,
            zIndex: 99999,
            width: 360,
            maxWidth: 'calc(100vw - 32px)',
            background: 'linear-gradient(135deg, rgba(13, 20, 44, 0.98) 0%, rgba(20, 32, 68, 0.98) 100%)',
            border: '1.5px solid rgba(0, 210, 255, 0.5)',
            boxShadow: '0 12px 35px rgba(0,0,0,0.6), 0 0 20px rgba(0, 210, 255, 0.25)',
            borderRadius: 12,
            padding: '16px 18px',
            color: '#fff',
            animation: 'slideInRight 0.3s ease-out forwards',
            backdropFilter: 'blur(12px)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8, marginBottom: 6 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: '1.2rem' }}>
                {toast.type === 'MANAGER_ASSIGNED' ? '👨‍💼' : toast.type === 'DOC_REQUEST' ? '📋' : toast.type === 'STATUS_UPDATE' ? '🎉' : '🔔'}
              </span>
              <strong style={{ fontSize: '.9rem', color: '#00d2ff' }}>{toast.title}</strong>
            </div>
            <button
              onClick={() => setToast(null)}
              style={{ background: 'transparent', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: 2, fontSize: '1rem', lineHeight: 1 }}
            >
              ×
            </button>
          </div>
          <p style={{ margin: 0, fontSize: '.84rem', color: '#cbd5e1', lineHeight: 1.45 }}>{toast.message}</p>
        </div>
      )}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error('useNotifications must be used within NotificationProvider');
  return ctx;
}
