import React, { useState, useRef, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';
import { LogOut, Bell, Check, Radio, Volume2, ArrowRight, FileText, UserCheck, CheckCircle2 } from 'lucide-react';

const titles = {
  '/':             'Dashboard',
  '/dashboard':    'Dashboard',
  '/schemes':      'Loan Schemes',
  '/emi':          'EMI Calculator',
  '/documents':    'Document Upload & Review',
  '/apply':        'Apply for Loan',
  '/applications': 'Applications Queue',
};

export default function Header() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { currentUser, logout } = useAuth();
  const { notifications, unreadCount, isConnected, markRead, markAllRead, testChime } = useNotifications();
  const [isOpen, setIsOpen] = useState(false);
  const popoverRef = useRef(null);

  const base = '/' + pathname.split('/')[1];
  const title = titles[base] || 'Dashboard';

  const getInitials = (name) => {
    if (!name) return 'EP';
    const parts = name.trim().split(' ');
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.substring(0, 2).toUpperCase();
  };

  // Close popover when clicking outside
  useEffect(() => {
    function handleClickOutside(e) {
      if (popoverRef.current && !popoverRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleNotificationClick = (n) => {
    markRead(n.id);
    setIsOpen(false);
    if (n.applicationId) {
      navigate(`/applications/${n.applicationId}`);
    } else if (n.customerId) {
      navigate(`/documents`);
    } else {
      navigate(`/applications`);
    }
  };

  const getIcon = (type) => {
    switch (type) {
      case 'DOCUMENT_UPLOADED':
        return <FileText size={15} color="var(--accent)" />;
      case 'NEW_CASE_ASSIGNED':
        return <UserCheck size={15} color="var(--green)" />;
      case 'DECISION_RECORDED':
        return <CheckCircle2 size={15} color="var(--purple)" />;
      default:
        return <Bell size={15} color="var(--yellow)" />;
    }
  };

  return (
    <header className="header" style={{ position: 'relative' }}>
      <div className="header-title">{title}</div>
      <div className="header-right" style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <span className="badge-server">Cloud Live · Azure</span>

        {currentUser && (
          <>
            {/* Real-time Notification Bell */}
            <div style={{ position: 'relative' }} ref={popoverRef}>
              <button
                onClick={() => setIsOpen(prev => !prev)}
                style={{
                  background: isOpen ? 'rgba(0, 210, 255, 0.15)' : 'rgba(255,255,255,0.05)',
                  border: '1px solid ' + (unreadCount > 0 ? 'rgba(0, 210, 255, 0.4)' : 'rgba(255,255,255,0.1)'),
                  borderRadius: 10,
                  padding: '7px 10px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  color: unreadCount > 0 ? 'var(--accent)' : 'var(--muted)',
                  position: 'relative',
                  transition: 'all 0.15s ease',
                }}
                title="Live Activity Notifications"
              >
                <Bell size={17} className={unreadCount > 0 ? 'bell-animated' : ''} />
                
                {/* Live Stream Pulse Dot */}
                <span
                  style={{
                    width: 7,
                    height: 7,
                    borderRadius: '50%',
                    background: isConnected ? 'var(--green)' : 'var(--muted)',
                    boxShadow: isConnected ? '0 0 6px var(--green)' : 'none',
                    display: 'inline-block',
                  }}
                  title={isConnected ? 'Live WebSocket/SSE Stream Connected' : 'Connecting to Stream...'}
                />

                {/* Unread Counter Badge */}
                {unreadCount > 0 && (
                  <span
                    style={{
                      background: 'linear-gradient(135deg, #e74c3c 0%, #c0392b 100%)',
                      color: '#fff',
                      fontSize: '.7rem',
                      fontWeight: 700,
                      borderRadius: 10,
                      padding: '1px 6px',
                      lineHeight: 1.2,
                    }}
                  >
                    {unreadCount}
                  </span>
                )}
              </button>

              {/* Notification Popover Tray */}
              {isOpen && (
                <>
                  <div
                    style={{
                      position: 'fixed',
                      inset: 0,
                      zIndex: 99998,
                    }}
                    onClick={() => setIsOpen(false)}
                  />
                  <div
                    style={{
                      position: 'absolute',
                      top: 'calc(100% + 10px)',
                      right: 0,
                      width: 400,
                      maxWidth: '92vw',
                      background: '#0e1326',
                      border: '1px solid rgba(0, 210, 255, 0.45)',
                      borderRadius: 14,
                      boxShadow: '0 24px 60px rgba(0, 0, 0, 0.9), 0 0 25px rgba(0, 210, 255, 0.25)',
                      zIndex: 99999,
                      overflow: 'hidden',
                      animation: 'slideInRight 0.2s cubic-bezier(0.16, 1, 0.3, 1)',
                    }}
                  >
                  <div
                    style={{
                      padding: '12px 16px',
                      background: 'rgba(255,255,255,0.03)',
                      borderBottom: '1px solid rgba(255,255,255,0.06)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700, fontSize: '.88rem' }}>
                      <Bell size={16} color="var(--accent)" />
                      <span>Assigned Activity & Alerts</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <button
                        onClick={testChime}
                        style={{
                          background: 'rgba(0, 210, 255, 0.1)',
                          border: 'none',
                          color: 'var(--accent)',
                          cursor: 'pointer',
                          padding: '3px 8px',
                          borderRadius: 6,
                          fontSize: '.72rem',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 4,
                        }}
                        title="Test Bell Chime Sound"
                      >
                        <Volume2 size={12} /> Test Bell 🔔
                      </button>
                      {unreadCount > 0 && (
                        <button
                          onClick={markAllRead}
                          style={{
                            background: 'transparent',
                            border: 'none',
                            color: 'var(--muted)',
                            cursor: 'pointer',
                            fontSize: '.72rem',
                          }}
                        >
                          Mark all read
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Notification Items List */}
                  <div style={{ maxHeight: 340, overflowY: 'auto' }}>
                    {notifications.length === 0 ? (
                      <div style={{ padding: '24px 16px', textAlign: 'center', color: 'var(--muted)', fontSize: '.82rem' }}>
                        No notifications yet. New assigned cases and customer uploads will alert here in real time.
                      </div>
                    ) : (
                      notifications.map(n => (
                        <div
                          key={n.id}
                          onClick={() => handleNotificationClick(n)}
                          style={{
                            padding: '12px 16px',
                            borderBottom: '1px solid rgba(255,255,255,0.04)',
                            background: !n.read ? 'rgba(0, 210, 255, 0.05)' : 'transparent',
                            cursor: 'pointer',
                            transition: 'background 0.15s',
                            display: 'flex',
                            gap: 12,
                            alignItems: 'flex-start',
                          }}
                          onMouseEnter={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.06)'; }}
                          onMouseLeave={e => { e.currentTarget.style.background = !n.read ? 'rgba(0, 210, 255, 0.05)' : 'transparent'; }}
                        >
                          <div style={{ padding: 6, borderRadius: 8, background: 'rgba(255,255,255,0.06)', flexShrink: 0, marginTop: 2 }}>
                            {getIcon(n.eventType)}
                          </div>
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 3 }}>
                              <span style={{ fontWeight: 600, fontSize: '.84rem', color: '#fff' }}>
                                {n.title}
                              </span>
                              {!n.read && (
                                <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--accent)', flexShrink: 0 }} />
                              )}
                            </div>
                            <div style={{ fontSize: '.78rem', color: 'var(--muted)', lineHeight: 1.35, marginBottom: 4 }}>
                              {n.message}
                            </div>
                            {(n.customerId || n.applicationId) && (
                              <div style={{ fontSize: '.72rem', color: 'var(--accent)', display: 'flex', gap: 10 }}>
                                {n.customerId && <span>Cust: {n.customerId}</span>}
                                {n.applicationId && <span>App: {n.applicationId}</span>}
                              </div>
                            )}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </>
            )}
            </div>

            {/* Profile Info */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div
                className="avatar"
                style={{
                  background: 'linear-gradient(135deg, #00d2ff 0%, #3a7bd5 100%)'
                }}
              >
                <div className="avatar-initials">{getInitials(currentUser.name || currentUser.username)}</div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <span className="user-name" style={{ fontSize: '0.85rem', lineHeight: 1.2 }}>
                  {currentUser.name || currentUser.username}
                </span>
                <span style={{ fontSize: '0.7rem', color: 'var(--accent)', fontWeight: 600 }}>
                  {currentUser.role || 'Credit Manager'}
                </span>
              </div>

              <button
                className="btn btn-ghost"
                style={{ padding: '5px 8px', fontSize: '0.75rem', color: 'var(--muted)', marginLeft: 4 }}
                onClick={logout}
                title="Sign Out"
              >
                <LogOut size={14} />
              </button>
            </div>
          </>
        )}
      </div>
    </header>
  );
}
