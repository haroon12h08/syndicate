import { apiGet, apiPost, apiPut } from './client';

export function listWorkstreams(transactionId) {
  return apiGet(`/transactions/${transactionId}/workstreams`);
}

export function createWorkstream(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/workstreams`, payload);
}

export function getWorkstream(id) {
  return apiGet(`/workstreams/${id}`);
}

export function updateWorkstream(id, payload) {
  return apiPut(`/workstreams/${id}`, payload);
}
