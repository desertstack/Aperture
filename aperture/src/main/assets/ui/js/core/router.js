/**
 * Hash routing.
 *
 * Every view has an address: #/network/42, #/prefs/settings, #/db/app/users?page=2. That makes
 * a view something you can reload, bookmark and paste to someone else, and it lets the browser
 * back button mean what it looks like it means.
 *
 * The first segment names the panel. The rest belongs to that panel.
 */

let onRoute = null;
let current = { path: '/', segments: [], params: new URLSearchParams() };

export function currentRoute() {
  return current;
}

export function go(path, replace = false) {
  const target = `#${path.startsWith('/') ? path : `/${path}`}`;
  if (location.hash === target) return;
  if (replace) {
    history.replaceState(null, '', target);
    resolve();
  } else {
    location.hash = target;
  }
}

/** Change one query parameter without losing the rest of the address. */
export function setParam(key, value) {
  const params = new URLSearchParams(current.params);
  if (value === null || value === undefined || value === '') params.delete(key);
  else params.set(key, String(value));
  const text = params.toString();
  go(`${current.path}${text ? `?${text}` : ''}`, true);
}

function parse() {
  const raw = location.hash.replace(/^#/, '') || '/';
  const [path, search] = raw.split('?');
  const segments = (path || '/').split('/').filter(Boolean).map(decodeURIComponent);
  return { path: path || '/', segments, params: new URLSearchParams(search || '') };
}

function resolve() {
  current = parse();
  if (onRoute) onRoute(current);
}

/** @param {(route: {path: string, segments: string[], params: URLSearchParams}) => void} fn */
export function start(fn) {
  onRoute = fn;
  window.addEventListener('hashchange', resolve);
  resolve();
}
