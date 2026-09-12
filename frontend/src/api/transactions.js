import { apiDelete, apiGet, apiPost, apiPut } from './client';

export function listTransactionsForCompany(companyId) {
  return apiGet(`/companies/${companyId}/transactions`);
}

export function listMyTransactions() {
  return apiGet('/transactions');
}

export function createTransaction(companyId, payload) {
  return apiPost(`/companies/${companyId}/transactions`, payload);
}

export function getTransaction(id) {
  return apiGet(`/transactions/${id}`);
}

export function updateTransactionStatus(id, payload) {
  return apiPut(`/transactions/${id}`, payload);
}

export function listMemberships(transactionId) {
  return apiGet(`/transactions/${transactionId}/memberships`);
}

export function addMembership(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/memberships`, payload);
}

export function removeMembership(transactionId, membershipId) {
  return apiDelete(`/transactions/${transactionId}/memberships/${membershipId}`);
}

export function deleteTransaction(id) {
  return apiDelete(`/transactions/${id}`);
}

export function leaveTransaction(id) {
  return apiPost(`/transactions/${id}/leave`);
}
