import { apiGet, apiPost, apiPut } from './client';

export function listQuestions(transactionId) {
  return apiGet(`/transactions/${transactionId}/diligence-questions`);
}

export function answerQuestion(questionId, payload) {
  return apiPut(`/diligence-questions/${questionId}/answer`, payload);
}

export function acceptAnswer(questionId) {
  return apiPost(`/diligence-questions/${questionId}/accept`);
}

export function addQuestion(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/diligence-questions`, payload);
}
