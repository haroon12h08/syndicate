import { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import * as regApi from '../api/regulatory';

const LIFECYCLE_STAGES = [
  'DRAFT',
  'ACTIVE',
  'DUE_DILIGENCE',
  'READINESS_REVIEW',
  'DOCUMENT_PREPARATION',
  'INTERNAL_APPROVAL',
  'FILING_READY',
  'FILED',
  'OBSERVATIONS',
  'RESPONSE',
  'RESUBMISSION',
  'ISSUE_PREPARATION',
  'LISTED'
];

export default function RegulatoryWorkbench({ transactionId, onStateChange }) {
  const [snapshot, setSnapshot] = useState(null);
  const [drift, setDrift] = useState(null);
  const [evaluations, setEvaluations] = useState([]);
  const [exceptions, setExceptions] = useState([]);
  const [requirements, setRequirements] = useState([]);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [evaluating, setEvaluating] = useState(false);

  // Sub-tabs: controls, exceptions, governance, history
  const [activeSubTab, setActiveSubTab] = useState('controls');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Modals
  const [showTransitionModal, setShowTransitionModal] = useState(false);
  const [targetState, setTargetState] = useState('');
  const [transitionReason, setTransitionReason] = useState('');
  const [forceWithException, setForceWithException] = useState(false);
  const [transitionBlockers, setTransitionBlockers] = useState([]);
  const [checkingBlockers, setCheckingBlockers] = useState(false);

  const [showExceptionModal, setShowExceptionModal] = useState(false);
  const [selectedControl, setSelectedControl] = useState(null);
  const [exceptionForm, setExceptionForm] = useState({
    reason: '',
    affectedState: 'INTERNAL_APPROVAL',
    riskClassification: 'MEDIUM',
    expiryDate: '',
    mitigationPlan: ''
  });

  const [showInputsModal, setShowInputsModal] = useState(false);
  const [inspectingEval, setInspectingEval] = useState(null);

  const [showReqModal, setShowReqModal] = useState(false);
  const [reqForm, setReqForm] = useState({
    code: '',
    authority: 'SEBI',
    jurisdiction: 'INDIA',
    title: '',
    description: '',
    citation: '',
    sourceUrl: '',
    effectiveFrom: new Date().toISOString().split('T')[0],
    controlCode: '',
    controlName: '',
    controlType: 'DETERMINISTIC_EXPRESSION',
    testName: 'Basic Test Case',
    testInputs: '{"value": 100}',
    testExpected: 'PASS'
  });

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const [snapData, driftData, evalsData, excData, histData] = await Promise.all([
        regApi.getRegulatorySnapshot(transactionId).catch(() => null),
        regApi.getSnapshotDrift(transactionId).catch(() => null),
        regApi.getRegulatoryEvaluations(transactionId).catch(() => []),
        regApi.listExceptions(transactionId).catch(() => []),
        regApi.getLifecycleHistory(transactionId).catch(() => [])
      ]);
      let evals = evalsData;
      if (!evals || evals.length === 0) {
        evals = await regApi.evaluateRegulatoryControls(transactionId).catch(() => []);
      }
      setSnapshot(snapData);
      setDrift(driftData);
      setEvaluations(evals || []);
      setExceptions(excData || []);
      setHistory(histData || []);
    } catch (err) {
      toast.error('Failed to load regulatory data: ' + err.message);
    } finally {
      setLoading(false);
    }
  }, [transactionId]);

  const loadRequirements = useCallback(async () => {
    try {
      const data = await regApi.listRegulatoryRequirements();
      setRequirements(data || []);
    } catch (err) {
      toast.error('Failed to load requirements: ' + err.message);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (activeSubTab === 'governance') {
      loadRequirements();
    }
  }, [activeSubTab, loadRequirements]);

  const handleEvaluate = async () => {
    try {
      setEvaluating(true);
      const evals = await regApi.evaluateRegulatoryControls(transactionId);
      setEvaluations(evals);
      toast.success(`Evaluated ${evals.length} regulatory controls`);
      await loadData();
    } catch (err) {
      toast.error('Evaluation failed: ' + err.message);
    } finally {
      setEvaluating(false);
    }
  };

  const handleUpgradeSnapshot = async () => {
    if (!window.confirm('Upgrade pinned regulatory snapshot to the latest active statutory requirements? This updates your regulatory ruleset.')) {
      return;
    }
    try {
      const upgraded = await regApi.upgradeSnapshot(transactionId);
      setSnapshot(upgraded);
      toast.success('Regulatory snapshot upgraded to latest statutory standards');
      await loadData();
    } catch (err) {
      toast.error('Upgrade failed: ' + err.message);
    }
  };

  const handleOpenTransitionModal = async (stage) => {
    setTargetState(stage);
    setTransitionReason('');
    setForceWithException(false);
    setShowTransitionModal(true);
    try {
      setCheckingBlockers(true);
      const blockers = await regApi.checkTransitionBlockers(transactionId, stage);
      setTransitionBlockers(blockers || []);
    } catch (err) {
      toast.error('Failed to check transition blockers: ' + err.message);
    } finally {
      setCheckingBlockers(false);
    }
  };

  const handleExecuteTransition = async () => {
    try {
      const res = await regApi.transitionLifecycle(transactionId, {
        targetState,
        reason: transitionReason || `Transitioned to ${targetState}`,
        forceWithException
      });
      toast.success(`Lifecycle transitioned to ${res.toState}`);
      setShowTransitionModal(false);
      await loadData();
      if (onStateChange) onStateChange(res.toState);
    } catch (err) {
      toast.error(err.message || 'Transition blocked');
    }
  };

  const handleOpenExceptionModal = (control) => {
    setSelectedControl(control);
    setExceptionForm({
      reason: '',
      affectedState: 'INTERNAL_APPROVAL',
      riskClassification: 'MEDIUM',
      expiryDate: '',
      mitigationPlan: ''
    });
    setShowExceptionModal(true);
  };

  const handleSubmitException = async (e) => {
    e.preventDefault();
    try {
      await regApi.requestException(transactionId, {
        controlId: selectedControl.id || selectedControl.controlId,
        reason: exceptionForm.reason,
        affectedState: exceptionForm.affectedState,
        riskClassification: exceptionForm.riskClassification,
        expiryDate: exceptionForm.expiryDate || null,
        mitigationPlan: exceptionForm.mitigationPlan
      });
      toast.success('Regulatory exception requested. Pending multi-eye authorization.');
      setShowExceptionModal(false);
      await loadData();
    } catch (err) {
      toast.error('Failed to submit exception: ' + err.message);
    }
  };

  const handleApproveException = async (exceptionId) => {
    try {
      await regApi.approveException(exceptionId);
      toast.success('Regulatory exception approved. Overriding control.');
      await loadData();
    } catch (err) {
      toast.error(err.message || 'Approval failed');
    }
  };

  const handleCreateRequirement = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        code: reqForm.code,
        authority: reqForm.authority,
        jurisdiction: reqForm.jurisdiction,
        title: reqForm.title,
        description: reqForm.description,
        citation: reqForm.citation,
        sourceUrl: reqForm.sourceUrl,
        effectiveFrom: reqForm.effectiveFrom,
        controls: [
          {
            code: reqForm.controlCode,
            name: reqForm.title + ' Check',
            description: reqForm.description,
            controlType: reqForm.controlType,
            severity: 'HIGH',
            orderIndex: 1
          }
        ],
        testCases: [
          {
            name: reqForm.testName,
            fixtureInputs: reqForm.testInputs,
            expectedStatus: reqForm.testExpected
          }
        ]
      };
      await regApi.createRequirement(payload);
      toast.success('Requirement authored in draft. Test fixtures seeded.');
      setShowReqModal(false);
      await loadRequirements();
    } catch (err) {
      toast.error('Failed to author requirement: ' + err.message);
    }
  };

  const handleRunTests = async (versionId) => {
    try {
      const results = await regApi.testRequirementVersion(versionId);
      const passed = results.every((r) => r.lastRunPassed);
      if (passed) {
        toast.success(`All ${results.length} test fixture(s) passed!`);
      } else {
        toast.error('One or more test fixtures failed');
      }
      await loadRequirements();
    } catch (err) {
      toast.error('Test execution error: ' + err.message);
    }
  };

  const handleSubmitRequirement = async (versionId) => {
    try {
      await regApi.submitRequirementVersion(versionId);
      toast.success('Submitted for peer approval (status: PENDING_APPROVAL)');
      await loadRequirements();
    } catch (err) {
      toast.error('Submission failed: ' + err.message);
    }
  };

  const handleApproveRequirement = async (versionId) => {
    try {
      await regApi.approveRequirementVersion(versionId);
      toast.success('Requirement version approved and activated!');
      await loadRequirements();
    } catch (err) {
      toast.error(err.message || 'Approval rejected by governance rule');
    }
  };

  // Helper formatting & counts
  const currentStage = history.length > 0 ? history[0].toState : (snapshot ? 'ACTIVE' : 'DRAFT');
  const currentStageIndex = LIFECYCLE_STAGES.indexOf(currentStage);

  const passedCount = evaluations.filter((e) => e.status === 'PASS').length;
  const failedCount = evaluations.filter((e) => e.status === 'FAIL').length;
  const exceptionCount = evaluations.filter((e) => e.status === 'EXCEPTION').length;
  const missingCount = evaluations.filter((e) => e.status === 'MISSING').length;

  const filteredEvals = evaluations.filter((e) => {
    if (statusFilter === 'ALL') return true;
    return e.status === statusFilter;
  });

  const getStatusBadge = (status) => {
    switch (status) {
      case 'PASS':
        return <span className="badge badge-complete">PASS</span>;
      case 'FAIL':
        return <span className="badge badge-failed">FAIL</span>;
      case 'EXCEPTION':
        return <span className="badge badge-stale" style={{ backgroundColor: '#fef3c7', color: '#92400e' }}>EXCEPTION</span>;
      case 'MISSING':
        return <span className="badge badge-pending">MISSING</span>;
      case 'NEEDS_REVIEW':
        return <span className="badge badge-pending">NEEDS REVIEW</span>;
      default:
        return <span className="badge">{status}</span>;
    }
  };

  if (loading && !snapshot) {
    return <div className="p-8 text-center text-secondary">Loading regulatory engine...</div>;
  }

  return (
    <div className="regulatory-workbench">
      {/* 1. Header & Title */}
      <div className="workbench-header flex justify-between items-center mb-4">
        <div>
          <h2 className="text-xl font-bold">Regulatory Engine & Governance</h2>
          <p className="text-sm text-secondary">
            Deterministic control evaluations, rule-set pinning, four-eyes governance, and state machine lifecycle
          </p>
        </div>
        <div className="flex gap-2">
          <button
            className="btn btn-primary text-sm flex items-center gap-1"
            onClick={handleEvaluate}
            disabled={evaluating}
          >
            {evaluating ? 'Evaluating Controls...' : '⚡ Re-evaluate Controls'}
          </button>
        </div>
      </div>

      {/* 2. Lifecycle Progression Bar (Ticket D6) */}
      <div className="card p-4 mb-4">
        <div className="flex justify-between items-center mb-2">
          <span className="text-xs font-semibold text-secondary uppercase tracking-wider">
            Transaction State Machine Pipeline (Spec §37, §38)
          </span>
          <span className="text-xs font-mono font-medium bg-neutral-100 px-2 py-0.5 rounded">
            Current Stage: <span className="font-bold text-primary">{currentStage}</span>
          </span>
        </div>

        <div className="lifecycle-track flex items-center gap-1 overflow-x-auto pb-2">
          {LIFECYCLE_STAGES.map((st, idx) => {
            const isCurrent = st === currentStage;
            const isCompleted = idx < currentStageIndex;
            const isPending = idx > currentStageIndex;

            let pillStyle = 'bg-neutral-100 text-neutral-600 border border-neutral-200';
            if (isCurrent) {
              pillStyle = 'bg-primary text-white font-bold ring-2 ring-primary ring-offset-1';
            } else if (isCompleted) {
              pillStyle = 'bg-emerald-50 text-emerald-800 border border-emerald-300 font-medium';
            }

            return (
              <div
                key={st}
                className={`lifecycle-step flex-shrink-0 px-2.5 py-1 rounded text-xs cursor-pointer transition ${pillStyle}`}
                onClick={() => handleOpenTransitionModal(st)}
                title={`Click to check blockers or transition to ${st}`}
              >
                {isCompleted && <span className="mr-1">✓</span>}
                {st.replace(/_/g, ' ')}
              </div>
            );
          })}
        </div>
      </div>

      {/* 3. Pinned Regulatory Snapshot & Drift Banner (Ticket D3) */}
      {drift?.hasDrift && (
        <div className="alert alert-warning mb-4 flex justify-between items-center">
          <div>
            <div className="font-semibold flex items-center gap-1">
              <span>⚠️</span> Statutory Rule Drift Detected
            </div>
            <div className="text-xs mt-1">
              {drift.outdatedCount} statutory rule(s) have newer versions in the active registry. Your transaction remains pinned to snapshot dated {snapshot?.effectiveDate}.
            </div>
          </div>
          <button className="btn btn-secondary text-xs" onClick={handleUpgradeSnapshot}>
            Inspect & Upgrade Snapshot
          </button>
        </div>
      )}

      <div className="flex items-center justify-between text-xs text-secondary mb-4 bg-neutral-50 p-2.5 rounded border border-neutral-200">
        <div className="flex items-center gap-3">
          <span>
            📌 <strong>Pinned Snapshot:</strong> {snapshot?.name || 'SEBI ICDR 2025 Standard'}
          </span>
          <span>
            📅 <strong>Effective:</strong> {snapshot?.effectiveDate || '2026-01-01'}
          </span>
          <span>
            📜 <strong>Rules Count:</strong> {snapshot?.itemsCount || evaluations.length}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="badge badge-complete">PINNED & REPRODUCIBLE</span>
        </div>
      </div>

      {/* 4. Sub-Navigation Tabs */}
      <div className="tab-strip mb-4">
        <button
          className={`tab-btn ${activeSubTab === 'controls' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('controls')}
        >
          Controls Matrix ({evaluations.length})
        </button>
        <button
          className={`tab-btn ${activeSubTab === 'exceptions' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('exceptions')}
        >
          Regulatory Exceptions ({exceptions.length})
        </button>
        <button
          className={`tab-btn ${activeSubTab === 'governance' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('governance')}
        >
          Rule Governance & Testing
        </button>
        <button
          className={`tab-btn ${activeSubTab === 'history' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('history')}
        >
          Lifecycle History ({history.length})
        </button>
      </div>

      {/* SUB-TAB 1: Controls Matrix */}
      {activeSubTab === 'controls' && (
        <div>
          {/* KPI Cards */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-4">
            <div className="stat-card cursor-pointer" onClick={() => setStatusFilter('ALL')}>
              <div className="stat-label">Total Controls</div>
              <div className="stat-value">{evaluations.length}</div>
              <div className="stat-sub">Configured checks</div>
            </div>
            <div className="stat-card cursor-pointer" onClick={() => setStatusFilter('PASS')}>
              <div className="stat-label">Passed</div>
              <div className="stat-value text-emerald-600">{passedCount}</div>
              <div className="stat-sub">State satisfies condition</div>
            </div>
            <div className="stat-card cursor-pointer" onClick={() => setStatusFilter('FAIL')}>
              <div className="stat-label">Failed</div>
              <div className="stat-value text-rose-600">{failedCount}</div>
              <div className="stat-sub">Blocking filing</div>
            </div>
            <div className="stat-card cursor-pointer" onClick={() => setStatusFilter('EXCEPTION')}>
              <div className="stat-label">Exceptions</div>
              <div className="stat-value text-amber-600">{exceptionCount}</div>
              <div className="stat-sub">Authorized overrides</div>
            </div>
            <div className="stat-card cursor-pointer" onClick={() => setStatusFilter('MISSING')}>
              <div className="stat-label">Missing Facts</div>
              <div className="stat-value text-neutral-500">{missingCount}</div>
              <div className="stat-sub">Awaiting verification</div>
            </div>
          </div>

          {/* Filter Bar */}
          <div className="flex gap-2 mb-3">
            {['ALL', 'FAIL', 'EXCEPTION', 'MISSING', 'PASS'].map((f) => (
              <button
                key={f}
                className={`btn text-xs ${statusFilter === f ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setStatusFilter(f)}
              >
                {f} {f === 'ALL' ? `(${evaluations.length})` : `(${evaluations.filter((e) => e.status === f).length})`}
              </button>
            ))}
          </div>

          {/* Controls Table */}
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Control & Citation</th>
                  <th>Status</th>
                  <th>Deterministic Explanation (Invariant 18)</th>
                  <th>Result Value</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredEvals.length === 0 ? (
                  <tr>
                    <td colSpan="5" className="text-center py-6 text-secondary">
                      No controls match filter {statusFilter}.
                    </td>
                  </tr>
                ) : (
                  filteredEvals.map((e) => (
                    <tr key={e.id}>
                      <td style={{ maxWidth: '240px' }}>
                        <div className="font-semibold text-xs">{e.controlName}</div>
                        <div className="font-mono text-xxs text-secondary">{e.controlCode}</div>
                        <div className="text-xs text-secondary mt-1">
                          {e.sourceUrl ? (
                            <a
                              href={e.sourceUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-primary hover:underline"
                            >
                              🔗 {e.citation}
                            </a>
                          ) : (
                            <span className="text-neutral-500">
                              <span className="badge badge-pending text-xxs mr-1">INTERNAL</span>
                              {e.citation}
                            </span>
                          )}
                        </div>
                      </td>
                      <td>{getStatusBadge(e.status)}</td>
                      <td style={{ maxWidth: '380px' }}>
                        <div className="text-xs">{e.explanation}</div>
                        {e.exceptionReason && (
                          <div className="text-xxs text-amber-800 bg-amber-50 p-1 rounded mt-1 border border-amber-200">
                            <strong>Exception Note:</strong> {e.exceptionReason}
                          </div>
                        )}
                      </td>
                      <td className="font-mono text-xs">{e.resultValue || '—'}</td>
                      <td>
                        <div className="flex flex-col gap-1">
                          <button
                            className="btn btn-secondary text-xxs px-1.5 py-0.5"
                            onClick={() => {
                              setInspectingEval(e);
                              setShowInputsModal(true);
                            }}
                          >
                            Inspect Inputs
                          </button>
                          <button
                            className="btn btn-secondary text-xxs px-1.5 py-0.5 text-amber-500"
                            onClick={() => handleOpenExceptionModal(e)}
                          >
                            Request Exception
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* SUB-TAB 2: Regulatory Exceptions Register (Ticket D5) */}
      {activeSubTab === 'exceptions' && (
        <div>
          <div className="flex justify-between items-center mb-3">
            <p className="text-xs text-secondary">
              Statutory and policy exceptions recorded transparently (spec §39, invariant 19). Overrides control failures without falsifying data.
            </p>
          </div>

          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Control</th>
                  <th>Reason & State</th>
                  <th>Risk</th>
                  <th>Status</th>
                  <th>Expiry</th>
                  <th>Mitigation Plan</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {exceptions.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="text-center py-6 text-secondary">
                      No regulatory exceptions recorded for this transaction.
                    </td>
                  </tr>
                ) : (
                  exceptions.map((ex) => (
                    <tr key={ex.id}>
                      <td>
                        <div className="font-semibold text-xs">{ex.controlName}</div>
                        <div className="font-mono text-xxs text-secondary">{ex.controlCode}</div>
                      </td>
                      <td style={{ maxWidth: '200px' }}>
                        <div className="text-xs font-medium">{ex.reason}</div>
                        <div className="text-xxs text-secondary">Target: {ex.affectedState}</div>
                      </td>
                      <td>
                        <span className={`badge ${ex.riskClassification === 'CRITICAL' ? 'badge-failed' : 'badge-pending'}`}>
                          {ex.riskClassification}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${ex.status === 'APPROVED' ? 'badge-complete' : 'badge-stale'}`}>
                          {ex.status}
                        </span>
                      </td>
                      <td className="text-xs font-mono">{ex.expiryDate || 'Indefinite'}</td>
                      <td style={{ maxWidth: '240px' }} className="text-xs text-secondary">
                        {ex.mitigationPlan}
                      </td>
                      <td>
                        {ex.status === 'REQUESTED' && (
                          <div className="flex gap-1">
                            <button
                              className="btn btn-primary text-xxs px-2 py-0.5"
                              onClick={() => handleApproveException(ex.id)}
                            >
                              Approve
                            </button>
                          </div>
                        )}
                        {ex.status === 'APPROVED' && (
                          <span className="text-xxs text-emerald-700">✓ Effective</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* SUB-TAB 3: Rule Governance & Testing (Ticket D4) */}
      {activeSubTab === 'governance' && (
        <div>
          <div className="flex justify-between items-center mb-3">
            <p className="text-xs text-secondary">
              Author ≠ Approver multi-eye governance. Requirements must pass deterministic fixture test suites before peer approval (spec §57).
            </p>
            <button className="btn btn-primary text-xs" onClick={() => setShowReqModal(true)}>
              + Author New Requirement
            </button>
          </div>

          <div className="space-y-4">
            {requirements.map((req) => (
              <div key={req.id} className="card p-4 border border-neutral-200">
                <div className="flex justify-between items-start mb-2">
                  <div>
                    <span className="badge badge-secondary text-xxs mr-2">{req.authority}</span>
                    <strong className="text-sm">{req.title}</strong>
                    <span className="font-mono text-xs text-secondary ml-2">({req.code})</span>
                  </div>
                  <span className="badge badge-complete">{req.status}</span>
                </div>
                <p className="text-xs text-secondary mb-3">{req.description}</p>

                {/* Versions */}
                <div className="space-y-2 mt-2">
                  {req.versions?.map((v) => (
                    <div key={v.id} className="bg-neutral-50 p-2.5 rounded border border-neutral-200 text-xs">
                      <div className="flex justify-between items-center mb-1">
                        <div>
                          <strong className="text-xs">Version {v.versionNumber}</strong>
                          <span className="text-secondary ml-2">Citation: {v.citation}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className={`badge ${v.status === 'ACTIVE' ? 'badge-complete' : 'badge-pending'}`}>
                            {v.status}
                          </span>
                        </div>
                      </div>

                      <div className="text-xxs text-secondary mb-2">
                        <span>Author: {v.authorName || 'System'}</span>
                        {v.approvedByName && <span className="ml-3">Approver: {v.approvedByName}</span>}
                        {v.sourceUrl && (
                          <a href={v.sourceUrl} target="_blank" rel="noreferrer" className="ml-3 text-primary">
                            External Official Source ↗
                          </a>
                        )}
                      </div>

                      {/* Test Cases Runner */}
                      {v.testCases && v.testCases.length > 0 && (
                        <div className="bg-white p-2 rounded border border-neutral-200 mt-2">
                          <div className="font-semibold text-xxs text-secondary mb-1">FIXTURE TEST CASES</div>
                          {v.testCases.map((tc) => (
                            <div key={tc.id} className="flex justify-between items-center text-xxs py-0.5">
                              <span>🧪 {tc.name}</span>
                              <div className="flex items-center gap-2">
                                <span className="font-mono text-neutral-500">Exp: {tc.expectedStatus}</span>
                                {tc.lastRunPassed !== null && (
                                  <span className={tc.lastRunPassed ? 'text-emerald-600 font-bold' : 'text-rose-600 font-bold'}>
                                    {tc.lastRunPassed ? 'PASSED' : 'FAILED'}
                                  </span>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Actions */}
                      <div className="flex gap-2 mt-2 pt-1 border-t border-neutral-200">
                        <button
                          className="btn btn-secondary text-xxs px-2 py-0.5"
                          onClick={() => handleRunTests(v.id)}
                        >
                          ▶ Run Test Fixtures
                        </button>
                        {v.status === 'DRAFT' && (
                          <button
                            className="btn btn-secondary text-xxs px-2 py-0.5"
                            onClick={() => handleSubmitRequirement(v.id)}
                          >
                            Submit for Approval
                          </button>
                        )}
                        {v.status === 'PENDING_APPROVAL' && (
                          <button
                            className="btn btn-primary text-xxs px-2 py-0.5"
                            onClick={() => handleApproveRequirement(v.id)}
                            title="Four-eyes rule: must be a different authorized user than author"
                          >
                            ✓ Peer Approve & Activate
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* SUB-TAB 4: Lifecycle History (Ticket D6) */}
      {activeSubTab === 'history' && (
        <div>
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Transition</th>
                  <th>Actor</th>
                  <th>Timestamp</th>
                  <th>Reason</th>
                  <th>Blockers Checked</th>
                  <th>Exceptions Applied</th>
                </tr>
              </thead>
              <tbody>
                {history.length === 0 ? (
                  <tr>
                    <td colSpan="6" className="text-center py-6 text-secondary">
                      No lifecycle transitions recorded yet.
                    </td>
                  </tr>
                ) : (
                  history.map((h) => (
                    <tr key={h.id}>
                      <td className="font-mono text-xs font-semibold">
                        <span className="text-secondary">{h.fromState}</span> → <span className="text-primary">{h.toState}</span>
                      </td>
                      <td className="text-xs">{h.actorName || 'Authorized User'}</td>
                      <td className="text-xs font-mono">{new Date(h.transitionedAt).toLocaleString()}</td>
                      <td className="text-xs">{h.reason || 'Standard state transition'}</td>
                      <td className="text-xs font-mono">{h.blockersSnapshot ? 'Evaluated' : 'None'}</td>
                      <td className="text-xs font-mono">{h.exceptionsSnapshot ? 'Logged' : 'None'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* MODAL: Lifecycle Transition (Ticket D6) */}
      {showTransitionModal && (
        <div className="modal-backdrop">
          <div className="modal-card max-w-lg">
            <div className="modal-header">
              <h3 className="text-lg font-bold">Advance Lifecycle State: {targetState}</h3>
              <button className="btn-close" onClick={() => setShowTransitionModal(false)}>✕</button>
            </div>
            <div className="modal-body space-y-3">
              <p className="text-xs text-secondary">
                Checking state machine preconditions and regulatory gating blockers for <strong>{targetState}</strong>.
              </p>

              {checkingBlockers ? (
                <div className="text-center py-4 text-xs text-secondary">Evaluating blockers...</div>
              ) : transitionBlockers.length === 0 ? (
                <div className="alert alert-success text-xs">
                  ✓ All preconditions satisfied! No regulatory control failures or open conflicts.
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="text-xs font-semibold text-rose-700">
                    {transitionBlockers.length} Blocker(s) Found:
                  </div>
                  {transitionBlockers.map((b, idx) => (
                    <div
                      key={idx}
                      className={`p-2 rounded text-xs border ${
                        b.exceptionApplied
                          ? 'bg-amber-50 border-amber-200 text-amber-800'
                          : 'bg-rose-50 border-rose-200 text-rose-800'
                      }`}
                    >
                      <div className="font-semibold flex justify-between">
                        <span>{b.title}</span>
                        <span className="badge text-xxs">{b.severity}</span>
                      </div>
                      <div className="text-xxs mt-0.5">{b.description}</div>
                      {b.exceptionApplied && (
                        <div className="text-xxs font-bold text-amber-700 mt-1">
                          ✓ Covered by approved regulatory exception override
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {transitionBlockers.some((b) => !b.exceptionApplied) && (
                <div className="text-xs text-rose-700 font-medium">
                  Cannot advance stage until unresolved blockers are cleared or covered by approved exceptions.
                </div>
              )}

              {transitionBlockers.length > 0 && transitionBlockers.every((b) => b.exceptionApplied) && (
                <label className="flex items-center gap-2 text-xs font-semibold text-amber-800 bg-amber-50 p-2 rounded border border-amber-300">
                  <input
                    type="checkbox"
                    checked={forceWithException}
                    onChange={(e) => setForceWithException(e.target.checked)}
                  />
                  <span>Confirm transition with authorized board exceptions</span>
                </label>
              )}

              <div>
                <label className="text-xs font-medium">Transition Reason / Notes</label>
                <textarea
                  className="input-field text-xs w-full mt-1"
                  rows="2"
                  value={transitionReason}
                  onChange={(e) => setTransitionReason(e.target.value)}
                  placeholder="Record formal steering committee transition rationale..."
                />
              </div>
            </div>
            <div className="modal-footer flex justify-end gap-2">
              <button className="btn btn-secondary text-xs" onClick={() => setShowTransitionModal(false)}>
                Cancel
              </button>
              <button
                className="btn btn-primary text-xs"
                disabled={
                  transitionBlockers.some((b) => !b.exceptionApplied) ||
                  (transitionBlockers.length > 0 && !forceWithException)
                }
                onClick={handleExecuteTransition}
              >
                Execute State Transition
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Request Exception (Ticket D5) */}
      {showExceptionModal && (
        <div className="modal-backdrop">
          <div className="modal-card max-w-lg">
            <div className="modal-header">
              <h3 className="text-lg font-bold">Request Regulatory Exception</h3>
              <button className="btn-close" onClick={() => setShowExceptionModal(false)}>✕</button>
            </div>
            <form onSubmit={handleSubmitException}>
              <div className="modal-body space-y-3">
                <div className="text-xs bg-neutral-50 p-2 rounded border border-neutral-200">
                  <strong>Control:</strong> {selectedControl?.controlCode} — {selectedControl?.controlName}
                </div>

                <div>
                  <label className="text-xs font-medium">Reason for Exception *</label>
                  <textarea
                    className="input-field text-xs w-full mt-1"
                    rows="2"
                    required
                    value={exceptionForm.reason}
                    onChange={(e) => setExceptionForm({ ...exceptionForm, reason: e.target.value })}
                    placeholder="E.g., Board resolution passed authorizing supplementary lock-in..."
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-xs font-medium">Affected State</label>
                    <select
                      className="input-field text-xs w-full mt-1"
                      value={exceptionForm.affectedState}
                      onChange={(e) => setExceptionForm({ ...exceptionForm, affectedState: e.target.value })}
                    >
                      <option value="INTERNAL_APPROVAL">INTERNAL_APPROVAL</option>
                      <option value="FILING_READY">FILING_READY</option>
                      <option value="READINESS_REVIEW">READINESS_REVIEW</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium">Risk Classification</label>
                    <select
                      className="input-field text-xs w-full mt-1"
                      value={exceptionForm.riskClassification}
                      onChange={(e) => setExceptionForm({ ...exceptionForm, riskClassification: e.target.value })}
                    >
                      <option value="LOW">LOW</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="HIGH">HIGH</option>
                      <option value="CRITICAL">CRITICAL</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="text-xs font-medium">Expiry Date (Optional)</label>
                  <input
                    type="date"
                    className="input-field text-xs w-full mt-1"
                    value={exceptionForm.expiryDate}
                    onChange={(e) => setExceptionForm({ ...exceptionForm, expiryDate: e.target.value })}
                  />
                </div>

                <div>
                  <label className="text-xs font-medium">Mitigation Plan *</label>
                  <textarea
                    className="input-field text-xs w-full mt-1"
                    rows="2"
                    required
                    value={exceptionForm.mitigationPlan}
                    onChange={(e) => setExceptionForm({ ...exceptionForm, mitigationPlan: e.target.value })}
                    placeholder="Document compensating controls, escrow, or indemnity..."
                  />
                </div>
              </div>
              <div className="modal-footer flex justify-end gap-2">
                <button type="button" className="btn btn-secondary text-xs" onClick={() => setShowExceptionModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary text-xs">
                  Submit Exception Request
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL: Inspect Inputs Snapshot (Invariant 18) */}
      {showInputsModal && inspectingEval && (
        <div className="modal-backdrop">
          <div className="modal-card max-w-lg">
            <div className="modal-header">
              <h3 className="text-lg font-bold">Evaluation Inputs Snapshot</h3>
              <button className="btn-close" onClick={() => setShowInputsModal(false)}>✕</button>
            </div>
            <div className="modal-body space-y-3">
              <div className="text-xs">
                <strong>Control:</strong> {inspectingEval.controlCode} ({inspectingEval.controlName})
              </div>
              <div className="text-xs">
                <strong>Evaluated At:</strong> {new Date(inspectingEval.evaluatedAt).toLocaleString()}
              </div>
              <div className="text-xs font-medium">Captured Input Data:</div>
              <pre className="bg-neutral-900 text-emerald-400 p-3 rounded text-xxs font-mono overflow-x-auto">
                {JSON.stringify(JSON.parse(inspectingEval.inputsSnapshot || '{}'), null, 2)}
              </pre>
            </div>
            <div className="modal-footer flex justify-end">
              <button className="btn btn-secondary text-xs" onClick={() => setShowInputsModal(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL: Author Requirement (Ticket D4) */}
      {showReqModal && (
        <div className="modal-backdrop">
          <div className="modal-card max-w-lg">
            <div className="modal-header">
              <h3 className="text-lg font-bold">Author New Regulatory Requirement</h3>
              <button className="btn-close" onClick={() => setShowReqModal(false)}>✕</button>
            </div>
            <form onSubmit={handleCreateRequirement}>
              <div className="modal-body space-y-2">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-xs font-medium">Requirement Code *</label>
                    <input
                      type="text"
                      className="input-field text-xs w-full mt-1 font-mono"
                      required
                      placeholder="SEBI_ICDR_REG_..."
                      value={reqForm.code}
                      onChange={(e) => setReqForm({ ...reqForm, code: e.target.value })}
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium">Authority *</label>
                    <select
                      className="input-field text-xs w-full mt-1"
                      value={reqForm.authority}
                      onChange={(e) => setReqForm({ ...reqForm, authority: e.target.value })}
                    >
                      <option value="SEBI">SEBI</option>
                      <option value="BSE">BSE</option>
                      <option value="NSE">NSE</option>
                      <option value="MCA">MCA</option>
                      <option value="INTERNAL_POLICY">INTERNAL_POLICY</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="text-xs font-medium">Title *</label>
                  <input
                    type="text"
                    className="input-field text-xs w-full mt-1"
                    required
                    placeholder="E.g., Promoter Shareholding Minimum..."
                    value={reqForm.title}
                    onChange={(e) => setReqForm({ ...reqForm, title: e.target.value })}
                  />
                </div>

                <div>
                  <label className="text-xs font-medium">Statutory Citation *</label>
                  <input
                    type="text"
                    className="input-field text-xs w-full mt-1"
                    required
                    placeholder="E.g., SEBI ICDR 2018 Regulation 238(1)"
                    value={reqForm.citation}
                    onChange={(e) => setReqForm({ ...reqForm, citation: e.target.value })}
                  />
                </div>

                <div>
                  <label className="text-xs font-medium">Verified Source URL (leave empty for UNSOURCED)</label>
                  <input
                    type="url"
                    className="input-field text-xs w-full mt-1 font-mono"
                    placeholder="https://www.sebi.gov.in/..."
                    value={reqForm.sourceUrl}
                    onChange={(e) => setReqForm({ ...reqForm, sourceUrl: e.target.value })}
                  />
                </div>

                <div>
                  <label className="text-xs font-medium">Description</label>
                  <textarea
                    className="input-field text-xs w-full mt-1"
                    rows="2"
                    value={reqForm.description}
                    onChange={(e) => setReqForm({ ...reqForm, description: e.target.value })}
                  />
                </div>

                <div className="pt-2 border-t border-neutral-200">
                  <div className="text-xs font-semibold mb-1">Attached Control</div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <input
                        type="text"
                        className="input-field text-xs w-full font-mono"
                        placeholder="Control Code (CTRL_...)"
                        required
                        value={reqForm.controlCode}
                        onChange={(e) => setReqForm({ ...reqForm, controlCode: e.target.value })}
                      />
                    </div>
                    <div>
                      <select
                        className="input-field text-xs w-full"
                        value={reqForm.controlType}
                        onChange={(e) => setReqForm({ ...reqForm, controlType: e.target.value })}
                      >
                        <option value="DETERMINISTIC_EXPRESSION">DETERMINISTIC_EXPRESSION</option>
                        <option value="DOMAIN_RECONCILIATION">DOMAIN_RECONCILIATION</option>
                        <option value="EVIDENCE_PRESENCE">EVIDENCE_PRESENCE</option>
                      </select>
                    </div>
                  </div>
                </div>
              </div>
              <div className="modal-footer flex justify-end gap-2">
                <button type="button" className="btn btn-secondary text-xs" onClick={() => setShowReqModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary text-xs">
                  Create Requirement Version
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
