import { apiGet, apiPost, apiPut } from './client';

export function listCompanies() {
  return apiGet('/companies');
}

export function createCompany(payload) {
  return apiPost('/companies', payload);
}

export function getCompany(id) {
  return apiGet(`/companies/${id}`);
}

export function updateCompany(id, payload) {
  return apiPut(`/companies/${id}`, payload);
}
