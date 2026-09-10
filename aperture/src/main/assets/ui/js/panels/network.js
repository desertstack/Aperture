/**
 * HTTP traffic.
 *
 * The list holds summaries, never bodies. A body is fetched for the one request you open, so
 * showing a hundred rows costs the host app nothing.
 */

import { h, fill, icon } from '../core/dom.js';
import { api, query } from '../core/api.js';
import * as sse from '../core/sse.js';
import { go, setParam } from '../core/router.js';
import { toast } from '../ui/toast.js';
import { confirmAction } from '../ui/dialog.js';
import { bytes, duration, time, dateTime, statusTone, prettyBody, headerLines } from '../core/format.js';

const PAGE = 100;

export function createNetworkPanel(ctx) {
  let rows = [];
  let total = 0;
  let selectedId = null;
  let filters = { search: '', method: '', status: '' };
  let unsubscribe = [];
  let searchTimer = null;

  const listBody = h('div', { class: 'rows' });
  const footer = h('div', { class: 'list-pane__footer' });

  const searchInput = h('input', {
    class: 'input',
    type: 'search',
    placeholder: 'Search URL',
    'aria-label': 'Search by URL',
    onInput: (e) => {
      filters.search = e.target.value;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        setParam('q', filters.search);
        reload();
      }, 200);
    },
  });

  const methodSelect = select('Any method', ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'], (v) => {
    filters.method = v;
    setParam('method', v);
    reload();
  });

  const statusSelect = select('Any status', ['2xx', '3xx', '4xx', '5xx'], (v) => {
    filters.status = v;
    setParam('status', v);
    reload();
  });

  const toolbar = h(
    'div',
    { class: 'list-pane__toolbar' },
    h('div', { class: 'search' }, icon('search', 14), searchInput),
    h(
      'div',
      { class: 'list-pane__filters' },
      methodSelect,
      statusSelect,
      h('button', {
        class: 'btn btn--quiet btn--icon',
        title: 'Reload',
        'aria-label': 'Reload',
        onClick: () => reload(),
      }, icon('refresh')),
      h('button', {
        class: 'btn btn--quiet btn--icon',
        title: 'Clear captured traffic',
        'aria-label': 'Clear captured traffic',
        onClick: clearAll,
      }, icon('trash'))
    )
  );

  function select(placeholder, options, onPick) {
    const el = h(
      'select',
      { class: 'select', 'aria-label': placeholder, onChange: (e) => onPick(e.target.value) },
      h('option', { value: '', text: placeholder }),
      ...options.map((o) => h('option', { value: o, text: o }))
    );
    return el;
  }

  async function reload() {
    try {
      const data = await api.get(
        `/api/network/transactions${query({
          limit: PAGE,
          offset: 0,
          search: filters.search,
          method: filters.method,
          status: filters.status,
        })}`
      );
      rows = data.transactions;
      total = data.total;
      renderList();
    } catch (e) {
      toast.error(e.message);
    }
  }

  async function loadMore() {
    try {
      const data = await api.get(
        `/api/network/transactions${query({
          limit: PAGE,
          offset: rows.length,
          search: filters.search,
          method: filters.method,
          status: filters.status,
        })}`
      );
      rows = rows.concat(data.transactions);
      total = data.total;
      renderList();
    } catch (e) {
      toast.error(e.message);
    }
  }

  function renderList() {
    if (rows.length === 0) {
      fill(
        listBody,
        h(
          'div',
          { class: 'empty' },
          h('span', { class: 'empty__title', text: 'No requests captured yet.' }),
          h('span', { text: 'Make a call in the app and it appears here.' })
        )
      );
    } else {
      fill(listBody, ...rows.map((row) => renderRow(row)));
    }

    fill(
      footer,
      h('span', { class: 'num', text: `${rows.length} of ${total}` }),
      rows.length < total
        ? h('button', { class: 'btn btn--quiet btn--sm', text: 'Load more', onClick: loadMore })
        : null
    );
  }

  function renderRow(row, isNew = false) {
    const tone = statusTone(row.responseCode);
    const failed = Boolean(row.error);
    const el = h(
      'button',
      {
        class: `row${isNew ? ' row--new' : ''}`,
        id: `net-row-${row.id}`,
        'aria-selected': String(row.id === selectedId),
        onClick: () => go(`/network/${row.id}`),
      },
      h(
        'div',
        { class: 'row__top' },
        h('span', { class: 'row__method', text: row.method }),
        h('span', {
          class: `row__status${tone ? ` t-${tone}` : ' faint'}`,
          text: failed ? '!' : row.responseCode ?? '···',
        }),
        h('span', { class: 'row__title', text: row.url }),
        row.mockEnabled ? h('span', { class: 'chip chip--mock', text: 'Mock' }) : null
      ),
      h(
        'div',
        { class: 'row__sub' },
        h('span', { text: duration(row.duration) }),
        h('span', { text: bytes(row.responsePayloadSize) }),
        h('span', { text: time(row.requestDate) })
      )
    );
    return el;
  }

  async function clearAll() {
    const ok = await confirmAction({
      title: 'Clear captured traffic',
      body: 'This removes every request Aperture has captured on this device. It does not touch the app.',
      confirmLabel: 'Clear traffic',
    });
    if (!ok) return;
    try {
      await api.del('/api/network/transactions');
      rows = [];
      total = 0;
      selectedId = null;
      renderList();
      go('/network');
      toast.ok('Captured traffic cleared.');
    } catch (e) {
      toast.error(e.message);
    }
  }

  // ---------- Detail ----------

  async function showDetail(id) {
    selectedId = Number(id);
    markSelection();
    ctx.setCrumbs(['Network', `#${id}`]);
    fill(ctx.detail, h('div', { class: 'detail' }, h('div', { class: 'skeleton', style: { width: '40%' } })));
    try {
      const tx = await api.get(`/api/network/transactions/${id}`);
      fill(ctx.detail, renderDetail(tx));
    } catch (e) {
      fill(ctx.detail, h('div', { class: 'detail' }, h('div', { class: 'empty' }, h('span', { text: e.message }))));
    }
  }

  function renderDetail(tx) {
    const tone = statusTone(tx.responseCode);
    return h(
      'div',
      { class: 'detail' },
      h(
        'div',
        { class: 'detail__head' },
        h('div', { class: 'detail__title', text: `${tx.method} ${tx.url}` }),
        h(
          'div',
          { class: 'detail__meta' },
          h(
            'span',
            { style: { display: 'inline-flex', 'align-items': 'center', gap: '6px' } },
            h('span', { class: `dot${tone ? ` dot--${tone}` : ''}` }),
            h('span', { class: tone ? `t-${tone}` : '', text: tx.error ? 'Failed' : `${tx.responseCode ?? '—'} ${tx.responseMessage ?? ''}`.trim() })
          ),
          h('span', { class: 'num', text: duration(tx.duration) }),
          h('span', { class: 'num', text: bytes(tx.responsePayloadSize) }),
          tx.mockEnabled ? h('span', { class: 'chip chip--mock', text: 'Mocked' }) : null
        )
      ),
      section('General', kv([
        ['URL', tx.url],
        ['Method', tx.method],
        ['Protocol', tx.protocol ?? '—'],
        ['Status', tx.error ? 'Failed' : `${tx.responseCode ?? '—'} ${tx.responseMessage ?? ''}`.trim()],
        ['Duration', duration(tx.duration)],
        ['Request size', bytes(tx.requestPayloadSize)],
        ['Response size', bytes(tx.responsePayloadSize)],
        ['Time', dateTime(tx.requestDate)],
      ])),
      tx.error ? section('Error', h('pre', { class: 'code t-err', text: tx.error })) : null,
      section('Request headers', code(headerLines(tx.requestHeaders), 'No request headers.')),
      section('Request body', code(prettyBody(tx.requestBody, tx.requestContentType), 'No request body.')),
      section('Response headers', code(headerLines(tx.responseHeaders), 'No response headers.')),
      section('Response body', code(prettyBody(tx.responseBody, tx.responseContentType), 'No response body.')),
      renderMock(tx)
    );
  }

  function renderMock(tx) {
    const statusField = h('input', {
      class: 'input mono',
      type: 'number',
      min: '100',
      max: '599',
      value: String(tx.mockResponseCode ?? tx.responseCode ?? 200),
      'aria-label': 'Mock status code',
    });
    const bodyField = h('textarea', {
      class: 'textarea',
      'aria-label': 'Mock response body',
      value: tx.mockResponseBody ?? tx.responseBody ?? '',
    });

    async function save(enable) {
      const statusCode = Number(statusField.value);
      if (!Number.isInteger(statusCode) || statusCode < 100 || statusCode > 599) {
        toast.error('A status code is a number from 100 to 599.');
        return;
      }
      try {
        await api.put(`/api/network/transactions/${tx.id}/response`, {
          statusCode,
          headers: null,
          body: bodyField.value,
        });
        await api.put(`/api/network/transactions/${tx.id}/mock`, { enabled: enable });
        toast.ok(enable ? 'Mock saved and turned on.' : 'Mock saved.');
        showDetail(tx.id);
      } catch (e) {
        toast.error(e.message);
      }
    }

    async function clearMock() {
      try {
        await api.del(`/api/network/transactions/${tx.id}/mock`);
        toast.ok('Mock removed.');
        showDetail(tx.id);
      } catch (e) {
        toast.error(e.message);
      }
    }

    return section(
      'Mock response',
      h(
        'div',
        null,
        h('p', {
          class: 'muted',
          style: { 'margin-bottom': 'var(--sp-3)', 'font-size': 'var(--fs-sm)' },
          text: `Every later request to this URL answers with this instead of reaching the network.`,
        }),
        tx.mockEnabled
          ? h('div', { class: 'banner', style: { 'border-left-color': 'var(--mock)', background: 'var(--mock-soft)' } },
              icon('alert', 14),
              h('span', { text: 'This URL is mocked right now.' }))
          : null,
        h('div', { class: 'field' },
          h('label', { class: 'field__label', text: 'Status code' }),
          statusField),
        h('div', { class: 'field' },
          h('label', { class: 'field__label', text: 'Body' }),
          bodyField),
        h(
          'div',
          { style: { display: 'flex', gap: 'var(--sp-2)' } },
          h('button', { class: 'btn btn--primary', text: tx.mockEnabled ? 'Save mock' : 'Save and turn on', onClick: () => save(true) }),
          tx.mockEnabled
            ? h('button', { class: 'btn', text: 'Turn off', onClick: () => save(false) })
            : null,
          tx.mockResponseBody || tx.mockResponseCode
            ? h('button', { class: 'btn btn--danger', text: 'Remove mock', onClick: clearMock })
            : null
        )
      )
    );
  }

  function markSelection() {
    for (const el of listBody.children) {
      if (el.id) el.setAttribute('aria-selected', String(el.id === `net-row-${selectedId}`));
    }
  }

  // ---------- Live updates ----------

  function subscribe() {
    unsubscribe.push(
      sse.on('new_transaction', (tx) => {
        if (!matchesFilters(tx)) return;
        const existing = rows.findIndex((r) => r.id === tx.id);
        if (existing >= 0) rows[existing] = tx;
        else {
          rows.unshift(tx);
          total += 1;
        }
        renderList();
      })
    );
    unsubscribe.push(
      sse.on('updated_transaction', (tx) => {
        const index = rows.findIndex((r) => r.id === tx.id);
        if (index >= 0) {
          rows[index] = { ...rows[index], ...tx };
          renderList();
        }
        if (tx.id === selectedId) fill(ctx.detail, renderDetail(tx));
      })
    );
    unsubscribe.push(
      sse.on('deleted_transaction', (payload) => {
        rows = rows.filter((r) => r.id !== payload.id);
        total = Math.max(0, total - 1);
        renderList();
      })
    );
    unsubscribe.push(
      sse.on('all_deleted', () => {
        rows = [];
        total = 0;
        renderList();
      })
    );
  }

  function matchesFilters(tx) {
    if (filters.method && tx.method !== filters.method) return false;
    if (filters.search && !String(tx.url).toLowerCase().includes(filters.search.toLowerCase())) return false;
    if (filters.status) {
      const band = `${Math.floor(Number(tx.responseCode) / 100)}xx`;
      if (band !== filters.status) return false;
    }
    return true;
  }

  return {
    id: 'network',

    activate({ segments, params }) {
      filters = {
        search: params.get('q') || '',
        method: params.get('method') || '',
        status: params.get('status') || '',
      };
      searchInput.value = filters.search;
      methodSelect.value = filters.method;
      statusSelect.value = filters.status;

      fill(ctx.list, toolbar, h('div', { class: 'list-pane__scroll' }, listBody), footer);
      subscribe();
      reload();

      if (segments.length > 1) {
        showDetail(segments[1]);
      } else {
        selectedId = null;
        ctx.setCrumbs(['Network']);
        fill(
          ctx.detail,
          h('div', { class: 'detail' },
            h('div', { class: 'empty' },
              h('span', { class: 'empty__title', text: 'Pick a request.' }),
              h('span', { text: 'Its headers, bodies and timing appear here.' })))
        );
      }
    },

    deactivate() {
      for (const off of unsubscribe) off();
      unsubscribe = [];
      clearTimeout(searchTimer);
    },

    commands() {
      return rows.slice(0, 30).map((row) => ({
        label: `${row.method} ${row.url}`,
        hint: row.responseCode ? String(row.responseCode) : 'pending',
        icon: 'activity',
        run: () => go(`/network/${row.id}`),
      }));
    },
  };
}

// ---------- Small shared builders ----------

function section(title, content) {
  return h(
    'section',
    { class: 'section' },
    h('div', { class: 'section__head' }, h('span', { class: 'eyebrow', text: title })),
    content
  );
}

function kv(pairs) {
  return h(
    'div',
    { class: 'kv' },
    ...pairs.flatMap(([k, v]) => [
      h('div', { class: 'kv__k', text: k }),
      h('div', { class: 'kv__v', text: v === null || v === undefined || v === '' ? '—' : String(v) }),
    ])
  );
}

function code(text, emptyMessage) {
  if (!text) return h('div', { class: 'code code--empty', text: emptyMessage });
  return h('pre', { class: 'code', text });
}
