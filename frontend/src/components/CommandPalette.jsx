import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as companiesApi from '../api/companies';
import * as transactionsApi from '../api/transactions';
import { humanize } from '../constants';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const STATIC_ENTRIES = [
  { kind: 'Page', label: 'Home', to: '/home' },
  { kind: 'Page', label: 'Companies', to: '/companies' },
  { kind: 'Page', label: 'Organizations', to: '/organizations' },
  { kind: 'Page', label: 'Invitations', to: '/invitations' },
];

export default function CommandPalette() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [cursor, setCursor] = useState(0);
  const [entries, setEntries] = useState(STATIC_ENTRIES);
  const inputRef = useRef(null);

  const load = useCallback(async () => {
    try {
      const [companies, transactions] = await Promise.all([
        companiesApi.listCompanies().catch(() => []),
        transactionsApi.listMyTransactions().catch(() => []),
      ]);
      setEntries([
        ...STATIC_ENTRIES,
        ...companies.map((c) => ({
          kind: 'Company', label: c.legalName, hint: c.ownerOrganizationName, to: `/companies/${c.id}`,
        })),
        ...transactions.map((t) => ({
          kind: 'Transaction', label: t.name,
          hint: `${t.companyName} · ${humanize(t.status)}`, to: `/transactions/${t.id}`,
        })),
      ]);
    } catch {
      setEntries(STATIC_ENTRIES);
    }
  }, []);

  useEffect(() => {
    function onKey(e) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setOpen((wasOpen) => {
          if (!wasOpen) { setQuery(''); setCursor(0); load(); }
          return !wasOpen;
        });
      }
      if (e.key === 'Escape') setOpen(false);
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [load]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return entries.slice(0, 12);
    // A pasted id is a direct jump — pros paste fact ids rather than hunting for them.
    if (UUID_RE.test(q)) {
      return [
        { kind: 'Fact', label: 'Open evidence trace for this fact id', hint: q, to: `/facts/${q}/trace` },
        { kind: 'Transaction', label: 'Open transaction with this id', hint: q, to: `/transactions/${q}` },
        { kind: 'Workstream', label: 'Open workstream with this id', hint: q, to: `/workstreams/${q}` },
      ];
    }
    return entries
      .filter((e) => `${e.kind} ${e.label} ${e.hint || ''}`.toLowerCase().includes(q))
      .slice(0, 12);
  }, [query, entries]);

  useEffect(() => { setCursor(0); }, [query]);

  if (!open) return null;

  function go(entry) {
    setOpen(false);
    navigate(entry.to);
  }

  function onInputKey(e) {
    if (e.key === 'ArrowDown') { e.preventDefault(); setCursor((c) => Math.min(c + 1, results.length - 1)); }
    if (e.key === 'ArrowUp') { e.preventDefault(); setCursor((c) => Math.max(c - 1, 0)); }
    if (e.key === 'Enter' && results[cursor]) { e.preventDefault(); go(results[cursor]); }
  }

  return (
    <div className="palette-backdrop" onClick={() => setOpen(false)}>
      <div className="palette" onClick={(e) => e.stopPropagation()}>
        <input
          ref={inputRef}
          className="palette-input"
          placeholder="Jump to a company, transaction, or paste a fact id…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={onInputKey}
        />
        <ul className="palette-results">
          {results.map((entry, i) => (
            <li key={`${entry.kind}-${entry.to}-${i}`}>
              <button
                className={`palette-item${i === cursor ? ' active' : ''}`}
                onMouseEnter={() => setCursor(i)}
                onClick={() => go(entry)}
              >
                <span className="palette-kind">{entry.kind}</span>
                <span className="palette-label">{entry.label}</span>
                {entry.hint && <span className="palette-hint">{entry.hint}</span>}
              </button>
            </li>
          ))}
          {results.length === 0 && <li className="palette-empty">No matches.</li>}
        </ul>
        <div className="palette-footer">
          <span>↑↓ navigate</span><span>⏎ open</span><span>esc close</span>
        </div>
      </div>
    </div>
  );
}
