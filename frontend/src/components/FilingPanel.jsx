import { useState } from 'react';
import { humanize } from '../constants';
import { apiDownload } from '../api/client';
import * as filingApi from '../api/filing';

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/**
 * The last mile: who has approved this exact document, what is still in the way, and the package
 * to hand to whoever files. Syndicate does not file anything itself.
 */
export default function FilingPanel({ filing, currentRole, onApprove, onAssemble }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  if (!filing) return null;
  const { documentId, documentVersion, documentStatus, approvals, awaitingApprovalFrom, blockers,
    readyToAssemble, filingPackage } = filing;

  async function run(action) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function download() {
    const blob = await apiDownload(`/drhp/versions/${documentId}/filing-package`);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filingPackage.fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  const myApprovalOutstanding = currentRole && awaitingApprovalFrom.includes(currentRole);

  return (
    <section>
      <div className="page-header"><h2>Filing</h2></div>
      {!documentId && (
        <div className="empty-state">
          <span>Compile the document on the DRHP tab first. The package is assembled from a compiled, approved version.</span>
        </div>
      )}

      {documentId && (
        <>
          <div className={`drhp-status ${documentStatus === 'COMPILED' ? 'ok' : 'invalid'}`}>
            <strong>DRHP v{documentVersion} — {humanize(documentStatus)}</strong>
            {blockers.length > 0 && (
              <ul className="hint">{blockers.map((b, i) => <li key={i}>{b}</li>)}</ul>
            )}
            {readyToAssemble && !filingPackage && (
              <div className="hint">Approved and unchanged since. Ready to assemble.</div>
            )}
          </div>

          <h3 className="eyebrow">Approvals</h3>
          <table className="data-table">
            <thead><tr><th>Role</th><th>Approved by</th><th>When</th><th>Status</th></tr></thead>
            <tbody>
              {approvals.map((a) => (
                <tr key={a.id}>
                  <td>{humanize(a.role)}</td>
                  <td>{a.approver.fullName}</td>
                  <td>{new Date(a.grantedAt).toLocaleString()}</td>
                  <td>
                    {a.current
                      ? <span className="badge badge-complete">Current</span>
                      : <span className="badge badge-failed" title={a.invalidationReason}>Invalidated</span>}
                  </td>
                </tr>
              ))}
              {awaitingApprovalFrom.map((role) => (
                <tr key={role}>
                  <td>{humanize(role)}</td>
                  <td className="hint" colSpan={2}>Not yet approved</td>
                  <td><span className="badge badge-pending">Awaited</span></td>
                </tr>
              ))}
            </tbody>
          </table>

          {error && <div className="error-banner">{error}</div>}

          <div className="inline-form">
            {myApprovalOutstanding && documentStatus === 'COMPILED' && (
              <button disabled={busy} onClick={() => run(() => onApprove(documentId))}>
                Approve DRHP v{documentVersion} as {humanize(currentRole)}
              </button>
            )}
            {readyToAssemble && !filingPackage && (
              <button disabled={busy} onClick={() => run(() => onAssemble(documentId))}>
                Assemble filing package
              </button>
            )}
            {filingPackage && (
              <button className="secondary" onClick={download}>
                Download {filingPackage.fileName} ({formatSize(filingPackage.sizeBytes)})
              </button>
            )}
          </div>

          {filingPackage && (
            <p className="hint">
              Assembled {new Date(filingPackage.builtAt).toLocaleString()} by {filingPackage.builtByName}.
              SHA-256 {filingPackage.sha256.slice(0, 16)}… · contains the document, the evidence index,
              the provenance manifest and the approval certificate.
            </p>
          )}
        </>
      )}
    </section>
  );
}
