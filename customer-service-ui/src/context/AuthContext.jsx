import React, { createContext, useCallback, useContext, useState } from 'react';
import { authLogin, authRegister, getMyProfile } from '../services/api';

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
    if (!raw) return null;
    const user = JSON.parse(raw);
    if (!user || !user.token) {
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(TOKEN_KEY);
      return null;
    }
    const claims = parseJwt(user.token);
    if (!claims) {
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(TOKEN_KEY);
      return null;
    }
    // Expired token check
    if (claims.exp && claims.exp * 1000 <= Date.now()) {
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(TOKEN_KEY);
      return null;
    }
    // APIM issuer check (tokens from dev server or other issuers are cleared)
    if (claims.iss && claims.iss !== 'https://azure-api.net') {
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(TOKEN_KEY);
      return null;
    }
    return user;
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
  const token = res.access_token || res.token || res.accessToken || '';
  const claims = token ? parseJwt(token) : null;
  const role = res.role || claims?.roles?.replace(/^ROLE_/, '')?.toLowerCase() || 'customer';
  return {
    username: res.username || claims?.preferred_username || fallbackUsername,
    email: res.email || claims?.email || (fallbackUsername?.includes('@') ? fallbackUsername : null),
    name: res.name || claims?.name || res.username || fallbackUsername,
    role: role,
    userId: res.userId ?? claims?.userId ?? null,
    token: token,
    customerServiceId: res.customerId || claims?.customerId || null,
    onboardingStatus: res.onboardingStatus || null,
    phoneNumber: res.phoneNumber || null,
    loanCustomerId: (res.customerId || claims?.customerId) ? `CUST-${res.customerId || claims?.customerId}` : null,
    loginTime: new Date().toISOString(),
  };
}

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(readUser);
  const [loading, setLoading] = useState(false);
  const [authError, setAuthError] = useState('');

  React.useEffect(() => {
    const handleUnauthorized = () => {
      setCurrentUser(null);
      setAuthError('Session expired or access denied. Please sign in again.');
    };
    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized);
  }, []);

  const login = useCallback(async (username, password) => {
    setLoading(true);
    setAuthError('');
    try {
      const res = await authLogin(username.trim(), password);
      let user = toUser(res, username.trim().toLowerCase());
      // Persist first so the follow-up request carries the bearer token.
      persist(user);

      // The login token only carries the customer id — pull the email + profile
      // from customer-service so the loan application form can be used.
      if (!user.email || !user.customerServiceId) {
        try {
          const me = await getMyProfile();
          user = {
            ...user,
            email: me.email || user.email,
            name: user.name || `${me.firstName || ''} ${me.lastName || ''}`.trim() || user.name,
            customerServiceId: me.id || user.customerServiceId,
            onboardingStatus: me.onboardingStatus || user.onboardingStatus,
            phoneNumber: me.phoneNumber || user.phoneNumber,
          };
          persist(user);
        } catch {
          /* profile lookup failed — portal still usable, email stays as derived */
        }
      }

      setCurrentUser(user);
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
