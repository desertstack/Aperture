/**
 * HTTP traffic.
 *
 * The list holds summaries, never bodies. A body is fetched for the one request you open, so
 * showing a hundred rows costs the host app nothing.
 *
 * The list always shows the calls in the order they happened. Search and method narrow it;
 * there is no status filter, because hiding calls by status breaks that order. A failed or
 * 4xx/5xx call stands out by its badge and its left edge instead.
 *
 * The detail is one page: request, response and mock in one column, cURL in a column beside
 * them. Every section folds, and the console remembers which ones you closed.
 */

import { h, fill, icon } from '../core/dom.js';
import { api, query } from '../core/api.js';
import * as sse from '../core/sse.js';
import { go, setParam } from '../core/router.js';
import { toast } from '../ui/toast.js';
import { confirmAction } from '../ui/dialog.js';
import { copyButton } from '../ui/clipboard.js';
import { codeView } from '../ui/codeview.js';
import {
  bytes, duration, time, dateTime, statusTone, prettyBody, headerEntries, curlCommand,
  isJson, jsonError,
} from '../core/format.js';

const PAGE = 100;

/** Up to this many headers start open. A longer list starts closed, so it does not bury the rest. */
const OPEN_HEADER_LIMIT = 12;
const FOLDS_KEY = 'aperture.network.folds';

