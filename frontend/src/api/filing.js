import { apiBaseUrl, apiDownload, apiGet, apiPost } from './client';

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

export function getFilingDiff(transactionId, base, target) {
  let url = `/transactions/${transactionId}/filing/diff`;
  const params = [];
  if (base) params.push(`base=${encodeURIComponent(base)}`);
  if (target) params.push(`target=${encodeURIComponent(target)}`);
  if (params.length > 0) url += `?${params.join('&')}`;
  return apiGet(url);
}

export function getDocumentDiff(documentId) {
  return apiGet(`/drhp/versions/${documentId}/diff`);
}

export function downloadStatementOfChanges(documentId) {
  return apiDownload(`/drhp/versions/${documentId}/statement-of-changes`);
}
