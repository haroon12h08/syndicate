import { apiGet, apiPost, apiPut } from './client';

export function listIssues(workstreamId) {
  return apiGet(`/workstreams/${workstreamId}/issues`);
}

export function createIssue(workstreamId, payload) {
  return apiPost(`/workstreams/${workstreamId}/issues`, payload);
}

export function updateIssue(id, payload) {
  return apiPut(`/issues/${id}`, payload);
}