export function createNetworkPanel(ctx) {
  let rows = [];
  let total = 0;
  let selectedId = null;
  let current = null; // the full transaction on screen
  let mockDirty = false;
  let filters = { search: '', method: '' };
  let mounted = false;
  let unsubscribe = [];
  let searchTimer = null;
  let headObserver = null;

  const listBody = h('div', { class: 'rows' });
  const listScroll = h('div', { class: 'list-pane__scroll' }, listBody);
  const footer = h('div', { class: 'list-pane__footer' });

  const searchInput = h('input', {
    class: 'input',
    type: 'search',
    placeholder: 'Filter by URL',
    'aria-label': 'Filter by URL',
    onInput: (e) => {
      const value = e.target.value;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => setParam('q', value), 200);
    },
  });

  const methodSelect = h(
    'select',
    {
      class: 'select select--compact',
      'aria-label': 'Method',
      onChange: (e) => setParam('method', e.target.value),
    },
    h('option', { value: '', text: 'Method' }),
    ...['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'].map((o) => h('option', { value: o, text: o }))
  );

  const toolbar = h(
    'div',
    { class: 'list-pane__toolbar' },
    h(
      'div',
      { class: 'list-pane__filters' },
      h('div', { class: 'search search--grow' }, icon('search', 14), searchInput),
      methodSelect,
      h('button', {
        class: 'btn btn--quiet btn--icon',
        type: 'button',
        title: 'Reload',
        'aria-label': 'Reload',
        onClick: () => reload(),
      }, icon('refresh', 15)),
      h('button', {
        class: 'btn btn--quiet btn--icon',
        type: 'button',
        title: 'Clear captured traffic',
        'aria-label': 'Clear captured traffic',
        onClick: clearAll,
      }, icon('trash', 15))
    )
  );

  function paintFilters() {
    if (searchInput.value !== filters.search) searchInput.value = filters.search;
    methodSelect.value = filters.method;
  }

  /** The address of one request, with the filters kept. */
  function detailPath(id) {
    const params = new URLSearchParams();
    if (filters.search) params.set('q', filters.search);
    if (filters.method) params.set('method', filters.method);
    const text = params.toString();
    return `/network/${id}${text ? `?${text}` : ''}`;
  }

  function listQuery(offset) {
    return `/api/network/transactions${query({
      limit: PAGE,
      offset,
      search: filters.search,
      method: filters.method,
    })}`;
  }

  async function reload() {
    try {
      const data = await api.get(listQuery(0));
      rows = data.transactions;
      total = data.total;
      renderList();
    } catch (e) {
      toast.error(e.message);
    }
  }

  async function loadMore() {
    try {
      const data = await api.get(listQuery(rows.length));
      rows = rows.concat(data.transactions);
      total = data.total;
      renderList();
    } catch (e) {
      toast.error(e.message);
    }
  }

  function renderList() {
    if (rows.length === 0) {
      const filtered = filters.search || filters.method;
      fill(
        listBody,
        h(
          'div',
          { class: 'empty empty--fill' },
          h('span', { class: 'empty__icon' }, icon(filtered ? 'search' : 'activity', 18)),
          h('span', { class: 'empty__title', text: filtered ? 'No requests match.' : 'No requests captured yet.' }),
          h('span', { text: filtered ? 'Change or clear the filters.' : 'Make a call in the app and it appears here.' })
        )
      );
    } else {
      fill(listBody, ...rows.map((row) => renderRow(row)));
    }

    fill(
      footer,
      h('span', { class: 'num', text: `${rows.length} of ${total}` }),
      rows.length < total
        ? h('button', { class: 'btn btn--quiet btn--sm', type: 'button', text: 'Load more', onClick: loadMore })
        : null
    );
  }

  function renderRow(row) {
    const tone = row.error ? 'err' : statusTone(row.responseCode);
    const { host, path } = splitUrl(row.url);
    return h(
      'button',
      {
        class: `row row--net${tone ? ` tone-${tone}` : ''}`,
        type: 'button',
        id: `net-row-${row.id}`,
        'aria-selected': String(row.id === selectedId),
        onClick: () => go(detailPath(row.id)),
      },
      h(
        'div',
        { class: 'row__top' },
        statusBadge(row),
        h('span', { class: 'row__method', text: row.method }),
        h('span', { class: 'row__title', title: row.url, text: path }),
        row.mockEnabled ? h('span', { class: 'chip chip--mock', text: 'Mock' }) : null
      ),
      h(
        'div',
        { class: 'row__sub' },
        h('span', { class: 'row__host', text: host }),
        h('span', { class: 'row__metric', text: row.error ? 'failed' : duration(row.duration) }),
        h('span', { class: 'row__metric', text: bytes(row.responsePayloadSize) }),
        h('span', { class: 'row__metric', text: time(row.requestDate) })
      )
    );
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
      current = null;
      renderList();
      go('/network');
      toast.ok('Captured traffic cleared.');
    } catch (e) {
      toast.error(e.message);
    }
  }

  // ---------- Detail ----------

  async function showDetail(id) {
    selectedId = id;
    current = null;
    mockDirty = false;
    markSelection();
    ctx.setCrumbs(['Network', `#${id}`]);
    stopHeadObserver();
    fill(ctx.detail, h('div', { class: 'detail' }, h('div', { class: 'skeleton', style: { width: '40%' } })));
    try {
      const tx = await api.get(`/api/network/transactions/${id}`);
      if (selectedId !== id) return; // another row was picked while this one loaded
      current = tx;
      paintDetail();
    } catch (e) {
      fill(ctx.detail, h('div', { class: 'detail' }, h('div', { class: 'empty empty--fill' }, h('span', { text: e.message }))));
    }
  }

  function paintDetail() {
    if (!current) return;
    const scroll = ctx.detail.scrollTop;
    stopHeadObserver();
    fill(ctx.detail, renderDetail(current));
    ctx.detail.scrollTop = scroll;
  }

  function stopHeadObserver() {
    headObserver?.disconnect();
    headObserver = null;
  }

  function renderDetail(tx) {
    const { host, path } = splitUrl(tx.url);
    const reqHeaders = headerEntries(tx.requestHeaders) || [];
    const resHeaders = headerEntries(tx.responseHeaders) || [];
    const mock = renderMock(tx);

    const jump = (label, target, before) =>
      h('a', {
        href: `#${target}`,
        text: label,
        onClick: (e) => {
          e.preventDefault();
          before?.();
          document.getElementById(target)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        },
      });

    const head = h(
      'header',
      { class: 'req-head' },
      h(
        'div',
        { class: 'req-head__top' },
        h(
          'div',
          { class: 'req-head__main' },
          h(
            'div',
            { class: 'req-head__line' },
            h('span', { class: 'badge badge--method', text: tx.method }),
            statusBadge(tx),
            h('span', { class: 'req-head__path', text: path })
          ),
          h('div', { class: 'req-head__host', text: `${tx.scheme ? `${tx.scheme}://` : ''}${host}` })
        ),
        h(
          'div',
          { class: 'req-head__actions' },
          copyButton(() => tx.url, 'URL copied.', { label: 'Copy URL' }),
          copyButton(() => curlCommand(tx), 'cURL command copied.', { label: 'Copy as cURL' })
        )
      ),
      h(
        'div',
        { class: 'stats' },
        stat('clock', duration(tx.duration), 'Duration'),
        stat('download', bytes(tx.responsePayloadSize), 'Response size'),
        stat('globe', tx.protocol || '—', 'Protocol'),
        h('span', { class: 'stats__item', title: dateTime(tx.requestDate), text: time(tx.requestDate) }),
        tx.mockEnabled ? h('span', { class: 'chip chip--mock', text: 'Mocked' }) : null
      ),
      h(
        'nav',
        { class: 'jumpbar', 'aria-label': 'Parts of this request' },
        jump('Request', 'sec-req'),
        jump('Response', 'sec-res'),
        jump('Mock', 'sec-mock', () => { mock.open = true; })
      )
    );

    // The side column and the jump targets sit just under the head, whatever its height.
    headObserver = new ResizeObserver(() => ctx.detail.style.setProperty('--head-h', `${head.offsetHeight}px`));
    headObserver.observe(head);

    const curl = h(
      'section',
      { class: 'panel', id: 'sec-curl' },
      h(
        'div',
        { class: 'panel__head' },
        h('span', { class: 'panel__title', text: 'cURL' }),
        h('div', { class: 'panel__actions' }, copyButton(() => curlCommand(tx), 'cURL command copied.'))
      ),
      h('pre', { class: 'code code--curl', text: curlCommand(tx) })
    );

    return h(
      'div',
      { class: 'detail detail--request' },
      head,
      h(
        'div',
        { class: 'split' },
        h(
          'div',
          { class: 'split__main' },
          tx.error
            ? h('div', { class: 'banner banner--err' }, icon('alert', 14), h('span', { class: 'mono', text: tx.error }))
            : null,
          h('div', { class: 'group-title', id: 'sec-req', text: 'Request' }),
          headersPanel('Headers', reqHeaders, 'No request headers.', 'reqHeaders'),
          remember(codeView({
            title: 'Request body',
            text: tx.requestBody,
            contentType: tx.requestContentType,
            binary: tx.requestBodyIsPlainText === false,
            emptyMessage: 'No request body.',
            fold: true,
            open: foldOpen('reqBody', true),
          }), 'reqBody'),
          h('div', { class: 'group-title', id: 'sec-res', text: 'Response' }),
          headersPanel('Headers', resHeaders, 'No response headers.', 'resHeaders'),
          remember(codeView({
            title: 'Response body',
            text: tx.responseBody,
            contentType: tx.responseContentType,
            binary: tx.responseBodyIsPlainText === false,
            emptyMessage: tx.error ? 'The request failed before a response arrived.' : 'No response body.',
            fold: true,
            open: foldOpen('resBody', true),
          }), 'resBody'),
          h('div', { class: 'group-title', text: 'Mock' }),
          mock
        ),
        h('aside', { class: 'split__side' }, curl)
      )
    );
  }

  function renderMock(tx) {
    const contentType = tx.responseContentType;
    const statusField = h('input', {
      class: 'input mono',
      type: 'number',
      min: '100',
      max: '599',
      id: 'mock-status',
      value: String(tx.mockResponseCode ?? tx.responseCode ?? 200),
      onInput: () => { mockDirty = true; },
    });
    const errorLine = h('div', { class: 'field__error', hidden: true });
    const bodyField = h('textarea', {
      class: 'textarea',
      id: 'mock-body',
      rows: '14',
      spellcheck: 'false',
      value: prettyBody(tx.mockResponseBody ?? tx.responseBody ?? '', contentType),
      onInput: () => {
        mockDirty = true;
        checkJson();
      },
    });

    const wantsJson = (contentType || '').toLowerCase().includes('json') ||
      isJson(tx.mockResponseBody ?? tx.responseBody ?? '', contentType);

    function checkJson() {
      const problem = wantsJson && bodyField.value.trim() ? jsonError(bodyField.value) : null;
      errorLine.hidden = !problem;
      errorLine.textContent = problem ? `Not valid JSON: ${problem}` : '';
      bodyField.setAttribute('aria-invalid', String(Boolean(problem)));
    }

    function format() {
      const problem = jsonError(bodyField.value);
      if (problem) {
        checkJson();
        toast.error('Fix the JSON first, then format it.');
        return;
      }
      bodyField.value = JSON.stringify(JSON.parse(bodyField.value), null, 2);
      mockDirty = true;
      checkJson();
    }

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
        mockDirty = false;
        toast.ok(enable ? 'Mock saved and turned on.' : 'Mock saved and turned off.');
        showDetail(tx.id);
      } catch (e) {
        toast.error(e.message);
      }
    }

    async function clearMock() {
      try {
        await api.del(`/api/network/transactions/${tx.id}/mock`);
        mockDirty = false;
        toast.ok('Mock removed.');
        showDetail(tx.id);
      } catch (e) {
        toast.error(e.message);
      }
    }

    const panel = h(
      'details',
      { class: 'panel panel--fold', id: 'sec-mock', open: foldOpen('mock', false) },
      h(
        'summary',
        { class: 'panel__head' },
        icon('chevron', 14),
        h('span', { class: 'panel__title', text: 'Mock response' }),
        tx.mockEnabled ? h('span', { class: 'chip chip--mock', text: 'On' }) : h('span', { class: 'panel__meta', text: 'Off' })
      ),
      h(
        'div',
        { class: 'panel__body mock-form' },
        h('p', {
          class: 'field__hint',
          text: 'When the mock is on, every later request to this URL gets this answer and does not reach the network.',
        }),
        h('div', { class: 'field field--status' },
          h('label', { class: 'field__label', for: 'mock-status', text: 'Status code' }),
          statusField),
        h(
          'div',
          { class: 'field' },
          h(
            'div',
            { class: 'panel__head panel__head--bare' },
            h('label', { class: 'field__label', for: 'mock-body', text: 'Body' }),
            wantsJson
              ? h('div', { class: 'panel__actions' },
                  h('button', { class: 'btn btn--quiet btn--sm', type: 'button', onClick: format }, icon('wand', 13), 'Format JSON'))
              : null
          ),
          bodyField,
          errorLine
        ),
        h(
          'div',
          { class: 'actions' },
          h('button', {
            class: 'btn btn--primary',
            type: 'button',
            text: tx.mockEnabled ? 'Save mock' : 'Save and turn on',
            onClick: () => save(true),
          }),
          tx.mockEnabled
            ? h('button', { class: 'btn', type: 'button', text: 'Turn off', onClick: () => save(false) })
            : null,
          tx.mockResponseBody || tx.mockResponseCode
            ? h('button', { class: 'btn btn--danger', type: 'button', text: 'Remove mock', onClick: clearMock })
            : null
        )
      )
    );
    checkJson();
    return remember(panel, 'mock');
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
        // Do not throw away a mock someone is typing.
        if (tx.id === selectedId && !mockDirty) {
          current = tx;
          paintDetail();
        }
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
    return true;
  }

  function showPlaceholder() {
    selectedId = null;
    current = null;
    stopHeadObserver();
    markSelection();
    ctx.setCrumbs(['Network']);
    fill(
      ctx.detail,
      h('div', { class: 'detail detail--center' },
        h('div', { class: 'empty empty--fill' },
          h('span', { class: 'empty__icon' }, icon('activity', 18)),
          h('span', { class: 'empty__title', text: 'Pick a request.' }),
          h('span', { text: 'Its headers, bodies and timing appear here.' })))
    );
  }

  return {
    id: 'network',

    activate({ segments, params }) {
      const next = {
        search: params.get('q') || '',
        method: params.get('method') || '',
      };
      const filtersChanged = next.search !== filters.search || next.method !== filters.method;
      filters = next;

      // A route change inside this panel (a row or a filter) keeps the list as it is, with its
      // scroll position. Only a change of filters asks the device again.
      if (!mounted) {
        mounted = true;
        paintFilters();
        fill(ctx.list, toolbar, listScroll, footer);
        subscribe();
        reload();
      } else if (filtersChanged) {
        paintFilters();
        reload();
      }

      if (segments.length > 1) {
        const id = Number(segments[1]);
        if (id === selectedId && current) {
          ctx.setCrumbs(['Network', `#${id}`]);
        } else {
          showDetail(id);
        }
      } else {
        showPlaceholder();
      }
    },

    deactivate() {
      for (const off of unsubscribe) off();
      unsubscribe = [];
      clearTimeout(searchTimer);
      stopHeadObserver();
      mounted = false;
      selectedId = null;
      current = null;
    },

    commands() {
      return rows.slice(0, 30).map((row) => ({
        label: `${row.method} ${row.url}`,
        hint: row.responseCode ? String(row.responseCode) : 'pending',
        icon: 'activity',
        run: () => go(detailPath(row.id)),
      }));
    },
  };
}

