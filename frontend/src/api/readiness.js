import { apiGet, apiPost } from './client';

export function getReadiness(transactionId) {
  return apiGet(`/transactions/${transactionId}/readiness`);
}

export function evaluateReadiness(transactionId) {
  return apiPost(`/transactions/${transactionId}/readiness/evaluate`);
}
