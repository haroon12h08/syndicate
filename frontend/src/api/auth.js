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

export function logout() {
  return apiPost('/auth/logout');
}

export function setupMfa() {
  return apiPost('/auth/mfa/setup');
}

export function enableMfa(code) {
  return apiPost('/auth/mfa/enable', { code });
}

export function disableMfa(code) {
  return apiPost('/auth/mfa/disable', { code });
}

export function verifyMfaLogin(mfaToken, code) {
  return apiPost('/auth/mfa/verify-login', { mfaToken, code });
}
