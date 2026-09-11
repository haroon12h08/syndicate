import { apiGet, apiPost } from './client';

export function register(payload) {
  return apiPost('/auth/register', payload);
}

export function login(payload) {
  return apiPost('/auth/login', payload);
}

export function me() {
  return apiGet('/auth/me');
}
