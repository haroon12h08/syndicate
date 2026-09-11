import { Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import AppShell from './components/AppShell';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import HomePage from './pages/HomePage';
import OrganizationPage from './pages/OrganizationPage';
import CompaniesPage from './pages/CompaniesPage';
import CompanyDetailPage from './pages/CompanyDetailPage';
import TransactionDetailPage from './pages/TransactionDetailPage';
import WorkstreamDetailPage from './pages/WorkstreamDetailPage';

function Shell({ children }) {
  return (
    <ProtectedRoute>
      <AppShell>{children}</AppShell>
    </ProtectedRoute>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/home" element={<Shell><HomePage /></Shell>} />
        <Route path="/organizations/:id" element={<Shell><OrganizationPage /></Shell>} />
        <Route path="/companies" element={<Shell><CompaniesPage /></Shell>} />
        <Route path="/companies/:id" element={<Shell><CompanyDetailPage /></Shell>} />
        <Route path="/transactions/:id" element={<Shell><TransactionDetailPage /></Shell>} />
        <Route path="/workstreams/:id" element={<Shell><WorkstreamDetailPage /></Shell>} />
      </Routes>
    </AuthProvider>
  );
}
