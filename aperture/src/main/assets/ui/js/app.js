/**
 * The console shell.
 *
 * It owns the frame — top bar, rail, the two panes — and nothing about any one domain. Panels
 * are handed the two panes and get on with it. The rail is built from what the device says it
 * offers, so a domain the host app turned off simply is not here.
 */

import { h, fill, icon } from './core/dom.js';
import { api, setToken, getToken, onAuthRequired } from './core/api.js';
import * as sse from './core/sse.js';
import { start as startRouter, go, currentRoute } from './core/router.js';
import { openPalette, registerCommands, closePalette } from './ui/palette.js';
import { showPanel, closeDialog } from './ui/dialog.js';
import { createNetworkPanel } from './panels/network.js';
import { createPrefsPanel, createDataStorePanel } from './panels/prefs.js';
import { createFilesPanel } from './panels/files.js';
import { createDatabasesPanel } from './panels/databases.js';

const PANEL_FACTORIES = {
  network: createNetworkPanel,
  prefs: createPrefsPanel,
  datastore: createDataStorePanel,
  db: createDatabasesPanel,
  files: createFilesPanel,
};

const root = document.getElementById('root');

let capabilities = null;
let panels = new Map();
let activePanel = null;

boot();

async function boot() {
  try {
    capabilities = await api.get('/api/capabilities');
  } catch (e) {
    if (e.status === 401) {
      renderTokenScreen();
      return;
    }
    renderFatal(e.message);
    return;
  }
  renderShell();
}

// ---------- Token ----------

onAuthRequired(() => {
  if (!document.getElementById('token-screen')) renderTokenScreen();
});

function renderTokenScreen() {
  const input = h('input', {
    class: 'input mono',
    type: 'password',
    placeholder: 'Access token',
    'aria-label': 'Access token',
    value: getToken(),
  });

  const form = h(
    'form',
    {
      onSubmit: async (event) => {
        event.preventDefault();
        setToken(input.value.trim());
        await boot();
      },
      style: { display: 'flex', gap: 'var(--sp-2)', 'margin-top': 'var(--sp-3)' },
    },
    input,
    h('button', { class: 'btn btn--primary', type: 'submit', text: 'Open' })
  );

  fill(
    root,
    h(
      'div',
      { id: 'token-screen', style: { display: 'grid', 'place-items': 'center', height: '100%', padding: 'var(--sp-4)' } },
      h(
        'div',
        { style: { width: 'min(420px, 100%)' } },
        h('div', { style: { display: 'flex', 'align-items': 'center', gap: 'var(--sp-2)' } },
          icon('lock', 18),
          h('h1', { style: { 'font-size': 'var(--fs-lg)' }, text: 'Aperture is locked' })),
        h('p', {
          class: 'muted',
          style: { 'margin-top': 'var(--sp-2)', 'font-size': 'var(--fs-sm)' },
          text: 'This app runs Aperture with requireAuth turned on. Filter Logcat on the Aperture tag to find the token.',
        }),
        form
      )
    )
  );
  input.focus();
}

function renderFatal(message) {
  fill(
    root,
    h('div', { style: { display: 'grid', 'place-items': 'center', height: '100%' } },
      h('div', { class: 'empty' },
        h('span', { class: 'empty__title', text: 'Aperture is not answering.' }),
        h('span', { text: message })))
  );
}

// ---------- Shell ----------

