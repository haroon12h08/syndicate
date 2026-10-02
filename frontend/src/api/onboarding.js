import { apiPost } from './client';

/** Opens the company, the transaction and its diligence areas in one step. */
export function startTransaction(payload) {
  return apiPost('/transactions/start', payload);
}
