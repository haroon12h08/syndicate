import { apiDelete, apiGet, apiPost, apiPut } from './client';

export function listDisclosures(transactionId) {
  return apiGet(`/transactions/${transactionId}/disclosures`);
}

export function createDisclosure(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/disclosures`, payload);
}

export function updateDisclosure(id, payload) {
  return apiPut(`/disclosures/${id}`, payload);
}

export function linkFact(disclosureId, factId) {
  return apiPost(`/disclosures/${disclosureId}/fact-links`, { factId });
}

export function unlinkFact(disclosureId, factId) {
  return apiDelete(`/disclosures/${disclosureId}/fact-links/${factId}`);
}

export function compileDrhp(transactionId, mode = 'DRAFT_PREVIEW') {
  return apiPost(`/transactions/${transactionId}/drhp/compile?mode=${mode}`);
}

export function getLatestDrhp(transactionId) {
  return apiGet(`/transactions/${transactionId}/drhp`);
}

export function getProvenance(drhpDocumentId) {
  return apiGet(`/drhp/versions/${drhpDocumentId}/provenance`);
}
