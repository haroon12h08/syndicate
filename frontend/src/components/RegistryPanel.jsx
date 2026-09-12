import { Link } from 'react-router-dom';

const STATUS_LABELS = {
  VERIFIED_MATCH: 'Verified — matches registry',
  MISMATCH_DETECTED: 'Mismatch detected',
  NEVER_RUN: 'Never run',
  NOT_IN_REGISTRY: 'Not found in registry',
};

function statusClass(status) {
  if (status === 'VERIFIED_MATCH') return 'registry-status ok';
  if (status === 'MISMATCH_DETECTED') return 'registry-status warn';
  if (status === 'NOT_IN_REGISTRY') return 'registry-status bad';
  return 'registry-status idle';
}

export default function RegistryPanel({ registry, verifying, onVerify }) {
  const status = registry?.status || 'NEVER_RUN';
  const mismatches = (registry?.comparisons || []).filter((c) => !c.match);

  return (
    <section className="registry-panel">
      <div className="page-header">
        <h2>Registry verification</h2>
        <button onClick={onVerify} disabled={verifying}>
          {verifying ? 'Checking registry…' : 'Verify with MCA Registry'}
        </button>
      </div>

      <div className={statusClass(status)}>
        <div className="registry-status-head">
          {verifying && <span className="spinner" aria-hidden="true" />}
          <strong>{STATUS_LABELS[status] || status}</strong>
          {registry?.source && <span className="hint">via {registry.source}</span>}
        </div>
        <div className="hint">
          {registry?.fetchedAt
            ? `Last checked ${new Date(registry.fetchedAt).toLocaleString()}`
            : 'This company has not been checked against the registry yet.'}
          {registry?.lookupKey && <> · {registry.lookupKey}</>}
          {registry?.payloadSha256 && (
            <> · snapshot <code>{registry.payloadSha256.slice(0, 12)}…</code></>
          )}
        </div>
      </div>

      {mismatches.length > 0 && (
        <div className="registry-alert">
          <strong>
            {mismatches.length} field{mismatches.length > 1 ? 's' : ''} disagree with the registry.
          </strong>
          <p className="hint">
            No value has been selected automatically — confirm which source is correct.
          </p>

          <table className="data-table registry-compare">
            <thead>
              <tr><th>Field</th><th>Syndicate</th><th>MCA registry</th><th></th></tr>
            </thead>
            <tbody>
              {mismatches.map((c) => (
                <tr key={c.field}>
                  <td>
                    {c.field}
                    <div className="hint">{c.internalSource}</div>
                  </td>
                  <td className="registry-value internal">{c.internalValue || '(not recorded)'}</td>
                  <td className="registry-value external">{c.registryValue || '(not recorded)'}</td>
                  <td>
                    {c.issueWorkstreamId ? (
                      <Link to={`/workstreams/${c.issueWorkstreamId}`}>
                        <button className="secondary">Resolve</button>
                      </Link>
                    ) : (
                      <span className="hint">No transaction yet</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {registry && status === 'VERIFIED_MATCH' && (
        <p className="hint">{registry.message}</p>
      )}
      {registry && status === 'NOT_IN_REGISTRY' && (
        <div className="registry-alert"><strong>{registry.message}</strong></div>
      )}
    </section>
  );
}
