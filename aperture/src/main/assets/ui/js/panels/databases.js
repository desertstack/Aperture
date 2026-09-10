/**
 * SQLite databases.
 *
 * A database the host app registered can be edited, and the app sees the change. One Aperture
 * found for itself is read-only, and the panel says why.
 */

import { h, fill, icon } from '../core/dom.js';
import { api, query } from '../core/api.js';
import * as sse from '../core/sse.js';
import { go } from '../core/router.js';
import { toast } from '../ui/toast.js';
import { showPanel } from '../ui/dialog.js';
import { bytes } from '../core/format.js';

const PAGE = 50;

export function createDatabasesPanel(ctx) {
  let databases = [];
  let detail = null;
  let openDb = null;
  let openTable = null;
  let offset = 0;
  let unsubscribe = [];

  const listBody = h('div', { class: 'rows' });
  const footer = h('div', { class: 'list-pane__footer' });

  async function loadDatabases() {
    try {
      const data = await api.get('/api/db');
      databases = data.databases;
      if (!openDb) renderDatabases();
    } catch (e) {
      toast.error(e.message);
    }
  }

  function renderDatabases() {
    if (databases.length === 0) {
      fill(listBody, h('div', { class: 'empty' },
        h('span', { class: 'empty__title', text: 'No databases.' }),
        h('span', { text: 'This app has not created one yet.' })));
    } else {
      fill(listBody, ...databases.map((db) =>
        h(
          'button',
          { class: 'row', onClick: () => go(`/db/${encodeURIComponent(db.name)}`) },
          h('div', { class: 'row__top' },
            icon('database', 14),
            h('span', { class: 'row__title', style: { direction: 'ltr' }, text: db.name }),
            db.registered ? h('span', { class: 'chip chip--quiet', text: 'Registered' }) : null,
            !db.readable ? h('span', { class: 'chip chip--warn', text: 'Unreadable' }) : null),
          h('div', { class: 'row__sub', style: { 'padding-left': '22px' } },
            h('span', { text: bytes(db.sizeBytes) }),
            h('span', { text: db.walMode ? 'WAL' : 'rollback journal' }))
        )
      ));
    }
    fill(footer, h('span', { text: `${databases.length} database${databases.length === 1 ? '' : 's'}` }));
  }

  function renderTables() {
    fill(
      listBody,
      h(
        'button',
        { class: 'row', onClick: () => go('/db') },
        h('div', { class: 'row__top' }, icon('back', 14), h('span', { class: 'row__title', style: { direction: 'ltr' }, text: 'All databases' }))
      ),
      ...detail.tables.map((table) =>
        h(
          'button',
          {
            class: 'row',
            id: `db-table-${table.name}`,
            'aria-selected': String(table.name === openTable),
            onClick: () => go(`/db/${encodeURIComponent(openDb)}/${encodeURIComponent(table.name)}`),
          },
          h('div', { class: 'row__top' },
            h('span', { class: 'row__title', style: { direction: 'ltr' }, text: table.name }),
            table.kind === 'view' ? h('span', { class: 'chip chip--quiet', text: 'view' }) : null,
            !table.editable ? h('span', { class: 'chip chip--quiet' }, icon('lock', 11)) : null),
          h('div', { class: 'row__sub', style: { 'padding-left': '0' } },
            h('span', { class: 'num', text: `${table.rowCount} row${table.rowCount === 1 ? '' : 's'}` }),
            h('span', { text: `${table.columns.length} columns` }))
        )
      )
    );
    fill(footer, h('span', { text: `${detail.tables.length} table${detail.tables.length === 1 ? '' : 's'}` }));
  }

  // ---------- Detail ----------

  async function openDatabase(name) {
    openDb = name;
    openTable = null;
    try {
      detail = await api.get(`/api/db/${encodeURIComponent(name)}`);
      renderTables();
      ctx.setCrumbs(['Databases', name]);
      fill(ctx.detail, h('div', { class: 'detail' },
        databaseHead(),
        querySection(),
        h('div', { class: 'empty' },
          h('span', { class: 'empty__title', text: 'Pick a table.' }),
          h('span', { text: 'Its rows appear here.' }))));
    } catch (e) {
      toast.error(e.message);
    }
  }

  function databaseHead() {
    const db = databases.find((d) => d.name === openDb);
    return h(
      'div',
      { class: 'detail__head' },
      h('div', { class: 'detail__title', text: openDb }),
      h(
        'div',
        { class: 'detail__meta' },
        h('span', { text: `SQLite ${detail.sqliteVersion}` }),
        detail.registered
          ? h('span', { class: 'chip chip--quiet', text: 'Registered' })
          : h('span', { class: 'chip chip--quiet', text: 'Read-only' }),
        db && db.walMode ? h('span', { class: 'chip chip--quiet', text: 'WAL' }) : null
      )
    );
  }

  async function openTableRows(name, table, page = 0) {
    openDb = name;
    openTable = table;
    offset = page * PAGE;
    try {
      if (!detail || detail.name !== name) {
        detail = await api.get(`/api/db/${encodeURIComponent(name)}`);
        renderTables();
      }
      for (const el of listBody.children) {
        if (el.id) el.setAttribute('aria-selected', String(el.id === `db-table-${table}`));
      }
      ctx.setCrumbs(['Databases', name, table]);
      const rows = await api.get(
        `/api/db/${encodeURIComponent(name)}/tables/${encodeURIComponent(table)}${query({ limit: PAGE, offset })}`
      );
      fill(ctx.detail, h('div', { class: 'detail' }, databaseHead(), querySection(), rowsSection(rows)));
    } catch (e) {
      fill(ctx.detail, h('div', { class: 'detail' }, h('div', { class: 'empty' }, h('span', { text: e.message }))));
    }
  }

  function rowsSection(rows) {
    const body = h('tbody');

    const rebuild = () => {
      if (rows.rows.length === 0) {
        fill(body, h('tr', null, h('td', { colspan: String(rows.columns.length + 1) },
          h('div', { class: 'empty' }, h('span', { text: 'No rows.' })))));
        return;
      }
      fill(body, ...rows.rows.map((row) => h(
        'tr',
        null,
        h('td', { class: 'faint num', text: row.rowId ?? '—' }),
        ...row.cells.map((cell, index) => cellTd(rows, row, index, cell, rebuild))
      )));
    };
    rebuild();

    const pages = Math.ceil(rows.total / PAGE) || 1;
    const page = Math.floor(rows.offset / PAGE);

    return h(
      'section',
      { class: 'section' },
      h(
        'div',
        { class: 'section__head' },
        h('span', { class: 'eyebrow', text: rows.table }),
        h(
          'div',
          { style: { display: 'flex', 'align-items': 'center', gap: 'var(--sp-2)' } },
          h('span', { class: 'faint num', text: `${rows.total} rows` }),
          !rows.editable ? h('span', { class: 'chip chip--quiet', text: 'Read-only' }) : null,
          h('button', {
            class: 'btn btn--sm',
            text: 'Prev',
            disabled: page === 0,
            onClick: () => openTableRows(openDb, rows.table, page - 1),
          }),
          h('span', { class: 'faint num', text: `${page + 1} / ${pages}` }),
          h('button', {
            class: 'btn btn--sm',
            text: 'Next',
            disabled: page + 1 >= pages,
            onClick: () => openTableRows(openDb, rows.table, page + 1),
          })
        )
      ),
      h(
        'div',
        { class: 'table-wrap' },
        h(
          'table',
          { class: 'data' },
          h('thead', null, h('tr', null,
            h('th', { text: 'rowid' }),
            ...rows.columns.map((c) => h('th', { text: c })))),
          body
        )
      )
    );
  }

  function cellTd(rows, row, index, cell, rebuild) {
    const text = cell.type === 'null' ? 'NULL' : (cell.preview ?? '');
    const td = cell.truncated
      ? h(
          'td',
          {
            class: 'has-more',
            title: `${cell.sizeBytes} bytes, showing the first part`,
          },
          h('span', { class: 'truncate', text: `${text}…` }),
          h('button', {
            class: 'btn btn--quiet btn--sm',
            text: 'more',
            onClick: () => showWholeCell(rows.table, row.rowId, rows.columns[index]),
          })
        )
      : h('td', {
          class: cell.type === 'null' ? 'null' : '',
          title: text,
          text,
        });

    if (rows.editable && row.rowId !== null) {
      td.addEventListener('dblclick', () => startEdit(rows, row, index, cell, td, rebuild));
      td.title = `${td.title}\nDouble-click to edit`;
    }

    return td;
  }

  function startEdit(rows, row, index, cell, td, rebuild) {
    const column = rows.columns[index];
    const input = h('input', {
      class: 'input mono',
      'aria-label': `New value for ${column}`,
      value: cell.type === 'null' ? '' : (cell.preview ?? ''),
    });

    const save = async () => {
      try {
        const updated = await api.put(`/api/db/${encodeURIComponent(openDb)}/cell`, {
          table: rows.table,
          rowId: row.rowId,
          column,
          type: cell.type === 'null' ? 'text' : cell.type,
          value: input.value,
          expectedType: cell.type,
          expectedValue: cell.type === 'null' ? null : cell.preview,
        });
        row.cells[index] = {
          type: updated.type,
          preview: updated.value,
          sizeBytes: updated.sizeBytes,
          truncated: false,
        };
        rebuild();
        toast.undoable(`${column} saved.`, async () => {
          await api.put(`/api/db/${encodeURIComponent(openDb)}/cell`, {
            table: rows.table,
            rowId: row.rowId,
            column,
            type: cell.type,
            value: cell.preview,
            expectedType: updated.type,
            expectedValue: updated.value,
          });
          openTableRows(openDb, rows.table, Math.floor(rows.offset / PAGE));
          toast.ok('Put back.');
        });
      } catch (e) {
        toast.error(e.message);
        rebuild();
      }
    };

    fill(
      td,
      h('div', { class: 'faint', style: { 'font-size': 'var(--fs-xs)' } },
        h('span', { text: 'was ' }),
        h('span', { class: 'mono', text: cell.type === 'null' ? 'NULL' : (cell.preview ?? '') })),
      input,
      h('div', { style: { display: 'flex', gap: '4px', 'margin-top': '4px' } },
        h('button', { class: 'btn btn--primary btn--sm', text: 'Save', onClick: save }),
        h('button', { class: 'btn btn--sm', text: 'Cancel', onClick: rebuild }))
    );
    td.className = 'editing';
    input.focus();
  }

  async function showWholeCell(table, rowId, column) {
    try {
      const cell = await api.get(
        `/api/db/${encodeURIComponent(openDb)}/cell${query({ table, column, rowId })}`
      );
      showPanel(`${table}.${column}`, h(
        'div',
        null,
        h('div', { class: 'faint', style: { 'margin-bottom': 'var(--sp-2)' } },
          h('span', { text: `${cell.type}, ${bytes(cell.sizeBytes)}` })),
        h('pre', { class: 'code', text: cell.value ?? 'NULL' })
      ));
    } catch (e) {
      toast.error(e.message);
    }
  }

  // ---------- Query box ----------

  function querySection() {
    const input = h('textarea', {
      class: 'textarea',
      style: { 'min-height': '72px' },
      'aria-label': 'SQL',
      placeholder: 'SELECT * FROM …',
      spellcheck: 'false',
    });
    const result = h('div');

    const run = async () => {
      const sql = input.value.trim();
      if (!sql) return;
      try {
        const response = await api.post(`/api/db/${encodeURIComponent(openDb)}/query`, { sql });
        if (response.kind === 'changed') {
          fill(result, h('div', { class: 'banner', style: { 'border-left-color': 'var(--ok)', background: 'var(--ok-soft)' } },
            icon('check', 14),
            h('span', { text: `${response.rowsAffected} row${response.rowsAffected === 1 ? '' : 's'} changed.` })));
          if (openTable) openTableRows(openDb, openTable, Math.floor(offset / PAGE));
          return;
        }
        fill(result, h(
          'div',
          { class: 'table-wrap', style: { 'margin-top': 'var(--sp-3)', 'max-height': '40vh' } },
          h(
            'table',
            { class: 'data' },
            h('thead', null, h('tr', null, ...response.columns.map((c) => h('th', { text: c })))),
            h('tbody', null, ...response.rows.map((row) => h('tr', null,
              ...row.cells.map((cell) => h('td', {
                class: cell.type === 'null' ? 'null' : '',
                text: cell.type === 'null' ? 'NULL' : (cell.preview ?? ''),
              })))))
          )
        ));
      } catch (e) {
        fill(result, h('div', { class: 'banner', style: { 'border-left-color': 'var(--err)', background: 'var(--err-soft)' } },
          icon('alert', 14), h('span', { text: e.message })));
      }
    };

    return h(
      'section',
      { class: 'section' },
      h(
        'div',
        { class: 'section__head' },
        h('span', { class: 'eyebrow', text: 'Query' }),
        h('button', { class: 'btn btn--sm', text: 'Run', onClick: run }, icon('play', 12))
      ),
      input,
      h('div', { class: 'faint', style: { 'font-size': 'var(--fs-xs)', 'margin-top': '4px' } },
        h('span', { text: 'Reads run on a read-only connection. Statements that would damage the app are refused.' })),
      result
    );
  }

  return {
    id: 'db',

    activate({ segments }) {
      fill(ctx.list, h('div', { class: 'list-pane__scroll' }, listBody), footer);
      unsubscribe.push(sse.on('db_changed', () => {
        if (openDb && openTable) openTableRows(openDb, openTable, Math.floor(offset / PAGE));
      }));

      loadDatabases().then(() => {
        if (segments.length > 2) openTableRows(segments[1], segments[2]);
        else if (segments.length > 1) openDatabase(segments[1]);
        else {
          openDb = null;
          openTable = null;
          detail = null;
          renderDatabases();
          ctx.setCrumbs(['Databases']);
          fill(ctx.detail, h('div', { class: 'detail' },
            h('div', { class: 'empty' },
              h('span', { class: 'empty__title', text: 'Pick a database.' }),
              h('span', { text: 'Its tables, rows and a query box appear here.' }))));
        }
      });
    },

    deactivate() {
      for (const off of unsubscribe) off();
      unsubscribe = [];
    },

    commands() {
      const items = databases.map((db) => ({
        label: db.name,
        hint: 'database',
        icon: 'database',
        run: () => go(`/db/${encodeURIComponent(db.name)}`),
      }));
      if (detail) {
        for (const table of detail.tables) {
          items.push({
            label: `${detail.name} › ${table.name}`,
            hint: `${table.rowCount} rows`,
            icon: 'database',
            run: () => go(`/db/${encodeURIComponent(detail.name)}/${encodeURIComponent(table.name)}`),
          });
        }
      }
      return items;
    },
  };
}
