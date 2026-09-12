import { apiGet, apiPost, apiSend } from './client';

export function listTasks(transactionId, filters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.append(key, value);
  });
  const query = params.toString();
  return apiGet(`/transactions/${transactionId}/tasks${query ? `?${query}` : ''}`);
}

export function createTask(transactionId, payload) {
  return apiPost(`/transactions/${transactionId}/tasks`, payload);
}

export function updateTask(taskId, payload) {
  return apiSend('PATCH', `/tasks/${taskId}`, payload);
}

export function listNotifications() {
  return apiGet('/notifications');
}

export function markNotificationRead(id) {
  return apiPost(`/notifications/${id}/read`);
}
