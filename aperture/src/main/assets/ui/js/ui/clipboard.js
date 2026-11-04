/** Copy to the clipboard, from any panel. */

import { h, icon } from '../core/dom.js';
import { toast } from './toast.js';

/**
 * The Clipboard API works only on a secure origin. The console is often opened over plain
 * HTTP on the device's address, so fall back to a hidden text area.
 */
export async function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const area = h('textarea', { value: text, style: { position: 'fixed', opacity: '0' } });
  document.body.append(area);
  area.select();
  const ok = document.execCommand('copy');
  area.remove();
  if (!ok) throw new Error('copy failed');
}

/**
 * A small Copy button.
 *
 * @param {() => string} getText read when clicked, so the button copies what is on screen now
 * @param {string} doneMessage the toast after a good copy
 * @param {{label?: string, quiet?: boolean}} [opts]
 */
export function copyButton(getText, doneMessage, opts = {}) {
  const label = opts.label ?? 'Copy';
  return h('button', {
    class: `btn btn--sm${opts.quiet === false ? '' : ' btn--quiet'}`,
    type: 'button',
    title: label,
    onClick: async () => {
      try {
        await copyText(getText());
        toast.ok(doneMessage);
      } catch (e) {
        toast.error('Could not copy. Select the text and copy it by hand.');
      }
    },
  }, icon('copy', 13), label);
}
