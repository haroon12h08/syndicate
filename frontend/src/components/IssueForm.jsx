import { useState } from 'react';
import { ISSUE_SEVERITIES, humanize } from '../constants';

const empty = { title: '', description: '', severity: ISSUE_SEVERITIES[1], dueDate: '', relatedFactIds: [], relatedEvidenceIds: [] };

export default function IssueForm({ facts, evidence, onSubmit, onCancel }) {
  const [form, setForm] = useState(empty);
  const [submitting, setSubmitting] = useState(false);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  function toggleId(field, id) {
    setForm((f) => {
      const set = new Set(f[field]);
      if (set.has(id)) set.delete(id); else set.add(id);
      return { ...f, [field]: Array.from(set) };
    });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await onSubmit({ ...form, dueDate: form.dueDate || null });
      setForm(empty);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card-form" onSubmit={handleSubmit}>
      <label>
        Title
        <input value={form.title} onChange={(e) => update('title', e.target.value)} required />
      </label>
      <label>
        Description
        <textarea value={form.description} onChange={(e) => update('description', e.target.value)} rows={2} />
      </label>
      <label>
        Severity
        <select value={form.severity} onChange={(e) => update('severity', e.target.value)}>
          {ISSUE_SEVERITIES.map((s) => (
            <option key={s} value={s}>{humanize(s)}</option>
          ))}
        </select>
      </label>
      <label>
        Due date
        <input type="date" value={form.dueDate} onChange={(e) => update('dueDate', e.target.value)} />
      </label>
      {facts.length > 0 && (
        <fieldset>
          <legend>Related facts</legend>
          {facts.map((f) => (
            <label key={f.id} className="checkbox-label">
              <input
                type="checkbox"
                checked={form.relatedFactIds.includes(f.id)}
                onChange={() => toggleId('relatedFactIds', f.id)}
              />
              {f.label} = {f.value} (v{f.version})
            </label>
          ))}
        </fieldset>
      )}
      {evidence.length > 0 && (
        <fieldset>
          <legend>Related evidence</legend>
          {evidence.map((e) => (
            <label key={e.id} className="checkbox-label">
              <input
                type="checkbox"
                checked={form.relatedEvidenceIds.includes(e.id)}
                onChange={() => toggleId('relatedEvidenceIds', e.id)}
              />
              {e.fileName}
            </label>
          ))}
        </fieldset>
      )}
      <div className="form-actions">
        <button type="submit" disabled={submitting}>{submitting ? 'Creating...' : 'Create issue'}</button>
        {onCancel && <button type="button" className="secondary" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}
