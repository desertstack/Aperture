/**
 * The live stream.
 *
 * One EventSource for the whole console. Panels subscribe to the event names they draw, so a
 * panel that is not on screen costs nothing, and opening a second panel does not open a second
 * connection to the device.
 */

import { withToken } from './api.js';

const EVENTS = [
  'connected',
  'new_transaction',
  'updated_transaction',
  'deleted_transaction',
  'all_deleted',
  'prefs_changed',
  'datastore_changed',
  'file_changed',
  'db_changed',
];

const listeners = new Map();
const stateListeners = new Set();

let source = null;
let state = 'idle';
let retryMs = 1000;
let retryTimer = null;

function setState(next) {
  if (state === next) return;
  state = next;
  for (const fn of stateListeners) fn(state);
}

export function connectionState() {
  return state;
}

export function onConnectionState(fn) {
  stateListeners.add(fn);
  fn(state);
  return () => stateListeners.delete(fn);
}

/**
 * @param {string} type event name, as the device sends it
 * @param {(payload: any) => void} fn
 * @returns {() => void} stop listening
 */
export function on(type, fn) {
  if (!listeners.has(type)) listeners.set(type, new Set());
  listeners.get(type).add(fn);
  return () => listeners.get(type)?.delete(fn);
}

function dispatch(type, raw) {
  const set = listeners.get(type);
  if (!set || set.size === 0) return;
  let payload = null;
  try {
    payload = raw ? JSON.parse(raw) : null;
  } catch (e) {
    return;
  }
  for (const fn of set) {
    try {
      fn(payload);
    } catch (e) {
      console.error('[Aperture] listener for', type, 'failed', e);
    }
  }
}

export function connect() {
  disconnect();
  setState('connecting');

  try {
    source = new EventSource(withToken('/api/stream'));
  } catch (e) {
    setState('down');
    scheduleRetry();
    return;
  }

  source.addEventListener('open', () => {
    retryMs = 1000;
    setState('live');
  });

  for (const type of EVENTS) {
    source.addEventListener(type, (event) => {
      if (type === 'connected') setState('live');
      dispatch(type, event.data);
    });
  }

  source.addEventListener('error', () => {
    // The browser reconnects on its own for a dropped connection, but not for a closed one.
    if (source && source.readyState === EventSource.CLOSED) {
      setState('down');
      scheduleRetry();
    } else {
      setState('connecting');
    }
  });
}

function scheduleRetry() {
  clearTimeout(retryTimer);
  retryTimer = setTimeout(connect, retryMs);
  // Back off to half a minute, so a stopped app does not make the browser busy.
  retryMs = Math.min(retryMs * 2, 30000);
}

export function disconnect() {
  clearTimeout(retryTimer);
  if (source) {
    source.close();
    source = null;
  }
}
