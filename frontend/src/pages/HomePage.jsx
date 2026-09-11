import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import * as companiesApi from '../api/companies';
import * as transactionsApi from '../api/transactions';
import * as organizationsApi from '../api/organizations';
import { humanize } from '../constants';

export default function HomePage() {
  const { user } = useAuth();
  const [companies, setCompanies] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [organizations, setOrganizations] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([
      companiesApi.listCompanies(),
      transactionsApi.listMyTransactions(),
      organizationsApi.listOrganizations(),
    ])
      .then(([c, t, o]) => {
        setCompanies(c);
        setTransactions(t);
        setOrganizations(o);
      })
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div className="page">
      <div className="eyebrow">Welcome to Syndicate</div>
      <h1>Infrastructure for What's Next, {user?.fullName?.split(' ')[0]}</h1>
      <p className="hint">
        A unified platform to take companies from private to public, with structured
        data, collaborative workflows, and evidence-grounded facts.
      </p>

      <div className="form-actions" style={{ margin: '1rem 0' }}>
        <Link to="/companies"><button>View Companies →</button></Link>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="stat-grid">
        <div className="stat-tile">
          <div className="stat-value">{companies.length}</div>
          <div className="stat-label">Companies</div>
        </div>
        <div className="stat-tile">
          <div className="stat-value">{transactions.length}</div>
          <div className="stat-label">Active Transactions</div>
        </div>
        <div className="stat-tile">
          <div className="stat-value">{organizations.length}</div>
          <div className="stat-label">Your Organizations</div>
        </div>
      </div>

      <div className="page-header"><h2>Your transactions</h2></div>
      <ul className="entity-list">
        {transactions.map((t) => (
          <li key={t.id}>
            <Link to={`/transactions/${t.id}`}>{t.name}</Link>
            <span className="hint"> — {t.companyName} — {humanize(t.status)}</span>
          </li>
        ))}
        {transactions.length === 0 && <li className="hint">No transactions yet.</li>}
      </ul>

      <div className="page-header"><h2>Your companies</h2></div>
      <ul className="entity-list">
        {companies.map((c) => (
          <li key={c.id}>
            <Link to={`/companies/${c.id}`}>{c.legalName}</Link>
            <span className="hint"> — {c.ownerOrganizationName}</span>
          </li>
        ))}
        {companies.length === 0 && <li className="hint">No companies yet.</li>}
      </ul>
    </div>
  );
}
