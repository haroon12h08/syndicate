import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import * as aiApi from '../api/ai';
import AiProvenanceModal from './AiProvenanceModal';

export default function AiSuggestionsPanel({ transactionId, canEdit = true }) {
  const [suggestions, setSuggestions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedArtifactId, setSelectedArtifactId] = useState(null);
  const [dismissingId, setDismissingId] = useState(null);
  const [dismissReason, setDismissReason] = useState('');

  const load = async () => {
    if (!transactionId) return;
    setLoading(true);
    try {
      const data = await aiApi.listSuggestions(transactionId);
      setSuggestions(data || []);
    } catch (err) {
      toast.error('Failed to load AI suggestions: ' + (err.message || 'Error'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [transactionId]);

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const res = await aiApi.generateSuggestions(transactionId);
      toast.success(`Generated ${res.generatedCount} new suggestions (${res.pendingCount} pending)`);
      await load();
    } catch (err) {
      toast.error('Failed to run AI scan: ' + (err.message || 'Error'));
    } finally {
      setGenerating(false);
    }
  };

  const handleAccept = async (id) => {
    try {
      await aiApi.acceptSuggestion(transactionId, id);
      toast.success('Suggestion accepted and action applied');
      await load();
    } catch (err) {
      toast.error('Failed to accept suggestion: ' + (err.message || 'Error'));
    }
  };

  const handleDismiss = async (id) => {
    try {
      await aiApi.dismissSuggestion(transactionId, id, dismissReason);
      toast.success('Suggestion dismissed');
      setDismissingId(null);
      setDismissReason('');
      await load();
    } catch (err) {
      toast.error('Failed to dismiss suggestion: ' + (err.message || 'Error'));
    }
  };

  const filtered = suggestions.filter((s) => {
    if (statusFilter === 'ALL') return true;
    return s.status === statusFilter;
  });

  const pendingCount = suggestions.filter((s) => s.status === 'PENDING').length;
  const blockingCount = suggestions.filter((s) => s.status === 'PENDING' && s.severity === 'BLOCKING').length;

  return (
    <div className="ai-suggestions-panel">
      <div className="panel-header-row" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
        <div>
          <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <span>🧠 AI Suggestions Engine</span>
            {pendingCount > 0 && (
              <span className={`badge ${blockingCount > 0 ? 'badge-failed' : 'badge-pending'}`}>
                {pendingCount} Pending {blockingCount > 0 ? `(${blockingCount} Blocking)` : ''}
              </span>
            )}
          </h3>
          <p className="dim" style={{ margin: '0.25rem 0 0', fontSize: '0.85rem' }}>
            Zero AI Authority pre-checks: pre-conflicts, missing statutory evidence, and diligence gap detection.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {canEdit && (
            <button
              className="primary"
              onClick={handleGenerate}
              disabled={generating}
              style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}
            >
              <span>{generating ? '⏳ Scanning...' : '⚡ Run Intelligence Scan'}</span>
            </button>
          )}
          <button className="secondary" onClick={load} disabled={loading}>
            ↻ Refresh
          </button>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="tabs" style={{ marginBottom: '1rem' }}>
        {['ALL', 'PENDING', 'ACCEPTED', 'DISMISSED'].map((st) => (
          <button
            key={st}
            className={`tab-btn ${statusFilter === st ? 'active' : ''}`}
            onClick={() => setStatusFilter(st)}
          >
            {st === 'ALL' ? `All (${suggestions.length})` : `${st.charAt(0) + st.slice(1).toLowerCase()} (${suggestions.filter((s) => s.status === st).length})`}
          </button>
        ))}
      </div>

      {loading && <p className="dim">Loading suggestions...</p>}

      {!loading && filtered.length === 0 && (
        <div className="card" style={{ padding: '2rem', textAlign: 'center' }}>
          <p className="dim" style={{ margin: 0 }}>
            {statusFilter === 'PENDING'
              ? 'No pending suggestions! Click "Run Intelligence Scan" to detect pre-conflicts or missing evidence.'
              : 'No suggestions match this filter.'}
          </p>
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
        {filtered.map((s) => {
          let payloadObj = {};
          try {
            payloadObj = typeof s.actionPayload === 'string' ? JSON.parse(s.actionPayload) : (s.actionPayload || {});
          } catch (e) {}

          const severityClass = s.severity === 'BLOCKING' ? 'badge-failed' : s.severity === 'WARNING' ? 'badge-pending' : 'badge-not_applicable';

          return (
            <div
              key={s.id}
              className="card"
              style={{
                borderLeft: s.severity === 'BLOCKING' ? '3px solid #ef4444' : s.severity === 'WARNING' ? '3px solid #f59e0b' : '3px solid #3b82f6',
                padding: '1.1rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.4rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <span className={`badge ${severityClass}`}>{s.severity}</span>
                  <span className="badge badge-not_applicable" style={{ letterSpacing: '0.04em' }}>{s.suggestionType}</span>
                  <strong style={{ fontSize: '0.95rem' }}>{s.title}</strong>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  {s.aiArtifactId && (
                    <button
                      className="secondary small"
                      onClick={() => setSelectedArtifactId(s.aiArtifactId)}
                      title="Inspect model provenance, prompt hash, and cost"
                    >
                      AI Provenance
                    </button>
                  )}
                  <span className={`badge ${s.status === 'ACCEPTED' ? 'badge-complete' : s.status === 'DISMISSED' ? 'badge-rejected' : 'badge-pending'}`}>
                    {s.status}
                  </span>
                </div>
              </div>

              <p style={{ margin: '0.35rem 0 0.65rem', fontSize: '0.88rem', color: 'var(--text)' }}>
                {s.description}
              </p>

              {/* Action payload summary */}
              {payloadObj && Object.keys(payloadObj).length > 0 && (
                <div
                  style={{
                    background: 'rgba(0, 0, 0, 0.25)',
                    padding: '0.5rem 0.75rem',
                    borderRadius: '4px',
                    fontSize: '0.8rem',
                    fontFamily: 'monospace',
                    marginBottom: '0.75rem',
                    border: '1px solid var(--border)',
                  }}
                >
                  {s.suggestionType === 'PRE_CONFLICT' && (
                    <div>
                      <div><strong>Fact Key:</strong> {payloadObj.factKey}</div>
                      <div><strong>Candidate Proposed:</strong> <span style={{ color: '#ef4444' }}>{payloadObj.proposedValue}</span> vs <strong>Existing Fact:</strong> <span style={{ color: '#10b981' }}>{payloadObj.existingValue}</span></div>
                    </div>
                  )}
                  {s.suggestionType === 'MISSING_EVIDENCE' && (
                    <div>
                      <strong>Expected Document Type:</strong> {payloadObj.expectedDocumentType}
                    </div>
                  )}
                  {s.suggestionType === 'DILIGENCE_GAP' && (
                    <div>
                      <strong>Code:</strong> {payloadObj.code} | <strong>Category:</strong> {payloadObj.category}
                    </div>
                  )}
                </div>
              )}

              {/* Footer review actions */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.8rem' }}>
                <span className="dim">
                  Created {new Date(s.createdAt).toLocaleDateString()}
                  {s.reviewedBy && ` · Reviewed by ${s.reviewedBy.fullName || s.reviewedBy.email}`}
                  {s.dismissReason && ` (Reason: "${s.dismissReason}")`}
                </span>

                {s.status === 'PENDING' && canEdit && (
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    {dismissingId === s.id ? (
                      <div style={{ display: 'flex', gap: '0.35rem', alignItems: 'center' }}>
                        <input
                          type="text"
                          placeholder="Dismissal justification..."
                          value={dismissReason}
                          onChange={(e) => setDismissReason(e.target.value)}
                          style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem', width: '220px' }}
                        />
                        <button className="primary small" onClick={() => handleDismiss(s.id)}>Confirm</button>
                        <button className="secondary small" onClick={() => setDismissingId(null)}>Cancel</button>
                      </div>
                    ) : (
                      <>
                        <button className="primary small" onClick={() => handleAccept(s.id)}>
                          ✓ Accept & Apply
                        </button>
                        <button className="secondary small" onClick={() => { setDismissingId(s.id); setDismissReason(''); }}>
                          ✕ Dismiss
                        </button>
                      </>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {selectedArtifactId && (
        <AiProvenanceModal
          artifactId={selectedArtifactId}
          onClose={() => setSelectedArtifactId(null)}
        />
      )}
    </div>
  );
}
