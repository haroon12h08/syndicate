import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import * as companiesApi from '../api/companies';
import * as transactionsApi from '../api/transactions';
import StartTransactionCard from '../components/StartTransactionCard';
import * as organizationsApi from '../api/organizations';
import * as invitationsApi from '../api/invitations';
import { humanize } from '../constants';

export default function HomePage() {
  const { user } = useAuth();
  const [companies, setCompanies] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [organizations, setOrganizations] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([
      companiesApi.listCompanies(),
      transactionsApi.listMyTransactions(),
      organizationsApi.listOrganizations(),
      invitationsApi.listMyInvitations().catch(() => []),
    ])
      .then(([c, t, o, i]) => {
        setCompanies(c);
        setTransactions(t);
        setOrganizations(o);
        setInvitations(i);
      })
      .catch((err) => setError(err.message));
  }, []);

  const firstName = user?.fullName?.split(' ')[0] || 'there';
  const isNew = companies.length === 0 && transactions.length === 0;

  return (
    <div className="page">
      <div className="eyebrow">Welcome to Syndicate</div>
      <h1>{isNew ? `Let's get started, ${firstName}` : `Welcome back, ${firstName}`}</h1>
      <p className="hint page-intro">
        Upload what the company gives you, confirm what it establishes, and Syndicate keeps the
        transaction honest: contradictions surface, nothing unverified reaches the document, and
        every figure can be traced back to the page it came from.
      </p>

      {error && <div className="error-banner">{error}</div>}

      {invitations.length > 0 && (
        <div className="callout">
          <div>
            <strong>You have {invitations.length} pending invitation{invitations.length > 1 ? 's' : ''}.</strong>
            <div className="hint">Accept one to join an existing transaction workspace.</div>
          </div>
          <Link to="/invitations"><button>Review invitations</button></Link>
        </div>
      )}

      {isNew ? (
        <StartTransactionCard />
      ) : (
        <>
          <StartTransactionCard compact />
          <div className="form-actions page-actions">
            <Link to="/companies"><button className="secondary">Companies</button></Link>
            <Link to="/organizations"><button className="secondary">Organizations</button></Link>
          </div>
        </>
      )}

      <div className="stat-grid">
        <Link className="stat-tile" to="/companies">
          <div className="stat-value">{companies.length}</div>
          <div className="stat-label">Companies</div>
        </Link>
        <div className="stat-tile">
          <div className="stat-value">{transactions.length}</div>
          <div className="stat-label">Your transactions</div>
        </div>
        <Link className="stat-tile" to="/organizations">
          <div className="stat-value">{organizations.length}</div>
          <div className="stat-label">Your organizations</div>
        </Link>
      </div>

      <div className="page-header"><h2>Your transactions</h2></div>
      <ul className="entity-list">
        {transactions.map((t) => (
          <li key={t.id}>
            <Link to={`/transactions/${t.id}`}>{t.name}</Link>
            <span className="hint"> — {t.companyName} — {humanize(t.status)}</span>
          </li>
        ))}
        {transactions.length === 0 && (
          <li className="empty-state">
            <span>No transactions yet. Create a company first, then open a transaction on it.</span>
            <Link to="/companies"><button className="secondary">Go to companies</button></Link>
          </li>
        )}
      </ul>

      <div className="page-header"><h2>Your companies</h2></div>
      <ul className="entity-list">
        {companies.map((c) => (
          <li key={c.id}>
            <Link to={`/companies/${c.id}`}>{c.legalName}</Link>
            <span className="hint"> — {c.ownerOrganizationName}</span>
          </li>
        ))}
        {companies.length === 0 && (
          <li className="empty-state">
            <span>No companies yet.</span>
            <Link to="/companies"><button className="secondary">Add a company</button></Link>
          </li>
        )}
      </ul>
    </div>
  );
}
