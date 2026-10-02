// The API is served from the same origin as the app, so no host needs configuring anywhere.
// In development Vite proxies /api to the local backend (see vite.config.js).
const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export function apiBaseUrl() {
  return BASE_URL;
}

// The access token lives in memory only: it is short-lived, and nothing a script on this page
// can read survives the tab. Staying signed in is the session cookie's job, and the browser
// will not hand that to any script.
let accessToken = null;

function getToken() {
  return accessToken;
}

/** For the one place that cannot send a header: the server-sent events stream. */
export function currentAccessToken() {
  return accessToken;
}

export function setToken(token) {
  accessToken = token || null;
}

/** Exchanges the session cookie for a fresh access token. Returns false when there is no session. */
export async function refreshAccessToken() {
  try {
    const response = await fetch(`${BASE_URL}/auth/refresh`, { method: 'POST', credentials: 'same-origin' });
    if (!response.ok) {
      accessToken = null;
      return false;
    }
    const body = await response.json();
    accessToken = body.token;
    return body;
  } catch {
    accessToken = null;
    return false;
  }
}

/**
 * One place where a request is made, and the only place that knows an expired access token can
 * be replaced silently. A person should never be thrown out of a half-finished review because a
 * token aged out between two clicks.
 */
async function send(path, init, retrying = false) {
  const response = await fetch(`${BASE_URL}${path}`, { credentials: 'same-origin', ...init,
    headers: authHeaders(init.headers) });
  if (response.status === 401 && !retrying && !path.startsWith('/auth/')) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      return send(path, init, true);
    }
  }
  return response;
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
  return handleResponse(await send(path, { method: 'GET' }));
}

export async function apiSend(method, path, body) {
  return handleResponse(await send(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  }));
}

export async function apiPost(path, body) {
  return apiSend('POST', path, body);
}

export async function apiPut(path, body) {
  return apiSend('PUT', path, body);
}

export async function apiDelete(path) {
  return handleResponse(await send(path, { method: 'DELETE' }));
}

export async function apiUpload(path, formData) {
  return handleResponse(await send(path, { method: 'POST', body: formData }));
}

export async function apiDownload(path) {
  const response = await send(path, { method: 'GET' });
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
  const response = await send(path, { method: 'GET' });
  if (!response.ok) {
    throw new Error('Failed to load image');
  }
  const blob = await response.blob();
  return window.URL.createObjectURL(blob);
}
