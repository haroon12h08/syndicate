import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import OrganizationPage from './pages/OrganizationPage';
import CompaniesPage from './pages/CompaniesPage';
import CompanyDetailPage from './pages/CompanyDetailPage';
import TransactionDetailPage from './pages/TransactionDetailPage';
import WorkstreamDetailPage from './pages/WorkstreamDetailPage';

export default function App() {
  return (
    <AuthProvider>
      <Navbar />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/" element={<Navigate to="/companies" replace />} />
        <Route path="/organizations/:id" element={<ProtectedRoute><OrganizationPage /></ProtectedRoute>} />
        <Route path="/companies" element={<ProtectedRoute><CompaniesPage /></ProtectedRoute>} />
        <Route path="/companies/:id" element={<ProtectedRoute><CompanyDetailPage /></ProtectedRoute>} />
        <Route path="/transactions/:id" element={<ProtectedRoute><TransactionDetailPage /></ProtectedRoute>} />
        <Route path="/workstreams/:id" element={<ProtectedRoute><WorkstreamDetailPage /></ProtectedRoute>} />
      </Routes>
    </AuthProvider>
  );
}
