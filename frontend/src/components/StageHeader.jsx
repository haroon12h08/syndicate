import { useState } from 'react';

const STAGE_LABELS = {
  COLLECTING_DOCUMENTS: 'Collecting documents',
  DILIGENCE: 'Diligence',
  PREPARING_THE_DOCUMENT: 'Preparing the document',
  AWAITING_APPROVAL: 'Awaiting approval',
  FILING_READY: 'Filing ready',
  FILED: 'Filed',
  ANSWERING_OBSERVATIONS: 'Answering observations',
  LISTED: 'Listed',
};

const MILESTONE_LABELS = { FILED: 'Record the filing', LISTED: 'Record the listing' };

function today() {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Where the transaction stands, worked out from what it holds. The only things a person reports
 * are what happened outside Syndicate: the filing, and the listing.
 */
export default function StageHeader({ stage, onRecord }) {
  const [recording, setRecording] = useState(null);
  const [form, setForm] = useState({ occurredOn: today(), reference: '' });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  if (!stage) return null;

  async function submit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await onRecord(recording, { occurredOn: form.occurredOn, reference: form.reference || null });
      setRecording(null);
      setForm({ occurredOn: today(), reference: '' });
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="stage-header">
      <div>
        <div className="eyebrow">Stage</div>
        <strong>{STAGE_LABELS[stage.stage] || stage.stage}</strong>
        <div className="hint">{stage.summary}{stage.nextAction ? ` Next: ${stage.nextAction.toLowerCase()}.` : ''}</div>
      </div>
      <div className="inline-form">
        {stage.recordable.map((type) => (
          <button key={type} className="secondary" onClick={() => setRecording(type)}>
            {MILESTONE_LABELS[type]}
          </button>
        ))}
      </div>
      {recording && (
        <form className="card-form" onSubmit={submit}>
          <label>
            Date
            <input type="date" value={form.occurredOn}
              onChange={(e) => setForm((f) => ({ ...f, occurredOn: e.target.value }))} required />
          </label>
          <label>
            Their reference
            <input value={form.reference} onChange={(e) => setForm((f) => ({ ...f, reference: e.target.value }))}
              placeholder="Optional, e.g. BSE/SME/2026/118" />
          </label>
          {error && <div className="error-banner">{error}</div>}
          <div className="form-actions">
            <button type="submit" disabled={saving}>{saving ? 'Recording…' : MILESTONE_LABELS[recording]}</button>
            <button type="button" className="secondary" onClick={() => setRecording(null)}>Cancel</button>
          </div>
        </form>
      )}
    </div>
  );
}
