/**
 * This app's Logcat, live and coloured by level.
 *
 * The lines come on their own stream, not the shared one. An app that logs in a loop would
 * otherwise push every network and storage event out of the shared stream's buffer.
 *
 * Every line has a number from the device. The panel keeps the last one it drew, so a stream
 * that reconnects asks only for what it missed, and a jump in the numbers shows as a gap
 * instead of passing unseen.
 */

import { h, fill, icon } from '../core/dom.js';
import { withToken, query } from '../core/api.js';
import { setParam } from '../core/router.js';

/** Lines kept in the browser, whatever the filter. */
const KEEP = 20000;

/** Lines drawn at once. Filtering draws the newest this many that match. */
const DRAW = 5000;

const LEVELS = ['V', 'D', 'I', 'W', 'E', 'A'];
const LEVEL_NAMES = { V: 'Verbose', D: 'Debug', I: 'Info', W: 'Warn', E: 'Error', A: 'Assert', F: 'Fatal' };

function rank(level) {
  const index = LEVELS.indexOf(level === 'F' ? 'A' : level);
  return index < 0 ? 0 : index;
}

export function createLogcatPanel(ctx) {
  /** Log lines, plus markers for gaps and restarts, oldest first. */
  let items = [];
  let session = null;
  let lastSeq = 0;
  let status = null;

  let minLevel = 'V';
  let search = '';
  let paused = false;
  let follow = true;
  // A phone has no room for a long line, so there it wraps from the start.
  let wrap = window.matchMedia('(max-width: 820px)').matches;
  let unseen = 0;

  let source = null;
  let retryTimer = null;
  let retryMs = 1000;
  let active = false;
  let searchTimer = null;

  let pending = [];
  let frame = 0;
  /** The last line drawn, so the next one can tell whether it continues it. */
  let lastDrawn = null;

  // ---------- Frame ----------

  const view = h('div', {
    class: 'log',
    role: 'log',
    'aria-live': 'off',
    onScroll: onViewScroll,
    onClick: onViewClick,
  });

  const emptyNote = h(
    'div',
    { class: 'empty' },
    h('span', { class: 'empty__title', text: 'No log lines yet.' }),
    h('span', { text: 'Log something in the app and it appears here.' })
  );

  const banner = h('div', { class: 'banner banner--err', hidden: true });

  const searchInput = h('input', {
    class: 'input',
    type: 'search',
    placeholder: 'Filter text, or tag:Name',
    'aria-label': 'Filter log lines',
    onInput: (e) => {
      search = e.target.value;
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => {
        setParam('q', search);
        redraw();
      }, 150);
    },
  });

  const levelButtons = LEVELS.map((level) =>
    h('button', {
      class: `seg__btn lv-${level}`,
      title: `${LEVEL_NAMES[level]} and above`,
      'aria-label': `${LEVEL_NAMES[level]} and above`,
      'aria-pressed': 'false',
      text: level,
      onClick: () => {
        minLevel = level;
        setParam('level', level === 'V' ? '' : level);
        paintControls();
        redraw();
      },
    })
  );

  const pauseBtn = h('button', {
    class: 'btn btn--quiet btn--icon',
    onClick: () => setPaused(!paused),
  });

  const wrapBtn = h('button', {
    class: 'btn btn--quiet btn--sm',
    title: 'Wrap long lines',
    'aria-pressed': 'false',
    text: 'Wrap',
    onClick: () => {
      wrap = !wrap;
      paintControls();
    },
  });

  const latestBtn = h(
    'button',
    { class: 'btn btn--sm log__latest', hidden: true, onClick: () => setFollow(true) },
    icon('down', 14),
    h('span', { text: 'Latest' })
  );

  const liveState = h(
    'span',
    { class: 'live', 'data-state': 'idle' },
    h('span', { class: 'live__dot' }),
    h('span', { class: 'live__text', text: 'Offline' })
  );
  const counts = h('span', { class: 'num' });

  const root = h(
    'div',
    { class: 'logcat' },
    h(
      'div',
      { class: 'logcat__toolbar' },
      h('div', { class: 'search logcat__search' }, icon('search', 14), searchInput),
      h('div', { class: 'seg', role: 'group', 'aria-label': 'Lowest level shown' }, ...levelButtons),
      h(
        'div',
        { class: 'logcat__tools' },
        pauseBtn,
        wrapBtn,
        h('button', {
          class: 'btn btn--quiet btn--icon',
          title: 'Save the shown lines as text',
          'aria-label': 'Save the shown lines as text',
          onClick: download,
        }, icon('download')),
        h('button', {
          class: 'btn btn--quiet btn--icon',
          title: 'Clear the view',
          'aria-label': 'Clear the view',
          onClick: clearView,
        }, icon('trash'))
      )
    ),
    banner,
    h('div', { class: 'logcat__body' }, view, latestBtn),
    h('div', { class: 'logcat__footer' }, liveState, counts)
  );

  // ---------- Stream ----------

  function connect() {
    disconnect();
    paintLive('connecting');
    const since = session ? `${session}:${lastSeq}` : '';
    try {
      source = new EventSource(withToken(`/api/logcat/stream${query({ since })}`));
    } catch (e) {
      paintLive('down');
      scheduleRetry();
      return;
    }

    source.addEventListener('open', () => {
      retryMs = 1000;
      paintLive('live');
    });
    source.addEventListener('logcat_status', (event) => onStatus(parse(event.data)));
    source.addEventListener('logcat_lines', (event) => onLines(parse(event.data)?.lines || []));
    source.addEventListener('error', () => {
      // The browser reconnects by itself after a drop, and sends the last id it saw. After a
      // close it does neither, so the panel starts again from what it has drawn.
      if (source && source.readyState === EventSource.CLOSED) {
        paintLive('down');
        scheduleRetry();
      } else {
        paintLive('connecting');
      }
    });
  }

  function scheduleRetry() {
    clearTimeout(retryTimer);
    if (!active) return;
    retryTimer = setTimeout(connect, retryMs);
    retryMs = Math.min(retryMs * 2, 30000);
  }

  function disconnect() {
    clearTimeout(retryTimer);
    if (source) {
      source.close();
      source = null;
    }
  }

  function parse(raw) {
    try {
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;
    }
  }

  function onStatus(next) {
    if (!next) return;
    if (session && next.session !== session) {
      // The app started again. Its numbers start again too.
      add({ marker: 'restart', text: `App restarted · pid ${next.pid}` });
      lastSeq = 0;
    }
    session = next.session;
    status = next;
    paintBanner();
    paintCounts();
  }

  function onLines(lines) {
    for (const line of lines) {
      if (line.seq <= lastSeq) continue;
      if (lastSeq > 0 && line.seq > lastSeq + 1) {
        const missed = line.seq - lastSeq - 1;
        add({ marker: 'gap', text: `${missed.toLocaleString()} line${missed === 1 ? '' : 's'} not received` });
      }
      lastSeq = line.seq;
      add(line);
    }
    if (paused) paintCounts();
  }

  /** Keep an item, and draw it unless paused or filtered out. */
  function add(item) {
    items.push(item);
    if (items.length > KEEP) items.splice(0, items.length - KEEP);

    if (paused) {
      if (!item.marker) unseen += 1;
      return;
    }
    if (matches(item)) {
      pending.push(item);
      if (!frame) frame = requestAnimationFrame(flush);
    }
  }

  // ---------- Drawing ----------

  /** Draw what arrived since the last frame, in one step, however fast the app logs. */
  function flush() {
    frame = 0;
    if (pending.length === 0) return;
    const batch = pending;
    pending = [];

    emptyNote.remove();
    const fragment = document.createDocumentFragment();
    for (const item of batch) fragment.append(row(item));
    view.append(fragment);

    // Drop the oldest rows from the page. They are still kept and come back with a redraw.
    let excess = view.childElementCount - DRAW;
    while (excess-- > 0) view.firstElementChild.remove();

    if (follow) view.scrollTop = view.scrollHeight;
    paintCounts();
  }

  /** Draw from scratch, after a filter changes or the pause ends. */
  function redraw() {
    cancelAnimationFrame(frame);
    frame = 0;
    pending = [];
    lastDrawn = null;
    unseen = 0;

    const shown = [];
    for (let i = items.length - 1; i >= 0 && shown.length < DRAW; i--) {
      if (matches(items[i])) shown.push(items[i]);
    }
    shown.reverse();

    if (shown.length === 0) {
      fill(view, emptyNote);
    } else {
      const fragment = document.createDocumentFragment();
      for (const item of shown) fragment.append(row(item));
      fill(view, fragment);
    }
    if (follow) view.scrollTop = view.scrollHeight;
    paintCounts();
  }

  function row(item) {
    if (item.marker) {
      lastDrawn = null;
      return h('div', { class: `log__marker log__marker--${item.marker}`, text: item.text });
    }

    // A message with a newline arrives as several lines with the same header. Drawn with the
    // header once, a stack trace reads as one block.
    const continues =
      lastDrawn &&
      lastDrawn.time === item.time &&
      lastDrawn.tid === item.tid &&
      lastDrawn.level === item.level &&
      lastDrawn.tag === item.tag;
    lastDrawn = item;

    const level = item.level === 'F' ? 'A' : item.level;
    return h(
      'div',
      { class: `log__row lv-${level}${continues ? ' log__row--cont' : ''}` },
      h('span', { class: 'log__time', title: item.time, text: item.time.slice(6) }),
      h('span', { class: 'log__tid', title: `pid ${item.pid} · tid ${item.tid}`, text: item.tid }),
      h('span', { class: 'log__lv', title: LEVEL_NAMES[item.level] || item.level, text: item.level }),
      h('span', { class: 'log__tag', title: `Show only ${item.tag}`, 'data-tag': item.tag, text: item.tag }),
      h('span', { class: 'log__msg', text: item.message })
    );
  }

  // ---------- Filtering ----------

  /** `tag:Name` narrows to one tag. Everything else must appear in the tag or the message. */
  function parsedSearch() {
    const tags = [];
    const words = [];
    for (const token of search.trim().split(/\s+/).filter(Boolean)) {
      if (token.toLowerCase().startsWith('tag:') && token.length > 4) tags.push(token.slice(4).toLowerCase());
      else words.push(token.toLowerCase());
    }
    return { tags, text: words.join(' ') };
  }

  let filterCache = { key: null, value: null };

  function matches(item) {
    if (item.marker) return true;
    if (rank(item.level) < rank(minLevel)) return false;
    if (!search) return true;

    if (filterCache.key !== search) filterCache = { key: search, value: parsedSearch() };
    const { tags, text } = filterCache.value;
    const tag = item.tag.toLowerCase();
    if (tags.length > 0 && !tags.includes(tag)) return false;
    if (text && !tag.includes(text) && !item.message.toLowerCase().includes(text)) return false;
    return true;
  }

  function onViewClick(event) {
    const tag = event.target?.dataset?.tag;
    if (tag === undefined || tag === '') return;
    // Only a click, not the end of a text selection.
    if (String(window.getSelection?.() || '').length > 0) return;
    search = `tag:${tag}`;
    searchInput.value = search;
    setParam('q', search);
    redraw();
  }

  // ---------- Controls ----------

  function setPaused(value) {
    paused = value;
    paintControls();
    if (!paused) redraw();
    else paintCounts();
  }

  function setFollow(value) {
    follow = value;
    latestBtn.hidden = follow;
    if (follow) view.scrollTop = view.scrollHeight;
  }

  function onViewScroll() {
    const fromBottom = view.scrollHeight - view.scrollTop - view.clientHeight;
    // Scrolling up to read stops the view from jumping. Scrolling back down starts it again.
    if (fromBottom > 40 && follow) setFollow(false);
    else if (fromBottom <= 4 && !follow) setFollow(true);
  }

  function clearView() {
    items = [];
    unseen = 0;
    redraw();
  }

  function download() {
    const lines = items
      .filter((item) => !item.marker && matches(item))
      .map((l) => `${l.time} ${String(l.pid).padStart(5)} ${String(l.tid).padStart(5)} ${l.level} ${l.tag}: ${l.message}`);
    if (lines.length === 0) return;
    const blob = new Blob([`${lines.join('\n')}\n`], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const link = h('a', { href: url, download: `logcat-${ctx.capabilities.appId}-${stamp}.txt` });
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function paintControls() {
    for (const btn of levelButtons) {
      btn.setAttribute('aria-pressed', String(btn.textContent === minLevel));
    }
    fill(pauseBtn, icon(paused ? 'play' : 'pause'));
    pauseBtn.title = paused ? 'Resume' : 'Pause';
    pauseBtn.setAttribute('aria-label', pauseBtn.title);
    wrapBtn.setAttribute('aria-pressed', String(wrap));
    view.classList.toggle('log--wrap', wrap);
  }

  function paintLive(state) {
    liveState.dataset.state = state;
    liveState.querySelector('.live__text').textContent =
      state === 'live' ? (status ? `Reading pid ${status.pid}` : 'Live') : state === 'connecting' ? 'Connecting' : 'Offline';
  }

  function paintCounts() {
    const kept = items.reduce((n, item) => (item.marker ? n : n + 1), 0);
    const parts = [`${kept.toLocaleString()} line${kept === 1 ? '' : 's'}`];
    if (paused) parts.push(`paused · ${unseen.toLocaleString()} new`);
    counts.textContent = parts.join(' · ');
    if (liveState.dataset.state === 'live') paintLive('live');
  }

  function paintBanner() {
    const error = status && status.error;
    banner.hidden = !error;
    if (error) {
      fill(banner, icon('alert', 14), h('span', { text: `Logcat is not reading: ${error}` }));
    }
  }

  function applyParams(params) {
    const level = (params.get('level') || 'V').toUpperCase();
    minLevel = LEVELS.includes(level) ? level : 'V';
    search = params.get('q') || '';
    searchInput.value = search;
  }

  // ---------- Panel ----------

  return {
    id: 'logcat',
    layout: 'wide',

    activate({ params }) {
      const sameFilters = active && (params.get('q') || '') === search && (params.get('level') || 'V').toUpperCase() === minLevel;
      applyParams(params);
      paintControls();

      if (active) {
        // A filter changed the address. The stream stays open.
        if (!sameFilters) redraw();
        return;
      }

      active = true;
      ctx.setCrumbs(['Logcat']);
      fill(ctx.detail, root);
      redraw();
      setFollow(true);
      connect();
    },

    deactivate() {
      active = false;
      disconnect();
      clearTimeout(searchTimer);
      cancelAnimationFrame(frame);
      frame = 0;
      pending = [];
    },

    commands() {
      return [
        { label: paused ? 'Resume Logcat' : 'Pause Logcat', icon: paused ? 'play' : 'pause', run: () => setPaused(!paused) },
        { label: 'Clear the Logcat view', icon: 'trash', run: clearView },
        ...LEVELS.slice(1).map((level) => ({
          label: `Logcat: ${LEVEL_NAMES[level]} and above`,
          icon: 'terminal',
          run: () => {
            minLevel = level;
            setParam('level', level);
            paintControls();
            redraw();
          },
        })),
      ];
    },
  };
}
