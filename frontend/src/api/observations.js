import { apiGet, apiPost, apiPut } from './client';

export function listObservations(transactionId) {
  return apiGet(`/transactions/${transactionId}/observations`);
}

export function recordObservation(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/observations`, payload);
}

export function respond(observationId, payload) {
  return apiPut(`/observations/${observationId}/response`, payload);
}

export function approveResponse(observationId) {
  return apiPost(`/observations/${observationId}/response/approve`);
}

export function markSent(observationId) {
  return apiPost(`/observations/${observationId}/response/sent`);
}

export function closeObservation(observationId) {
  return apiPost(`/observations/${observationId}/close`);
}