// ---------- Folds the console remembers ----------

/** Which sections the viewer opened or closed last. Kept in this browser only. */
function readFolds() {
  try {
    return JSON.parse(localStorage.getItem(FOLDS_KEY) || '{}') || {};
  } catch (e) {
    return {};
  }
}

function foldOpen(key, fallback) {
  const stored = readFolds()[key];
  return typeof stored === 'boolean' ? stored : fallback;
}

/**
 * Save the open state of a folding panel when the viewer clicks its head. A click on a
 * button in the head is marked with preventDefault and does not count.
 */
function remember(details, key) {
  const summary = details.querySelector(':scope > summary');
  summary?.addEventListener('click', (e) => {
    if (e.defaultPrevented) return;
    // The browser toggles the panel after this handler, so read the state a moment later.
    setTimeout(() => {
      try {
        const folds = readFolds();
        folds[key] = details.open;
        localStorage.setItem(FOLDS_KEY, JSON.stringify(folds));
      } catch (err) {
        // A private window refuses storage. The panel still opens and closes.
      }
    });
  });
  return details;
}

// ---------- Small shared builders ----------

function splitUrl(url) {
  try {
    const u = new URL(url);
    return { host: u.host, path: `${u.pathname}${u.search}` || '/' };
  } catch (e) {
    return { host: '', path: url || '' };
  }
}

