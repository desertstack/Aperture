/**
 * Modal questions.
 *
 * The confirm button always carries the name of the action, never "OK". A dialog that says
 * "Delete settings.xml" and offers a button reading "Delete file" cannot be dismissed by
 * habit the way an OK can.
 */

import { h, icon } from '../core/dom.js';

let openScrim = null;

function close() {
  if (!openScrim) return;
  openScrim.remove();
  openScrim = null;
  document.removeEventListener('keydown', onKey, true);
}

function onKey(event) {
  if (event.key === 'Escape') {
    event.stopPropagation();
    close();
  }
}

/**
 * @param {object} options
 * @param {string} options.title
 * @param {string} options.body
 * @param {string} [options.target] the exact thing being acted on, shown verbatim
 * @param {string} options.confirmLabel names the action, e.g. "Delete file"
 * @param {boolean} [options.danger]
 * @returns {Promise<boolean>}
 */
export function confirmAction({ title, body, target, confirmLabel, danger = true }) {
  close();
  return new Promise((resolve) => {
    const finish = (answer) => {
      close();
      resolve(answer);
    };

    const confirmBtn = h('button', {
      class: `btn ${danger ? 'btn--danger' : 'btn--primary'}`,
      text: confirmLabel,
      onClick: () => finish(true),
    });

    const dialog = h(
      'div',
      { class: 'dialog', role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
      h('div', { class: 'dialog__title', text: title }),
      h(
        'div',
        { class: 'dialog__body' },
        h('span', { text: body }),
        target ? h('code', { class: 'dialog__target', text: target }) : null
      ),
      h(
        'div',
        { class: 'dialog__actions' },
        h('button', { class: 'btn', text: 'Cancel', onClick: () => finish(false) }),
        confirmBtn
      )
    );

    openScrim = h('div', {
      class: 'scrim',
      onClick: (event) => {
        if (event.target === openScrim) finish(false);
      },
    }, dialog);

    document.body.append(openScrim);
    document.addEventListener('keydown', onKey, true);
    confirmBtn.focus();
  });
}

/** A plain panel for content that is not a question. */
export function showPanel(title, content) {
  close();
  const dialog = h(
    'div',
    { class: 'dialog', role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
    h(
      'div',
      { style: { display: 'flex', 'align-items': 'center', 'justify-content': 'space-between' } },
      h('div', { class: 'dialog__title', text: title }),
      h('button', { class: 'btn btn--quiet btn--icon', 'aria-label': 'Close', onClick: close }, icon('x'))
    ),
    content
  );
  openScrim = h('div', {
    class: 'scrim',
    onClick: (event) => {
      if (event.target === openScrim) close();
    },
  }, dialog);
  document.body.append(openScrim);
  document.addEventListener('keydown', onKey, true);
}

export const closeDialog = close;
