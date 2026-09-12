import { Route, Routes } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './context/AuthContext';
import { BreadcrumbProvider } from './context/BreadcrumbContext';
import CommandPalette from './components/CommandPalette';
import RouteProgress from './components/RouteProgress';
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
      <BreadcrumbProvider>
      <RouteProgress />
      <CommandPalette />
      <Toaster
        position="bottom-right"
        toastOptions={{
          style: {
            background: '#111111',
            color: '#e5e5e5',
            border: '1px solid #2e2e2e',
            borderRadius: '4px',
            fontFamily: "'JetBrains Mono', ui-monospace, monospace",
            fontSize: '0.8rem',
          },
          success: { iconTheme: { primary: '#4ade80', secondary: '#111111' } },
          error: { iconTheme: { primary: '#f87171', secondary: '#111111' } },
        }}
      />
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
      </BreadcrumbProvider>
    </AuthProvider>
  );
}
