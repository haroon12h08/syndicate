import { apiBaseUrl, apiGet, apiPost } from './client';

export function getFilingStatus(transactionId) {
  return apiGet(`/transactions/${transactionId}/filing`);
}

export function approveDocument(documentId) {
  return apiPost(`/drhp/versions/${documentId}/approvals`);
}

export function assemblePackage(documentId) {
  return apiPost(`/drhp/versions/${documentId}/filing-package`);
}

export function filingPackageUrl(documentId) {
  return `${apiBaseUrl()}/drhp/versions/${documentId}/filing-package`;
}
