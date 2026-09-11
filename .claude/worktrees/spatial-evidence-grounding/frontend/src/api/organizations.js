import { apiGet, apiPost } from './client';

export function listOrganizations() {
  return apiGet('/organizations');
}

export function createOrganization(payload) {
  return apiPost('/organizations', payload);
}

export function getOrganization(id) {
  return apiGet(`/organizations/${id}`);
}

export function listMembers(id) {
  return apiGet(`/organizations/${id}/members`);
}

export function addMember(id, payload) {
  return apiPost(`/organizations/${id}/members`, payload);
}