function renderShell() {
  const crumbs = h('div', { class: 'topbar__crumbs' });
  const listPane = h('div', { class: 'list-pane' });
  const detailPane = h('div', { class: 'detail-pane' });
  const rail = buildRail();

  const resizer = h('div', { class: 'resizer', role: 'separator', 'aria-orientation': 'vertical' });
  listPane.append(resizer);
  wireResizer(resizer, listPane);

  fill(
    root,
    h(
      'div',
      { class: 'shell' },
      h(
        'header',
        { class: 'topbar' },
        h('button', {
          class: 'btn btn--quiet btn--icon back-btn',
          'aria-label': 'Back to list',
          onClick: () => document.body.setAttribute('data-stack', 'list'),
        }, icon('back')),
        h('span', { class: 'topbar__brand', text: 'Aperture' }),
        crumbs,
        h('div', { class: 'topbar__tools' }, ...buildTools())
      ),
      h('div', { class: 'body' }, rail, listPane, detailPane)
    )
  );

  const ctx = {
    capabilities,
    get list() {
      // The pane keeps its resizer across panel changes.
      return listPaneContent(listPane, resizer);
    },
    detail: detailPane,
    setCrumbs(parts) {
      fill(
        crumbs,
        ...parts.flatMap((part, index) => [
          index > 0 ? h('span', { class: 'sep', text: '›' }) : null,
          h('span', { class: index === parts.length - 1 && parts.length > 1 ? 'leaf' : '', text: part }),
        ])
      );
      if (parts.length > 1) document.body.setAttribute('data-stack', 'detail');
    },
  };

  for (const inspector of capabilities.inspectors) {
    const factory = PANEL_FACTORIES[inspector.id];
    if (factory) panels.set(inspector.id, factory(ctx));
  }

  applyStoredTheme();
  applyStoredDensity();
  registerGlobalCommands();
  wireKeyboard();
  sse.connect();
  sse.onConnectionState(paintLiveState);

  startRouter(handleRoute);
}

/** Keep one scrollable content holder inside the list pane, alongside the resizer. */
function listPaneContent(listPane, resizer) {
  let content = listPane.querySelector('[data-list-content]');
  if (!content) {
    content = h('div', {
      'data-list-content': '',
      style: { display: 'flex', 'flex-direction': 'column', 'min-height': '0', flex: '1' },
    });
    listPane.insertBefore(content, resizer);
  }
  return content;
}

function buildRail() {
  const rail = h('nav', { class: 'rail', 'aria-label': 'Inspectors' });
  for (const inspector of capabilities.inspectors) {
    rail.append(
      h(
        'button',
        {
          class: 'rail__item',
          id: `rail-${inspector.id}`,
          title: inspector.label,
          onClick: () => go(`/${inspector.id}`),
        },
        icon(inspector.icon),
        h('span', { class: 'rail__label', text: inspector.label })
      )
    );
  }
  rail.append(h('div', { class: 'rail__spacer' }));
  rail.append(
    h(
      'button',
      { class: 'rail__item', title: 'About this device', onClick: showAbout },
      icon('eye'),
      h('span', { class: 'rail__label', text: 'Device' })
    )
  );
  return rail;
}

function buildTools() {
  const live = h(
    'span',
    { class: 'live', id: 'live', 'data-state': 'idle' },
    h('span', { class: 'live__dot' }),
    h('span', { class: 'live__text', text: 'Offline' })
  );

  const tools = [live];

  if (!capabilities.allowWrites) {
    tools.push(
      h('span', {
        class: 'chip chip--quiet',
        title: 'Aperture is read-only. Set allowWrites = true in ApertureConfig to edit app state.',
        text: 'Read-only',
      })
    );
  }

  if (capabilities.exposedOnNetwork) {
    tools.push(
      h(
        'button',
        {
          class: 'chip chip--warn',
          title: 'Open on this network with no token. Click for what to do about it.',
          onClick: showExposure,
        },
        icon('alert', 12),
        h('span', { text: 'Open network' })
      )
    );
  }

  tools.push(
    h('button', {
      class: 'btn btn--quiet btn--icon',
      id: 'theme-btn',
      'aria-label': 'Switch theme',
      title: 'Switch theme',
      onClick: toggleTheme,
    }, icon('moon')),
    h('button', {
      class: 'btn btn--quiet btn--icon',
      'aria-label': 'Commands',
      title: 'Commands  (Cmd/Ctrl K)',
      onClick: openPalette,
    }, icon('command'))
  );

  return tools;
}

