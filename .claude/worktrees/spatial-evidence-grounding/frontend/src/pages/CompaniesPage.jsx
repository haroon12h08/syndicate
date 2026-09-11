import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as companiesApi from '../api/companies';
import * as organizationsApi from '../api/organizations';
import { COMPANY_CONSTITUTIONS, humanize } from '../constants';

const emptyForm = {
  legalName: '',
  cin: '',
  pan: '',
  registeredOffice: '',
  incorporationDate: '',
  constitution: COMPANY_CONSTITUTIONS[0],
  ownerOrganizationId: '',
};

export default function CompaniesPage() {
  const [companies, setCompanies] = useState([]);
  const [organizations, setOrganizations] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [showForm, setShowForm] = useState(false);

  async function load() {
    try {
      const [companyList, orgList] = await Promise.all([
        companiesApi.listCompanies(),
        organizationsApi.listOrganizations(),
      ]);
      setCompanies(companyList);
      setOrganizations(orgList);
      setForm((f) => ({ ...f, ownerOrganizationId: f.ownerOrganizationId || orgList[0]?.id || '' }));
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleCreate(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await companiesApi.createCompany({ ...form, cin: form.cin || null, pan: form.pan || null, incorporationDate: form.incorporationDate || null });
      setForm({ ...emptyForm, ownerOrganizationId: form.ownerOrganizationId });
      setShowForm(false);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>Companies</h1>
        <button onClick={() => setShowForm((s) => !s)}>{showForm ? 'Cancel' : 'New company'}</button>
      </div>
      {error && <div className="error-banner">{error}</div>}

      {showForm && (
        <form className="card-form" onSubmit={handleCreate}>
          <label>
            Legal name
            <input value={form.legalName} onChange={(e) => update('legalName', e.target.value)} required />
          </label>
          <label>
            Owner organization
            <select value={form.ownerOrganizationId} onChange={(e) => update('ownerOrganizationId', e.target.value)} required>
              {organizations.map((o) => (
                <option key={o.id} value={o.id}>{o.name}</option>
              ))}
            </select>
          </label>
          <label>
            CIN
            <input value={form.cin} onChange={(e) => update('cin', e.target.value)} />
          </label>
          <label>
            PAN
            <input value={form.pan} onChange={(e) => update('pan', e.target.value)} />
          </label>
          <label>
            Registered office
            <input value={form.registeredOffice} onChange={(e) => update('registeredOffice', e.target.value)} />
          </label>
          <label>
            Incorporation date
            <input type="date" value={form.incorporationDate} onChange={(e) => update('incorporationDate', e.target.value)} />
          </label>
          <label>
            Constitution
            <select value={form.constitution} onChange={(e) => update('constitution', e.target.value)}>
              {COMPANY_CONSTITUTIONS.map((c) => (
                <option key={c} value={c}>{humanize(c)}</option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={submitting}>{submitting ? 'Creating...' : 'Create company'}</button>
        </form>
      )}

      <ul className="entity-list">
        {companies.map((c) => (
          <li key={c.id}>
            <Link to={`/companies/${c.id}`}>{c.legalName}</Link>
            <span className="hint"> — {c.ownerOrganizationName}</span>
          </li>
        ))}
        {companies.length === 0 && <li className="hint">No companies yet.</li>}
      </ul>

      <h2>Your organizations</h2>
      <ul className="entity-list">
        {organizations.map((o) => (
          <li key={o.id}>
            <Link to={`/organizations/${o.id}`}>{o.name}</Link>
            <span className="hint"> — {humanize(o.type)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
