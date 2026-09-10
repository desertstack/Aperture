/**
 * The app's file sandbox.
 *
 * Ids come from the device and go straight back to it. The console never builds a path, so
 * there is nothing here for a traversal attempt to work on.
 */

import { h, fill, icon } from '../core/dom.js';
import { api, query, withToken } from '../core/api.js';
import * as sse from '../core/sse.js';
import { go } from '../core/router.js';
import { toast } from '../ui/toast.js';
import { confirmAction } from '../ui/dialog.js';
import { bytes, dateTime } from '../core/format.js';

export function createFilesPanel(ctx) {
  let roots = [];
  let listing = null;
  let openId = null;
  let unsubscribe = [];

  const listBody = h('div', { class: 'rows' });
  const trailBar = h('div', { class: 'list-pane__toolbar', style: { 'flex-direction': 'row', 'flex-wrap': 'wrap', gap: '4px' } });
  const footer = h('div', { class: 'list-pane__footer' });

  const canWrite = () => ctx.capabilities.allowWrites;

  async function loadRoots() {
    try {
      const data = await api.get('/api/files');
      roots = data.roots;
      if (!listing) renderRoots();
    } catch (e) {
      toast.error(e.message);
    }
  }

  function renderRoots() {
    fill(trailBar, h('span', { class: 'eyebrow', text: 'Roots' }));
    fill(
      listBody,
      ...roots.map((root) =>
        h(
          'button',
          { class: 'row', onClick: () => go(`/files/${encodeURIComponent(root.id)}`) },
          h('div', { class: 'row__top' },
            icon('folder', 14),
            h('span', { class: 'row__title', style: { direction: 'ltr' }, text: root.label })),
          h('div', { class: 'row__sub', style: { 'padding-left': '22px' } },
            h('span', { class: 'truncate', text: root.path }))
        )
      )
    );
    fill(footer, h('span', { text: `${roots.length} roots` }));
  }

  async function openDirectory(id) {
    try {
      listing = await api.get(`/api/files/list${query({ id })}`);
      renderListing();
    } catch (e) {
      toast.error(e.message);
    }
  }

  function renderListing() {
    fill(
      trailBar,
      ...listing.trail.flatMap((step, index) => [
        index > 0 ? h('span', { class: 'faint', text: '/' }) : null,
        h('button', {
          class: 'btn btn--quiet btn--sm',
          text: step.name,
          onClick: () => go(`/files/${encodeURIComponent(step.id)}`),
        }),
      ])
    );

    if (listing.entries.length === 0) {
      fill(listBody, h('div', { class: 'empty' }, h('span', { text: 'This directory is empty.' })));
    } else {
      fill(listBody, ...listing.entries.map(renderEntry));
    }

    fill(
      footer,
      h('span', { text: `${listing.entries.length} item${listing.entries.length === 1 ? '' : 's'}` }),
      listing.truncated ? h('span', { class: 't-warn', text: 'Listing capped' }) : null
    );
  }

  function renderEntry(entry) {
    return h(
      'button',
      {
        class: 'row',
        id: `file-row-${entry.id}`,
        'aria-selected': String(entry.id === openId),
        onClick: () => go(`/files/${encodeURIComponent(entry.id)}`),
      },
      h(
        'div',
        { class: 'row__top' },
        icon(entry.directory ? 'folder' : 'file', 14),
        h('span', { class: 'row__title', style: { direction: 'ltr' }, text: entry.name }),
        entry.link ? h('span', { class: 'chip chip--quiet', text: 'link' }) : null
      ),
      h(
        'div',
        { class: 'row__sub', style: { 'padding-left': '22px' } },
        h('span', { text: entry.directory ? 'Directory' : bytes(entry.sizeBytes) }),
        entry.lastModified ? h('span', { text: dateTime(entry.lastModified) }) : null
      )
    );
  }

  // ---------- Detail ----------

  async function openTarget(id) {
    openId = id;
    // A directory shows in the list pane; a file shows in the detail pane. Try the listing
    // first, and fall back to reading it as a file.
    try {
      listing = await api.get(`/api/files/list${query({ id })}`);
      renderListing();
      ctx.setCrumbs(['Files', listing.trail.map((s) => s.name).join('/')]);
      fill(ctx.detail, h('div', { class: 'detail' },
        h('div', { class: 'empty' },
          h('span', { class: 'empty__title', text: 'Pick a file.' }),
          h('span', { text: 'Its contents appear here.' }))));
      return;
    } catch (e) {
      // Not a directory, so read it as a file.
    }

    try {
      const content = await api.get(`/api/files/read${query({ id })}`);
      ctx.setCrumbs(['Files', content.name]);
      fill(ctx.detail, renderFile(content));
      for (const el of listBody.children) {
        if (el.id) el.setAttribute('aria-selected', String(el.id === `file-row-${id}`));
      }
    } catch (e) {
      fill(ctx.detail, h('div', { class: 'detail' }, h('div', { class: 'empty' }, h('span', { text: e.message }))));
    }
  }

  function renderFile(content) {
    const editable = canWrite() && !content.binary && !content.truncated;
    const area = h('textarea', {
      class: 'textarea',
      style: { 'min-height': '320px' },
      'aria-label': `Contents of ${content.name}`,
      value: content.text ?? '',
      readonly: !editable,
    });

    return h(
      'div',
      { class: 'detail' },
      h(
        'div',
        { class: 'detail__head' },
        h('div', { class: 'detail__title', text: content.name }),
        h(
          'div',
          { class: 'detail__meta' },
          h('span', { class: 'num', text: bytes(content.sizeBytes) }),
          content.binary ? h('span', { class: 'chip chip--quiet', text: 'Binary' }) : null,
          content.truncated ? h('span', { class: 'chip chip--warn', text: 'Preview only' }) : null
        )
      ),
      content.truncated
        ? h('div', { class: 'banner' }, icon('alert', 14),
            h('span', { text: 'Showing the first 256 KB. Download the file to see all of it.' }))
        : null,
      h(
        'section',
        { class: 'section' },
        h(
          'div',
          { class: 'section__head' },
          h('span', { class: 'eyebrow', text: content.binary ? 'File' : 'Contents' }),
          h(
            'div',
            { style: { display: 'flex', gap: 'var(--sp-2)' } },
            h('a', {
              class: 'btn btn--sm',
              href: withToken(`/api/files/download?id=${encodeURIComponent(content.id)}`),
              download: content.name,
            }, icon('download', 13), h('span', { text: 'Download' })),
            editable
              ? h('button', {
                  class: 'btn btn--primary btn--sm',
                  text: 'Save',
                  onClick: () => save(content, area.value),
                })
              : null,
            canWrite()
              ? h('button', { class: 'btn btn--danger btn--sm', text: 'Delete', onClick: () => remove(content) })
              : null
          )
        ),
        content.binary
          ? h('div', { class: 'code code--empty', text: 'This file is not text. Download it to look inside.' })
          : area
      )
    );
  }

  async function save(content, text) {
    try {
      const updated = await api.put('/api/files/content', { id: content.id, text });
      fill(ctx.detail, renderFile(updated));
      toast.undoable(`${content.name} saved.`, async () => {
        const reverted = await api.put('/api/files/content', { id: content.id, text: content.text ?? '' });
        fill(ctx.detail, renderFile(reverted));
        toast.ok('Put back.');
      });
    } catch (e) {
      toast.error(e.message);
    }
  }

  async function remove(content) {
    const ok = await confirmAction({
      title: `Delete ${content.name}`,
      body: 'The app may be using this file. Deleting it cannot be undone.',
      target: content.name,
      confirmLabel: 'Delete file',
    });
    if (!ok) return;
    try {
      await api.del(`/api/files${query({ id: content.id })}`);
      toast.ok(`${content.name} deleted.`);
      go('/files');
    } catch (e) {
      toast.error(e.message);
    }
  }

  return {
    id: 'files',

    activate({ segments }) {
      fill(ctx.list, trailBar, h('div', { class: 'list-pane__scroll' }, listBody), footer);
      unsubscribe.push(sse.on('file_changed', () => {
        if (listing) openDirectory(listing.trail[listing.trail.length - 1].id);
      }));

      loadRoots().then(() => {
        if (segments.length > 1) {
          openTarget(decodeURIComponent(segments[1]));
        } else {
          listing = null;
          openId = null;
          renderRoots();
          ctx.setCrumbs(['Files']);
          fill(ctx.detail, h('div', { class: 'detail' },
            h('div', { class: 'empty' },
              h('span', { class: 'empty__title', text: 'Pick a root.' }),
              h('span', { text: 'The app sandbox holds files, cache, databases and preferences.' }))));
        }
      });
    },

    deactivate() {
      for (const off of unsubscribe) off();
      unsubscribe = [];
    },

    commands() {
      return roots.map((root) => ({
        label: root.label,
        hint: 'files',
        icon: 'folder',
        run: () => go(`/files/${encodeURIComponent(root.id)}`),
      }));
    },
  };
}
