/**
 * A body viewer: a panel with a title, the size, a Pretty/Raw switch for JSON, and Copy.
 *
 * Colour comes from spans whose text is set with textContent, never from markup, so a body
 * that holds HTML still shows as text.
 */

import { h, fill, icon } from '../core/dom.js';
import { bytes, isJson, prettyBody, jsonTokens } from '../core/format.js';
import { copyButton } from './clipboard.js';

/** Above this many characters, colour costs more than it gives. */
const HIGHLIGHT_LIMIT = 200_000;
const TRUNCATION_MARK = '[Aperture truncated this body at';

/**
 * @param {object} opts
 * @param {string} opts.title
 * @param {string|null} opts.text
 * @param {string|null} [opts.contentType]
 * @param {boolean} [opts.binary] the text is base64 of a binary body
 * @param {string} opts.emptyMessage
 * @param {Node[]} [opts.actions] extra buttons for the head
 * @param {boolean} [opts.fold] the head opens and closes the panel
 * @param {boolean} [opts.open] how a folding panel starts; an empty body always starts closed
 * @param {string} [opts.id] the element id
 */
export function codeView(opts) {
  const text = opts.text || '';
  const title = h('span', { class: 'panel__title', text: opts.title });
  const head = opts.fold
    ? h('summary', { class: 'panel__head' }, icon('chevron', 14), title)
    : h('div', { class: 'panel__head' }, title);
  const panel = opts.fold
    ? h('details', { class: 'panel panel--fold', id: opts.id, open: text ? opts.open !== false : false }, head)
    : h('section', { class: 'panel', id: opts.id }, head);
  // In a folding head, a click on a button must not also open or close the panel.
  const actions = (...nodes) =>
    h('div', { class: 'panel__actions', onClick: opts.fold ? (e) => e.preventDefault() : null }, ...nodes);

  if (!text) {
    if (opts.fold) head.append(h('span', { class: 'panel__meta', text: 'Empty' }));
    head.append(actions(...(opts.actions || [])));
    panel.append(h('div', { class: 'code code--empty', text: opts.emptyMessage }));
    return panel;
  }

  const json = !opts.binary && isJson(text, opts.contentType);
  const pretty = json ? prettyBody(text, opts.contentType) : text;
  let mode = json ? 'pretty' : 'raw';

  const pre = h('pre', { class: 'code' });
  const shown = () => (mode === 'pretty' ? pretty : text);

  function paint() {
    if (mode === 'pretty' && pretty.length <= HIGHLIGHT_LIMIT) {
      fill(pre, ...jsonTokens(pretty).map(([type, piece]) =>
        type ? h('span', { class: `syn-${type}`, text: piece }) : piece));
    } else {
      pre.textContent = shown();
    }
    for (const btn of seg?.children || []) {
      btn.setAttribute('aria-pressed', String(btn.dataset.mode === mode));
    }
  }

  const seg = json
    ? h(
        'div',
        { class: 'seg seg--text seg--sm', role: 'group', 'aria-label': 'Body view' },
        ...['pretty', 'raw'].map((m) =>
          h('button', {
            class: 'seg__btn',
            type: 'button',
            dataset: { mode: m },
            text: m === 'pretty' ? 'Pretty' : 'Raw',
            onClick: () => {
              mode = m;
              paint();
            },
          })
        )
      )
    : null;

  head.append(
    h('span', { class: 'panel__meta', text: `${json ? 'JSON · ' : ''}${bytes(new TextEncoder().encode(text).length)}` }),
    actions(seg, ...(opts.actions || []), copyButton(shown, `${opts.title} copied.`))
  );

  if (opts.binary) {
    panel.append(h('div', { class: 'panel__note', text: 'Binary body, shown as base64.' }));
  } else if (text.includes(TRUNCATION_MARK)) {
    panel.append(h('div', { class: 'panel__note', text: 'Aperture keeps the first 512 KB of a body. The rest is not stored.' }));
  }

  panel.append(pre);
  paint();
  return panel;
}
