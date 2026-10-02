import { useEffect, useMemo, useState } from 'react';
import * as factDefinitionsApi from '../api/factDefinitions';

let cache = null;

/** Loaded once per session: the catalogue changes with releases, not with use. */
export function useFactDefinitions() {
  const [definitions, setDefinitions] = useState(cache || []);
  useEffect(() => {
    if (cache) return;
    factDefinitionsApi.listFactDefinitions()
      .then((list) => { cache = list; setDefinitions(list); })
      .catch(() => setDefinitions([]));
  }, []);
  return definitions;
}

/**
 * Choosing what a value *is*, rather than typing a key. The key is the system's business; the
 * person picks the thing they mean, or names something the catalogue does not cover.
 */
export default function FactKeyPicker({ value, label, onChange, allowCustom = true }) {
  const definitions = useFactDefinitions();
  const [query, setQuery] = useState('');
  const selected = definitions.find((d) => d.factKey === value);

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return definitions.slice(0, 8);
    return definitions.filter((d) => d.displayLabel.toLowerCase().includes(q) || d.factKey.includes(q)).slice(0, 8);
  }, [definitions, query]);

  if (selected) {
    return (
      <div className="picked-fact">
        <span><strong>{selected.displayLabel}</strong> <span className="hint">{selected.factKey}</span></span>
        <button type="button" className="link-button" onClick={() => onChange(null, '')}>Change</button>
      </div>
    );
  }

  return (
    <div className="fact-picker">
      <input
        value={query}
        onChange={(e) => { setQuery(e.target.value); if (allowCustom) onChange(null, e.target.value); }}
        placeholder="What is this value? e.g. revenue, promoter shareholding"
      />
      {query.trim() && (
        <ul className="fact-picker-list">
          {matches.map((d) => (
            <li key={d.factKey}>
              <button type="button" onClick={() => { onChange(d.factKey, d.displayLabel); setQuery(''); }}>
                {d.displayLabel}
                {d.defaultMateriality === 'CRITICAL' && <span className="badge badge-failed">Critical</span>}
                {d.defaultMateriality === 'MATERIAL' && <span className="badge badge-pending">Material</span>}
              </button>
            </li>
          ))}
          {allowCustom && (
            <li className="hint">
              No match? “{query.trim()}” will be recorded as its own kind of value.
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
