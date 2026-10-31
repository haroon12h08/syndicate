import { apiGet, apiPost } from './client';

export async function getRegulatorySnapshot(transactionId) {
  return apiGet(`/transactions/${transactionId}/regulatory-snapshot`);
}

export async function getSnapshotDrift(transactionId) {
  return apiGet(`/transactions/${transactionId}/regulatory-snapshot/drift`);
}

export async function upgradeSnapshot(transactionId) {
  return apiPost(`/transactions/${transactionId}/regulatory-snapshot/upgrade`, {});
}

export async function evaluateRegulatoryControls(transactionId) {
  return apiPost(`/transactions/${transactionId}/regulatory/evaluate`, {});
}

export async function getRegulatoryEvaluations(transactionId) {
  return apiGet(`/transactions/${transactionId}/regulatory/evaluations`);
}

export async function listRegulatoryRequirements() {
  return apiGet('/regulatory/requirements');
}

export async function createRequirement(payload) {
  return apiPost('/regulatory/requirements', payload);
}

export async function testRequirementVersion(versionId) {
  return apiPost(`/regulatory/requirements/${versionId}/test`, {});
}

export async function submitRequirementVersion(versionId) {
  return apiPost(`/regulatory/requirements/${versionId}/submit`, {});
}

export async function approveRequirementVersion(versionId) {
  return apiPost(`/regulatory/requirements/${versionId}/approve`, {});
}

export async function listExceptions(transactionId) {
  return apiGet(`/transactions/${transactionId}/regulatory-exceptions`);
}

export async function requestException(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/regulatory-exceptions`, payload);
}

export async function approveException(exceptionId) {
  return apiPost(`/regulatory-exceptions/${exceptionId}/approve`, {});
}

export async function rejectException(exceptionId, reason = '') {
  return apiPost(`/regulatory-exceptions/${exceptionId}/reject?reason=${encodeURIComponent(reason)}`, {});
}

export async function checkTransitionBlockers(transactionId, targetState) {
  return apiGet(`/transactions/${transactionId}/lifecycle/blockers?targetState=${targetState}`);
}

export async function transitionLifecycle(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/lifecycle/transition`, payload);
}

export async function getLifecycleHistory(transactionId) {
  return apiGet(`/transactions/${transactionId}/lifecycle/history`);
}
