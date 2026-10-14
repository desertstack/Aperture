/**
 * Path routing.
 *
 * Every view has an address: /network/42, /prefs/settings, /db/app/users?page=2. That makes a
 * view something you can reload, bookmark and paste to someone else, and it lets the browser
 * back button mean what it looks like it means.
 *
 * The device answers every path outside /api and /ui with the console itself, so a reload of
 * /logcat gets this page, and this file reads the path to pick the view.
 *
 * The first segment names the panel. The rest belongs to that panel.
 */

let onRoute = null;
let current = { path: '/', segments: [], params: new URLSearchParams() };

export function currentRoute() {
  return current;
}

export function go(path, replace = false) {
  const target = path.startsWith('/') ? path : `/${path}`;
  if (`${location.pathname}${location.search}` === target) return;
  if (replace) history.replaceState(null, '', target);
  else history.pushState(null, '', target);
  resolve();
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
  const path = location.pathname || '/';
  const segments = path.split('/').filter(Boolean).map(decodeURIComponent);
  return { path, segments, params: new URLSearchParams(location.search) };
}

function resolve() {
  current = parse();
  if (onRoute) onRoute(current);
}

/**
 * Older links put the view after a hash, as in /#/logcat. Such a link still opens the view it
 * names, at its new address.
 */
function upgradeHashAddress() {
  if (!location.hash.startsWith('#/')) return;
  history.replaceState(null, '', location.hash.slice(1));
}

/** @param {(route: {path: string, segments: string[], params: URLSearchParams}) => void} fn */
export function start(fn) {
  onRoute = fn;
  upgradeHashAddress();
  window.addEventListener('popstate', resolve);
  resolve();
}
