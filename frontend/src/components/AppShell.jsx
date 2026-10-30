import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useBreadcrumbs } from '../context/BreadcrumbContext';
import { useAuth } from '../context/AuthContext';
import UserSettingsModal from './UserSettingsModal';

const NAV_ITEMS = [
  { to: '/home', label: 'Home' },
  { to: '/companies', label: 'Companies' },
  { to: '/organizations', label: 'Organizations' },
  { to: '/invitations', label: 'Invitations' },
];

export default function AppShell({ children }) {
  const { user, logout } = useAuth();
  const location = useLocation();
  const { trail } = useBreadcrumbs();
  const [settingsOpen, setSettingsOpen] = useState(false);

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <Link to="/home" className="app-sidebar-brand">
          <img src="/syndicate.png" alt="Syndicate" />
          <span>SYNDICATE</span>
        </Link>
        <nav className="app-sidebar-nav">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className={`app-sidebar-link${location.pathname.startsWith(item.to) ? ' active' : ''}`}
            >
              <span>{item.label}</span>
            </Link>
          ))}
        </nav>
        <div className="app-sidebar-footer">
          <div className="app-sidebar-user" style={{ cursor: 'pointer' }} onClick={() => setSettingsOpen(true)} title="Security & MFA settings">
            <strong>{user?.fullName}</strong>
            <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block' }}>
              {user?.mfaEnabled ? '🔒 2FA Active' : '⚙️ Security / MFA'}
            </span>
          </div>
          <button className="secondary" onClick={logout}>Log out</button>
        </div>
        <UserSettingsModal isOpen={settingsOpen} onClose={() => setSettingsOpen(false)} />
      </aside>
      <div className="app-main">
        <header className="app-topbar">
          <nav className="crumbs" aria-label="Breadcrumb">
            <Link to="/home">Syndicate</Link>
            {trail.map((c) => (
              <span key={`${c.label}-${c.to || 'leaf'}`}>
                <span className="crumb-sep">/</span>
                {c.to ? <Link to={c.to}>{c.label}</Link> : <span className="crumb-current">{c.label}</span>}
              </span>
            ))}
          </nav>
          <div className="app-topbar-right">
            <kbd className="palette-hint-key" title="Open the command palette">⌘K</kbd>
            <div className="app-topbar-meta">Unified Capital Intelligence<br />v0.1.0</div>
          </div>
        </header>
        <main>{children}</main>
      </div>
    </div>
  );
}
