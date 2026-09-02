import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Header  from './components/Header';
import Dashboard            from './pages/Dashboard';
import SchemesPage          from './pages/SchemesPage';
import EmiPage              from './pages/EmiPage';
import DocumentsPage        from './pages/DocumentsPage';
import ApplyPage            from './pages/ApplyPage';
import ApplicationsPage     from './pages/ApplicationsPage';
import ApplicationDetailPage from './pages/ApplicationDetailPage';
import EmployeeLoginPage    from './pages/EmployeeLoginPage';
import ProtectedRoute       from './components/ProtectedRoute';
import { AuthProvider, useAuth } from './context/AuthContext';
import { NotificationProvider } from './context/NotificationContext';
import { NotificationToastContainer } from './components/NotificationToast';
import './index.css';

function AppLayout() {
  return (
    <div className="layout">
      <Sidebar />
      <div className="main">
        <Header />
        <Routes>
          <Route path="/"                         element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard"                element={<Dashboard />} />
          <Route path="/schemes"                  element={<SchemesPage />} />
          <Route path="/emi"                      element={<EmiPage />} />
          <Route path="/documents"                element={<DocumentsPage />} />
          <Route path="/apply"                    element={<ApplyPage />} />
          <Route path="/applications"             element={<ApplicationsPage />} />
          <Route path="/applications/:id"         element={<ApplicationDetailPage />} />
          <Route path="*"                         element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </div>
      <NotificationToastContainer />
    </div>
  );
}

function RootNavigator() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={<EmployeeLoginPage />} />
      <Route
        path="/*"
        element={
          isAuthenticated ? (
            <AppLayout />
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
      <NotificationProvider>
        <BrowserRouter>
          <RootNavigator />
        </BrowserRouter>
      </NotificationProvider>
    </AuthProvider>
  );
}
