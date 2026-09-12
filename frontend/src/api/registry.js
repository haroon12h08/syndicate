import { apiGet, apiPost } from './client';

export function getRegistryStatus(companyId) {
  return apiGet(`/companies/${companyId}/registry-status`);
}

export function verifyRegistry(companyId) {
  return apiPost(`/companies/${companyId}/verify-registry`);
}
