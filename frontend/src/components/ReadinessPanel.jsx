import { useState } from 'react';
import { Link } from 'react-router-dom';
import { humanize } from '../constants';

const STATE_LABELS = {
  READY_FOR_FILING: 'Ready for filing',
  CONDITIONALLY_READY: 'Conditionally ready',
  NOT_READY: 'Not ready',
};

function stateClass(state) {
  if (state === 'READY_FOR_FILING') return 'readiness-gauge ready';
  if (state === 'CONDITIONALLY_READY') return 'readiness-gauge conditional';
  return 'readiness-gauge blocked';
}

function ruleStatusClass(status) {
  if (status === 'PASSED') return 'badge badge-complete';
  if (status === 'FAILED') return 'badge badge-failed';
  if (status === 'MISSING_EVIDENCE') return 'badge badge-pending';
  return 'badge badge-not_applicable';
}

export default function ReadinessPanel({ readiness, onEvaluate, evaluating }) {
  const [openRule, setOpenRule] = useState(null);
  const [drawerRule, setDrawerRule] = useState(null);

  if (!readiness) return null;

  const { state, rulesTotal, rulesPassed, rulesFailed, rulesMissingEvidence, lastEvaluatedAt } = readiness;
  const pct = rulesTotal > 0 ? Math.round((rulesPassed / rulesTotal) * 100) : 0;

  return (
    <section className="readiness-panel">
      <div className="page-header">
        <h2>Filing readiness</h2>
        <button onClick={onEvaluate} disabled={evaluating}>
          {evaluating ? 'Evaluating...' : 'Re-evaluate rules'}
        </button>
      </div>

      <div className={stateClass(state)}>
        <div className="readiness-gauge-state">{STATE_LABELS[state] || humanize(state)}</div>
        <div className="readiness-gauge-bar">
          <span style={{ width: `${pct}%` }} />
        </div>
        <div className="readiness-gauge-meta">
          {rulesPassed}/{rulesTotal} rules satisfied
          {rulesFailed > 0 && <> · {rulesFailed} failing</>}
          {rulesMissingEvidence > 0 && <> · {rulesMissingEvidence} awaiting evidence</>}
          {lastEvaluatedAt && <> · evaluated {new Date(lastEvaluatedAt).toLocaleString()}</>}
        </div>
      </div>

      {rulesTotal === 0 && (
        <p className="hint">No rules have been evaluated yet. Run an evaluation to assess this transaction.</p>
      )}

      <div className="rule-accordion">
        {readiness.rules.map((rule) => (
          <div key={rule.id} className="rule-row">
            <button
              className="rule-row-head"
              onClick={() => setOpenRule(openRule === rule.id ? null : rule.id)}
            >
              <span className={ruleStatusClass(rule.status)}>{humanize(rule.status)}</span>
              <span className="rule-row-title">{rule.ruleTitle}</span>
              <span className="rule-row-code">{rule.ruleCode}</span>
            </button>
            {openRule === rule.id && (
              <div className="rule-row-body">
                <p>{rule.ruleDescription}</p>
                <div className="detail-grid">
                  <div><strong>Required</strong><span>{rule.requirementSummary}</span></div>
                  <div><strong>Current value</strong><span>{rule.actualValue || 'Not recorded'}</span></div>
                  <div><strong>Owning workstream</strong><span>{humanize(rule.workstreamType)}</span></div>
                  <div><strong>Severity</strong><span>{humanize(rule.severity)}</span></div>
                </div>
                <p className="hint">{rule.detail}</p>
                <div className="hint">
                  Supporting facts:{' '}
                  {rule.supportingFactIds.length === 0
                    ? 'none'
                    : rule.supportingFactIds.map((fid) => <code key={fid}>{fid.slice(0, 8)} </code>)}
                </div>
                {rule.status !== 'PASSED' && (
                  <button onClick={() => setDrawerRule(rule)}>Inspect blocking detail</button>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {drawerRule && (
        <div className="drawer-backdrop" onClick={() => setDrawerRule(null)}>
          <aside className="drawer" onClick={(e) => e.stopPropagation()}>
            <div className="page-header">
              <h3>{drawerRule.ruleTitle}</h3>
              <button className="secondary" onClick={() => setDrawerRule(null)}>Close</button>
            </div>
            <span className={ruleStatusClass(drawerRule.status)}>{humanize(drawerRule.status)}</span>
            <p>{drawerRule.ruleDescription}</p>

            <div className="detail-grid">
              <div><strong>Required threshold</strong><span>{drawerRule.requirementSummary}</span></div>
              <div>
                <strong>{drawerRule.status === 'MISSING_EVIDENCE' ? 'Missing fact' : 'Current value'}</strong>
                <span>{drawerRule.actualValue || 'No verified fact recorded'}</span>
              </div>
            </div>
            <p className="hint">{drawerRule.detail}</p>

            {drawerRule.generatedIssueWorkstreamId && (
              <Link className="drawer-cta" to={`/workstreams/${drawerRule.generatedIssueWorkstreamId}`}>
                Go to {humanize(drawerRule.workstreamType)} fact verification
              </Link>
            )}
          </aside>
        </div>
      )}

      {readiness.blockingIssues.length > 0 && (
        <>
          <h3 className="eyebrow">Blocking issues</h3>
          <table className="data-table">
            <thead>
              <tr><th>Issue</th><th>Workstream</th><th>Severity</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {readiness.blockingIssues.map((issue) => (
                <tr key={issue.id}>
                  <td>{issue.title}</td>
                  <td>{humanize(issue.workstreamType)}</td>
                  <td><span className="badge badge-failed">{humanize(issue.severity)}</span></td>
                  <td>{humanize(issue.status)}</td>
                  <td><Link to={`/workstreams/${issue.workstreamId}`}>Open workstream</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
