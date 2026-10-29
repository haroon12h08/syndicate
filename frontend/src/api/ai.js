import { apiGet, apiPost } from './client';

export function getAiArtifact(id) {
  return apiGet(`/ai/artifacts/${id}`);
}

export function listAiArtifactsForEvidence(evidenceId) {
  return apiGet(`/ai/artifacts?evidenceId=${evidenceId}`);
}

export function getAiMetrics(workstreamId) {
  return apiGet(`/ai/metrics?workstreamId=${workstreamId}`);
}

export function acceptEvidenceClassification(evidenceId) {
  return apiPost(`/evidence/${evidenceId}/accept-classification`, {});
}

// ---- Ticket G3: Transaction Search ----
export function searchTransaction(transactionId, { q = '', types = '', limit = 50 } = {}) {
  const params = new URLSearchParams();
  if (q) params.set('q', q);
  if (types) params.set('types', types);
  if (limit) params.set('limit', limit);
  return apiGet(`/transactions/${transactionId}/search?${params.toString()}`);
}

// ---- Ticket G4: AI Suggestions Engine ----
export function listSuggestions(transactionId, status = '') {
  const query = status ? `?status=${status}` : '';
  return apiGet(`/transactions/${transactionId}/suggestions${query}`);
}

export function generateSuggestions(transactionId) {
  return apiPost(`/transactions/${transactionId}/suggestions/generate`, {});
}

export function acceptSuggestion(transactionId, suggestionId) {
  return apiPost(`/transactions/${transactionId}/suggestions/${suggestionId}/accept`, {});
}

export function dismissSuggestion(transactionId, suggestionId, reason = '') {
  return apiPost(`/transactions/${transactionId}/suggestions/${suggestionId}/dismiss`, { reason });
}

// ---- Ticket G5: AI Drafting Assist ----
export function draftDisclosure(transactionId, { sectionCode, title, userInstructions }) {
  return apiPost(`/transactions/${transactionId}/drhp/disclosures/draft-ai`, {
    sectionCode,
    title,
    userInstructions,
  });
}

export function draftObservationResponse(observationId, { userInstructions }) {
  return apiPost(`/observations/${observationId}/draft-ai`, {
    userInstructions,
  });
}
