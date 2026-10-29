import { useState } from 'react';
import toast from 'react-hot-toast';
import { humanize } from '../constants';
import * as aiApi from '../api/ai';
import AiProvenanceModal from './AiProvenanceModal';

function observationBadge(status) {
  if (status === 'CLOSED') return 'badge badge-complete';
  if (status === 'RESPONSE_APPROVED') return 'badge badge-complete';
  if (status === 'RESPONSE_DRAFTED') return 'badge badge-pending';
  if (status === 'OPEN') return 'badge badge-failed';
  return 'badge badge-not_applicable';
}

const EMPTY_RECORD = {
  authority: 'BSE',
  reference: '',
  receivedDate: new Date().toISOString().slice(0, 10),
  responseDeadline: '',
  observation: '',
  sectionCode: '',
  severity: 'HIGH',
};

function RecordForm({ onRecord, onCancel }) {
  const [form, setForm] = useState(EMPTY_RECORD);
  const [saving, setSaving] = useState(false);
  const update = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  return (
    <form className="card-form" onSubmit={async (e) => {
      e.preventDefault();
      setSaving(true);
      try {
        await onRecord({
          ...form,
          responseDeadline: form.responseDeadline || null,
          sectionCode: form.sectionCode || null,
        });
        setForm(EMPTY_RECORD);
        onCancel();
      } finally {
        setSaving(false);
      }
    }}>
      <div className="grid grid-3">
        <label>Authority<input value={form.authority} onChange={update('authority')} required placeholder="e.g. BSE" /></label>
        <label>Reference<input value={form.reference} onChange={update('reference')} placeholder="e.g. BSE/LIST/2026/04" /></label>
        <label>Section<input value={form.sectionCode} onChange={update('sectionCode')} placeholder="e.g. CAPITAL_STRUCTURE" /></label>
      </div>
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
  const [drafting, setDrafting] = useState(false);
  const [aiArtifactId, setAiArtifactId] = useState(null);
  const [showArtifactModal, setShowArtifactModal] = useState(false);

  const toggle = (setter) => (id) => setter((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]));

  const handleAiDraft = async () => {
    setDrafting(true);
    try {
      const draft = await aiApi.draftObservationResponse(observation.id, {
        userInstructions: 'Draft legally rigorous response grounded in transaction facts and evidence',
      });
      if (draft.suggestedResponse) {
        setResponse(draft.suggestedResponse);
      }
      if (draft.suggestedFactIds && draft.suggestedFactIds.length > 0) {
        setFactIds((prev) => Array.from(new Set([...prev, ...draft.suggestedFactIds])));
      }
      if (draft.suggestedEvidenceIds && draft.suggestedEvidenceIds.length > 0) {
        setEvidenceIds((prev) => Array.from(new Set([...prev, ...draft.suggestedEvidenceIds])));
      }
      if (draft.aiArtifactId) {
        setAiArtifactId(draft.aiArtifactId);
      }
      toast.success('AI draft generated with linked facts & evidence');
    } catch (err) {
      toast.error('AI Draft failed: ' + (err.message || 'Error'));
    } finally {
      setDrafting(false);
    }
  };

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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
        <label style={{ margin: 0 }}><strong>Response</strong></label>
        <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
          {aiArtifactId && (
            <button
              type="button"
              className="secondary small"
              onClick={() => setShowArtifactModal(true)}
              style={{ fontSize: '0.75rem' }}
            >
              🔍 AI Provenance
            </button>
          )}
          <button
            type="button"
            className="secondary small"
            onClick={handleAiDraft}
            disabled={drafting}
            style={{ display: 'flex', alignItems: 'center', gap: '0.3rem', borderColor: '#8b5cf6', color: '#c084fc' }}
          >
            <span>{drafting ? '⏳ Drafting...' : '✨ AI Draft Response'}</span>
          </button>
        </div>
      </div>

      <textarea
        rows={5}
        value={response}
        onChange={(e) => setResponse(e.target.value)}
        required
        placeholder="The answer that will go back to the authority. Must cite verified facts and primary evidence."
      />

      <fieldset>
        <legend>What the answer rests on (select at least one fact or document)</legend>
        <div style={{ maxHeight: '180px', overflowY: 'auto' }}>
          {facts.filter((f) => f.status !== 'SUPERSEDED' && f.status !== 'REJECTED').slice(0, 15).map((f) => (
            <label key={f.id} className="checkbox-label" style={{ fontSize: '0.82rem' }}>
              <input type="checkbox" checked={factIds.includes(f.id)} onChange={() => toggle(setFactIds)(f.id)} />
              <span>{f.label}: <strong>{f.value}{f.unit ? ` ${f.unit}` : ''}</strong> ({f.period || 'current'})</span>
            </label>
          ))}
          {evidence.slice(0, 15).map((e) => (
            <label key={e.id} className="checkbox-label" style={{ fontSize: '0.82rem' }}>
              <input type="checkbox" checked={evidenceIds.includes(e.id)} onChange={() => toggle(setEvidenceIds)(e.id)} />
              <span>📄 {e.fileName} ({e.documentType})</span>
            </label>
          ))}
        </div>
      </fieldset>

      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.5rem' }}>
        <button type="submit" disabled={saving}>{saving ? 'Saving…' : 'Save response'}</button>
      </div>

      {showArtifactModal && aiArtifactId && (
        <AiProvenanceModal artifactId={aiArtifactId} onClose={() => setShowArtifactModal(false)} />
      )}
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

      {recording && <RecordForm onRecord={onRecord} onCancel={() => setRecording(false)} />}

      <div className="card-list">
        {observations.map((o) => (
          <div key={o.id} className="card">
            <div className="card-header">
              <span className={observationBadge(o.status)}>{humanize(o.status)}</span>
              <h3>{o.authority}: {o.reference || 'Unreferenced query'}</h3>
              <span className="card-meta">{o.sectionCode}</span>
            </div>
            <p className="observation-text">{o.observation}</p>
            <div className="meta-row">
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
                    <button onClick={() => onClose(o.id)}>Close observation</button>
                  )}
                </div>
              )}
          </div>
        ))}
      </div>
    </section>
  );
}
