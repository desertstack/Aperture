/**
 * The command palette.
 *
 * Every panel contributes what it knows about — a preferences file, a database table, a recent
 * request — so one keystroke reaches anything on the device without hunting through panes.
 */

import { h, fill, icon } from '../core/dom.js';

const providers = new Set();
let scrim = null;
let selected = 0;
let items = [];

/**
 * @param {() => Array<{label: string, hint?: string, icon?: string, run: () => void}>} fn
 */
export function registerCommands(fn) {
  providers.add(fn);
  return () => providers.delete(fn);
}

function collect(term) {
  const all = [];
  for (const provider of providers) {
    try {
      all.push(...provider());
    } catch (e) {
      // A panel that cannot list its commands must not close the palette.
    }
  }
  const needle = term.trim().toLowerCase();
  if (!needle) return all.slice(0, 60);
  return all
    .map((item) => ({ item, score: score(item, needle) }))
    .filter((row) => row.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, 60)
    .map((row) => row.item);
}

function score(item, needle) {
  const label = item.label.toLowerCase();
  const hint = (item.hint || '').toLowerCase();
  if (label.startsWith(needle)) return 100;
  if (label.includes(needle)) return 60;
  if (hint.includes(needle)) return 30;
  // Loose match: the letters in order, anywhere.
  let index = 0;
  for (const ch of needle) {
    index = label.indexOf(ch, index);
    if (index === -1) return 0;
    index += 1;
  }
  return 10;
}

export function closePalette() {
  if (!scrim) return;
  scrim.remove();
  scrim = null;
  items = [];
}

export function openPalette() {
  if (scrim) {
    closePalette();
    return;
  }

  const list = h('div', { class: 'palette__list', role: 'listbox' });
  const input = h('input', {
    class: 'palette__input',
    type: 'text',
    placeholder: 'Go to a panel, a file, a table…',
    'aria-label': 'Command palette',
    autocomplete: 'off',
    spellcheck: 'false',
  });

  function render(term) {
    items = collect(term);
    selected = 0;
    if (items.length === 0) {
      fill(list, h('div', { class: 'empty' }, h('span', { text: 'Nothing matches that.' })));
      return;
    }
    fill(
      list,
      ...items.map((item, index) =>
        h(
          'button',
          {
            class: 'palette__item',
            role: 'option',
            'aria-selected': String(index === selected),
            onClick: () => run(index),
            onMousemove: () => mark(index),
          },
          icon(item.icon || 'dot', 14),
          h('span', { text: item.label }),
          item.hint ? h('span', { class: 'faint', text: item.hint }) : null
        )
      )
    );
  }

  function mark(index) {
    if (index === selected) return;
    selected = index;
    [...list.children].forEach((child, i) => child.setAttribute('aria-selected', String(i === selected)));
  }

  function move(delta) {
    if (items.length === 0) return;
    mark((selected + delta + items.length) % items.length);
    list.children[selected]?.scrollIntoView({ block: 'nearest' });
  }

  function run(index) {
    const item = items[index];
    closePalette();
    if (item) item.run();
  }

  input.addEventListener('input', () => render(input.value));
  input.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowDown' || (event.key === 'n' && event.ctrlKey)) {
      event.preventDefault();
      move(1);
    } else if (event.key === 'ArrowUp' || (event.key === 'p' && event.ctrlKey)) {
      event.preventDefault();
      move(-1);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      run(selected);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      closePalette();
    }
  });

  scrim = h(
    'div',
    {
      class: 'scrim',
      style: { 'align-items': 'flex-start' },
      onClick: (event) => {
        if (event.target === scrim) closePalette();
      },
    },
    h('div', { class: 'palette' }, input, list)
  );

  document.body.append(scrim);
  render('');
  input.focus();
}
