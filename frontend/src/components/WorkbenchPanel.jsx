import { Link } from 'react-router-dom';
import { humanize } from '../constants';

const READINESS_LABELS = {
  NOT_READY: 'Not ready',
  STALE_AFTER_CHANGE: 'Stale after change',
  CONDITIONALLY_READY: 'Conditionally ready',
  READY_FOR_REVIEW: 'Ready for review',
  READY_FOR_FILING: 'Ready for filing',
};

const SEVERITY_ORDER = ['BLOCKING', 'STALE', 'CONDITIONAL', 'AWAITING_REVIEW'];

const SEVERITY_LABELS = {
  BLOCKING: 'Blocking',
  STALE: 'Stale after a change',
  CONDITIONAL: 'Known gaps',
  AWAITING_REVIEW: 'Awaiting review',
};

function gaugeClass(readiness) {
  if (readiness === 'READY_FOR_FILING' || readiness === 'READY_FOR_REVIEW') return 'readiness-gauge ready';
  if (readiness === 'CONDITIONALLY_READY') return 'readiness-gauge conditional';
  return 'readiness-gauge blocked';
}

/** Where to go to act on a blocker. */
function BlockerLink({ blocker, onOpenTab }) {
  if (blocker.objectType === 'FACT') {
    return <Link to={`/facts/${blocker.objectId}/trace`}>{blocker.title}</Link>;
  }
  const tab = { CONFLICT: 'conflicts', DISCLOSURE: 'drhp', DOCUMENT: 'drhp' }[blocker.objectType];
  if (tab) {
    return <button type="button" className="link-button" onClick={() => onOpenTab(tab)}>{blocker.title}</button>;
  }
  return <span>{blocker.title}</span>;
}

/**
 * Spec §8: what prevents this transaction from being confidently submitted right now?
 */
export default function WorkbenchPanel({ workbench, onOpenTab }) {
  if (!workbench) return null;
  const { readiness, explanation, blockers, coverage } = workbench;

  return (
    <section>
      <div className="page-header"><h2>Diligence workbench</h2></div>
      <div className={gaugeClass(readiness)}>
        <div className="readiness-gauge-state">{READINESS_LABELS[readiness] || humanize(readiness)}</div>
        <div className="hint">{explanation}</div>
      </div>

      <div className="detail-grid">
        <div><strong>Material facts</strong><span>{coverage.materialFacts}</span></div>
        <div><strong>Verified</strong><span>{coverage.verified} of {coverage.materialFacts}</span></div>
        <div><strong>With evidence</strong><span>{coverage.withEvidence} of {coverage.materialFacts}</span></div>
        <div><strong>Evidence assessed acceptable</strong><span>{coverage.withAcceptableEvidence} of {coverage.materialFacts}</span></div>
        <div><strong>In open conflict</strong><span>{coverage.inOpenConflict}</span></div>
        <div><strong>Stale disclosures</strong><span>{coverage.staleDisclosures} of {coverage.totalDisclosures}</span></div>
      </div>

      {blockers.length === 0 && (
        <div className="empty-state"><span>Nothing is blocking this transaction under the configured rules.</span></div>
      )}

      {SEVERITY_ORDER.map((severity) => {
        const group = blockers.filter((b) => b.severity === severity);
        if (group.length === 0) return null;
        return (
          <div key={severity}>
            <h3>{SEVERITY_LABELS[severity]} <span className="tab-count">{group.length}</span></h3>
            <table className="data-table">
              <thead>
                <tr><th>Item</th><th>Why</th><th>Next action</th><th>Owner</th></tr>
              </thead>
              <tbody>
                {group.map((b, i) => (
                  <tr key={`${b.type}-${b.objectId}-${i}`}>
                    <td><BlockerLink blocker={b} onOpenTab={onOpenTab} /></td>
                    <td>{b.reason}</td>
                    <td>{b.nextAction}</td>
                    <td>{b.ownerRole ? humanize(b.ownerRole) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      })}
    </section>
  );
}
