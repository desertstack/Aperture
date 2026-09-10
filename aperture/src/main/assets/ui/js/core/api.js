/**
 * Talking to the device.
 *
 * One place holds the token, so the token screen has one thing to fill in and the live stream
 * and ordinary requests cannot disagree about it.
 */

const TOKEN_KEY = 'aperture.token';

let token = readStoredToken();
const authListeners = new Set();

function readStoredToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY) || '';
  } catch (e) {
    return '';
  }
}

export function getToken() {
  return token;
}

export function setToken(value) {
  token = value || '';
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch (e) {
    // A private window refuses storage. The token still works for this page.
  }
}

/** Called when the device turns a request away for want of a token. */
export function onAuthRequired(fn) {
  authListeners.add(fn);
  return () => authListeners.delete(fn);
}

/** Add the token to a URL, for EventSource, which cannot set a header. */
export function withToken(url) {
  if (!token) return url;
  const joiner = url.includes('?') ? '&' : '?';
  return `${url}${joiner}token=${encodeURIComponent(token)}`;
}

export class ApiError extends Error {
  constructor(status, error, message) {
    super(message || error || `Request failed (${status})`);
    this.status = status;
    this.error = error;
  }
}

async function request(method, path, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (e) {
    throw new ApiError(0, 'Offline', 'The device is not answering. Is the app still running?');
  }

  if (response.status === 401) {
    for (const fn of authListeners) fn();
    throw new ApiError(401, 'Unauthorized', 'Aperture needs its access token.');
  }

  if (response.status === 204) return null;

  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch (e) {
      payload = null;
    }
  }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      payload?.error || 'Error',
      payload?.message || `The device answered ${response.status}.`
    );
  }

  return payload;
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body ?? {}),
  put: (path, body) => request('PUT', path, body ?? {}),
  del: (path, body) => request('DELETE', path, body),
};

/** Build a query string, leaving out anything empty. */
export function query(params) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '') continue;
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}
