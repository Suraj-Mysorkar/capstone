import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import Dashboard from './pages/Dashboard';
import CustomersPage from './pages/CustomersPage';
import CustomerDetailPage from './pages/CustomerDetailPage';
import RegisterPage from './pages/RegisterPage';
import LookupPage from './pages/LookupPage';
import SettingsPage from './pages/SettingsPage';
import './index.css';

export default function App() {
  return (
    <BrowserRouter>
      <div className="layout">
        <Sidebar />
        <div className="main">
          <Header />
          <Routes>
            <Route path="/"                element={<Dashboard />} />
            <Route path="/customers"       element={<CustomersPage />} />
            <Route path="/customers/:id"   element={<CustomerDetailPage />} />
            <Route path="/register"        element={<RegisterPage />} />
            <Route path="/lookup"          element={<LookupPage />} />
            <Route path="/settings"        element={<SettingsPage />} />
          </Routes>
        </div>
      </div>
    </BrowserRouter>
  );
}
