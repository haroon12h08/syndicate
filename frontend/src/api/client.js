// Baked in at build time so the same image can point at any API origin.
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export function apiBaseUrl() {
  return BASE_URL;
}

function getToken() {
  return localStorage.getItem('syndicate_token');
}

export function setToken(token) {
  if (token) {
    localStorage.setItem('syndicate_token', token);
  } else {
    localStorage.removeItem('syndicate_token');
  }
}

async function handleResponse(response) {
  if (response.status === 204) {
    return null;
  }
  const isJson = response.headers.get('content-type')?.includes('application/json');
  const body = isJson ? await response.json() : null;
  if (!response.ok) {
    if (response.status === 401) {
      // Clearing the token lets the router redirect; assigning location would force a full reload.
      setToken(null);
      window.dispatchEvent(new CustomEvent('syndicate:unauthorized'));
    }
    const message = body?.message || response.statusText;
    throw new Error(message);
  }
  return body;
}

function authHeaders(extra = {}) {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}`, ...extra } : extra;
}

export async function apiGet(path) {
  const response = await fetch(`${BASE_URL}${path}`, { headers: authHeaders() });
  return handleResponse(response);
}

export async function apiSend(method, path, body) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  return handleResponse(response);
}

export async function apiPost(path, body) {
  return apiSend('POST', path, body);
}

export async function apiPut(path, body) {
  return apiSend('PUT', path, body);
}

export async function apiDelete(path) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  return handleResponse(response);
}

export async function apiUpload(path, formData) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: authHeaders(),
    body: formData,
  });
  return handleResponse(response);
}

export async function apiDownload(path) {
  const response = await fetch(`${BASE_URL}${path}`, { headers: authHeaders() });
  if (!response.ok) {
    throw new Error('Download failed');
  }
  const disposition = response.headers.get('content-disposition') || '';
  const match = disposition.match(/filename="(.+)"/);
  const filename = match ? match[1] : 'download';
  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

export async function apiImageBlobUrl(path) {
  const response = await fetch(`${BASE_URL}${path}`, { headers: authHeaders() });
  if (!response.ok) {
    throw new Error('Failed to load image');
  }
  const blob = await response.blob();
  return window.URL.createObjectURL(blob);
}
