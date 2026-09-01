import React, { createContext, useContext, useState, useEffect } from 'react';
import { employeeLogin } from '../services/api';

const AuthContext = createContext(null);

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
      const userData = {
        username: res.username || username,
        role: res.role || 'Senior Underwriter / Operations Manager',
        name: res.name || res.fullName || (username === 'markj' ? 'Mark Jenkins' : username),
        token: res.token || res.accessToken || res.jwt || 'apim-session-token',
        loginTime: new Date().toISOString(),
        ...res
      };

      setCurrentUser(userData);
      localStorage.setItem('capstone_employee_user', JSON.stringify(userData));
      if (userData.token) {
        localStorage.setItem('capstone_employee_token', userData.token);
      }
      return userData;
    } catch (err) {
      const errMsg = err.message || 'Login failed. Please check your credentials.';
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
