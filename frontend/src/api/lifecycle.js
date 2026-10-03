import { apiGet, apiPost } from './client';

export function getStage(transactionId) {
  return apiGet(`/transactions/${transactionId}/stage`);
}

export function recordMilestone(transactionId, type, payload) {
  return apiPost(`/transactions/${transactionId}/milestones/${type}`, payload);
}
