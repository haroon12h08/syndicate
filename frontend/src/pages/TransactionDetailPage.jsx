import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as transactionsApi from '../api/transactions';
import * as workstreamsApi from '../api/workstreams';
import { TRANSACTION_ROLES, TRANSACTION_STATUSES, WORKSTREAM_TYPES, humanize } from '../constants';

export default function TransactionDetailPage() {
  const { id } = useParams();
  const [transaction, setTransaction] = useState(null);
  const [memberships, setMemberships] = useState([]);
  const [workstreams, setWorkstreams] = useState([]);
  const [error, setError] = useState(null);

  const [memberForm, setMemberForm] = useState({ organizationId: '', email: '', role: TRANSACTION_ROLES[0] });
  const [memberSubmitting, setMemberSubmitting] = useState(false);
  const [showMemberForm, setShowMemberForm] = useState(false);

  const [wsForm, setWsForm] = useState({ type: WORKSTREAM_TYPES[0], description: '' });
  const [wsSubmitting, setWsSubmitting] = useState(false);
  const [showWsForm, setShowWsForm] = useState(false);

  async function load() {
    try {
      const [txn, members, ws] = await Promise.all([
        transactionsApi.getTransaction(id),
        transactionsApi.listMemberships(id),
        workstreamsApi.listWorkstreams(id),
      ]);
      setTransaction(txn);
      setMemberships(members);
      setWorkstreams(ws);
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function handleStatusChange(status) {
    setError(null);
    try {
      await transactionsApi.updateTransactionStatus(id, { status });
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleAddMember(e) {
    e.preventDefault();
    setError(null);
    setMemberSubmitting(true);
    try {
      await transactionsApi.addMembership(id, memberForm);
      setMemberForm({ organizationId: '', email: '', role: TRANSACTION_ROLES[0] });
      setShowMemberForm(false);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setMemberSubmitting(false);
    }
  }

  async function handleCreateWorkstream(e) {
    e.preventDefault();
    setError(null);
    setWsSubmitting(true);
    try {
      await workstreamsApi.createWorkstream(id, wsForm);
      setWsForm({ type: WORKSTREAM_TYPES[0], description: '' });
      setShowWsForm(false);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setWsSubmitting(false);
    }
  }

  if (!transaction) {
    return <div className="page">{error || 'Loading...'}</div>;
  }

  return (
    <div className="page">
      <h1>{transaction.name}</h1>
      <div className="detail-grid">
        <div><strong>Company</strong><span>{transaction.companyName}</span></div>
        <div><strong>Lead organization</strong><span><Link to={`/organizations/${transaction.leadOrganizationId}`}>{transaction.leadOrganizationName}</Link></span></div>
        <div><strong>Type</strong><span>{humanize(transaction.type)}</span></div>
        <div>
          <strong>Status</strong>
          <select value={transaction.status} onChange={(e) => handleStatusChange(e.target.value)}>
            {TRANSACTION_STATUSES.map((s) => (
              <option key={s} value={s}>{humanize(s)}</option>
            ))}
          </select>
        </div>
      </div>
      {error && <div className="error-banner">{error}</div>}

      <div className="page-header">
        <h2>Participants</h2>
        <button onClick={() => setShowMemberForm((s) => !s)}>{showMemberForm ? 'Cancel' : 'Add participant'}</button>
      </div>
      {showMemberForm && (
        <form className="card-form" onSubmit={handleAddMember}>
          <label>
            Organization ID
            <input
              value={memberForm.organizationId}
              onChange={(e) => setMemberForm((f) => ({ ...f, organizationId: e.target.value }))}
              placeholder="organization the user belongs to"
              required
            />
          </label>
          <label>
            User email
            <input
              type="email"
              value={memberForm.email}
              onChange={(e) => setMemberForm((f) => ({ ...f, email: e.target.value }))}
              required
            />
          </label>
          <label>
            Role
            <select value={memberForm.role} onChange={(e) => setMemberForm((f) => ({ ...f, role: e.target.value }))}>
              {TRANSACTION_ROLES.map((r) => (
                <option key={r} value={r}>{humanize(r)}</option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={memberSubmitting}>{memberSubmitting ? 'Adding...' : 'Add participant'}</button>
        </form>
      )}
      <table className="data-table">
        <thead>
          <tr><th>Name</th><th>Organization</th><th>Role</th></tr>
        </thead>
        <tbody>
          {memberships.map((m) => (
            <tr key={m.id}>
              <td>{m.user.fullName} ({m.user.email})</td>
              <td>{m.organizationName}</td>
              <td>{humanize(m.role)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="page-header">
        <h2>Workstreams</h2>
        <button onClick={() => setShowWsForm((s) => !s)}>{showWsForm ? 'Cancel' : 'New workstream'}</button>
      </div>
      {showWsForm && (
        <form className="card-form" onSubmit={handleCreateWorkstream}>
          <label>
            Type
            <select value={wsForm.type} onChange={(e) => setWsForm((f) => ({ ...f, type: e.target.value }))}>
              {WORKSTREAM_TYPES.map((t) => (
                <option key={t} value={t}>{humanize(t)}</option>
              ))}
            </select>
          </label>
          <label>
            Description
            <input value={wsForm.description} onChange={(e) => setWsForm((f) => ({ ...f, description: e.target.value }))} />
          </label>
          <button type="submit" disabled={wsSubmitting}>{wsSubmitting ? 'Creating...' : 'Create workstream'}</button>
        </form>
      )}
      <ul className="entity-list">
        {workstreams.map((w) => (
          <li key={w.id}>
            <Link to={`/workstreams/${w.id}`}>{humanize(w.type)}</Link>
            {w.description && <span className="hint"> — {w.description}</span>}
          </li>
        ))}
        {workstreams.length === 0 && <li className="hint">No workstreams yet.</li>}
      </ul>
    </div>
  );
}
