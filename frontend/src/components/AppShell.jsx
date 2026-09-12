import { Link, useLocation } from 'react-router-dom';
import { useBreadcrumbs } from '../context/BreadcrumbContext';
import { useAuth } from '../context/AuthContext';

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
          <div className="app-sidebar-user">
            <strong>{user?.fullName}</strong>
          </div>
          <button className="secondary" onClick={logout}>Log out</button>
        </div>
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
