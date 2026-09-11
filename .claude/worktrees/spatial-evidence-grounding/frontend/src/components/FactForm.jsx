import { useState } from 'react';

const empty = { label: '', value: '', unit: '', period: '' };

export default function FactForm({ initial, submitLabel, onSubmit, onCancel }) {
  const [form, setForm] = useState(initial || empty);
  const [submitting, setSubmitting] = useState(false);

  function update(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    try {
      await onSubmit(form);
      if (!initial) {
        setForm(empty);
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card-form" onSubmit={handleSubmit}>
      <label>
        Label
        <input value={form.label} onChange={(e) => update('label', e.target.value)} placeholder="e.g. Revenue FY2026" required />
      </label>
      <label>
        Value
        <input value={form.value} onChange={(e) => update('value', e.target.value)} placeholder="e.g. 42.18" required />
      </label>
      <label>
        Unit
        <input value={form.unit} onChange={(e) => update('unit', e.target.value)} placeholder="e.g. INR Crore" />
      </label>
      <label>
        Period
        <input value={form.period} onChange={(e) => update('period', e.target.value)} placeholder="e.g. FY2026" />
      </label>
      <div className="form-actions">
        <button type="submit" disabled={submitting}>{submitting ? 'Saving...' : submitLabel}</button>
        {onCancel && <button type="button" className="secondary" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}
