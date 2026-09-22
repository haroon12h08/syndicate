import { apiGet } from './client';

export function getWorkbench(transactionId) {
  return apiGet(`/transactions/${transactionId}/workbench`);
}
