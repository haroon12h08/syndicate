import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import * as aiApi from '../api/ai';

const CATEGORIES = [
  { key: '', label: 'All' },
  { key: 'FACT', label: 'Facts' },
  { key: 'EVIDENCE', label: 'Evidence' },
  { key: 'CANDIDATE_FACT', label: 'Candidates' },
  { key: 'DISCLOSURE', label: 'Disclosures' },
  { key: 'DILIGENCE_QUESTION', label: 'Diligence' },
  { key: 'CONFLICT', label: 'Conflicts' },
  { key: 'AUDIT_EVENT', label: 'Audit' },
];

export default function TransactionSearchModal({ transactionId, transactionName, isOpen, onClose }) {
  const [query, setQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [resultsData, setResultsData] = useState({ results: [], countsByType: {}, totalCount: 0 });
  const [cursor, setCursor] = useState(0);
  const inputRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (isOpen) {
      inputRef.current?.focus();
    } else {
      setQuery('');
      setResultsData({ results: [], countsByType: {}, totalCount: 0 });
      setCursor(0);
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen || !transactionId) return;
    const trimmed = query.trim();
    if (!trimmed) {
      setResultsData({ results: [], countsByType: {}, totalCount: 0 });
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const data = await aiApi.searchTransaction(transactionId, {
          q: trimmed,
          types: typeFilter,
          limit: 40,
        });
        setResultsData(data || { results: [], countsByType: {}, totalCount: 0 });
        setCursor(0);
      } catch (err) {
        console.error('Search failed', err);
      } finally {
        setLoading(false);
      }
    }, 200);

    return () => clearTimeout(timer);
  }, [isOpen, transactionId, query, typeFilter]);

  if (!isOpen) return null;

  const results = resultsData.results || [];

  const handleSelect = (item) => {
    if (!item?.deepLinkPath) return;
    onClose();
    navigate(item.deepLinkPath);
  };

  const onKeyDown = (e) => {
    if (e.key === 'Escape') {
      onClose();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setCursor((c) => Math.min(c + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setCursor((c) => Math.max(c - 1, 0));
    } else if (e.key === 'Enter' && results[cursor]) {
      e.preventDefault();
      handleSelect(results[cursor]);
    }
  };

  const getBadgeClass = (entityType, state) => {
    if (state === 'VERIFIED' || state === 'READY' || state === 'COMPLETE' || state === 'RESOLVED') return 'badge-complete';
    if (state === 'BLOCKING' || state === 'FAILED' || state === 'REJECTED') return 'badge-failed';
    if (state === 'OPEN' || state === 'PENDING' || state === 'DRAFT') return 'badge-pending';
    return 'badge-not_applicable';
  };

  return (
    <div className="palette-backdrop" onClick={onClose} onKeyDown={onKeyDown}>
      <div
        className="palette"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: '780px', maxHeight: '82vh', display: 'flex', flexDirection: 'column' }}
      >
        <div style={{ padding: '0.85rem 1rem', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <span style={{ fontSize: '1.1rem' }}>🔍</span>
          <input
            ref={inputRef}
            className="palette-input"
            type="text"
            placeholder={`Search ${transactionName || 'transaction'} (facts, documents, disclosures, diligence, conflicts)...`}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ border: 'none', background: 'transparent', flex: 1, outline: 'none', color: 'var(--text)', fontSize: '0.95rem' }}
          />
          {loading && <span className="dim" style={{ fontSize: '0.8rem' }}>Searching...</span>}
          <button className="secondary small" onClick={onClose}>ESC</button>
        </div>

        {/* Category Filters */}
        <div style={{ display: 'flex', gap: '0.4rem', padding: '0.5rem 1rem', borderBottom: '1px solid var(--border)', background: 'var(--surface)' }}>
          {CATEGORIES.map((c) => {
            const count = c.key ? (resultsData.countsByType[c.key] || 0) : resultsData.totalCount;
            return (
              <button
                key={c.key}
                className={`tab-btn small ${typeFilter === c.key ? 'active' : ''}`}
                onClick={() => setTypeFilter(c.key)}
                style={{ padding: '0.2rem 0.55rem', fontSize: '0.75rem' }}
              >
                {c.label} {query.trim() ? `(${count})` : ''}
              </button>
            );
          })}
        </div>

        {/* Results List */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '0.5rem' }}>
          {!query.trim() && (
            <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-dim)', fontSize: '0.85rem' }}>
              Type keywords, semantic fact keys (e.g. <code>issuer.revenue</code>), figures, or document names to search across the entire transaction.
            </div>
          )}

          {query.trim() && !loading && results.length === 0 && (
            <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-dim)', fontSize: '0.85rem' }}>
              No matches found for "{query}".
            </div>
          )}

          {results.map((item, idx) => (
            <div
              key={`${item.entityType}-${item.entityId}-${idx}`}
              onClick={() => handleSelect(item)}
              style={{
                padding: '0.65rem 0.85rem',
                borderRadius: '4px',
                cursor: 'pointer',
                background: cursor === idx ? 'var(--highlight)' : 'transparent',
                borderBottom: '1px solid var(--border)',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.2rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span className="badge badge-not_applicable" style={{ fontSize: '0.68rem' }}>{item.entityType}</span>
                  <strong style={{ fontSize: '0.9rem' }}>{item.title}</strong>
                  {item.state && (
                    <span className={`badge ${getBadgeClass(item.entityType, item.state)}`} style={{ fontSize: '0.68rem' }}>
                      {item.state}
                    </span>
                  )}
                </div>
                <span className="dim" style={{ fontSize: '0.72rem' }}>{item.provenanceSummary}</span>
              </div>

              {item.subtitle && (
                <div style={{ fontSize: '0.8rem', color: 'var(--text-dim)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {item.subtitle}
                </div>
              )}

              {item.snippet && (
                <div style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: '#10b981', background: 'rgba(16, 185, 129, 0.08)', padding: '0.2rem 0.4rem', borderRadius: '3px', marginTop: '0.2rem' }}>
                  "{item.snippet}"
                </div>
              )}
            </div>
          ))}
        </div>

        <div style={{ padding: '0.4rem 1rem', borderTop: '1px solid var(--border)', background: 'var(--surface)', fontSize: '0.75rem', color: 'var(--text-dim)', display: 'flex', justifyContent: 'space-between' }}>
          <span>Navigate: <strong>↑↓</strong> | Select: <strong>↵</strong> | Close: <strong>ESC</strong></span>
          <span>{results.length} result(s)</span>
        </div>
      </div>
    </div>
  );
}
