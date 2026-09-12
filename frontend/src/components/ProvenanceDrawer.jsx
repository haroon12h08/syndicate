import { humanize } from '../constants';

const LEAF_LABELS = {
  FACT: 'Cited fact',
  EVIDENCE: 'Evidence file',
  APPROVAL: 'Approval signature',
};

/** Pulls the version out of the canonical leaf string, which is shaped TYPE|id|v3|label|... */
function versionOf(canonical) {
  const match = canonical?.match(/\|v(\d+)\|/);
  return match ? `v${match[1]}` : null;
}

/** Evidence leaves end with the file's SHA-256. */
function evidenceHashOf(canonical) {
  const parts = (canonical || '').split('|');
  return parts.length >= 4 ? parts[3] : null;
}

export default function ProvenanceDrawer({ provenance, onClose }) {
  if (!provenance) return null;

  function downloadManifest() {
    const blob = new Blob([JSON.stringify(provenance, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `provenance-v${provenance.documentVersion}-${provenance.merkleRoot.slice(0, 12)}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  const grouped = ['FACT', 'EVIDENCE', 'APPROVAL'].map((type) => ({
    type,
    leaves: provenance.leaves.filter((l) => l.type === type),
  }));

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer provenance-drawer" onClick={(e) => e.stopPropagation()}>
        <div className="page-header">
          <h3>Provenance manifest</h3>
          <button className="secondary" onClick={onClose}>Close</button>
        </div>

        {provenance.rootVerified ? (
          <div className="integrity-badge ok">
            <span className="integrity-check">✓</span>
            <div>
              <strong>Cryptographic State Integrity Verified</strong>
              <div className="hint">
                The root was recomputed from the stored leaves and matches.
              </div>
            </div>
          </div>
        ) : (
          <div className="integrity-badge bad">
            <span className="integrity-check">!</span>
            <div>
              <strong>Integrity check failed</strong>
              <div className="hint">The stored root does not match the recorded leaves.</div>
            </div>
          </div>
        )}

        <div className="detail-grid">
          <div><strong>Document</strong><span>v{provenance.documentVersion} · {humanize(provenance.compileMode)}</span></div>
          <div><strong>Algorithm</strong><span>{provenance.algorithm}</span></div>
          <div><strong>Leaf nodes</strong><span>{provenance.leafCount}</span></div>
          <div><strong>Compiled</strong><span>{new Date(provenance.compiledAt).toLocaleString()}</span></div>
        </div>

        <div className="hash-block">
          <div className="eyebrow">Merkle root</div>
          <code>{provenance.merkleRoot}</code>
        </div>

        {grouped.map(({ type, leaves }) => leaves.length > 0 && (
          <div key={type} className="leaf-group">
            <h4 className="eyebrow">{LEAF_LABELS[type]} ({leaves.length})</h4>
            <ul className="leaf-list">
              {leaves.map((leaf) => (
                <li key={leaf.index}>
                  <div className="leaf-head">
                    <span className="leaf-index">#{leaf.index}</span>
                    <strong>{leaf.label}</strong>
                    {versionOf(leaf.canonical) && (
                      <span className="badge badge-not_applicable">{versionOf(leaf.canonical)}</span>
                    )}
                  </div>
                  {type === 'EVIDENCE' && (
                    <div className="hint">file sha256 <code>{evidenceHashOf(leaf.canonical)}</code></div>
                  )}
                  <div className="hint">leaf hash <code>{leaf.hash}</code></div>
                  <div className="hint">{leaf.proof.length} step proof to root</div>
                </li>
              ))}
            </ul>
          </div>
        ))}

        <button onClick={downloadManifest}>Download manifest JSON</button>
        <p className="hint">
          The JSON contains every leaf and its audit path, so the root can be recomputed
          offline without trusting this server.
        </p>
      </aside>
    </div>
  );
}
