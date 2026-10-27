const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../desktopMain/resources/player-ui/controls.js'), 'utf8');
const section = (start, end) => {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from);
  assert.ok(from >= 0 && to > from, `Missing controls section: ${start}`);
  return source.slice(from, to);
};

// Small DOM substitute: execute the actual player renderers without a native WebView.
class Element {
  constructor(tagName = 'span') {
    this.tagName = tagName.toUpperCase();
    this.children = [];
    this.style = {};
    this.classes = new Set();
    this.classList = {
      toggle: (name, enabled) => enabled ? this.classes.add(name) : this.classes.delete(name),
      add: name => this.classes.add(name), remove: name => this.classes.delete(name),
      contains: name => this.classes.has(name),
    };
    this.listeners = {};
  }
  set textContent(text) { this.text = text; this.children = []; }
  get textContent() { return this.text; }
  get firstElementChild() { return this.children[0] || null; }
  set innerHTML(html) { this.children = [new Element()]; }
  append(...items) { this.children.push(...items); }
  appendChild(item) { this.children.push(item); }
  addEventListener(name, callback) { this.listeners[name] = callback; }
  scrollIntoView() { this.scrolled = true; }
  setAttribute(name, value) { this[name] = value; }
  getBoundingClientRect() { return { left: 100, width: 600 }; }
  querySelector(selector) {
    if (selector === '.chapter-row-bar > span') return this.querySelector('.chapter-row-bar').children[0];
    for (const child of this.children) {
      if (child.tagName === selector.toUpperCase()) return child;
      if (child.className === selector.slice(1)) return child;
      const nested = child.querySelector(selector);
      if (nested) return nested;
    }
    return null;
  }
}

function player() {
  const events = [];
  const context = {
    state: {
      chapters: [{ startMs: 0, title: 'Opening' }, { startMs: 60_000, title: 'Main feature' }],
      positionMs: 15_000, durationMs: 120_000,
      playerUiMode: 'codeine_xo', seekPreviewEnabled: false,
    },
    activeModal: 'chapters', lastRenderedModal: '', lastRenderedModalSignature: '',
    document: { createElement: tagName => new Element(tagName) },
    window: { setTimeout: callback => callback(), innerWidth: 1280 },
    send: (...event) => events.push(event),
    closePlayerModal: () => events.push(['close']),
    setVisible: (element, visible) => { element.hidden = !visible; },
    setActionButtonLabel() {},
    formatTime: milliseconds => String(milliseconds),
    previewTiles: [], isScrubbing: false, scrubPositionMs: 0,
    fetch: () => { throw new Error('Chapter hover must not fetch Seekr data'); },
  };
  for (const name of ['chapterTitle', 'chapterMarks', 'chaptersPanelTitle', 'chaptersCloseButton', 'chapterList',
    'chaptersButton', 'codeineChaptersButton', 'chaptersLabel', 'seekChapterLabel', 'codeineSeekPreview',
    'seekFilmstripCarousel', 'seekPreviewPill', 'codeineSeekContainer']) {
    context[name] = new Element();
  }
  context.codeineChaptersButton.append(new Element('span'));
  const script = section('const setText =', 'let playerBadgesSignature =') +
    section('const formatChapterClock =', 'const trackListSignature =') +
    section('const getActiveModalSignature =', 'window.nuvioNativeViewportChanged =') +
    section('let seekrCues =', 'function parseSeekrVtt') +
    section('function findSeekrCueIndex', 'function updateSeekrPreview') +
    section('function updateSeekrPreview', 'if (torrentStatsClock)') +
    section('if (codeineSeekContainer) {\n  codeineSeekContainer.addEventListener("mousemove"', 'volumeSlider.addEventListener') +
    '\nglobalThis.api = { renderChaptersModal, renderChapterMarks, renderChapterControls, renderActiveModal, currentChapterIndex,' +
    ' updateSeekrPreview, hideSeekrPreview, refreshSeekHover, syncSeekrPreviews, setCues: cues => { seekrCues = cues; } };';
  vm.runInNewContext(script, context);
  return { ...context, context, events };
}

test('chapter panel opens at the current chapter and refreshes through the modal cache', () => {
  const p = player();
  p.api.renderActiveModal(true);
  const rows = p.chapterList.children;
  assert.equal(rows.length, 2);
  assert.equal(rows[0].classes.has('current'), true);
  assert.equal(rows[0].scrolled, true);
  p.state.positionMs = 75_000;
  p.api.renderActiveModal();
  assert.equal(p.chapterList.children[0], rows[0]);
  assert.equal(rows[0].classes.has('current'), false);
  assert.equal(rows[1].classes.has('current'), true);
  assert.equal(rows[1].querySelector('.chapter-row-bar > span').style.width, '25%');
  assert.equal(rows[1].querySelector('.chapter-row-remaining').textContent, '00:45 left');
});

test('chapter selection sends its index and closes the modal', () => {
  const p = player();
  p.api.renderActiveModal(true);
  p.chapterList.children[1].listeners.click({ stopPropagation() {} });
  assert.deepEqual(p.events, [['selectChapter', 1], ['close']]);
});

