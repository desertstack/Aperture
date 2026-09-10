/**
 * Building blocks for elements.
 *
 * Everything here sets textContent. Nothing here sets innerHTML, and no caller should either.
 * The page this replaced built every view by pasting captured URLs, headers and response
 * bodies into innerHTML, so a response containing markup ran as markup. Content the device
 * captured is data. It is never allowed to be code.
 */

const SVG_NS = 'http://www.w3.org/2000/svg';

/**
 * Make an element.
 *
 * @param {string} tag
 * @param {object} [props] class, text, attrs, dataset, style, and on<Event> handlers
 * @param {...(Node|string|null|undefined|Array)} children
 */
export function h(tag, props, ...children) {
  const el = document.createElement(tag);
  applyProps(el, props);
  append(el, children);
  return el;
}

function applyProps(el, props) {
  if (!props) return;
  for (const [key, value] of Object.entries(props)) {
    if (value === null || value === undefined || value === false) continue;

    if (key === 'class') {
      el.className = value;
    } else if (key === 'text') {
      el.textContent = String(value);
    } else if (key === 'dataset') {
      for (const [dk, dv] of Object.entries(value)) {
        if (dv !== null && dv !== undefined) el.dataset[dk] = String(dv);
      }
    } else if (key === 'style') {
      for (const [sk, sv] of Object.entries(value)) el.style.setProperty(sk, sv);
    } else if (key.startsWith('on') && typeof value === 'function') {
      el.addEventListener(key.slice(2).toLowerCase(), value);
    } else if (key === 'value') {
      el.value = value;
    } else if (key === 'checked' || key === 'disabled' || key === 'hidden') {
      el[key] = Boolean(value);
    } else {
      el.setAttribute(key, value === true ? '' : String(value));
    }
  }
}

function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

/** Replace an element's children in one step. */
export function fill(el, ...children) {
  el.replaceChildren();
  append(el, children);
  return el;
}

/** A text node. Handy where a bare string would be ambiguous. */
export function t(value) {
  return document.createTextNode(value === null || value === undefined ? '' : String(value));
}

/**
 * A 16px line icon, drawn from a path this file owns.
 *
 * Built with createElementNS rather than an SVG string, so the no-innerHTML rule holds without
 * an exception for "trusted" markup.
 */
export function icon(name, size = 16) {
  const path = ICONS[name] || ICONS.dot;
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', String(size));
  svg.setAttribute('height', String(size));
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.6');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  for (const d of Array.isArray(path) ? path : [path]) {
    const node = document.createElementNS(SVG_NS, 'path');
    node.setAttribute('d', d);
    svg.append(node);
  }
  return svg;
}

const ICONS = {
  activity: 'M2 12h4l3 8 6-16 3 8h4',
  sliders: ['M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0', 'M16 4v4M10 10v4M18 16v4'],
  database: ['M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3z', 'M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6', 'M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3'],
  layers: ['M12 3 3 8l9 5 9-5-9-5z', 'M3 16l9 5 9-5', 'M3 12l9 5 9-5'],
  folder: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
  search: ['M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z', 'M20 20l-4-4'],
  x: 'M6 6l12 12M18 6L6 18',
  check: 'M5 13l4 4L19 7',
  trash: ['M4 7h16', 'M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2', 'M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12'],
  edit: ['M4 20h4L19 9a2 2 0 0 0-3-3L5 17z', 'M15 6l3 3'],
  download: ['M12 4v11', 'M8 12l4 4 4-4', 'M4 19h16'],
  refresh: ['M20 11a8 8 0 1 0-1 5', 'M20 5v6h-6'],
  sun: ['M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z', 'M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4'],
  moon: 'M20 14a8 8 0 1 1-9-11 7 7 0 0 0 9 11z',
  alert: ['M12 4 2 20h20L12 4z', 'M12 10v4M12 17v.5'],
  lock: ['M6 11h12v9H6z', 'M9 11V8a3 3 0 0 1 6 0v3'],
  eye: ['M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6z', 'M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z'],
  back: ['M20 12H4', 'M10 6l-6 6 6 6'],
  copy: ['M9 9h10v12H9z', 'M15 5H5v12'],
  command: 'M9 3a3 3 0 1 0 3 3v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3V6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3z',
  play: 'M7 4l12 8-12 8z',
  file: ['M6 3h8l4 4v14H6z', 'M14 3v4h4'],
  dot: 'M12 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2z',
};
