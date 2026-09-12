import { Fragment, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useBreadcrumbs } from '../context/BreadcrumbContext';
import * as workstreamsApi from '../api/workstreams';
import * as factsApi from '../api/facts';
import * as evidenceApi from '../api/evidence';
import * as issuesApi from '../api/issues';
import * as candidateFactsApi from '../api/candidateFacts';
import FactForm from '../components/FactForm';
import EvidenceUploadForm from '../components/EvidenceUploadForm';
import IssueForm from '../components/IssueForm';
import CandidateFactCard from '../components/CandidateFactCard';
import { ISSUE_SEVERITIES, ISSUE_STATUSES, humanize } from '../constants';

function IssueRow({ issue, onUpdate }) {
  const [status, setStatus] = useState(issue.status);
  const [severity, setSeverity] = useState(issue.severity);
  const [resolution, setResolution] = useState(issue.resolution || '');
  const [saving, setSaving] = useState(false);

  const dirty = status !== issue.status || severity !== issue.severity || resolution !== (issue.resolution || '');

  async function save() {
    setSaving(true);
    try {
      await onUpdate(issue.id, {
        title: issue.title,
        description: issue.description,
        severity,
        status,
        ownerUserId: issue.owner?.id || null,
        dueDate: issue.dueDate,
        resolution: resolution || null,
      });
    } finally {
      setSaving(false);
    }
  }

  return (
    <tr>
      <td>
        <strong>{issue.title}</strong>
        {issue.description && <div className="hint">{issue.description}</div>}
        {issue.relatedFactIds.length > 0 && <div className="hint">Facts: {issue.relatedFactIds.length}</div>}
        {issue.relatedEvidenceIds.length > 0 && <div className="hint">Evidence: {issue.relatedEvidenceIds.length}</div>}
      </td>
      <td>
        <select value={severity} onChange={(e) => setSeverity(e.target.value)}>
          {ISSUE_SEVERITIES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
        </select>
      </td>
      <td>
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          {ISSUE_STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
        </select>
      </td>
      <td>
        <input value={resolution} onChange={(e) => setResolution(e.target.value)} placeholder="resolution notes" />
      </td>
      <td>
        <button disabled={!dirty || saving} onClick={save}>{saving ? 'Saving...' : 'Save'}</button>
      </td>
    </tr>
  );
}

export default function WorkstreamDetailPage() {
  const { id } = useParams();
  const { setTrail } = useBreadcrumbs();
  const [workstream, setWorkstream] = useState(null);
  const [facts, setFacts] = useState([]);
  const [evidence, setEvidence] = useState([]);
  const [issues, setIssues] = useState([]);
  const [candidateFacts, setCandidateFacts] = useState([]);
  const [error, setError] = useState(null);

  const [showFactForm, setShowFactForm] = useState(false);
  const [correctingFactId, setCorrectingFactId] = useState(null);
  const [historyFactId, setHistoryFactId] = useState(null);
  const [history, setHistory] = useState([]);
  const [asOf, setAsOf] = useState('');
  const [asOfApplied, setAsOfApplied] = useState(null);
  const [showEvidenceForm, setShowEvidenceForm] = useState(false);
  const [showIssueForm, setShowIssueForm] = useState(false);
  const [linkChoice, setLinkChoice] = useState({});

  async function load() {
    try {
      const [ws, factList, evidenceList, issueList, candidateList] = await Promise.all([
        workstreamsApi.getWorkstream(id),
        factsApi.listFacts(id),
        evidenceApi.listEvidence(id),
        issuesApi.listIssues(id),
        candidateFactsApi.listCandidateFacts(id, 'PENDING'),
      ]);
      setWorkstream(ws);
      setFacts(factList);
      setEvidence(evidenceList);
      setIssues(issueList);
      setCandidateFacts(candidateList);
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => {
    if (!workstream) return undefined;
    setTrail([
      { label: 'Transaction', to: `/transactions/${workstream.transactionId}` },
      { label: humanize(workstream.type) },
    ]);
    return () => setTrail([]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workstream]);

  async function handleToggleHistory(factId) {
    if (historyFactId === factId) {
      setHistoryFactId(null);
      setHistory([]);
      return;
    }
    setError(null);
    try {
      const chain = await factsApi.getFactHistory(factId);
      setHistory(chain);
      setHistoryFactId(factId);
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleApplyAsOf() {
    setError(null);
    try {
      if (!asOf) {
        setAsOfApplied(null);
        const live = await factsApi.listFacts(id);
        setFacts(live);
        return;
      }
      const instant = new Date(asOf).toISOString();
      const snapshot = await factsApi.listFactsAsOf(id, instant);
      setFacts(snapshot);
      setAsOfApplied(instant);
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleCreateFact(payload) {
    setError(null);
    try {
      await factsApi.createFact(id, payload);
      setShowFactForm(false);
      await load();
    } catch (err) {
      setError(err.message);
      throw err;
    }
  }

  async function handleSupersedeFact(factId, payload) {
    setError(null);
    try {
      await factsApi.supersedeFact(factId, payload);
      setCorrectingFactId(null);
      await load();
    } catch (err) {
      setError(err.message);
      throw err;
    }
  }

  async function handleVerifyFact(factId) {
    setError(null);
    const previous = facts;
    setFacts((list) => list.map((f) => (f.id === factId ? { ...f, status: 'VERIFIED' } : f)));
    try {
      await factsApi.verifyFact(factId);
      toast.success('Fact verified');
      await load();
    } catch (err) {
      setFacts(previous);
      toast.error(`Verification failed: ${err.message}`);
      setError(err.message);
    }
  }

  async function handleLinkEvidence(factId) {
    const evidenceId = linkChoice[factId];
    if (!evidenceId) return;
    setError(null);
    try {
      await factsApi.linkEvidence(factId, evidenceId);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleUnlinkEvidence(factId, evidenceId) {
    setError(null);
    try {
      await factsApi.unlinkEvidence(factId, evidenceId);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleUpload(file, documentType) {
    setError(null);
    try {
      await evidenceApi.uploadEvidence(id, file, documentType);
      setShowEvidenceForm(false);
      await load();
    } catch (err) {
      setError(err.message);
      throw err;
    }
  }

  async function handleDownload(evidenceId) {
    setError(null);
    try {
      await evidenceApi.downloadEvidence(evidenceId);
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleDeleteEvidence(evidenceId) {
    setError(null);
    try {
      await evidenceApi.deleteEvidence(evidenceId);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleReprocessEvidence(evidenceId) {
    setError(null);
    try {
      await evidenceApi.reprocessEvidence(evidenceId);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleAcceptCandidate(candidateId, payload) {
    setError(null);
    // Optimistic: drop the card immediately, restore it if the server refuses.
    const previous = candidateFacts;
    setCandidateFacts((list) => list.filter((c) => c.id !== candidateId));
    try {
      await candidateFactsApi.acceptCandidateFact(candidateId, payload);
      toast.success(`Accepted "${payload.label || 'fact'}" — now a draft fact awaiting verification`);
      await load();
    } catch (err) {
      setCandidateFacts(previous);
      toast.error(`Could not accept: ${err.message}`);
      setError(err.message);
      throw err;
    }
  }

  async function handleRejectCandidate(candidateId, payload) {
    setError(null);
    const previous = candidateFacts;
    setCandidateFacts((list) => list.filter((c) => c.id !== candidateId));
    try {
      await candidateFactsApi.rejectCandidateFact(candidateId, payload);
      toast('Candidate rejected');
      await load();
    } catch (err) {
      setCandidateFacts(previous);
      toast.error(`Could not reject: ${err.message}`);
      setError(err.message);
      throw err;
    }
  }

  function badgeClass(status) {
    return `badge badge-${status.toLowerCase()}`;
  }

  async function handleCreateIssue(payload) {
    setError(null);
    try {
      await issuesApi.createIssue(id, payload);
      setShowIssueForm(false);
      await load();
    } catch (err) {
      setError(err.message);
      throw err;
    }
  }

  async function handleUpdateIssue(issueId, payload) {
    setError(null);
    try {
      await issuesApi.updateIssue(issueId, payload);
      await load();
    } catch (err) {
      setError(err.message);
    }
  }

  function evidenceNameById(evidenceId) {
    return evidence.find((e) => e.id === evidenceId)?.fileName || evidenceId;
  }

  if (!workstream) {
    return <div className="page">{error || 'Loading...'}</div>;
  }

  return (
    <div className="page">
      <Link className="breadcrumb-back" to={`/transactions/${workstream.transactionId}`}>
        ← Back to transaction
      </Link>
      <h1>{humanize(workstream.type)}</h1>
      {workstream.description && <p className="hint">{workstream.description}</p>}
      {error && <div className="error-banner">{error}</div>}

      <div className="page-header">
        <h2>Facts</h2>
        <button onClick={() => setShowFactForm((s) => !s)}>{showFactForm ? 'Cancel' : 'New fact'}</button>
      </div>
      {showFactForm && (
        <FactForm submitLabel="Create fact" onSubmit={handleCreateFact} onCancel={() => setShowFactForm(false)} />
      )}

      <div className="inline-form">
        <label className="hint" htmlFor="as-of-input">Reconstruct as of</label>
        <input
          id="as-of-input"
          type="datetime-local"
          value={asOf}
          onChange={(e) => setAsOf(e.target.value)}
        />
        <button onClick={handleApplyAsOf}>{asOf ? 'Time travel' : 'Show current'}</button>
        {asOfApplied && (
          <button
            className="secondary"
            onClick={() => { setAsOf(''); setAsOfApplied(null); load(); }}
          >
            Back to now
          </button>
        )}
      </div>
      {asOfApplied && (
        <p className="hint">
          Showing what Syndicate believed at {new Date(asOfApplied).toLocaleString()} — historical snapshot, not editable.
        </p>
      )}

      <table className="data-table">
        <thead>
          <tr><th>Label</th><th>Value</th><th>Status</th><th>Version</th><th>Evidence</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {facts.map((f) => (
            <Fragment key={f.id}>
              <tr>
                <td>{f.label}</td>
                <td>{f.value} {f.unit}</td>
                <td>{humanize(f.status)}</td>
                <td>v{f.version}</td>
                <td>
                  {f.evidenceIds.length === 0 && <span className="hint">none</span>}
                  {f.evidenceIds.map((eid) => (
                    <div key={eid} className="linked-evidence">
                      {evidenceNameById(eid)}
                      <button className="link-button" onClick={() => handleUnlinkEvidence(f.id, eid)}>unlink</button>
                    </div>
                  ))}
                  {evidence.length > 0 && (
                    <div className="inline-form">
                      <select
                        value={linkChoice[f.id] || ''}
                        onChange={(e) => setLinkChoice((c) => ({ ...c, [f.id]: e.target.value }))}
                      >
                        <option value="">Link evidence...</option>
                        {evidence.map((e) => (
                          <option key={e.id} value={e.id}>{e.fileName}</option>
                        ))}
                      </select>
                      <button onClick={() => handleLinkEvidence(f.id)}>Link</button>
                    </div>
                  )}
                </td>
                <td>
                  {!asOfApplied && f.status === 'DRAFT' && (
                    <button onClick={() => handleVerifyFact(f.id)}>Verify</button>
                  )}
                  {!asOfApplied && f.status !== 'SUPERSEDED' && (
                    <button onClick={() => setCorrectingFactId(correctingFactId === f.id ? null : f.id)}>
                      {correctingFactId === f.id ? 'Cancel' : 'Correct'}
                    </button>
                  )}
                  <button className="secondary" onClick={() => handleToggleHistory(f.id)}>
                    {historyFactId === f.id ? 'Hide history' : 'History'}
                  </button>
                </td>
              </tr>
              {historyFactId === f.id && (
                <tr>
                  <td colSpan={6}>
                    <div className="eyebrow">Version history</div>
                    <table className="data-table">
                      <thead>
                        <tr>
                          <th>Version</th><th>Value</th><th>Valid from</th><th>Valid to</th>
                          <th>Recorded</th><th>Superseded</th><th>By</th>
                        </tr>
                      </thead>
                      <tbody>
                        {history.map((h) => (
                          <tr key={h.id}>
                            <td>v{h.version}</td>
                            <td>{h.value} {h.unit}</td>
                            <td>{h.validFrom ? new Date(h.validFrom).toLocaleDateString() : '—'}</td>
                            <td>{h.validTo ? new Date(h.validTo).toLocaleDateString() : 'current'}</td>
                            <td>{h.systemRecordedAt ? new Date(h.systemRecordedAt).toLocaleString() : '—'}</td>
                            <td>{h.systemSupersededAt ? new Date(h.systemSupersededAt).toLocaleString() : '—'}</td>
                            <td>{h.createdBy?.fullName}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </td>
                </tr>
              )}
              {correctingFactId === f.id && (
                <tr>
                  <td colSpan={6}>
                    <FactForm
                      initial={{ label: f.label, value: f.value, unit: f.unit || '', period: f.period || '' }}
                      submitLabel="Save correction (new version)"
                      onSubmit={(payload) => handleSupersedeFact(f.id, payload)}
                      onCancel={() => setCorrectingFactId(null)}
                    />
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
          {facts.length === 0 && <tr><td colSpan={6} className="hint">No facts yet.</td></tr>}
        </tbody>
      </table>

      <div className="page-header">
        <h2>Evidence</h2>
        <button onClick={() => setShowEvidenceForm((s) => !s)}>{showEvidenceForm ? 'Cancel' : 'Upload evidence'}</button>
      </div>
      {showEvidenceForm && (
        <EvidenceUploadForm onUpload={handleUpload} onCancel={() => setShowEvidenceForm(false)} />
      )}
      <table className="data-table">
        <thead>
          <tr><th>File</th><th>Type</th><th>Checksum</th><th>Status</th><th>Uploaded by</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {evidence.map((e) => (
            <tr key={e.id}>
              <td>{e.fileName}</td>
              <td>{humanize(e.documentType)}</td>
              <td className="hint">{e.fileSha256 ? `${e.fileSha256.slice(0, 12)}...` : '—'}</td>
              <td>
                <span className={badgeClass(e.processingStatus)}>{humanize(e.processingStatus)}</span>
                {e.processingStatus === 'FAILED' && e.processingError && (
                  <div className="hint">{e.processingError}</div>
                )}
              </td>
              <td>{e.uploadedBy.fullName}</td>
              <td>
                <button onClick={() => handleDownload(e.id)}>Download</button>
                {e.processingStatus === 'FAILED' && (
                  <button onClick={() => handleReprocessEvidence(e.id)}>Retry</button>
                )}
                <button className="secondary" onClick={() => handleDeleteEvidence(e.id)}>Delete</button>
              </td>
            </tr>
          ))}
          {evidence.length === 0 && <tr><td colSpan={6} className="hint">No evidence yet.</td></tr>}
        </tbody>
      </table>

      <div className="page-header">
        <h2>Candidate Facts</h2>
        <button onClick={load}>Refresh</button>
      </div>
      {candidateFacts.length === 0 && <p className="hint">No pending candidate facts.</p>}
      {candidateFacts.map((c) => (
        <CandidateFactCard
          key={c.id}
          candidate={c}
          onAccept={handleAcceptCandidate}
          onReject={handleRejectCandidate}
        />
      ))}

      <div className="page-header">
        <h2>Issues</h2>
        <button onClick={() => setShowIssueForm((s) => !s)}>{showIssueForm ? 'Cancel' : 'New issue'}</button>
      </div>
      {showIssueForm && (
        <IssueForm facts={facts} evidence={evidence} onSubmit={handleCreateIssue} onCancel={() => setShowIssueForm(false)} />
      )}
      <table className="data-table">
        <thead>
          <tr><th>Issue</th><th>Severity</th><th>Status</th><th>Resolution</th><th></th></tr>
        </thead>
        <tbody>
          {issues.map((i) => (
            <IssueRow key={i.id} issue={i} onUpdate={handleUpdateIssue} />
          ))}
          {issues.length === 0 && <tr><td colSpan={5} className="hint">No issues yet.</td></tr>}
        </tbody>
      </table>
    </div>
  );
}
