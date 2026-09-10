/** Turning device numbers into something readable. */

export function bytes(value) {
  if (value === null || value === undefined) return '—';
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

export function duration(ms) {
  if (ms === null || ms === undefined) return '—';
  const n = Number(ms);
  if (!Number.isFinite(n)) return '—';
  if (n < 1000) return `${Math.round(n)} ms`;
  if (n < 60000) return `${(n / 1000).toFixed(2)} s`;
  return `${Math.floor(n / 60000)}m ${Math.round((n % 60000) / 1000)}s`;
}

export function time(epochMs) {
  if (!epochMs) return '—';
  const d = new Date(Number(epochMs));
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function dateTime(epochMs) {
  if (!epochMs) return '—';
  const d = new Date(Number(epochMs));
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString();
}

/** Which status band a code falls in, for colour and for filtering. */
export function statusClass(code) {
  if (code === null || code === undefined) return 'none';
  const hundreds = Math.floor(Number(code) / 100);
  return hundreds >= 1 && hundreds <= 5 ? `${hundreds}xx` : 'none';
}

export function statusTone(code) {
  switch (statusClass(code)) {
    case '2xx': return 'ok';
    case '3xx': return 'info';
    case '4xx': return 'warn';
    case '5xx': return 'err';
    default: return null;
  }
}

/**
 * Lay a body out if it is JSON, and leave it exactly as it came if it is not.
 * Never throws: a malformed body still has to be readable.
 */
export function prettyBody(text, contentType) {
  if (!text) return '';
  const looksJson = (contentType || '').toLowerCase().includes('json') ||
    /^\s*[[{]/.test(text);
  if (!looksJson) return text;
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch (e) {
    return text;
  }
}

/** Headers reach the console as a JSON object in a string. */
export function headerLines(json) {
  if (!json) return '';
  try {
    const parsed = JSON.parse(json);
    const entries = Object.entries(parsed);
    if (entries.length === 0) return '';
    const width = Math.max(...entries.map(([k]) => k.length));
    return entries.map(([k, v]) => `${k.padEnd(width)}  ${v}`).join('\n');
  } catch (e) {
    return json;
  }
}

/** Shorten the middle of a long path so both ends stay readable. */
export function middleTruncate(text, max = 64) {
  if (!text || text.length <= max) return text || '';
  const half = Math.floor((max - 1) / 2);
  return `${text.slice(0, half)}…${text.slice(text.length - half)}`;
}
