import { apiDelete, apiDownload, apiGet, apiUpload } from './client';

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

export function deleteEvidence(id) {
  return apiDelete(`/evidence/${id}`);
}
