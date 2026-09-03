import React, { createContext, useCallback, useContext, useState } from 'react';
import { authLogin, authRegister } from '../services/api';

const AuthContext = createContext(null);

const USER_KEY = 'csp_user';
const TOKEN_KEY = 'csp_token';

function parseJwt(token) {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function readUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function persist(user) {
  try {
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify(user));
      if (user.token) localStorage.setItem(TOKEN_KEY, user.token);
    } else {
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    /* ignore */
  }
}

/** Normalise an auth response into the shape the portal uses everywhere. */
function toUser(res, fallbackUsername) {
  const claims = res.token ? parseJwt(res.token) : null;
  return {
    username: res.username || claims?.preferred_username || fallbackUsername,
    email: res.email || claims?.email || fallbackUsername,
    name: res.name || claims?.name || res.username || fallbackUsername,
    role: res.role || claims?.role || 'customer',
    userId: res.userId ?? claims?.userId ?? null,
    token: res.token || '',
    customerServiceId: res.customerId || claims?.customerId || null,
    onboardingStatus: res.onboardingStatus || null,
    phoneNumber: res.phoneNumber || null,
    loanCustomerId: null,
    loginTime: new Date().toISOString(),
  };
}

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(readUser);
  const [loading, setLoading] = useState(false);
  const [authError, setAuthError] = useState('');

  const login = useCallback(async (username, password) => {
    setLoading(true);
    setAuthError('');
    try {
      const res = await authLogin(username.trim(), password);
      const user = toUser(res, username.trim().toLowerCase());
      setCurrentUser(user);
      persist(user);
      return user;
    } catch (err) {
      setAuthError(err.message || 'Invalid username or password.');
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const register = useCallback(async (payload) => {
    setLoading(true);
    setAuthError('');
    try {
      const res = await authRegister(payload);
      const user = toUser(res, (payload.email || '').toLowerCase());
      user.phoneNumber = payload.phoneNumber || user.phoneNumber;
      setCurrentUser(user);
      persist(user);
      return user;
    } catch (err) {
      setAuthError(err.message || 'Registration failed.');
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const updateUser = useCallback((patch) => {
    setCurrentUser((prev) => {
      const next = { ...(prev || {}), ...patch };
      persist(next);
      return next;
    });
  }, []);

  const logout = useCallback(() => {
    setCurrentUser(null);
    setAuthError('');
    persist(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        currentUser,
        isAuthenticated: !!currentUser,
        login,
        register,
        updateUser,
        logout,
        loading,
        authError,
        setAuthError,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
