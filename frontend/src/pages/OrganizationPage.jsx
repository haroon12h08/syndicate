import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import * as organizationsApi from '../api/organizations';
import * as invitationsApi from '../api/invitations';
import { ORG_ROLES, humanize } from '../constants';

function invitationBadgeClass(inv) {
  if (inv.status === 'PENDING' && inv.expiresAt && new Date(inv.expiresAt) < new Date()) return 'badge badge-failed';
  if (inv.status === 'PENDING') return 'badge badge-pending';
  if (inv.status === 'ACCEPTED') return 'badge badge-complete';
  return 'badge badge-not_applicable';
}

export default function OrganizationPage() {
  const { id } = useParams();
  const [organization, setOrganization] = useState(null);
  const [members, setMembers] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [error, setError] = useState(null);
  const [form, setForm] = useState({ email: '', role: ORG_ROLES[2] });
  const [submitting, setSubmitting] = useState(false);

  async function load() {
    try {
      const [org, memberList, invites] = await Promise.all([
        organizationsApi.getOrganization(id),
        organizationsApi.listMembers(id),
        invitationsApi.listOrganizationInvitations(id).catch(() => []),
      ]);
      setOrganization(org);
      setMembers(memberList);
      setInvitations(invites);
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function handleInvite(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await invitationsApi.sendOrganizationInvitation(id, form);
      setForm({ email: '', role: ORG_ROLES[2] });
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRevoke(invitationId) {
    setError(null);
    try {
      await invitationsApi.revokeOrganizationInvitation(id, invitationId);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  function copyInviteLink(token) {
    const url = `${window.location.origin}/invite/${token}`;
    navigator.clipboard?.writeText(url);
  }

  if (!organization) {
    return <div className="page">{error || 'Loading...'}</div>;
  }

  return (
    <div className="page">
      <h1>{organization.name}</h1>
      <p className="hint">{humanize(organization.type)}</p>
      {error && <div className="error-banner">{error}</div>}

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

      <h2>Invite member</h2>
      <p className="hint">Works even if they don't have a Syndicate account yet.</p>
      <form className="inline-form" onSubmit={handleInvite}>
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
        <button type="submit" disabled={submitting}>{submitting ? 'Sending...' : 'Send invite'}</button>
      </form>

      <div className="page-header"><h2>Pending invitations</h2></div>
      <table className="data-table">
        <thead>
          <tr><th>Email</th><th>Role</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>
          {invitations.map((inv) => (
            <tr key={inv.id}>
              <td>{inv.inviteeEmail}</td>
              <td>{inv.role}</td>
              <td><span className={invitationBadgeClass(inv)}>{humanize(inv.status)}</span></td>
              <td>
                {inv.status === 'PENDING' && (
                  <>
                    <button onClick={() => copyInviteLink(inv.token)}>Copy Invite Link</button>
                    <button className="secondary" onClick={() => handleRevoke(inv.id)}>Revoke</button>
                  </>
                )}
              </td>
            </tr>
          ))}
          {invitations.length === 0 && <tr><td colSpan={4} className="hint">No invitations yet.</td></tr>}
        </tbody>
      </table>
    </div>
  );
}
