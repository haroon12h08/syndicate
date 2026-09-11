import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import * as organizationsApi from '../api/organizations';
import { ORG_ROLES, humanize } from '../constants';

export default function OrganizationPage() {
  const { id } = useParams();
  const [organization, setOrganization] = useState(null);
  const [members, setMembers] = useState([]);
  const [error, setError] = useState(null);
  const [form, setForm] = useState({ email: '', role: ORG_ROLES[2] });
  const [submitting, setSubmitting] = useState(false);

  async function load() {
    try {
      const [org, memberList] = await Promise.all([
        organizationsApi.getOrganization(id),
        organizationsApi.listMembers(id),
      ]);
      setOrganization(org);
      setMembers(memberList);
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function handleAddMember(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await organizationsApi.addMember(id, form);
      setForm({ email: '', role: ORG_ROLES[2] });
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (!organization) {
    return <div className="page">{error || 'Loading...'}</div>;
  }

  return (
    <div className="page">
      <h1>{organization.name}</h1>
      <p className="hint">{humanize(organization.type)}</p>

      <h2>Members</h2>
      <table className="data-table">
        <thead>
          <tr><th>Name</th><th>Email</th><th>Role</th></tr>
        </thead>
        <tbody>
          {members.map((m) => (
            <tr key={m.id}>
              <td>{m.user.fullName}</td>
              <td>{m.user.email}</td>
              <td>{m.role}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>Add member</h2>
      <p className="hint">The user must already have a Syndicate account.</p>
      {error && <div className="error-banner">{error}</div>}
      <form className="inline-form" onSubmit={handleAddMember}>
        <input
          type="email"
          placeholder="user@example.com"
          value={form.email}
          onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
          required
        />
        <select value={form.role} onChange={(e) => setForm((f) => ({ ...f, role: e.target.value }))}>
          {ORG_ROLES.map((r) => (
            <option key={r} value={r}>{r}</option>
          ))}
        </select>
        <button type="submit" disabled={submitting}>{submitting ? 'Adding...' : 'Add member'}</button>
      </form>
    </div>
  );
}
