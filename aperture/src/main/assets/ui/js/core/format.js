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

/**
 * Headers reach the console as JSON in a string: a list of {name, value} pairs, which keeps
 * repeated names such as Set-Cookie. A plain {name: value} object also works.
 * Returns [name, value] pairs, or null if the text is not JSON.
 */
export function headerEntries(json) {
  if (!json) return [];
  let parsed;
  try {
    parsed = JSON.parse(json);
  } catch (e) {
    return null;
  }
  if (Array.isArray(parsed)) {
    return parsed
      .filter((item) => item && typeof item === 'object')
      .map((item) => [String(item.name ?? ''), String(item.value ?? '')]);
  }
  if (parsed && typeof parsed === 'object') {
    return Object.entries(parsed).map(([k, v]) => [k, String(v)]);
  }
  return null;
}

export function headerLines(json) {
  const entries = headerEntries(json);
  if (entries === null) return json;
  if (entries.length === 0) return '';
  const width = Math.max(...entries.map(([k]) => k.length));
  return entries.map(([k, v]) => `${k.padEnd(width)}  ${v}`).join('\n');
}

/** True if the text parses as JSON and looks like it was meant to be JSON. */
export function isJson(text, contentType) {
  if (!text) return false;
  const looksJson = (contentType || '').toLowerCase().includes('json') || /^\s*[[{]/.test(text);
  if (!looksJson) return false;
  try {
    JSON.parse(text);
    return true;
  } catch (e) {
    return false;
  }
}

/** Why a JSON text does not parse, or null if it does. */
export function jsonError(text) {
  try {
    JSON.parse(text);
    return null;
  } catch (e) {
    return e.message;
  }
}

const JSON_TOKEN = /("(?:[^"\\]|\\.)*")(\s*:)?|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b|([{}[\],])/g;

/**
 * Split JSON text into typed pieces for colour: key, str, num, lit, punct, or null for plain
 * text such as white space. Joined in order, the pieces give back the input exactly.
 */
export function jsonTokens(text) {
  const out = [];
  let last = 0;
  JSON_TOKEN.lastIndex = 0;
  for (let m = JSON_TOKEN.exec(text); m; m = JSON_TOKEN.exec(text)) {
    if (m.index > last) out.push([null, text.slice(last, m.index)]);
    if (m[1] !== undefined) {
      out.push([m[2] ? 'key' : 'str', m[1]]);
      if (m[2]) out.push(['punct', m[2]]);
    } else if (m[3] !== undefined) {
      out.push(['num', m[3]]);
    } else if (m[4] !== undefined) {
      out.push(['lit', m[4]]);
    } else {
      out.push(['punct', m[5]]);
    }
    last = JSON_TOKEN.lastIndex;
  }
  if (last < text.length) out.push([null, text.slice(last)]);
  return out;
}

/** Wrap text in single quotes for a POSIX shell. */
function shellQuote(text) {
  return `'${String(text).replace(/'/g, `'\\''`)}'`;
}

/**
 * The request as a curl command you can paste into a terminal.
 * Content-Length is left out because curl counts the body itself.
 */
export function curlCommand(tx) {
  const method = (tx.method || 'GET').toUpperCase();
  const parts = [`curl${method === 'GET' ? '' : ` -X ${method}`} ${shellQuote(tx.url)}`];

  const headers = (headerEntries(tx.requestHeaders) || [])
    .filter(([name]) => name.toLowerCase() !== 'content-length');
  const hasBody = Boolean(tx.requestBody);
  const hasContentType = headers.some(([name]) => name.toLowerCase() === 'content-type');
  // OkHttp keeps the body's content type on the body, not in the header list.
  if (hasBody && !hasContentType && tx.requestContentType) {
    headers.push(['Content-Type', tx.requestContentType]);
  }
  for (const [name, value] of headers) parts.push(`-H ${shellQuote(`${name}: ${value}`)}`);

  if (hasBody) {
    if (tx.requestBodyIsPlainText === false) {
      parts.push('# Binary request body left out');
    } else {
      parts.push(`--data-raw ${shellQuote(tx.requestBody)}`);
    }
  }
  return parts.join(' \\\n  ');
}

/** Shorten the middle of a long path so both ends stay readable. */
export function middleTruncate(text, max = 64) {
  if (!text || text.length <= max) return text || '';
  const half = Math.floor((max - 1) / 2);
  return `${text.slice(0, half)}…${text.slice(text.length - half)}`;
}
