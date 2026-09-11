import { apiGet, apiPost } from './client';

export function listCandidateFacts(workstreamId, status) {
  const query = status ? `?status=${status}` : '';
  return apiGet(`/workstreams/${workstreamId}/candidate-facts${query}`);
}

export function acceptCandidateFact(id, payload) {
  return apiPost(`/candidate-facts/${id}/accept`, payload);
}

export function rejectCandidateFact(id, payload) {
  return apiPost(`/candidate-facts/${id}/reject`, payload);
}
