import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as companiesApi from '../api/companies';
import * as organizationsApi from '../api/organizations';
import * as transactionsApi from '../api/transactions';
import { TRANSACTION_ROLES, TRANSACTION_TYPES, humanize } from '../constants';

export default function CompanyDetailPage() {
  const { id } = useParams();
  const [company, setCompany] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [organizations, setOrganizations] = useState([]);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    name: '',
    type: TRANSACTION_TYPES[0],
    leadOrganizationId: '',
    creatorRole: TRANSACTION_ROLES[0],
  });

  async function load() {
    try {
      const [companyData, txns, orgs] = await Promise.all([
        companiesApi.getCompany(id),
        transactionsApi.listTransactionsForCompany(id),
        organizationsApi.listOrganizations(),
      ]);
      setCompany(companyData);
      setTransactions(txns);
      setOrganizations(orgs);
      setForm((f) => ({ ...f, leadOrganizationId: f.leadOrganizationId || companyData.ownerOrganizationId }));
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleCreate(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await transactionsApi.createTransaction(id, form);
      setShowForm(false);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (!company) {
    return <div className="page">{error || 'Loading...'}</div>;
  }

  return (
    <div className="page">
      <h1>{company.legalName}</h1>
      <div className="detail-grid">
        <div><strong>CIN</strong><span>{company.cin || '—'}</span></div>
        <div><strong>PAN</strong><span>{company.pan || '—'}</span></div>
        <div><strong>Constitution</strong><span>{company.constitution ? humanize(company.constitution) : '—'}</span></div>
        <div><strong>Incorporated</strong><span>{company.incorporationDate || '—'}</span></div>
        <div><strong>Registered office</strong><span>{company.registeredOffice || '—'}</span></div>
        <div><strong>Owner organization</strong><span><Link to={`/organizations/${company.ownerOrganizationId}`}>{company.ownerOrganizationName}</Link></span></div>
      </div>

      <div className="page-header">
        <h2>Transactions</h2>
        <button onClick={() => setShowForm((s) => !s)}>{showForm ? 'Cancel' : 'New transaction'}</button>
      </div>
      {error && <div className="error-banner">{error}</div>}

      {showForm && (
        <form className="card-form" onSubmit={handleCreate}>
          <label>
            Name
            <input value={form.name} onChange={(e) => update('name', e.target.value)} required />
          </label>
          <label>
            Type
            <select value={form.type} onChange={(e) => update('type', e.target.value)}>
              {TRANSACTION_TYPES.map((t) => (
                <option key={t} value={t}>{humanize(t)}</option>
              ))}
            </select>
          </label>
          <label>
            Lead organization
            <select
              value={form.leadOrganizationId}
              onChange={(e) => update('leadOrganizationId', e.target.value)}
              required
            >
              <option value="" disabled>Select an organization you belong to</option>
              {organizations.map((o) => (
                <option key={o.id} value={o.id}>{o.name}</option>
              ))}
            </select>
          </label>
          <label>
            Your role on this transaction
            <select value={form.creatorRole} onChange={(e) => update('creatorRole', e.target.value)}>
              {TRANSACTION_ROLES.map((r) => (
                <option key={r} value={r}>{humanize(r)}</option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={submitting}>{submitting ? 'Creating...' : 'Create transaction'}</button>
        </form>
      )}

      <ul className="entity-list">
        {transactions.map((t) => (
          <li key={t.id}>
            <Link to={`/transactions/${t.id}`}>{t.name}</Link>
            <span className="hint"> — {humanize(t.status)}</span>
          </li>
        ))}
        {transactions.length === 0 && <li className="hint">No transactions yet.</li>}
      </ul>
    </div>
  );
}
