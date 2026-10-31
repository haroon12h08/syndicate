import { apiGet, apiPost } from './client';

export function getRptSummary(transactionId) {
  return apiGet(`/v1/transactions/${transactionId}/rpt`);
}

export function createRelatedParty(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/rpt/parties`, data);
}

export function confirmRelatedParty(transactionId, partyId) {
  return apiPost(`/v1/transactions/${transactionId}/rpt/parties/${partyId}/confirm`, {});
}

export function recordRptTransaction(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/rpt/transactions`, data);
}

export function getLitigationSummary(transactionId) {
  return apiGet(`/v1/transactions/${transactionId}/litigation`);
}

export function recordLitigationCase(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/litigation/cases`, data);
}

export function getContractSummary(transactionId) {
  return apiGet(`/v1/transactions/${transactionId}/contracts`);
}

export function recordContract(transactionId, data) {
  return apiPost(`/v1/transactions/${transactionId}/contracts`, data);
}
