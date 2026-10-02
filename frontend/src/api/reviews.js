import { apiGet, apiPost } from './client';

export function listFactReviews(factId) {
  return apiGet(`/facts/${factId}/reviews`);
}

export function reviewFact(factId, payload) {
  return apiPost(`/facts/${factId}/reviews`, payload);
}
