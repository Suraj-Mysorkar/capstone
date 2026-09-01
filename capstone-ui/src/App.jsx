import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
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
import { AuthProvider }     from './context/AuthContext';
import './index.css';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <div className="layout">
          <Sidebar />
          <div className="main">
            <Header />
            <Routes>
              <Route path="/"                         element={<Dashboard />} />
              <Route path="/schemes"                  element={<SchemesPage />} />
              <Route path="/emi"                      element={<EmiPage />} />
              <Route path="/documents"                element={<DocumentsPage />} />
              <Route path="/apply"                    element={<ApplyPage />} />
              <Route path="/applications"             element={<ApplicationsPage />} />
              <Route path="/applications/:id"         element={<ApplicationDetailPage />} />
              <Route path="/login"                    element={<EmployeeLoginPage />} />
              <Route path="/employee-login"           element={<EmployeeLoginPage />} />
            </Routes>
          </div>
        </div>
      </BrowserRouter>
    </AuthProvider>
  );
}
