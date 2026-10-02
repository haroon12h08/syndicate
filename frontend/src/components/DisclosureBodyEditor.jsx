import { useRef, useState } from 'react';
import { useFactDefinitions } from './FactKeyPicker';

/**
 * Writing a disclosure means writing prose and pointing at the facts it rests on. The reference
 * the system stores is a key; the writer picks a value by name and sees it in place.
 */
export default function DisclosureBodyEditor({ value, onChange, facts = [] }) {
  const definitions = useFactDefinitions();
  const [picking, setPicking] = useState(false);
  const [query, setQuery] = useState('');
  const areaRef = useRef(null);

  // Facts this transaction actually holds come first; the rest of the catalogue is still offered,
  // because a disclosure may be written before the value arrives.
  const available = facts
    .filter((f) => f.status !== 'SUPERSEDED' && f.status !== 'REJECTED')
    .map((f) => ({
      factKey: f.factKey,
      period: f.period,
      label: f.label,
      detail: `${f.value}${f.unit ? ` ${f.unit}` : ''}${f.period ? ` · ${f.period}` : ''}`,
      verified: f.status === 'VERIFIED',
    }));
  const catalogue = definitions
    .filter((d) => !available.some((a) => a.factKey === d.factKey))
    .map((d) => ({ factKey: d.factKey, period: null, label: d.displayLabel, detail: 'not recorded yet', verified: false }));

  const q = query.trim().toLowerCase();
  const options = [...available, ...catalogue]
    .filter((o) => !q || o.label.toLowerCase().includes(q) || o.factKey.includes(q))
    .slice(0, 10);

  function insert(option) {
    const reference = `{{fact:${option.factKey}${option.period ? `@${option.period}` : ''}}}`;
    const area = areaRef.current;
    const at = area ? area.selectionStart : (value || '').length;
    const next = `${(value || '').slice(0, at)}${reference}${(value || '').slice(at)}`;
    onChange(next);
    setPicking(false);
    setQuery('');
  }

  return (
    <div className="disclosure-editor">
      <textarea
        ref={areaRef}
        rows={5}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Write the disclosure. Use Insert value where a figure belongs."
        required
      />
      <div className="inline-form">
        <button type="button" className="secondary" onClick={() => setPicking((p) => !p)}>
          {picking ? 'Cancel' : 'Insert value'}
        </button>
        <span className="hint">Inserted values are filled in at compile time, and only from verified facts.</span>
      </div>
      {picking && (
        <div className="fact-picker">
          <input autoFocus value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder="Which value? e.g. revenue" />
          <ul className="fact-picker-list">
            {options.map((o) => (
              <li key={`${o.factKey}-${o.period || ''}`}>
                <button type="button" onClick={() => insert(o)}>
                  <span>{o.label} <span className="hint">{o.detail}</span></span>
                  {!o.verified && <span className="badge badge-pending">not verified yet</span>}
                </button>
              </li>
            ))}
            {options.length === 0 && <li className="hint">Nothing matches “{query}”.</li>}
          </ul>
        </div>
      )}
    </div>
  );
}
