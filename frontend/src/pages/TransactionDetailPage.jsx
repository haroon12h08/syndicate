import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import * as transactionsApi from '../api/transactions';
import * as workstreamsApi from '../api/workstreams';
import * as invitationsApi from '../api/invitations';
import * as readinessApi from '../api/readiness';
import * as tasksApi from '../api/tasks';
import * as drhpApi from '../api/drhp';
import * as evidenceApi from '../api/evidence';
import ReadinessPanel from '../components/ReadinessPanel';
import TaskBoard from '../components/TaskBoard';
import DrhpPanel from '../components/DrhpPanel';
import DocumentsPanel from '../components/DocumentsPanel';
import AuditTimeline from '../components/AuditTimeline';
import ProvenanceDrawer from '../components/ProvenanceDrawer';
import { TRANSACTION_ROLES, TRANSACTION_STATUSES, WORKSTREAM_TYPES, humanize } from '../constants';

const SIGNER_ROLES = ['ISSUER_ADMIN', 'LEAD_BANKER'];

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'documents', label: 'Documents' },
  { key: 'tasks', label: 'Tasks' },
  { key: 'drhp', label: 'DRHP' },
  { key: 'team', label: 'Team' },
  { key: 'audit', label: 'Audit' },
];

function invitationBadgeClass(inv) {
  if (inv.status === 'PENDING' && inv.expiresAt && new Date(inv.expiresAt) < new Date()) return 'badge badge-failed';
  if (inv.status === 'PENDING') return 'badge badge-pending';
  if (inv.status === 'ACCEPTED') return 'badge badge-complete';
  return 'badge badge-not_applicable';
}

