import { apiGet, apiPost } from './client';

export function getAiArtifact(id) {
  return apiGet(`/ai/artifacts/${id}`);
}

export function listAiArtifactsForEvidence(evidenceId) {
  return apiGet(`/ai/artifacts?evidenceId=${evidenceId}`);
}

export function getAiMetrics(workstreamId) {
  return apiGet(`/ai/metrics?workstreamId=${workstreamId}`);
}

export function acceptEvidenceClassification(evidenceId) {
  return apiPost(`/evidence/${evidenceId}/accept-classification`, {});
}
