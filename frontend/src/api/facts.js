import { apiDelete, apiGet, apiPost, apiPut } from './client';

export function listFacts(workstreamId, includeSuperseded = false) {
  return apiGet(`/workstreams/${workstreamId}/facts?includeSuperseded=${includeSuperseded}`);
}

export function createFact(workstreamId, payload) {
  return apiPost(`/workstreams/${workstreamId}/facts`, payload);
}

export function supersedeFact(id, payload) {
  return apiPut(`/facts/${id}`, payload);
}

export function verifyFact(id) {
  return apiPost(`/facts/${id}/verify`);
}

export function linkEvidence(factId, evidenceId) {
  return apiPost(`/facts/${factId}/evidence-links`, { evidenceId });
}

export function unlinkEvidence(factId, evidenceId) {
  return apiDelete(`/facts/${factId}/evidence-links/${evidenceId}`);
}
