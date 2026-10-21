import { useEffect, useState } from 'react';
import * as aiApi from '../api/ai';

export default function AiProvenanceModal({ artifactId, onClose }) {
  const [artifact, setArtifact] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!artifactId) return;
    setLoading(true);
    aiApi.getAiArtifact(artifactId)
      .then((data) => setArtifact(data))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [artifactId]);

  if (!artifactId) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card ai-provenance-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>AI Provenance Manifest (Spec &sect;27)</h3>
          <button className="icon-button" onClick={onClose}>&times;</button>
        </div>

        {loading && <div className="hint" style={{ padding: '1rem' }}>Loading provenance record...</div>}
        {error && <div className="error-banner" style={{ margin: '1rem' }}>{error}</div>}

        {artifact && (
          <div className="modal-body ai-provenance-body">
            <div className="ai-provenance-grid">
              <div className="provenance-item">
                <span className="label">Provider &amp; Model</span>
                <span className="value"><strong>{artifact.provider}</strong> / {artifact.model}</span>
              </div>
              <div className="provenance-item">
                <span className="label">Confidence</span>
                <span className="value">
                  {artifact.confidence ? `${Math.round(artifact.confidence * 100)}%` : 'N/A'}
                </span>
              </div>
              <div className="provenance-item">
                <span className="label">Prompt Template</span>
                <span className="value">{artifact.promptTemplateName} ({artifact.promptTemplateVersion})</span>
              </div>
              <div className="provenance-item">
                <span className="label">Tokens &amp; Cost</span>
                <span className="value">
                  {artifact.promptTokens || 0} in / {artifact.completionTokens || 0} out &middot; ${artifact.totalCostUsd || '0.000000'}
                </span>
              </div>
            </div>

            <div className="provenance-section">
              <span className="label">Prompt Template SHA-256 Hash</span>
              <code className="hash-code">{artifact.promptTemplateHash}</code>
            </div>

            <div className="provenance-section">
              <span className="label">Review State</span>
              <div style={{ marginTop: '0.25rem' }}>
                <span className={`badge ${artifact.reviewDecision === 'ACCEPTED' ? 'badge-complete' : artifact.reviewDecision === 'REJECTED' ? 'badge-failed' : 'badge-pending'}`}>
                  {artifact.reviewDecision}
                </span>
                {artifact.reviewer && (
                  <span className="hint" style={{ marginLeft: '0.5rem' }}>
                    Reviewed by {artifact.reviewer.email}
                  </span>
                )}
              </div>
              {artifact.reviewNotes && (
                <div className="hint" style={{ marginTop: '0.25rem' }}>
                  Note: &ldquo;{artifact.reviewNotes}&rdquo;
                </div>
              )}
            </div>

            {artifact.rawOutput && (
              <div className="provenance-section">
                <span className="label">Raw Model Output</span>
                <pre className="raw-output-block">{artifact.rawOutput}</pre>
              </div>
            )}
          </div>
        )}

        <div className="modal-footer">
          <button type="button" className="secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}