export default function TransactionDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [transaction, setTransaction] = useState(null);
  const [memberships, setMemberships] = useState([]);
  const [workstreams, setWorkstreams] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [approvalStatus, setApprovalStatus] = useState(null);
  const [readiness, setReadiness] = useState(null);
  const [evaluating, setEvaluating] = useState(false);
  const [tasks, setTasks] = useState([]);
  const [disclosures, setDisclosures] = useState([]);
  const [drhpDocument, setDrhpDocument] = useState(null);
  const [compileResult, setCompileResult] = useState(null);
  const [compiling, setCompiling] = useState(false);
  const [evidence, setEvidence] = useState([]);
  const [tab, setTab] = useState('overview');
  const [auditEvents, setAuditEvents] = useState([]);
  const [provenance, setProvenance] = useState(null);
  const [error, setError] = useState(null);

  const [inviteMode, setInviteMode] = useState('individual');
  const [inviteForm, setInviteForm] = useState({ targetOrganizationId: '', targetEmail: '', role: TRANSACTION_ROLES[0], workstreamId: '' });
  const [inviteSubmitting, setInviteSubmitting] = useState(false);
  const [showInviteForm, setShowInviteForm] = useState(false);

  const [wsForm, setWsForm] = useState({ type: WORKSTREAM_TYPES[0], description: '' });
  const [wsSubmitting, setWsSubmitting] = useState(false);
  const [showWsForm, setShowWsForm] = useState(false);

  async function load() {
    try {
      const [txn, members, ws, invites] = await Promise.all([
        transactionsApi.getTransaction(id),
        transactionsApi.listMemberships(id),
        workstreamsApi.listWorkstreams(id),
        invitationsApi.listTransactionInvitations(id).catch(() => []),
      ]);
      readinessApi.getReadiness(id).then(setReadiness).catch(() => setReadiness(null));
      tasksApi.listTasks(id).then(setTasks).catch(() => setTasks([]));
      drhpApi.listDisclosures(id).then(setDisclosures).catch(() => setDisclosures([]));
      drhpApi.getLatestDrhp(id).then(setDrhpDocument).catch(() => setDrhpDocument(null));
      evidenceApi.listTransactionEvidence(id).then(setEvidence).catch(() => setEvidence([]));
      transactionsApi.listAudit(id).then(setAuditEvents).catch(() => setAuditEvents([]));
      setTransaction(txn);
      setMemberships(members);
      setWorkstreams(ws);
      setInvitations(invites);
      if (txn.status === 'DRAFT') {
        invitationsApi.getApprovalStatus(id, 'DRAFT_TO_ACTIVE').then(setApprovalStatus).catch(() => setApprovalStatus(null));
      } else {
        setApprovalStatus(null);
      }
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // Extraction runs on a queue, so poll while anything is still in flight.
  useEffect(() => {
    const inFlight = evidence.some(
      (e) => e.processingStatus === 'PENDING' || e.processingStatus === 'PROCESSING'
    );
    if (!inFlight) return undefined;
    const timer = setInterval(() => {
      evidenceApi.listTransactionEvidence(id).then(setEvidence).catch(() => {});
    }, 2500);
    return () => clearInterval(timer);
  }, [evidence, id]);

  const myMembership = memberships.find((m) => m.user.id === user?.id);
  const canSign = myMembership && SIGNER_ROLES.includes(myMembership.role);
  const alreadySigned = approvalStatus?.signatures.some((s) => s.signedBy.id === user?.id);

  async function handleEvaluateReadiness() {
    setError(null);
    setEvaluating(true);
    try {
      setReadiness(await readinessApi.evaluateReadiness(id));
    } catch (err) {
      setError(err.message);
    } finally {
      setEvaluating(false);
    }
  }

  async function handleUploadEvidence(workstreamId, file, documentType) {
    setError(null);
    try {
      await evidenceApi.uploadEvidence(workstreamId, file, documentType);
      setEvidence(await evidenceApi.listTransactionEvidence(id));
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDeleteEvidence(evidenceId, fileName) {
    if (!window.confirm(`Delete "${fileName}"? This also removes its candidate facts.`)) return;
    setError(null);
    try {
      await evidenceApi.deleteEvidence(evidenceId);
      setEvidence(await evidenceApi.listTransactionEvidence(id));
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleReprocessEvidence(evidenceId) {
    setError(null);
    try {
      await evidenceApi.reprocessEvidence(evidenceId);
      setEvidence(await evidenceApi.listTransactionEvidence(id));
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleLeave() {
    if (!window.confirm('Leave this transaction? You will lose access to its workspace.')) return;
    setError(null);
    try {
      await transactionsApi.leaveTransaction(id);
      navigate('/home');
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDeleteTransaction() {
    const typed = window.prompt(
      `This permanently deletes "${transaction.name}" and all its documents, facts, tasks and DRHP versions.\n\nType the transaction name to confirm:`);
    if (typed !== transaction.name) {
      if (typed !== null) setError('Name did not match — deletion cancelled.');
      return;
    }
    setError(null);
    try {
      await transactionsApi.deleteTransaction(id);
      navigate('/home');
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleInspectProvenance(documentId) {
    setError(null);
    try {
      setProvenance(await drhpApi.getProvenance(documentId));
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleCompileDrhp(mode) {
    setError(null);
    setCompiling(true);
    try {
      const result = await drhpApi.compileDrhp(id, mode);
      setCompileResult(result);
      if (result.compiled) {
        setDrhpDocument(result.document);
      }
      setDisclosures(await drhpApi.listDisclosures(id));
    } catch (err) {
      setError(err.message);
    } finally {
      setCompiling(false);
    }
  }

  async function handleCreateDisclosure(payload) {
    setError(null);
    try {
      await drhpApi.createDisclosure(id, payload);
      setDisclosures(await drhpApi.listDisclosures(id));
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleUpdateDisclosure(disclosureId, payload) {
    setError(null);
    try {
      await drhpApi.updateDisclosure(disclosureId, payload);
      setDisclosures(await drhpApi.listDisclosures(id));
    } catch (err) {
      setError(err.message);
    }
  }

  async function patchTask(taskId, payload) {
    setError(null);
    try {
      const updated = await tasksApi.updateTask(taskId, payload);
      setTasks((list) => list.map((t) => (t.id === updated.id ? updated : t)));
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleStatusChange(status) {
    setError(null);
    try {
      await transactionsApi.updateTransactionStatus(id, { status });
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleSignApproval() {
    setError(null);
    try {
      await invitationsApi.signApproval(id, { transition: 'DRAFT_TO_ACTIVE', comment: '' });
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleOffboard(membershipId) {
    setError(null);
    try {
      await transactionsApi.removeMembership(id, membershipId);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleSendInvite(e) {
    e.preventDefault();
    setError(null);
    setInviteSubmitting(true);
    try {
      await invitationsApi.sendTransactionInvitation(id, {
        targetOrganizationId: inviteForm.targetOrganizationId,
        targetEmail: inviteMode === 'individual' ? inviteForm.targetEmail : null,
        role: inviteForm.role,
        workstreamId: inviteForm.workstreamId || null,
      });
      setInviteForm({ targetOrganizationId: '', targetEmail: '', role: TRANSACTION_ROLES[0], workstreamId: '' });
      setShowInviteForm(false);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setInviteSubmitting(false);
    }
  }

  async function handleRevokeInvite(invitationId) {
    setError(null);
    try {
      await invitationsApi.revokeTransactionInvitation(id, invitationId);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  function copyInviteLink(token) {
    const url = `${window.location.origin}/invite/${token}`;
    navigator.clipboard?.writeText(url);
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
      <div className="page-header">
        <h1>{transaction.name}</h1>
        <div className="inline-form">
          <button className="secondary" onClick={handleLeave}>Leave transaction</button>
          {myMembership?.role === 'ISSUER_ADMIN' && (
            <button className="danger" onClick={handleDeleteTransaction}>Delete transaction</button>
          )}
        </div>
      </div>
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

      <nav className="tab-strip">
        {TABS.map((t) => (
          <button
            key={t.key}
            className={`tab-button${tab === t.key ? ' active' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
            {t.key === 'documents' && evidence.length > 0 && <span className="tab-count">{evidence.length}</span>}
            {t.key === 'tasks' && tasks.length > 0 && <span className="tab-count">{tasks.length}</span>}
          </button>
        ))}
      </nav>

      {tab === 'overview' && approvalStatus && (
        <div className="detail-grid">
          <div>
            <strong>Approval to activate</strong>
            <span>
              {approvalStatus.signatures.map((s) => `${humanize(s.requiredRole)} ✓ ${s.signedBy.fullName}`).join(' · ') || 'No sign-offs yet'}
              {!approvalStatus.satisfied && (
                <> — awaiting: {approvalStatus.requiredRoles.filter((r) => !approvalStatus.signatures.some((s) => s.requiredRole === r)).map(humanize).join(', ')}</>
              )}
            </span>
          </div>
          {canSign && !alreadySigned && !approvalStatus.satisfied && (
            <div style={{ alignSelf: 'end' }}>
              <button onClick={handleSignApproval}>Sign off as {humanize(myMembership.role)}</button>
            </div>
          )}
        </div>
      )}

      {tab === 'overview' && workstreams.length === 0 && (
        <section className="quickstart">
          <h2>Next steps</h2>
          <ol className="quickstart-steps">
            <li>
              <strong>Create your workstreams</strong>
              <span className="hint">
                Financial due diligence, capital structure, litigation — documents and facts
                are filed against these.
              </span>
              <button onClick={() => setShowWsForm(true)}>New workstream →</button>
            </li>
            <li>
              <strong>Upload the source documents</strong>
              <span className="hint">Parsed into candidate facts with page anchors for review.</span>
              <button className="secondary" onClick={() => setTab('documents')}>Go to documents</button>
            </li>
            <li>
              <strong>Invite your advisors</strong>
              <span className="hint">Auditor, legal counsel, merchant banker.</span>
              <button className="secondary" onClick={() => { setTab('team'); setShowInviteForm(true); }}>
                Invite participant
              </button>
            </li>
          </ol>
        </section>
      )}

      {tab === 'overview' && (
        <ReadinessPanel
          readiness={readiness}
          onEvaluate={handleEvaluateReadiness}
          evaluating={evaluating}
        />
      )}

      {tab === 'documents' && (
        <DocumentsPanel
          evidence={evidence}
          workstreams={workstreams}
          onUpload={handleUploadEvidence}
          onDelete={handleDeleteEvidence}
          onReprocess={handleReprocessEvidence}
          onDownload={(evidenceId) => evidenceApi.downloadEvidence(evidenceId)}
          onCreateWorkstream={() => { setTab('overview'); setShowWsForm(true); }}
        />
      )}

      {tab === 'tasks' && (
      <TaskBoard
        tasks={tasks}
        members={memberships}
        onMove={(taskId, status) => patchTask(taskId, { status })}
        onAssign={(taskId, assignedUserId) => patchTask(taskId, { assignedUserId })}
        onResolutionNote={(taskId, resolutionNote) => patchTask(taskId, { resolutionNote })}
        onInviteAdvisor={() => {
          setTab('team');
          setShowInviteForm(true);
        }}
      />
      )}

      {tab === 'drhp' && (
      <DrhpPanel
        disclosures={disclosures}
        document={drhpDocument}
        compileResult={compileResult}
        compiling={compiling}
        onCompile={handleCompileDrhp}
        onCreateDisclosure={handleCreateDisclosure}
        onUpdateDisclosure={handleUpdateDisclosure}
        onInspectProvenance={handleInspectProvenance}
      />
      )}

      {tab === 'audit' && <AuditTimeline events={auditEvents} />}

      {provenance && (
        <ProvenanceDrawer provenance={provenance} onClose={() => setProvenance(null)} />
      )}

      {tab === 'team' && (
      <>
      <div className="page-header"><h2>Team &amp; Advisory</h2></div>

      <h3 className="eyebrow" style={{ marginTop: '1rem' }}>Members</h3>
      <table className="data-table">
        <thead>
          <tr><th>Name</th><th>Organization</th><th>Role</th><th></th></tr>
        </thead>
        <tbody>
          {memberships.map((m) => (
            <tr key={m.id}>
              <td>{m.user.fullName} ({m.user.email})</td>
              <td>{m.organizationName}</td>
              <td>{humanize(m.role)}</td>
              <td>
                {myMembership && (myMembership.role === 'ISSUER_ADMIN' || myMembership.role === 'LEAD_BANKER') && (
                  <button className="secondary" onClick={() => handleOffboard(m.id)}>Offboard</button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="page-header">
        <h3 className="eyebrow">Pending invitations</h3>
        <button onClick={() => setShowInviteForm((s) => !s)}>{showInviteForm ? 'Cancel' : 'Invite participant'}</button>
      </div>

      {showInviteForm && (
        <form className="card-form" onSubmit={handleSendInvite}>
          <div className="inline-form">
            <button type="button" className={inviteMode === 'individual' ? '' : 'secondary'} onClick={() => setInviteMode('individual')}>
              Invite Individual Email
            </button>
            <button type="button" className={inviteMode === 'organization' ? '' : 'secondary'} onClick={() => setInviteMode('organization')}>
              Invite Advisory Organization
            </button>
          </div>
          <label>
            Organization ID {inviteMode === 'individual' ? '(the org this person represents)' : '(the org being invited)'}
            <input
              value={inviteForm.targetOrganizationId}
              onChange={(e) => setInviteForm((f) => ({ ...f, targetOrganizationId: e.target.value }))}
              required
            />
          </label>
          {inviteMode === 'individual' && (
            <label>
              Email
              <input
                type="email"
                value={inviteForm.targetEmail}
                onChange={(e) => setInviteForm((f) => ({ ...f, targetEmail: e.target.value }))}
                required
              />
            </label>
          )}
          <label>
            Role
            <select value={inviteForm.role} onChange={(e) => setInviteForm((f) => ({ ...f, role: e.target.value }))}>
              {TRANSACTION_ROLES.map((r) => <option key={r} value={r}>{humanize(r)}</option>)}
            </select>
          </label>
          <label>
            Workstream (optional)
            <select value={inviteForm.workstreamId} onChange={(e) => setInviteForm((f) => ({ ...f, workstreamId: e.target.value }))}>
              <option value="">Not workstream-specific</option>
              {workstreams.map((w) => <option key={w.id} value={w.id}>{humanize(w.type)}</option>)}
            </select>
          </label>
          <button type="submit" disabled={inviteSubmitting}>{inviteSubmitting ? 'Sending...' : 'Send invitation'}</button>
        </form>
      )}

      <table className="data-table">
        <thead>
          <tr><th>Target</th><th>Role</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>
          {invitations.map((inv) => (
            <tr key={inv.id}>
              <td>{inv.organizationInvite ? `${inv.organizationName} (organization)` : `${inv.inviteeEmail} — ${inv.organizationName}`}</td>
              <td>{humanize(inv.role)}</td>
              <td><span className={invitationBadgeClass(inv)}>{humanize(inv.status)}</span></td>
              <td>
                {inv.status === 'PENDING' && (
                  <>
                    <button onClick={() => copyInviteLink(inv.token)}>Copy Invite Link</button>
                    <button className="secondary" onClick={() => handleRevokeInvite(inv.id)}>Revoke</button>
                  </>
                )}
              </td>
            </tr>
          ))}
          {invitations.length === 0 && <tr><td colSpan={4} className="hint">No invitations yet.</td></tr>}
        </tbody>
      </table>

      </>
      )}

      {tab === 'overview' && (
      <>
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
        {workstreams.length === 0 && (
          <li className="empty-state">
            <span>No workstreams yet. Documents and facts are filed against a workstream.</span>
            <button className="secondary" onClick={() => setShowWsForm(true)}>New workstream</button>
          </li>
        )}
      </ul>
      </>
      )}
    </div>
  );
}