function paintLiveState(state) {
  const el = document.getElementById('live');
  if (!el) return;
  el.dataset.state = state === 'live' ? 'live' : state === 'connecting' ? 'connecting' : 'down';
  el.querySelector('.live__text').textContent =
    state === 'live' ? 'Live' : state === 'connecting' ? 'Connecting' : 'Offline';
}

// ---------- Routing ----------

function handleRoute(route) {
  const wanted = route.segments[0] || capabilities.inspectors[0]?.id;
  if (!wanted) return;

  if (!panels.has(wanted)) {
    go(`/${capabilities.inspectors[0].id}`, true);
    return;
  }

  if (route.segments.length <= 1) document.body.setAttribute('data-stack', 'list');

  for (const item of document.querySelectorAll('.rail__item')) {
    item.toggleAttribute('aria-current', false);
    item.removeAttribute('aria-current');
  }
  document.getElementById(`rail-${wanted}`)?.setAttribute('aria-current', 'page');

  const panel = panels.get(wanted);
  if (activePanel && activePanel !== panel) activePanel.deactivate();
  activePanel = panel;
  panel.activate(route);
}

// ---------- Theme and density ----------

function applyStoredTheme() {
  const stored = read('aperture.theme');
  if (stored === 'light' || stored === 'dark') {
    document.documentElement.setAttribute('data-theme', stored);
  }
  paintThemeButton();
}

function toggleTheme() {
  const explicit = document.documentElement.getAttribute('data-theme');
  const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const currentlyDark = explicit ? explicit === 'dark' : systemDark;
  const next = currentlyDark ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', next);
  write('aperture.theme', next);
  paintThemeButton();
}

function paintThemeButton() {
  const btn = document.getElementById('theme-btn');
  if (!btn) return;
  const explicit = document.documentElement.getAttribute('data-theme');
  const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const dark = explicit ? explicit === 'dark' : systemDark;
  fill(btn, icon(dark ? 'sun' : 'moon'));
}

function applyStoredDensity() {
  const stored = read('aperture.density');
  if (stored === 'compact') document.documentElement.setAttribute('data-density', 'compact');
}

function toggleDensity() {
  const compact = document.documentElement.getAttribute('data-density') === 'compact';
  if (compact) {
    document.documentElement.removeAttribute('data-density');
    write('aperture.density', 'comfortable');
  } else {
    document.documentElement.setAttribute('data-density', 'compact');
    write('aperture.density', 'compact');
  }
}

// ---------- Resizer ----------

function wireResizer(resizer, listPane) {
  const stored = read('aperture.listWidth');
  if (stored) listPane.style.setProperty('--list-w', `${stored}px`);

  let startX = 0;
  let startWidth = 0;

  const onMove = (event) => {
    const width = Math.min(620, Math.max(260, startWidth + (event.clientX - startX)));
    listPane.style.setProperty('--list-w', `${width}px`);
  };

  const onUp = () => {
    resizer.dataset.dragging = 'false';
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.body.style.userSelect = '';
    write('aperture.listWidth', String(listPane.getBoundingClientRect().width | 0));
  };

  resizer.addEventListener('mousedown', (event) => {
    event.preventDefault();
    startX = event.clientX;
    startWidth = listPane.getBoundingClientRect().width;
    resizer.dataset.dragging = 'true';
    document.body.style.userSelect = 'none';
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });
}

// ---------- Keyboard ----------

