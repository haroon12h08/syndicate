import { currentAccessToken, apiBaseUrl, apiDelete, apiDownload, apiGet, apiImageBlobUrl, apiPost, apiUpload } from './client';

export function listEvidence(workstreamId) {
  return apiGet(`/workstreams/${workstreamId}/evidence`);
}

export function uploadEvidence(workstreamId, file, documentType) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('documentType', documentType);
  return apiUpload(`/workstreams/${workstreamId}/evidence`, formData);
}

export function downloadEvidence(id) {
  return apiDownload(`/evidence/${id}/download`);
}

// Evidence is never destroyed; the server archives it and keeps the original (spec §53).
export function archiveEvidence(id, reason) {
  const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
  return apiDelete(`/evidence/${id}${query}`);
}

export function reprocessEvidence(id) {
  return apiPost(`/evidence/${id}/reprocess`);
}

export function pageImageUrl(evidenceId, pageNumber) {
  return apiImageBlobUrl(`/evidence/${evidenceId}/pages/${pageNumber}/image`);
}

export function listTransactionEvidence(transactionId) {
  return apiGet(`/transactions/${transactionId}/evidence`);
}

/** EventSource cannot set headers, so the token travels as a query parameter. */
export function transactionEventsUrl(transactionId) {
  const token = currentAccessToken();
  return `${apiBaseUrl()}/transactions/${transactionId}/events?access_token=${encodeURIComponent(token || '')}`;
}
