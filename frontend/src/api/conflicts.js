import { apiGet, apiPost } from './client';

export function listConflicts(transactionId) {
  return apiGet(`/transactions/${transactionId}/conflicts`);
}

export function resolveConflict(conflictId, payload) {
  return apiPost(`/conflicts/${conflictId}/resolve`, payload);
}
