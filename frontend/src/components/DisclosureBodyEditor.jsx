import { useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { useFactDefinitions } from './FactKeyPicker';
import * as aiApi from '../api/ai';
import AiProvenanceModal from './AiProvenanceModal';

/**
 * Writing a disclosure means writing prose and pointing at the facts it rests on. The reference
 * the system stores is a key; the writer picks a value by name and sees it in place.
 */
export default function DisclosureBodyEditor({
  value,
  onChange,
  facts = [],
  transactionId,
  sectionCode = 'CAPITAL_STRUCTURE',
  title = '',
  onSuggestedTitle,
}) {
  const definitions = useFactDefinitions();
  const [picking, setPicking] = useState(false);
  const [query, setQuery] = useState('');
  const [aiModalOpen, setAiModalOpen] = useState(false);
  const [drafting, setDrafting] = useState(false);
  const [instructions, setInstructions] = useState('');
  const [previewDraft, setPreviewDraft] = useState(null);
  const [provenanceArtifactId, setProvenanceArtifactId] = useState(null);
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

  const handleGenerateDraft = async () => {
    if (!transactionId) {
      toast.error('Transaction ID required for AI draft');
      return;
    }
    setDrafting(true);
    try {
      const res = await aiApi.draftDisclosure(transactionId, {
        sectionCode: sectionCode || 'CAPITAL_STRUCTURE',
        title: title || 'Disclosure',
        userInstructions: instructions || 'Draft comprehensive DRHP section disclosure citing verified facts with valid placeholders',
      });
      setPreviewDraft(res);
      toast.success('Generated draft disclosure citing ' + (res.citedFactKeys?.length || 0) + ' verified facts');
    } catch (err) {
      toast.error('AI Draft failed: ' + (err.message || 'Error'));
    } finally {
      setDrafting(false);
    }
  };

  const handleApplyDraft = () => {
    if (previewDraft) {
      onChange(previewDraft.bodyTemplate);
      if (previewDraft.suggestedTitle && onSuggestedTitle) {
        onSuggestedTitle(previewDraft.suggestedTitle);
      }
      toast.success('Applied AI draft into editor');
      setAiModalOpen(false);
    }
  };

  return (
    <div className="disclosure-editor">
      <textarea
        ref={areaRef}
        rows={6}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Write the disclosure. Use 'Insert value' or '✨ AI Draft Assist' to link verified facts."
        required
      />
      <div className="inline-form" style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '0.4rem' }}>
        <button type="button" className="secondary" onClick={() => setPicking((p) => !p)}>
          {picking ? 'Cancel' : 'Insert value'}
        </button>
        {transactionId && (
          <button
            type="button"
            className="secondary"
            onClick={() => { setAiModalOpen(true); setPreviewDraft(null); }}
            style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', borderColor: '#8b5cf6', color: '#c084fc' }}
          >
            <span>✨ AI Draft Assist</span>
          </button>
        )}
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

      {/* AI Draft Assist Modal */}
      {aiModalOpen && (
        <div className="palette-backdrop" onClick={() => setAiModalOpen(false)}>
          <div
            className="palette"
            onClick={(e) => e.stopPropagation()}
            style={{ maxWidth: '640px', padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '0.85rem' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span>✨ AI Disclosure Draft Assistant</span>
              </h3>
              <button className="secondary small" onClick={() => setAiModalOpen(false)}>✕</button>
            </div>

            <p className="dim" style={{ margin: 0, fontSize: '0.85rem' }}>
              Generates formal DRHP prose citing active verified transaction facts via valid <code>{"{{fact:<key>@<period>}}"}</code> placeholders.
            </p>

            <div>
              <label style={{ fontSize: '0.8rem', display: 'block', marginBottom: '0.3rem' }}>
                Drafting Instructions (Optional)
              </label>
              <input
                type="text"
                placeholder="e.g. Focus on capital structure, authorized vs paid up capital, and promoter shareholding..."
                value={instructions}
                onChange={(e) => setInstructions(e.target.value)}
                style={{ width: '100%' }}
              />
            </div>

            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                type="button"
                className="primary"
                onClick={handleGenerateDraft}
                disabled={drafting}
              >
                {drafting ? '⏳ Drafting with LLM...' : 'Generate Draft Prose'}
              </button>
            </div>

            {previewDraft && (
              <div style={{ marginTop: '0.5rem', display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <strong style={{ fontSize: '0.88rem' }}>Generated Draft Preview</strong>
                  {previewDraft.aiArtifactId && (
                    <button
                      type="button"
                      className="secondary small"
                      onClick={() => setProvenanceArtifactId(previewDraft.aiArtifactId)}
                      style={{ fontSize: '0.72rem' }}
                    >
                      🔍 AI Provenance
                    </button>
                  )}
                </div>

                <div
                  style={{
                    background: 'var(--surface)',
                    border: '1px solid var(--border)',
                    borderRadius: '4px',
                    padding: '0.85rem',
                    fontSize: '0.85rem',
                    lineHeight: '1.5',
                    maxHeight: '200px',
                    overflowY: 'auto',
                    whiteSpace: 'pre-wrap',
                  }}
                >
                  {previewDraft.bodyTemplate}
                </div>

                {previewDraft.citedFactKeys && previewDraft.citedFactKeys.length > 0 && (
                  <div style={{ fontSize: '0.78rem' }}>
                    <span className="dim">Cited Verified Facts: </span>
                    {previewDraft.citedFactKeys.map((k) => (
                      <code key={k} style={{ marginRight: '0.4rem', color: '#10b981' }}>{k}</code>
                    ))}
                  </div>
                )}

                <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
                  <button type="button" className="primary" onClick={handleApplyDraft}>
                    Insert into Disclosure
                  </button>
                  <button type="button" className="secondary" onClick={() => setPreviewDraft(null)}>
                    Discard
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {provenanceArtifactId && (
        <AiProvenanceModal
          artifactId={provenanceArtifactId}
          onClose={() => setProvenanceArtifactId(null)}
        />
      )}
    </div>
  );
}
