import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider, useAuth } from './context/AuthContext';
import Dashboard from './pages/Dashboard';
import EmiPage from './pages/EmiPage';
import DocumentsPage from './pages/DocumentsPage';
import ApplyPage from './pages/ApplyPage';
import MyApplicationsPage from './pages/MyApplicationsPage';
import ApplicationDetailPage from './pages/ApplicationDetailPage';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import SettingsPage from './pages/SettingsPage';
import './index.css';

function AuthShell({ children, wide }) {
  return (
    <div className="auth-shell">
      <div className={wide ? 'auth-body auth-body-wide' : 'auth-body'}>{children}</div>
    </div>
  );
}

function AppLayout() {
  return (
    <div className="layout">
      <Sidebar />
      <div className="main">
        <Header />
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/emi" element={<EmiPage />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/apply" element={<ApplyPage />} />
          <Route path="/applications" element={<MyApplicationsPage />} />
          <Route path="/applications/:id" element={<ApplicationDetailPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </div>
  );
}

function RootNavigator() {
  const { isAuthenticated } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={<AuthShell><LoginPage /></AuthShell>} />
      <Route path="/register" element={<AuthShell wide><RegisterPage /></AuthShell>} />
      <Route
        path="/*"
        element={
          isAuthenticated ? (
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <RootNavigator />
      </BrowserRouter>
    </AuthProvider>
  );
}
