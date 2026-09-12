import { Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import AppShell from './components/AppShell';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import InviteAcceptPage from './pages/InviteAcceptPage';
import HomePage from './pages/HomePage';
import InvitationsInboxPage from './pages/InvitationsInboxPage';
import OrganizationPage from './pages/OrganizationPage';
import OrganizationsPage from './pages/OrganizationsPage';
import CompaniesPage from './pages/CompaniesPage';
import CompanyDetailPage from './pages/CompanyDetailPage';
import TransactionDetailPage from './pages/TransactionDetailPage';
import WorkstreamDetailPage from './pages/WorkstreamDetailPage';
import FactTracePage from './pages/FactTracePage';

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
        <Route path="/invite/:token" element={<InviteAcceptPage />} />
        <Route path="/home" element={<Shell><HomePage /></Shell>} />
        <Route path="/invitations" element={<Shell><InvitationsInboxPage /></Shell>} />
        <Route path="/organizations" element={<Shell><OrganizationsPage /></Shell>} />
        <Route path="/organizations/:id" element={<Shell><OrganizationPage /></Shell>} />
        <Route path="/companies" element={<Shell><CompaniesPage /></Shell>} />
        <Route path="/companies/:id" element={<Shell><CompanyDetailPage /></Shell>} />
        <Route path="/transactions/:id" element={<Shell><TransactionDetailPage /></Shell>} />
        <Route path="/workstreams/:id" element={<Shell><WorkstreamDetailPage /></Shell>} />
        <Route path="/facts/:id/trace" element={<Shell><FactTracePage /></Shell>} />
      </Routes>
    </AuthProvider>
  );
}
