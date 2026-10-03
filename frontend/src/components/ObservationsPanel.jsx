import { useState } from 'react';
import { humanize } from '../constants';

const STATUS_BADGE = {
  OPEN: 'badge badge-failed',
  RESPONSE_DRAFTED: 'badge badge-pending',
  RESPONSE_APPROVED: 'badge badge-processing',
  RESPONDED: 'badge badge-complete',
  CLOSED: 'badge badge-not_applicable',
};

function today() {
  return new Date().toISOString().slice(0, 10);
}

function RecordForm({ onRecord, onCancel }) {
  const [form, setForm] = useState({ authority: 'BSE', reference: '', receivedDate: today(), observation: '',
    responseDeadline: '' });
  const [saving, setSaving] = useState(false);
  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  return (
    <form className="card-form" onSubmit={async (e) => {
      e.preventDefault();
      setSaving(true);
      try {
        await onRecord({ ...form, responseDeadline: form.responseDeadline || null, reference: form.reference || null });
      } finally {
        setSaving(false);
      }
    }}>
      <label>
        Raised by
        <select value={form.authority} onChange={update('authority')}>
          <option value="BSE">BSE</option>
          <option value="NSE">NSE</option>
          <option value="SEBI">SEBI</option>
        </select>
      </label>
      <label>Their reference<input value={form.reference} onChange={update('reference')} placeholder="Optional" /></label>
      <label>Received<input type="date" value={form.receivedDate} onChange={update('receivedDate')} required /></label>
      <label>Reply by<input type="date" value={form.responseDeadline} onChange={update('responseDeadline')} /></label>
      <label>
        What they asked
        <textarea rows={3} value={form.observation} onChange={update('observation')} required
          placeholder="e.g. Explain the increase in inventory during FY2026." />
      </label>
      <div className="form-actions">
        <button type="submit" disabled={saving}>{saving ? 'Recording…' : 'Record observation'}</button>
        <button type="button" className="secondary" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

function RespondForm({ observation, facts, evidence, onRespond }) {
  const [response, setResponse] = useState(observation.response || '');
  const [factIds, setFactIds] = useState(observation.relatedFactIds || []);
  const [evidenceIds, setEvidenceIds] = useState(observation.relatedEvidenceIds || []);
  const [saving, setSaving] = useState(false);
  const toggle = (setter) => (id) => setter((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));

  return (
    <form className="card-form" onSubmit={async (e) => {
      e.preventDefault();
      setSaving(true);
      try {
        await onRespond(observation.id, { response, factIds, evidenceIds });
      } finally {
        setSaving(false);
      }
    }}>
      <label>
        Response
        <textarea rows={4} value={response} onChange={(e) => setResponse(e.target.value)} required
          placeholder="The answer that will go back to the authority" />
      </label>
      <fieldset>
        <legend>What the answer rests on</legend>
        {facts.filter((f) => f.status !== 'SUPERSEDED' && f.status !== 'REJECTED').slice(0, 10).map((f) => (
          <label key={f.id} className="checkbox-label">
            <input type="checkbox" checked={factIds.includes(f.id)} onChange={() => toggle(setFactIds)(f.id)} />
            {f.label}: {f.value}{f.unit ? ` ${f.unit}` : ''}
          </label>
        ))}
        {evidence.slice(0, 10).map((e) => (
          <label key={e.id} className="checkbox-label">
            <input type="checkbox" checked={evidenceIds.includes(e.id)} onChange={() => toggle(setEvidenceIds)(e.id)} />
            {e.fileName}
          </label>
        ))}
      </fieldset>
      <button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save response'}</button>
    </form>
  );
}

/** Spec §18: a query about the filing, answered against the state that produced it. */
export default function ObservationsPanel({ observations, facts = [], evidence = [], currentUserId,
  onRecord, onRespond, onApprove, onSent, onClose }) {
  const [recording, setRecording] = useState(false);
  const [respondingTo, setRespondingTo] = useState(null);

  return (
    <section>
      <div className="page-header">
        <h2>Observations</h2>
        <button className="secondary" onClick={() => setRecording((r) => !r)}>
          {recording ? 'Cancel' : 'Record an observation'}
        </button>
      </div>
      <p className="hint">
        Queries from an exchange or regulator, with the facts and documents each answer rests on,
        and the compiled version it was answered against.
      </p>

      {recording && <RecordForm onRecord={async (payload) => { await onRecord(payload); setRecording(false); }}
        onCancel={() => setRecording(false)} />}

      {observations.length === 0 && !recording && (
        <div className="empty-state"><span>No observations have been received.</span></div>
      )}

      {observations.map((o) => (
        <article key={o.id} className="conflict-card">
          <div className="page-header">
            <h3>{o.authority}{o.reference ? ` · ${o.reference}` : ''}</h3>
            <span className={STATUS_BADGE[o.status]}>{humanize(o.status)}</span>
          </div>
          <p>{o.observation}</p>
          <div className="hint">
            Received {o.receivedDate}
            {o.responseDeadline && <> · reply by {o.responseDeadline}</>}
            {o.documentVersion && <> · answered against DRHP v{o.documentVersion}</>}
          </div>
          {o.response && (
            <div>
              <div className="eyebrow">Response</div>
              <p>{o.response}</p>
              <div className="hint">
                Drafted by {o.respondedBy?.fullName}
                {o.approvedBy && <> · approved by {o.approvedBy.fullName}</>}
              </div>
            </div>
          )}
          {respondingTo === o.id
            ? <RespondForm observation={o} facts={facts} evidence={evidence}
                onRespond={async (...args) => { await onRespond(...args); setRespondingTo(null); }} />
            : (
              <div className="inline-form">
                {o.status !== 'CLOSED' && (
                  <button className="secondary" onClick={() => setRespondingTo(o.id)}>
                    {o.response ? 'Revise response' : 'Write response'}
                  </button>
                )}
                {o.status === 'RESPONSE_DRAFTED' && o.respondedBy?.id !== currentUserId && (
                  <button onClick={() => onApprove(o.id)}>Approve response</button>
                )}
                {o.status === 'RESPONSE_APPROVED' && (
                  <button onClick={() => onSent(o.id)}>Record as sent</button>
                )}
                {o.status === 'RESPONDED' && (
                  <button className="secondary" onClick={() => onClose(o.id)}>Close</button>
                )}
              </div>
            )}
        </article>
      ))}
    </section>
  );
}