function wireKeyboard() {
  let pendingGo = false;

  document.addEventListener('keydown', (event) => {
    const typing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(event.target.tagName);

    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      openPalette();
      return;
    }

    if (event.key === 'Escape') {
      closePalette();
      closeDialog();
      if (typing) event.target.blur();
      return;
    }

    if (typing || event.metaKey || event.ctrlKey || event.altKey) return;

    if (event.key === '/') {
      event.preventDefault();
      document.querySelector('.list-pane input[type="search"]')?.focus();
      return;
    }

    if (event.key === '?') {
      event.preventDefault();
      showShortcuts();
      return;
    }

    if (pendingGo) {
      pendingGo = false;
      const target = capabilities.inspectors.find((i) => i.id.startsWith(event.key.toLowerCase()));
      if (target) {
        event.preventDefault();
        go(`/${target.id}`);
      }
      return;
    }

    if (event.key === 'g') {
      pendingGo = true;
      setTimeout(() => { pendingGo = false; }, 1200);
      return;
    }

    if (event.key === 'j' || event.key === 'k') {
      event.preventDefault();
      moveSelection(event.key === 'j' ? 1 : -1);
    }
  });
}

function moveSelection(delta) {
  const rows = [...document.querySelectorAll('.list-pane .row')];
  if (rows.length === 0) return;
  const index = rows.findIndex((row) => row.getAttribute('aria-selected') === 'true');
  const next = rows[Math.min(rows.length - 1, Math.max(0, (index === -1 ? -1 : index) + delta))];
  if (!next) return;
  next.scrollIntoView({ block: 'nearest' });
  next.click();
}

// ---------- Commands and panels ----------

function registerGlobalCommands() {
  registerCommands(() => [
    ...capabilities.inspectors.map((inspector) => ({
      label: `Go to ${inspector.label}`,
      hint: `g ${inspector.id[0]}`,
      icon: inspector.icon,
      run: () => go(`/${inspector.id}`),
    })),
    { label: 'Switch theme', icon: 'moon', run: toggleTheme },
    { label: 'Switch density', icon: 'sliders', run: toggleDensity },
    { label: 'Keyboard shortcuts', hint: '?', icon: 'command', run: showShortcuts },
    { label: 'About this device', icon: 'eye', run: showAbout },
  ]);

  registerCommands(() => (activePanel?.commands ? activePanel.commands() : []));
}

function showShortcuts() {
  showPanel(
    'Keyboard',
    h(
      'div',
      { class: 'shortcuts' },
      ...[
        ['⌘K / Ctrl K', 'Commands'],
        ['/', 'Search the list'],
        ['j / k', 'Move down and up'],
        ['g then a letter', 'Go to an inspector'],
        ['Esc', 'Close'],
        ['?', 'This list'],
      ].flatMap(([keys, what]) => [h('kbd', { text: keys }), h('span', { text: what })])
    )
  );
}

function showAbout() {
  showPanel(
    'This device',
    h(
      'div',
      { class: 'kv' },
      ...[
        ['App', capabilities.appId],
        ['Version', capabilities.appVersion],
        ['Device', capabilities.device],
        ['Android', `API ${capabilities.androidApi}`],
        ['SQLite', capabilities.sqliteVersion],
        ['Aperture', capabilities.apertureVersion],
        ['Writes', capabilities.allowWrites ? 'Allowed' : 'Read-only'],
        ['Auth', capabilities.requireAuth ? 'Token required' : 'Open'],
      ].flatMap(([k, v]) => [h('div', { class: 'kv__k', text: k }), h('div', { class: 'kv__v', text: v })])
    )
  );
}

function showExposure() {
  showPanel(
    'Open on this network',
    h(
      'div',
      null,
      h('p', {
        style: { 'font-size': 'var(--fs-sm)', 'margin-bottom': 'var(--sp-3)' },
        text: 'Aperture answers on every network interface and asks for no token. Anyone on this network can read what this console shows, and that now includes app storage.',
      }),
      h('p', { class: 'muted', style: { 'font-size': 'var(--fs-sm)' }, text: 'Set one of these in ApertureConfig:' }),
      h('pre', { class: 'code', text: 'requireAuth = true\n// or\nlocalhostOnly = true' })
    )
  );
}

// ---------- Storage that never throws ----------

function read(key) {
  try {
    return localStorage.getItem(key);
  } catch (e) {
    return null;
  }
}

function write(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch (e) {
    // A private window refuses storage. The setting lasts for this page and no longer.
  }
}