test('marks and titles follow playback and disappear for chapterless sources', () => {
  const p = player();
  p.state.playerUiMode = 'official';
  p.api.renderChapterMarks();
  assert.equal(p.chapterMarks.children.length, 1);
  assert.equal(p.chapterMarks.children[0].style.left, '50%');
  assert.equal(p.chapterTitle.textContent, 'Opening');
  assert.equal(p.chapterTitle.hidden, false);
  p.state.positionMs = 75_000;
  p.api.renderChapterMarks();
  assert.equal(p.chapterTitle.textContent, 'Main feature');
  p.state.chapters = [];
  p.api.renderChapterMarks();
  assert.equal(p.chapterMarks.children.length, 0);
  assert.equal(p.chapterMarks.hidden, true);
  assert.equal(p.chapterTitle.hidden, true);
});

test('source changes replace chapter rows even at the same playback position', () => {
  const p = player();
  p.api.renderActiveModal(true);
  p.state.chapters = [{ startMs: 0, title: 'New opening' }, { startMs: 30_000, title: 'New chapter' }];
  p.api.renderActiveModal();
  assert.equal(p.chapterList.children[0].querySelector('.chapter-row-name').textContent, 'New opening');
  assert.equal(p.chapterList.children[1].querySelector('.chapter-row-time').textContent, '00:30');
});

test('CodeineXO has no persistent chapter title while Official retains it', () => {
  const p = player();
  p.api.renderChapterMarks();
  assert.equal(p.chapterTitle.hidden, true);
  p.state.playerUiMode = 'official';
  p.api.renderChapterMarks();
  assert.equal(p.chapterTitle.textContent, 'Opening');
  p.state.isInPip = true;
  p.api.renderChapterMarks();
  assert.equal(p.chapterTitle.hidden, true);
});

test('hover follows chapter boundaries with Seekr disabled', () => {
  const p = player();
  p.codeineSeekContainer.listeners.mousemove({ clientX: 399 });
  assert.equal(p.seekChapterLabel.textContent, 'Opening');
  p.codeineSeekContainer.listeners.mousemove({ clientX: 400 });
  assert.equal(p.seekChapterLabel.textContent, 'Main feature');
  assert.equal(p.seekFilmstripCarousel.hidden, true);
  assert.equal(p.seekPreviewPill.hidden, true);
  assert.equal(p.codeineSeekPreview.classes.has('visible'), true);
  p.api.syncSeekrPreviews();
  assert.equal(p.seekChapterLabel.textContent, 'Main feature');
  assert.equal(p.codeineSeekPreview.classes.has('visible'), true);
});

test('chapter hover survives unavailable Seekr and coexists with available thumbnails', () => {
  const p = player();
  p.state.seekPreviewEnabled = true;
  p.api.updateSeekrPreview(75_000, 400);
  assert.equal(p.seekChapterLabel.textContent, 'Main feature');
  assert.equal(p.seekPreviewPill.hidden, true);
  p.api.setCues([{ startMs: 60_000, endMs: 120_000, w: 144, h: 81, sheetUrl: 'mock' }]);
  p.api.refreshSeekHover();
  assert.equal(p.seekChapterLabel.textContent, 'Main feature');
  assert.equal(p.seekFilmstripCarousel.hidden, false);
  assert.equal(p.seekPreviewPill.hidden, false);
  assert.equal(p.seekPreviewPill.textContent, '60000');
});

test('hover clears on pointer leave, source changes and disabled chapters', () => {
  const p = player();
  p.api.updateSeekrPreview(75_000, 400);
  p.codeineSeekContainer.listeners.mouseleave();
  assert.equal(p.seekChapterLabel.hidden, true);
  assert.equal(p.codeineSeekPreview.classes.has('visible'), false);
  p.api.updateSeekrPreview(75_000, 400);
  p.state.streamTitle = 'Replacement source';
  p.api.refreshSeekHover();
  assert.equal(p.seekChapterLabel.hidden, true);
  p.api.updateSeekrPreview(75_000, 400);
  p.state.chapters = [];
  p.api.renderChapterControls();
  assert.equal(p.seekChapterLabel.hidden, true);
  assert.equal(p.codeineSeekPreview.classes.has('visible'), false);
});

test('no chapter tooltip appears before the first chapter or for chapterless content', () => {
  const p = player();
  p.state.chapters = [{ startMs: 10_000, title: 'First' }, { startMs: 60_000, title: 'Second' }];
  p.api.updateSeekrPreview(0, 100);
  assert.equal(p.seekChapterLabel.hidden, true);
  p.state.chapters = [];
  p.api.updateSeekrPreview(75_000, 400);
  assert.equal(p.codeineSeekPreview.classes.has('visible'), false);
});

test('PiP hides both chapter actions, closes the panel and restores actions on exit', () => {
  const p = player();
  p.api.renderChapterControls();
  assert.equal(p.chaptersButton.hidden, false);
  assert.equal(p.codeineChaptersButton.hidden, false);
  p.api.updateSeekrPreview(75_000, 400);
  p.state.isInPip = true;
  p.api.renderChapterControls();
  assert.equal(p.chaptersButton.hidden, true);
  assert.equal(p.codeineChaptersButton.hidden, true);
  assert.equal(p.seekChapterLabel.hidden, true);
  assert.deepEqual(p.events, [['close']]);
  p.state.isInPip = false;
  p.context.activeModal = '';
  p.api.renderChapterControls();
  assert.equal(p.chaptersButton.hidden, false);
  assert.equal(p.codeineChaptersButton.hidden, false);
});
