import { useState } from 'react';
import { humanize } from '../constants';

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString() : 'open-ended';
}

function ResolveForm({ conflict, onResolve }) {
  const [chosenFactId, setChosenFactId] = useState('');
  const [reason, setReason] = useState('');
  const [evidenceIds, setEvidenceIds] = useState([]);
  const [saving, setSaving] = useState(false);
  const sources = conflict.members.flatMap((m) => m.sources);

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    try {
      await onResolve(conflict.id, { chosenFactId, reason, evidenceConsideredIds: evidenceIds });
    } finally {
      setSaving(false);
    }
  }

  function toggleEvidence(id) {
    setEvidenceIds((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>
        Value that stands
        <select value={chosenFactId} onChange={(e) => setChosenFactId(e.target.value)} required>
          <option value="">Choose…</option>
          {conflict.members.map((m) => (
            <option key={m.factId} value={m.factId}>
              {m.value}{m.unit ? ` ${m.unit}` : ''} (from {formatDate(m.validFrom)})
            </option>
          ))}
        </select>
      </label>
      <label>
        Reason
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} required rows={3}
          placeholder="Why this value stands and the others do not" />
      </label>
      {sources.length > 0 && (
        <fieldset>
          <legend>Evidence considered</legend>
          {sources.map((s) => (
            <label key={s.evidenceId} className="checkbox">
              <input type="checkbox" checked={evidenceIds.includes(s.evidenceId)}
                onChange={() => toggleEvidence(s.evidenceId)} />
              {s.fileName}
            </label>
          ))}
        </fieldset>
      )}
      <p className="hint">The other values are rejected but kept on record, and anything built on them is marked stale.</p>
      <button type="submit" disabled={saving || !chosenFactId || !reason.trim()}>Record resolution</button>
    </form>
  );
}

export default function ConflictsPanel({ conflicts, onResolve }) {
  const [resolving, setResolving] = useState(null);

  return (
    <section>
      <div className="page-header"><h2>Conflicts</h2></div>
      <p className="hint">
        Sources that disagree about the same fact over the same period. Syndicate never picks a value; a
        reviewer decides and records why.
      </p>
      {conflicts.length === 0 && (
        <div className="empty-state"><span>No conflicting values have been found.</span></div>
      )}
      {conflicts.map((c) => (
        <article key={c.id} className="conflict-card">
          <div className="page-header">
            <h3>{c.members[0]?.label ?? c.factKey}{c.period ? ` (${c.period})` : ''}</h3>
            <span className={`badge ${c.status === 'OPEN' ? 'badge-failed' : 'badge-complete'}`}>{humanize(c.status)}</span>
          </div>
          <table className="data-table">
            <thead>
              <tr><th>Value</th><th>Valid</th><th>Status</th><th>Recorded by</th><th>Sources</th></tr>
            </thead>
            <tbody>
              {c.members.map((m) => (
                <tr key={m.factId}>
                  <td><strong>{m.value}{m.unit ? ` ${m.unit}` : ''}</strong></td>
                  <td>{formatDate(m.validFrom)} – {formatDate(m.validTo)}</td>
                  <td>{humanize(m.status)}</td>
                  <td>{m.createdBy?.fullName}</td>
                  <td>{m.sources.length ? m.sources.map((s) => s.fileName).join(', ') : 'Entered directly'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {c.resolutions.map((r) => (
            <p key={r.id} className="hint">
              Resolved by {r.resolvedBy?.fullName} on {new Date(r.resolvedAt).toLocaleString()}: {r.reason}
            </p>
          ))}
          {c.status === 'OPEN' && (resolving === c.id
            ? <ResolveForm conflict={c} onResolve={async (...args) => { await onResolve(...args); setResolving(null); }} />
            : <button className="secondary" onClick={() => setResolving(c.id)}>Resolve</button>)}
        </article>
      ))}
    </section>
  );
}
