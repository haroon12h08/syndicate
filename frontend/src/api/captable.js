import { apiGet, apiPost, apiPut } from './client';

export function getCapTable(transactionId, asOfDate) {
  const query = asOfDate ? `?asOf=${encodeURIComponent(asOfDate)}` : '';
  return apiGet(`/v1/transactions/${transactionId}/captable${query}`);
}

export function createSecurityClass(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/captable/security-classes`, data);
}

export function createHolder(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/captable/holders`, data);
}

export function recordShareTransaction(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/captable/transactions`, data);
}

export function updateIpoParameters(transactionId, data) {
  return apiPut(`/v1/transactions/${transactionId}/captable/ipo-parameters`, data);
}

export function recordEncumbrance(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/captable/encumbrances`, data);
}

export function recalculateLockIns(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/captable/lock-ins/recalculate`, data);
}
