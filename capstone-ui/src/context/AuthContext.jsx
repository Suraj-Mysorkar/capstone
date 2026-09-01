import React, { createContext, useContext, useState } from 'react';
import { employeeLogin } from '../services/api';

const AuthContext = createContext(null);

function parseJwt(token) {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      const stored = localStorage.getItem('capstone_employee_user');
      return stored ? JSON.parse(stored) : null;
    } catch (e) {
      return null;
    }
  });

  const [loading, setLoading] = useState(false);
  const [authError, setAuthError] = useState('');

  const login = async (username, password) => {
    setLoading(true);
    setAuthError('');
    try {
      const res = await employeeLogin(username, password);
      const token = res.access_token || res.token || res.accessToken || res.jwt || '';
      const jwtClaims = token ? parseJwt(token) : null;

      const userData = {
        username: jwtClaims?.preferred_username || res.username || username,
        role: jwtClaims?.roles?.replace(/^ROLE_/, '') || res.role || 'Employee',
        name: jwtClaims?.name || res.name || (username === 'markj' ? 'Mark Jack' : username),
        userId: jwtClaims?.userId || res.userId || '3',
        token: token,
        loginTime: new Date().toISOString(),
        ...res
      };

      setCurrentUser(userData);
      localStorage.setItem('capstone_employee_user', JSON.stringify(userData));
      if (token) {
        localStorage.setItem('capstone_employee_token', token);
      }
      return userData;
    } catch (err) {
      const errMsg = err.message || 'Invalid username or password.';
      setAuthError(errMsg);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    setCurrentUser(null);
    setAuthError('');
    localStorage.removeItem('capstone_employee_user');
    localStorage.removeItem('capstone_employee_token');
  };

  return (
    <AuthContext.Provider
      value={{
        currentUser,
        isAuthenticated: !!currentUser,
        login,
        logout,
        loading,
        authError,
        setAuthError
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
