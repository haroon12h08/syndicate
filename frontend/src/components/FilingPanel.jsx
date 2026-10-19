import { useEffect, useState } from 'react';
import { humanize } from '../constants';
import { apiDownload } from '../api/client';
import { downloadDrhpPdf, downloadDrhpDocx } from '../api/drhp';
import { downloadStatementOfChanges, getDocumentDiff } from '../api/filing';

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/**
 * The last mile: who has approved this exact document, what is still in the way, and the package
 * to hand to whoever files. Syndicate does not file anything itself.
 *
 * For resubmissions (version > 1), presents the semantic diff against the previous filing version
 * and allows downloading the regulatory Statement of Changes.
 */
export default function FilingPanel({ transactionId, filing, currentRole, onApprove, onAssemble }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [diff, setDiff] = useState(null);
  const [loadingDiff, setLoadingDiff] = useState(false);

  if (!filing) return null;
  const { documentId, documentVersion, documentStatus, approvals, awaitingApprovalFrom, blockers,
    readyToAssemble, filingPackage } = filing;

  useEffect(() => {
    let active = true;
    if (documentId && documentVersion > 1) {
      setLoadingDiff(true);
      getDocumentDiff(documentId)
        .then((data) => {
          if (active) setDiff(data);
        })
        .catch(() => {
          if (active) setDiff(null);
        })
        .finally(() => {
          if (active) setLoadingDiff(false);
        });
    } else {
      setDiff(null);
    }
    return () => {
      active = false;
    };
  }, [documentId, documentVersion]);

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
    await apiDownload(`/drhp/versions/${documentId}/filing-package`);
  }

  const myApprovalOutstanding = currentRole && awaitingApprovalFrom.includes(currentRole);

  const changedFacts = diff?.facts?.filter((f) => f.changeType !== 'UNCHANGED') || [];
  const changedDisclosures = diff?.disclosures?.filter((d) => d.changeType !== 'UNCHANGED') || [];

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
              <button className="secondary" onClick={() => run(download)}>
                Download {filingPackage.fileName} ({formatSize(filingPackage.sizeBytes)})
              </button>
            )}
            {documentId && (
              <>
                <button className="secondary" disabled={busy} onClick={() => run(() => downloadDrhpPdf(documentId))}>
                  Download PDF
                </button>
                <button className="secondary" disabled={busy} onClick={() => run(() => downloadDrhpDocx(documentId))}>
                  Download DOCX
                </button>
              </>
            )}
            {documentVersion > 1 && (
              <button className="secondary" disabled={busy} onClick={() => run(() => downloadStatementOfChanges(documentId))}>
                Download Statement of Changes
              </button>
            )}
          </div>

          {filingPackage && (
            <p className="hint">
              Assembled {new Date(filingPackage.builtAt).toLocaleString()} by {filingPackage.builtByName}.
              SHA-256 {filingPackage.sha256.slice(0, 16)}… · contains the document, the evidence index,
              the provenance manifest, the approval certificate
              {documentVersion > 1 ? ', and the formal statement of changes' : ''}.
            </p>
          )}

          {documentVersion > 1 && diff && (
            <div className="resubmission-diff" style={{ marginTop: '2.5rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                <h3 className="eyebrow" style={{ margin: 0 }}>
                  Resubmission Tracking & Version Diff (v{diff.baseDocument?.version} → v{diff.targetDocument?.version})
                </h3>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
                <div style={{ padding: '0.75rem 1rem', background: 'rgba(255,255,255,0.03)', borderRadius: '4px', border: '1px solid var(--border)' }}>
                  <div className="hint" style={{ fontSize: '0.75rem' }}>Facts Superseded</div>
                  <strong style={{ fontSize: '1.25rem', color: diff.stats.factsSuperseded > 0 ? 'var(--warning, #f2c94c)' : 'inherit' }}>
                    {diff.stats.factsSuperseded}
                  </strong>
                </div>
                <div style={{ padding: '0.75rem 1rem', background: 'rgba(255,255,255,0.03)', borderRadius: '4px', border: '1px solid var(--border)' }}>
                  <div className="hint" style={{ fontSize: '0.75rem' }}>Facts Added</div>
                  <strong style={{ fontSize: '1.25rem' }}>{diff.stats.factsAdded}</strong>
                </div>
                <div style={{ padding: '0.75rem 1rem', background: 'rgba(255,255,255,0.03)', borderRadius: '4px', border: '1px solid var(--border)' }}>
                  <div className="hint" style={{ fontSize: '0.75rem' }}>Disclosures Modified</div>
                  <strong style={{ fontSize: '1.25rem', color: diff.stats.disclosuresModified > 0 ? 'var(--warning, #f2c94c)' : 'inherit' }}>
                    {diff.stats.disclosuresModified}
                  </strong>
                </div>
                <div style={{ padding: '0.75rem 1rem', background: 'rgba(255,255,255,0.03)', borderRadius: '4px', border: '1px solid var(--border)' }}>
                  <div className="hint" style={{ fontSize: '0.75rem' }}>Queries Addressed</div>
                  <strong style={{ fontSize: '1.25rem', color: diff.stats.observationsAddressed > 0 ? 'var(--success, #7ee787)' : 'inherit' }}>
                    {diff.stats.observationsAddressed}
                  </strong>
                </div>
              </div>

              <h4 style={{ margin: '1.5rem 0 0.5rem 0', fontSize: '0.95rem' }}>Schedule of Fact Corrections</h4>
              {changedFacts.length === 0 ? (
                <p className="hint">No underlying facts were modified between these versions.</p>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Fact / Label</th>
                      <th>Period</th>
                      <th>Status</th>
                      <th>Previous (v{diff.baseDocument?.version})</th>
                      <th>Resubmission (v{diff.targetDocument?.version})</th>
                      <th>Reason for Change</th>
                      <th>Corrected By</th>
                    </tr>
                  </thead>
                  <tbody>
                    {changedFacts.map((f, i) => (
                      <tr key={i}>
                        <td>
                          <strong>{f.label}</strong>
                          <div className="hint" style={{ fontSize: '0.75rem' }}>{f.factKey}</div>
                        </td>
                        <td>{f.period || '—'}</td>
                        <td>
                          <span className={`badge ${f.changeType.includes('SUPERSEDED') ? 'badge-pending' : 'badge-complete'}`}>
                            {humanize(f.changeType)}
                          </span>
                        </td>
                        <td><code>{f.baseValue || 'None'}</code></td>
                        <td><code>{f.targetValue || 'None'} {f.unit || ''}</code></td>
                        <td>{f.changeReason || <span className="hint">—</span>}</td>
                        <td>
                          {f.correctedBy ? (
                            <div>
                              {f.correctedBy}
                              {f.correctedAt && (
                                <div className="hint" style={{ fontSize: '0.7rem' }}>
                                  {new Date(f.correctedAt).toLocaleDateString()}
                                </div>
                              )}
                            </div>
                          ) : (
                            <span className="hint">—</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              <h4 style={{ margin: '1.5rem 0 0.5rem 0', fontSize: '0.95rem' }}>Disclosure Changes</h4>
              {changedDisclosures.length === 0 ? (
                <p className="hint">No disclosures were modified between these versions.</p>
              ) : (
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Section</th>
                      <th>Title</th>
                      <th>Change Type</th>
                      <th>Affected Facts</th>
                    </tr>
                  </thead>
                  <tbody>
                    {changedDisclosures.map((d, i) => (
                      <tr key={i}>
                        <td><code>{d.sectionCode}</code></td>
                        <td>{d.title}</td>
                        <td>
                          <span className={`badge ${d.changeType === 'MODIFIED' ? 'badge-pending' : 'badge-complete'}`}>
                            {humanize(d.changeType)}
                          </span>
                        </td>
                        <td>
                          {d.affectedFactKeys?.length > 0 ? (
                            <div style={{ display: 'flex', gap: '0.25rem', flexWrap: 'wrap' }}>
                              {d.affectedFactKeys.map((k) => (
                                <span key={k} className="badge" style={{ fontSize: '0.75rem' }}>{k}</span>
                              ))}
                            </div>
                          ) : (
                            <span className="hint">—</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              {diff.addressedObservations?.length > 0 && (
                <>
                  <h4 style={{ margin: '1.5rem 0 0.5rem 0', fontSize: '0.95rem' }}>Regulatory Observations Addressed</h4>
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Authority</th>
                        <th>Reference</th>
                        <th>Observation</th>
                        <th>Status</th>
                        <th>Approved Response</th>
                      </tr>
                    </thead>
                    <tbody>
                      {diff.addressedObservations.map((o) => (
                        <tr key={o.id}>
                          <td><strong>{o.authority}</strong></td>
                          <td><code>{o.reference || o.id.slice(0, 8)}</code></td>
                          <td>{o.observation}</td>
                          <td>
                            <span className={`badge ${o.status === 'APPROVED' ? 'badge-complete' : 'badge-pending'}`}>
                              {humanize(o.status)}
                            </span>
                          </td>
                          <td>{o.responseDraft || <span className="hint">—</span>}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </>
              )}
            </div>
          )}
        </>
      )}
    </section>
  );
}
