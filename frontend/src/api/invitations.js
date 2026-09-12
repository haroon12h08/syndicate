import { apiDelete, apiGet, apiPost } from './client';

export function sendOrganizationInvitation(organizationId, payload) {
  return apiPost(`/organizations/${organizationId}/invitations`, payload);
}

export function listOrganizationInvitations(organizationId) {
  return apiGet(`/organizations/${organizationId}/invitations`);
}

export function revokeOrganizationInvitation(organizationId, invitationId) {
  return apiDelete(`/organizations/${organizationId}/invitations/${invitationId}`);
}

export function sendTransactionInvitation(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/invitations`, payload);
}

export function listTransactionInvitations(transactionId) {
  return apiGet(`/transactions/${transactionId}/invitations`);
}

export function revokeTransactionInvitation(transactionId, invitationId) {
  return apiDelete(`/transactions/${transactionId}/invitations/${invitationId}`);
}

export function listMyInvitations() {
  return apiGet('/invitations/mine');
}

export function previewInvitation(token) {
  return apiGet(`/invitations/${token}`);
}

export function acceptInvitation(token) {
  return apiPost(`/invitations/${token}/accept`);
}

export function rejectInvitation(token) {
  return apiPost(`/invitations/${token}/reject`);
}

export function signApproval(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/approval-signatures`, payload);
}

export function getApprovalStatus(transactionId, transition) {
  return apiGet(`/transactions/${transactionId}/approval-signatures?transition=${transition}`);
}
