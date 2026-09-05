import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import { useAuth } from './AuthContext';
import {
  getNotificationStreamUrl,
  fetchNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
  sendTestNotification
} from '../services/api';

const NotificationContext = createContext(null);

// Synthesize a crystal-clear luxury bell chime using Web Audio API
function playBellChime() {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    const ctx = new AudioContextClass();

    if (ctx.state === 'suspended') {
      ctx.resume();
    }

    const now = ctx.currentTime;

    // Harmonic tones for crystal notification bell (D6 + A6)
    const freqs = [1174.66, 1760.00, 2349.32];
    const gains = [0.25, 0.18, 0.08];

    freqs.forEach((freq, idx) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, now);

      gain.gain.setValueAtTime(0.001, now);
      gain.gain.exponentialRampToValueAtTime(gains[idx], now + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 1.2);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start(now);
      osc.stop(now + 1.2);
    });
  } catch (e) {
    console.debug('Web Audio chime not playable:', e);
  }
}

const getSeenManagerAlerts = () => {
  try {
    return new Set(JSON.parse(localStorage.getItem('manager_seen_alerts') || '[]'));
  } catch (e) {
    return new Set();
  }
};

const recordSeenManagerAlert = (id) => {
  try {
    const current = Array.from(getSeenManagerAlerts());
    if (!current.includes(id)) {
      current.push(id);
      localStorage.setItem('manager_seen_alerts', JSON.stringify(current.slice(-150)));
    }
  } catch (e) {}
};

export function NotificationProvider({ children }) {
  const { currentUser, isAuthenticated } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [toasts, setToasts] = useState([]);
  const [isConnected, setIsConnected] = useState(false);
  const eventSourceRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);

  const username = currentUser?.username || 'mgr1';

  const loadHistory = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      const data = await fetchNotifications(username);
      if (Array.isArray(data)) {
        setNotifications(data);
      }
    } catch (e) {
      console.debug('Could not fetch notifications history:', e);
    }
  }, [isAuthenticated, username]);

  const addToast = useCallback((notif) => {
    const toastId = notif.id || `toast-${Date.now()}-${Math.random()}`;
    const seen = getSeenManagerAlerts();

    if (!seen.has(toastId)) {
      recordSeenManagerAlert(toastId);
      // Play bell sound
      playBellChime();

      // Add to toasts list (auto-dismisses after 7s)
      const newToast = { ...notif, toastId, createdAt: new Date() };
      setToasts(prev => [newToast, ...prev.slice(0, 4)]);
    }

    // Update notifications list
    setNotifications(prev => {
      const exists = prev.some(n => n.id === notif.id);
      if (exists) return prev;
      return [notif, ...prev];
    });

    // Notify other components via window event for zero-refresh updates
    window.dispatchEvent(new CustomEvent('loan-data-updated', { detail: notif }));
  }, []);

  const dismissToast = useCallback((toastId) => {
    setToasts(prev => prev.filter(t => t.toastId !== toastId));
  }, []);

  const markRead = useCallback(async (id) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
    await markNotificationAsRead(id);
  }, []);

  const markAllRead = useCallback(async () => {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
    await markAllNotificationsAsRead(username);
  }, [username]);

  // Connect SSE Notification Stream
  useEffect(() => {
    if (!isAuthenticated) {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      setIsConnected(false);
      return;
    }

    loadHistory();

    function connectSSE() {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }

      const streamUrl = getNotificationStreamUrl(username);
      console.log('Connecting to real-time notification stream:', streamUrl);

      const es = new EventSource(streamUrl);
      eventSourceRef.current = es;

      es.addEventListener('open', () => {
        console.log('SSE Stream Connected for user:', username);
        setIsConnected(true);
      });

      es.addEventListener('CONNECTED', (e) => {
        console.log('SSE Handshake Ack:', e.data);
        setIsConnected(true);
      });

      es.addEventListener('NOTIFICATION', (e) => {
        try {
          const data = JSON.parse(e.data);
          addToast(data);
        } catch (err) {
          console.warn('Error parsing SSE notification payload:', err);
        }
      });

      es.onerror = (err) => {
        console.debug('SSE stream disconnected, will attempt reconnect in 10s...', err);
        setIsConnected(false);
        es.close();
        eventSourceRef.current = null;

        // Auto-reconnect after 10s
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = setTimeout(() => {
          if (isAuthenticated) {
            connectSSE();
          }
        }, 10000);
      };
    }

    connectSSE();

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
        eventSourceRef.current = null;
      }
      clearTimeout(reconnectTimeoutRef.current);
    };
  }, [isAuthenticated, username, loadHistory, addToast]);

  const unreadCount = notifications.filter(n => !n.read).length;

  const testChime = () => {
    playBellChime();
    addToast({
      id: `TEST-${Date.now()}`,
      title: 'Customer Document Activity',
      message: 'Customer Rahul Sharma (CUST-12) uploaded Pan_Card.pdf for Loan Application APP-958E058E.',
      eventType: 'DOCUMENT_UPLOADED',
      customerId: 'CUST-12',
      customerName: 'Rahul Sharma',
      applicationId: 'APP-958E058E',
      timestamp: new Date().toISOString(),
      isRead: false
    });
  };

  return (
    <NotificationContext.Provider
      value={{
        notifications,
        toasts,
        unreadCount,
        isConnected,
        dismissToast,
        markRead,
        markAllRead,
        testChime,
        playBellChime
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider');
  }
  return context;
}