function statusBadge(tx) {
  if (tx.error) return h('span', { class: 'badge badge--err', text: 'ERR', title: tx.error });
  if (tx.responseCode === null || tx.responseCode === undefined) {
    return h('span', { class: 'badge', text: '···', title: 'Waiting for the response' });
  }
  const tone = statusTone(tx.responseCode);
  return h('span', {
    class: `badge${tone ? ` badge--${tone}` : ''}`,
    title: `${tx.responseCode} ${tx.responseMessage ?? ''}`.trim(),
    text: String(tx.responseCode),
  });
}

function stat(iconName, text, label) {
  return h('span', { class: 'stats__item', title: label }, icon(iconName, 13), text);
}

function headersPanel(title, entries, emptyMessage, key) {
  const asText = () => entries.map(([k, v]) => `${k}: ${v}`).join('\n');
  const open = entries.length > 0 && foldOpen(key, entries.length <= OPEN_HEADER_LIMIT);
  const panel = h(
    'details',
    { class: 'panel panel--fold', open },
    h(
      'summary',
      { class: 'panel__head' },
      icon('chevron', 14),
      h('span', { class: 'panel__title', text: title }),
      h('span', { class: 'panel__meta', text: entries.length ? String(entries.length) : 'Empty' }),
      entries.length
        ? h(
            'div',
            // A click on Copy must not also open or close the panel.
            { class: 'panel__actions', onClick: (e) => e.preventDefault() },
            copyButton(asText, `${title} copied.`, { label: 'Copy all' })
          )
        : null
    ),
    entries.length
      ? h(
          'table',
          { class: 'hdr-table' },
          h(
            'tbody',
            null,
            ...entries.map(([name, value]) =>
              h(
                'tr',
                null,
                h('td', { class: 'hdr-table__name', text: name }),
                h(
                  'td',
                  { class: 'hdr-table__value' },
                  value === '[REDACTED]' ? h('span', { class: 'chip chip--quiet', text: 'Redacted' }) : value
                )
              )
            )
          )
        )
      : h('div', { class: 'code code--empty', text: emptyMessage })
  );
  return remember(panel, key);
}
