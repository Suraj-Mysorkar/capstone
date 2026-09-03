import React, { useState, useRef, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { LogOut, Bell, Check, ExternalLink } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';

const titles = {
  '/': 'Home',
  '/emi': 'EMI Calculator',
  '/documents': 'My Documents',
  '/apply': 'Apply for Loan',
  '/applications': 'My Applications',
  '/settings': 'Settings',
};

export default function Header() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { currentUser, logout } = useAuth();
  const { notifications, unreadCount, markAllAsRead, markAsRead } = useNotifications();
  const [openNotifs, setOpenNotifs] = useState(false);
  const dropdownRef = useRef(null);

  const base = '/' + pathname.split('/')[1];
  const title = pathname.startsWith('/applications/') ? 'Application Detail' : (titles[base] || 'Everyday Bank');

  const initials = (currentUser?.name || currentUser?.email || '?')
    .split(/[\s@.]+/).filter(Boolean).slice(0, 2).map((s) => s[0]?.toUpperCase()).join('');

  useEffect(() => {
    const handleOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setOpenNotifs(false);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, []);

  return (
    <header className="header" style={{ position: 'relative', zIndex: 1000 }}>
      <div className="header-title">{title}</div>
      <div className="header-right" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span className="badge-server">Online</span>

        {currentUser && (
          <>
            {/* NOTIFICATION BELL */}
            <div style={{ position: 'relative' }} ref={dropdownRef}>
              <button
                className="btn btn-ghost"
                style={{
                  position: 'relative',
                  padding: '7px 9px',
                  borderRadius: 8,
                  border: openNotifs ? '1px solid var(--accent)' : '1px solid rgba(255,255,255,0.08)',
                  background: openNotifs ? 'rgba(0, 210, 255, 0.1)' : 'transparent',
                }}
                onClick={() => setOpenNotifs((o) => !o)}
                title="Customer Alerts & Notifications"
              >
                <Bell size={17} color={unreadCount > 0 ? '#00d2ff' : 'var(--muted)'} />
                {unreadCount > 0 && (
                  <span
                    style={{
                      position: 'absolute',
                      top: -4,
                      right: -4,
                      background: '#ef4444',
                      color: '#fff',
                      fontSize: '.68rem',
                      fontWeight: 800,
                      borderRadius: '50%',
                      minWidth: 16,
                      height: 16,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      padding: '0 4px',
                      boxShadow: '0 0 8px rgba(239, 68, 68, 0.8)',
                    }}
                  >
                    {unreadCount}
                  </span>
                )}
              </button>

              {/* NOTIFICATION DROPDOWN */}
              {openNotifs && (
                <div
                  style={{
                    position: 'absolute',
                    top: 'calc(100% + 8px)',
                    right: 0,
                    width: 380,
                    maxWidth: '90vw',
                    background: 'var(--card-bg, #0d142c)',
                    border: '1.5px solid rgba(0, 210, 255, 0.3)',
                    borderRadius: 12,
                    boxShadow: '0 16px 40px rgba(0,0,0,0.7), 0 0 20px rgba(0, 210, 255, 0.15)',
                    padding: 0,
                    zIndex: 9999,
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '12px 16px',
                      borderBottom: '1px solid rgba(255,255,255,0.08)',
                      background: 'rgba(255,255,255,0.02)',
                    }}
                  >
                    <div style={{ fontWeight: 700, fontSize: '.9rem', color: '#fff', display: 'flex', alignItems: 'center', gap: 6 }}>
                      <Bell size={15} color="var(--accent)" /> Case &amp; Manager Alerts
                    </div>
                    {unreadCount > 0 && (
                      <button
                        onClick={markAllAsRead}
                        style={{
                          background: 'none',
                          border: 'none',
                          color: 'var(--accent)',
                          fontSize: '.75rem',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 4,
                          fontWeight: 600,
                        }}
                      >
                        <Check size={12} /> Mark all read
                      </button>
                    )}
                  </div>

                  <div style={{ maxHeight: 360, overflowY: 'auto', padding: '8px 0' }}>
                    {notifications.length === 0 ? (
                      <div style={{ padding: '24px 16px', textAlign: 'center', color: 'var(--muted)', fontSize: '.84rem' }}>
                        No notifications yet.
                      </div>
                    ) : (
                      notifications.map((n) => (
                        <div
                          key={n.id}
                          onClick={() => {
                            markAsRead(n.id);
                            if (n.link) {
                              navigate(n.link);
                              setOpenNotifs(false);
                            }
                          }}
                          style={{
                            padding: '10px 16px',
                            borderBottom: '1px solid rgba(255,255,255,0.04)',
                            background: n.read ? 'transparent' : 'rgba(0, 210, 255, 0.05)',
                            cursor: 'pointer',
                            transition: 'background 0.15s',
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
                            <div style={{ fontWeight: 600, fontSize: '.84rem', color: n.read ? '#e2e8f0' : '#00d2ff' }}>
                              {n.type === 'MANAGER_ASSIGNED' ? '👨‍💼 ' : n.type === 'DOC_REQUEST' ? '📋 ' : '🔔 '}
                              {n.title}
                            </div>
                            {!n.read && (
                              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#00d2ff', marginTop: 4, flexShrink: 0 }} />
                            )}
                          </div>
                          <div style={{ fontSize: '.78rem', color: 'var(--muted)', marginTop: 3, lineHeight: 1.4 }}>
                            {n.message}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}
            </div>

            <div className="avatar"><div className="avatar-initials">{initials}</div></div>
            <span className="user-name" style={{ fontSize: '.82rem' }}>{currentUser.name || currentUser.email}</span>
            <button
              className="btn btn-ghost"
              style={{ padding: '5px 10px', fontSize: '.75rem' }}
              onClick={() => { logout(); navigate('/login'); }}
            >
              <LogOut size={13} /> Sign out
            </button>
          </>
        )}
      </div>
    </header>
  );
}
