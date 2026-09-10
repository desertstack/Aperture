/**
 * Short messages, with room for an Undo.
 *
 * A write that can be reversed says so here rather than asking first. Asking before every edit
 * makes editing tiresome; offering the way back makes it safe.
 */

import { h, fill } from '../core/dom.js';
import { icon } from '../core/dom.js';

let host = null;

function container() {
  if (!host) {
    host = h('div', { class: 'toasts', role: 'status', 'aria-live': 'polite' });
    document.body.append(host);
  }
  return host;
}

function show(message, { tone = null, action = null, onAction = null, ms = 5000 } = {}) {
  const el = h(
    'div',
    { class: `toast${tone ? ` toast--${tone}` : ''}` },
    h('span', { text: message }),
    action && onAction
      ? h('button', {
          class: 'btn btn--sm',
          text: action,
          onClick: () => {
            dismiss();
            onAction();
          },
        })
      : null,
    h('button', {
      class: 'btn btn--quiet btn--icon btn--sm',
      'aria-label': 'Dismiss',
      onClick: () => dismiss(),
    }, icon('x', 14))
  );

  let timer = setTimeout(dismiss, ms);

  function dismiss() {
    clearTimeout(timer);
    el.remove();
  }

  // Reading a message should not race a timer.
  el.addEventListener('mouseenter', () => clearTimeout(timer));
  el.addEventListener('mouseleave', () => {
    timer = setTimeout(dismiss, 2000);
  });

  container().append(el);
  return dismiss;
}

export const toast = {
  info: (message, options) => show(message, options),
  ok: (message, options) => show(message, { tone: 'ok', ...options }),
  error: (message, options) => show(message, { tone: 'err', ms: 8000, ...options }),
  /** A write that went through, with the way back. */
  undoable: (message, undo) => show(message, { tone: 'ok', action: 'Undo', onAction: undo, ms: 8000 }),
};
