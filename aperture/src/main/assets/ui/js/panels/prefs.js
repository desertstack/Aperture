/**
 * Key and value stores: SharedPreferences and Preferences DataStore.
 *
 * Both speak the same shape on the wire, so both use this panel. A value carries its type, and
 * the editor shows the old value beside the new one, because saving here changes the running
 * app.
 */

import { h, fill, icon } from '../core/dom.js';
import { api, query } from '../core/api.js';
import * as sse from '../core/sse.js';
import { go } from '../core/router.js';
import { toast } from '../ui/toast.js';
import { confirmAction } from '../ui/dialog.js';
import { bytes } from '../core/format.js';

const TYPES = ['string', 'int', 'long', 'float', 'double', 'boolean', 'stringSet'];

function createKeyValuePanel(ctx, opts) {
  let files = [];
  let openFile = null;
  let unsubscribe = [];

  const listBody = h('div', { class: 'rows' });
  const footer = h('div', { class: 'list-pane__footer' });

  const canWrite = () => ctx.capabilities.allowWrites;

  async function reload() {
    try {
      const data = await api.get(opts.base);
      files = data.files;
      renderList();
    } catch (e) {
      toast.error(e.message);
    }
  }

  function renderList() {
    if (files.length === 0) {
      fill(listBody, h('div', { class: 'empty' },
        h('span', { class: 'empty__title', text: opts.emptyTitle }),
        h('span', { text: opts.emptyHint })));
    } else {
      fill(listBody, ...files.map(renderFileRow));
    }
    fill(footer, h('span', { class: 'num', text: `${files.length} ${files.length === 1 ? opts.unit : opts.unitPlural}` }));
  }

  function renderFileRow(file) {
    return h(
      'button',
      {
        class: 'row',
        id: `${opts.id}-row-${file.name}`,
        'aria-selected': String(file.name === openFile),
        onClick: () => go(`/${opts.id}/${encodeURIComponent(file.name)}`),
      },
      h(
        'div',
        { class: 'row__top' },
        h('span', { class: 'row__title', style: { direction: 'ltr' }, text: file.name }),
        file.encrypted ? h('span', { class: 'chip chip--quiet' }, icon('lock', 11)) : null,
        file.registered ? h('span', { class: 'chip chip--quiet', text: 'Registered' }) : null
      ),
      h(
        'div',
        { class: 'row__sub', style: { 'padding-left': '0' } },
        h('span', { text: file.readable ? `${file.keyCount} key${file.keyCount === 1 ? '' : 's'}` : 'Unreadable' }),
        h('span', { text: bytes(file.sizeBytes) })
      )
    );
  }

  // ---------- Detail ----------

  async function showFile(name) {
    openFile = name;
    for (const el of listBody.children) {
      if (el.id) el.setAttribute('aria-selected', String(el.id === `${opts.id}-row-${name}`));
    }
    ctx.setCrumbs([opts.label, name]);
    try {
      const detail = await api.get(`${opts.base}/${encodeURIComponent(name)}`);
      fill(ctx.detail, renderDetail(detail));
    } catch (e) {
      fill(ctx.detail, h('div', { class: 'detail' }, h('div', { class: 'empty' }, h('span', { text: e.message }))));
    }
  }

  function renderDetail(detail) {
    const body = h('div', { class: 'detail' });

    body.append(
      h(
        'div',
        { class: 'detail__head' },
        h('div', { class: 'detail__title', text: `${detail.name}${opts.suffix}` }),
        h(
          'div',
          { class: 'detail__meta' },
          h('span', { text: `${detail.entries.length} entr${detail.entries.length === 1 ? 'y' : 'ies'}` }),
          detail.registered ? h('span', { class: 'chip chip--quiet', text: 'Registered instance' }) : null,
          detail.encrypted ? h('span', { class: 'chip chip--quiet', text: 'Encrypted' }) : null
        )
      )
    );

    if (detail.note) {
      body.append(h('div', { class: 'banner' }, icon('alert', 14), h('span', { text: detail.note })));
    }

    if (!detail.readable) return body;

    const table = h('tbody');
    const rebuild = () => {
      fill(
        table,
        ...detail.entries.map((entry) => entryRow(detail.name, entry, rebuild))
      );
      if (detail.entries.length === 0) {
        fill(table, h('tr', null, h('td', { colspan: '4' }, h('div', { class: 'empty' }, h('span', { text: 'This file is empty.' })))));
      }
    };

    body.append(
      h(
        'section',
        { class: 'section' },
        h(
          'div',
          { class: 'section__head' },
          h('span', { class: 'eyebrow', text: 'Entries' }),
          canWrite()
            ? h(
                'div',
                { style: { display: 'flex', gap: 'var(--sp-2)' } },
                h('button', { class: 'btn btn--sm', text: 'Add key', onClick: () => addKey(detail) }, icon('edit', 13)),
                opts.canClear
                  ? h('button', { class: 'btn btn--sm btn--danger', text: 'Clear file', onClick: () => clearFile(detail) })
                  : null
              )
            : null
        ),
        h(
          'div',
          { class: 'table-wrap' },
          h(
            'table',
            { class: 'data' },
            h('thead', null, h('tr', null,
              h('th', { text: 'Key' }),
              h('th', { text: 'Type' }),
              h('th', { text: 'Value' }),
              h('th', { style: { width: '1%' } }))),
            table
          )
        )
      )
    );

    rebuild();
    return body;
  }

  function entryRow(file, entry, rebuild) {
    const valueText = entry.type === 'stringSet'
      ? (entry.values || []).join(', ')
      : entry.value;

    const cells = [
      h('td', { text: entry.key }),
      h('td', { class: 'faint', text: entry.type }),
      h('td', {
        class: valueText === null || valueText === undefined ? 'null' : '',
        title: valueText ?? 'null',
        text: valueText === null || valueText === undefined ? 'null' : valueText,
      }),
    ];

    if (canWrite()) {
      cells.push(
        h(
          'td',
          { style: { 'white-space': 'nowrap' } },
          h('button', {
            class: 'btn btn--quiet btn--icon btn--sm',
            'aria-label': `Edit ${entry.key}`,
            title: 'Edit',
            onClick: () => startEdit(file, entry, row, rebuild),
          }, icon('edit', 13)),
          h('button', {
            class: 'btn btn--quiet btn--icon btn--sm',
            'aria-label': `Remove ${entry.key}`,
            title: 'Remove',
            onClick: () => removeKey(file, entry, rebuild),
          }, icon('trash', 13))
        )
      );
    } else {
      cells.push(h('td'));
    }

    const row = h('tr', null, ...cells);
    return row;
  }

  /** Editing shows what is there now beside what would replace it. Saving is deliberate. */
  function startEdit(file, entry, row, rebuild) {
    const isSet = entry.type === 'stringSet';
    const input = isSet
      ? h('textarea', {
          class: 'textarea',
          style: { 'min-height': '64px' },
          'aria-label': `New value for ${entry.key}`,
          value: (entry.values || []).join('\n'),
        })
      : h('input', {
          class: 'input mono',
          'aria-label': `New value for ${entry.key}`,
          value: entry.value ?? '',
        });

    const wasText = isSet ? (entry.values || []).join(', ') : (entry.value ?? 'null');

    const editor = h(
      'td',
      { colspan: '2', class: 'editing' },
      h('div', { class: 'faint', style: { 'font-size': 'var(--fs-xs)', 'margin-bottom': '4px' } },
        h('span', { text: 'was ' }),
        h('span', { class: 'mono', text: wasText })),
      input,
      isSet ? h('div', { class: 'faint', style: { 'font-size': 'var(--fs-xs)' }, text: 'One value per line.' }) : null,
      h(
        'div',
        { style: { display: 'flex', gap: 'var(--sp-2)', 'margin-top': 'var(--sp-2)' } },
        h('button', { class: 'btn btn--primary btn--sm', text: 'Save', onClick: save }),
        h('button', { class: 'btn btn--sm', text: 'Cancel', onClick: rebuild })
      )
    );

    fill(
      row,
      h('td', { text: entry.key }),
      h('td', { class: 'faint', text: entry.type }),
      editor
    );
    input.focus();

    async function save() {
      const payload = {
        key: entry.key,
        type: entry.type,
        value: isSet ? null : input.value,
        values: isSet ? input.value.split('\n').filter((line) => line.length > 0) : null,
      };
      await put(file, payload, rebuild, `${entry.key} saved.`);
    }
  }

  async function put(file, payload, rebuild, message) {
    try {
      const result = await api.put(`${opts.base}/${encodeURIComponent(file)}/entry`, payload);
      await refreshDetail(file);
      if (result.previous) {
        toast.undoable(message, async () => {
          await api.put(`${opts.base}/${encodeURIComponent(file)}/entry`, {
            key: result.previous.key,
            type: result.previous.type,
            value: result.previous.value,
            values: result.previous.values,
            allowTypeChange: true,
          });
          await refreshDetail(file);
          toast.ok('Put back.');
        });
      } else {
        toast.ok(message);
      }
    } catch (e) {
      toast.error(e.message);
      if (rebuild) rebuild();
    }
  }

  async function removeKey(file, entry, rebuild) {
    const ok = await confirmAction({
      title: `Remove ${entry.key}`,
      body: 'The app reads this key from its own preferences. Removing it makes the app fall back to its default.',
      target: `${file}${opts.suffix} → ${entry.key}`,
      confirmLabel: 'Remove key',
    });
    if (!ok) return;
    try {
      const result = await api.del(`${opts.base}/${encodeURIComponent(file)}/entry${query({ key: entry.key })}`);
      await refreshDetail(file);
      toast.undoable(`${entry.key} removed.`, async () => {
        await api.put(`${opts.base}/${encodeURIComponent(file)}/entry`, {
          key: result.previous.key,
          type: result.previous.type,
          value: result.previous.value,
          values: result.previous.values,
          allowTypeChange: true,
        });
        await refreshDetail(file);
        toast.ok('Put back.');
      });
    } catch (e) {
      toast.error(e.message);
    }
  }

  async function clearFile(detail) {
    const ok = await confirmAction({
      title: `Clear ${detail.name}${opts.suffix}`,
      body: `This removes all ${detail.entries.length} entries from the running app. It cannot be undone.`,
      target: `${detail.name}${opts.suffix}`,
      confirmLabel: 'Clear file',
    });
    if (!ok) return;
    try {
      await api.del(`${opts.base}/${encodeURIComponent(detail.name)}`);
      await refreshDetail(detail.name);
      toast.ok(`${detail.name}${opts.suffix} cleared.`);
    } catch (e) {
      toast.error(e.message);
    }
  }

  function addKey(detail) {
    const keyInput = h('input', { class: 'input mono', 'aria-label': 'New key', placeholder: 'key' });
    const typeSelect = h('select', { class: 'select', 'aria-label': 'Type' },
      ...TYPES.map((t) => h('option', { value: t, text: t })));
    const valueInput = h('input', { class: 'input mono', 'aria-label': 'Value', placeholder: 'value' });

    const form = h(
      'div',
      { style: { padding: 'var(--sp-3)', 'border-top': '1px solid var(--line)' } },
      h('div', { class: 'field' }, h('label', { class: 'field__label', text: 'Key' }), keyInput),
      h('div', { class: 'field' }, h('label', { class: 'field__label', text: 'Type' }), typeSelect),
      h('div', { class: 'field' }, h('label', { class: 'field__label', text: 'Value' }), valueInput),
      h('button', {
        class: 'btn btn--primary',
        text: 'Add key',
        onClick: async () => {
          const type = typeSelect.value;
          await put(detail.name, {
            key: keyInput.value.trim(),
            type,
            value: type === 'stringSet' ? null : valueInput.value,
            values: type === 'stringSet' ? valueInput.value.split(',').map((s) => s.trim()).filter(Boolean) : null,
          }, null, `${keyInput.value.trim()} added.`);
        },
      })
    );

    ctx.detail.querySelector('.section')?.append(form);
    keyInput.focus();
  }

  async function refreshDetail(file) {
    await reload();
    if (openFile === file) await showFile(file);
  }

  return {
    id: opts.id,

    activate({ segments }) {
      fill(ctx.list, h('div', { class: 'list-pane__scroll' }, listBody), footer);
      unsubscribe.push(
        sse.on(opts.event, (payload) => {
          if (payload?.file && payload.file === openFile) showFile(openFile);
          else reload();
        })
      );
      reload();

      if (segments.length > 1) {
        showFile(segments[1]);
      } else {
        openFile = null;
        ctx.setCrumbs([opts.label]);
        fill(ctx.detail, h('div', { class: 'detail' },
          h('div', { class: 'empty' },
            h('span', { class: 'empty__title', text: opts.pickTitle }),
            h('span', { text: 'Its keys, types and values appear here.' }))));
      }
    },

    deactivate() {
      for (const off of unsubscribe) off();
      unsubscribe = [];
    },

    commands() {
      return files.map((file) => ({
        label: file.name,
        hint: opts.label.toLowerCase(),
        icon: opts.icon,
        run: () => go(`/${opts.id}/${encodeURIComponent(file.name)}`),
      }));
    },
  };
}

export function createPrefsPanel(ctx) {
  return createKeyValuePanel(ctx, {
    id: 'prefs',
    base: '/api/prefs',
    label: 'Preferences',
    icon: 'sliders',
    event: 'prefs_changed',
    suffix: '.xml',
    unit: 'file',
    unitPlural: 'files',
    canClear: true,
    emptyTitle: 'No preferences files.',
    emptyHint: 'This app has not written any yet.',
    pickTitle: 'Pick a preferences file.',
  });
}

export function createDataStorePanel(ctx) {
  return createKeyValuePanel(ctx, {
    id: 'datastore',
    base: '/api/datastore',
    label: 'DataStore',
    icon: 'layers',
    event: 'datastore_changed',
    suffix: '',
    unit: 'store',
    unitPlural: 'stores',
    canClear: false,
    emptyTitle: 'No DataStore instances.',
    emptyHint: 'Register one with Aperture.registerDataStore(name, dataStore).',
    pickTitle: 'Pick a store.',
  });
}
