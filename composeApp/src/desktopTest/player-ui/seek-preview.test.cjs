const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../desktopMain/resources/player-ui/controls.js'), 'utf8');
function section(start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from);
  assert.ok(from >= 0 && to > from, start);
  return source.slice(from, to);
}

function player(mode = 'official', width = 1280) {
  const timers = new Map();
  let sequence = 0;
  const events = [];
  const rect = { left: 100, right: width - 100, top: 600, bottom: 625, width: width - 200 };
  const element = () => ({
    style: {}, hidden: false, textContent: '', listeners: {},
    classes: new Set(),
    classList: {
      add(name) { this.owner.classes.add(name); },
      remove(name) { this.owner.classes.delete(name); },
      toggle(name, enabled) { enabled ? this.owner.classes.add(name) : this.owner.classes.delete(name); },
      contains(name) { return this.owner.classes.has(name); },
    },
    setAttribute(name, value) { this[name] = value; },
    addEventListener(name, listener) { this.listeners[name] = listener; },
    getBoundingClientRect: () => rect,
  });
  const context = {
    state: {
      playerUiMode: mode, positionMs: 20000, durationMs: 100000,
      seekPreviewEnabled: true, seekPreviewThumbnailCount: 5,
      seekPreviewWhileDragging: true, seekPreviewWhileHovering: true,
      seekPreviewWhileKeyboardSeeking: true,
      chapters: [{ startMs: 0, title: 'Opening' }],
    },
    window: {
      innerWidth: width, innerHeight: 720, listeners: {},
      setTimeout(fn, delay) { const id = ++sequence; timers.set(id, { fn, delay }); return id; },
      clearTimeout(id) { timers.delete(id); },
      addEventListener(name, listener) { this.listeners[name] = listener; },
    },
    document: { documentElement: { clientWidth: width } },
    previewTiles: Array.from({ length: 5 }, element),
    isScrubbing: false, scrubPositionMs: 0,
    currentChapterIndex: chapters => chapters.length ? 0 : -1,
    setText: (el, text) => { el.textContent = text; el.hidden = !text; },
    setVisible: (el, visible) => { el.hidden = !visible; },
    formatTime: String,
    noteChromeActivity() {}, triggerSeekbarOnlyOnSeek() {}, markSeek() {}, setProgress() {}, render() {}, hideSeekbarOnly() {},
    showPlayerToast() {}, currentParty: () => ({ synced: false }),
    send: (...args) => events.push(args),
    rangePositionMs: () => context.rangeValue,
    rangeValue: 40000, playerToast: null,
  };
  for (const name of ['root', 'seekFeedback', 'seek', 'codeineSeekContainer', 'codeineSeekPreview', 'seekFilmstripCarousel', 'seekPreviewPill', 'seekChapterLabel']) { context[name] = element(); context[name].classList.owner = context[name]; }
  // Official wrappers use display:contents and have no usable bounds.
  if (mode === 'official') context.codeineSeekContainer.getBoundingClientRect = () => ({ left: 0, width: 0 });
  context.codeineSeekPreview.offsetParent = { getBoundingClientRect: () => ({ left: 0, bottom: 720 }) };
  const code = section('let fineSeekTimer =', 'const sendKeyboardVolume') +
    section('let seekrCues =', 'function parseSeekrVtt') +
    section('function parseSeekrVtt', 'function updateSeekrPreview') +
    section('function updateSeekrPreview', 'if (torrentStatsClock)') +
    section('seek.addEventListener("input"', 'volumeSlider.addEventListener') +
    '\nglobalThis.api = { showKeyboardSeekPreview, refreshSeekHover, clearSeekPreviewInteraction, seekPreviewCount, seekPreviewGeometry,' +
    'activeSeekPreview, fineSeek, syncSeekrPreviews, getCues: () => seekrCues, setCues: value => { seekrCues = value; } };';
  vm.runInNewContext(code, context);
  context.api.setCues(Array.from({ length: 10 }, (_, index) => ({ startMs: index * 10000, endMs: (index + 1) * 10000, w: 144, h: 81, sheetUrl: 'sprite.jpg', x: index * 144, y: 0 })));
  const pointer = fraction => ({ clientX: rect.left + fraction * rect.width, clientY: 610 });
  const hover = fraction => context.seek.listeners.pointermove(pointer(fraction));
  const expire = () => { const pending = [...timers.values()]; timers.clear(); for (const timer of pending) timer.fn(); };
  return { ...context, context, timers, events, rect, pointer, hover, expire };
}

for (const mode of ['official', 'codeine_xo']) {
  test(`${mode}: clicks never flash drag or keyboard thumbnails with hover disabled`, () => {
    for (const changeBeforeRelease of [true, false]) {
      const p = player(mode);
      p.state.seekPreviewWhileHovering = false;
      const noPreview = () => {
        assert.equal(p.codeineSeekPreview.classes.has('visible'), false);
        assert.equal(p.api.activeSeekPreview()?.trigger, 'hovering');
        assert.equal(p.timers.size, 0);
      };
      p.seek.listeners.pointerdown(p.pointer(0.4));
      noPreview();
      p.seek.listeners.input();
      noPreview();
      assert.deepEqual(p.events[0], ['scrubChange', 40000]);
      if (changeBeforeRelease) p.seek.listeners.change();
      p.window.listeners.pointerup(p.pointer(0.4));
      if (!changeBeforeRelease) p.seek.listeners.change();
      noPreview();
      assert.equal(p.state.positionMs, 40000);
      assert.deepEqual(p.events[1], ['scrubFinish', 40000]);
    }
  });

  test(`${mode}: drag previews require four horizontal pixels and honor the drag setting`, () => {
    for (const direction of [-1, 1]) {
      const p = player(mode);
      p.state.seekPreviewWhileHovering = false;
      const start = p.pointer(0.4);
      p.seek.listeners.pointerdown(start);
      p.seek.listeners.pointermove({ ...start, clientX: start.clientX + direction * 3 });
      p.seek.listeners.input();
      assert.equal(p.api.activeSeekPreview().trigger, 'hovering');
      assert.equal(p.codeineSeekPreview.classes.has('visible'), false);
      p.seek.listeners.pointermove({ ...start, clientX: start.clientX + direction * 4 });
      assert.equal(p.api.activeSeekPreview().trigger, 'dragging');
      assert.equal(p.codeineSeekPreview.classes.has('visible'), true);
      p.state.seekPreviewWhileDragging = false;
      p.api.refreshSeekHover();
      assert.equal(p.codeineSeekPreview.classes.has('visible'), false);
      p.window.listeners.pointerup(start);
      assert.equal(p.api.activeSeekPreview().trigger, 'hovering');
    }
  });

  test(`${mode}: clicks preserve enabled hover and cancel previous keyboard previews`, () => {
    const p = player(mode);
    p.api.showKeyboardSeekPreview(80000);
    p.seek.listeners.pointerdown(p.pointer(0.4));
    p.seek.listeners.input();
    assert.equal(p.api.activeSeekPreview().trigger, 'hovering');
    assert.equal(p.codeineSeekPreview.classes.has('visible'), true);
    assert.equal(p.timers.size, 0);
    p.window.listeners.pointerup(p.pointer(0.4));
    p.seek.listeners.change();
    p.hover(0.6);
    assert.equal(p.seekPreviewPill.textContent, '60000');
  });

  test(`${mode}: pending presses and drags clear on cancel, blur, reset and outside release`, () => {
    for (const dragging of [false, true]) {
      for (const finish of [
        p => p.window.listeners.pointercancel(),
        p => p.window.listeners.blur(),
        p => p.api.clearSeekPreviewInteraction(),
        p => p.window.listeners.pointerup({ clientX: 0, clientY: 0 }),
      ]) {
        const p = player(mode);
        p.seek.listeners.pointerdown(p.pointer(0.4));
        if (dragging) p.window.listeners.pointermove(p.pointer(0.5));
        p.seek.listeners.input();
        finish(p);
        assert.equal(p.api.activeSeekPreview(), null);
        assert.equal(p.codeineSeekPreview.classes.has('visible'), false);
        p.seek.listeners.input();
        assert.equal(p.api.activeSeekPreview().trigger, 'keyboard');
      }
    }
  });

  test(`${mode}: hover follows the actual input without changing playback`, () => {
    const p = player(mode);
    p.hover(0.4);
    const left = Number.parseFloat(p.codeineSeekPreview.style.left);
    p.hover(0.6);
    assert.ok(Number.parseFloat(p.codeineSeekPreview.style.left) > left);
    assert.equal(p.seekPreviewPill.textContent, '60000');
    assert.equal(p.state.positionMs, 20000);
    assert.equal(p.events.length, 0);
    assert.equal(p.codeineSeekPreview.style.bottom, '140px');
  });

  test(`${mode}: each trigger can independently suppress thumbnails and retain chapter behavior`, () => {
    const p = player(mode);
    p.state.seekPreviewWhileHovering = false;
    p.hover(0.2);
    assert.equal(p.seekFeedback.classes.has('visible'), true);
    assert.equal(p.seekFilmstripCarousel.hidden, true);
    assert.equal(p.seekChapterLabel.hidden, mode === 'official');
    p.seek.listeners.pointerdown(p.pointer(0.4));
    p.seek.listeners.pointermove(p.pointer(0.5));
    p.seek.listeners.input();
    assert.equal(p.seekFeedback.classes.has('visible'), true);
    assert.equal(p.seekFilmstripCarousel.hidden, false);
    p.state.seekPreviewWhileDragging = false;
    p.api.refreshSeekHover();
    assert.equal(p.seekFilmstripCarousel.hidden, true);
    assert.equal(p.seekFeedback.classes.has('visible'), true);
    p.window.listeners.pointerup(p.pointer(0.4));
    p.api.showKeyboardSeekPreview(70000);
    assert.equal(p.seekFilmstripCarousel.hidden, false);
    p.state.seekPreviewWhileKeyboardSeeking = false;
    p.api.refreshSeekHover();
    assert.equal(p.seekFilmstripCarousel.hidden, true);
    assert.equal(p.seekFeedback.classes.has('visible'), true);
  });

  test(`${mode}: keyboard priority and timeout reset, followed by hover`, () => {
    const p = player(mode);
    p.hover(0.2);
    p.api.showKeyboardSeekPreview(60000);
    p.hover(0.3);
    assert.equal(p.seekPreviewPill.textContent, '30000');
    p.api.showKeyboardSeekPreview(70000);
    assert.equal(p.timers.size, 1);
    assert.equal([...p.timers.values()][0].delay, 1500);
    p.expire();
    assert.equal(p.seekPreviewPill.textContent, '30000');
    p.state.seekPreviewWhileKeyboardSeeking = false;
    p.api.showKeyboardSeekPreview(80000);
    assert.equal(p.seekFeedback.classes.has('visible'), true);
    p.expire();
    assert.equal(p.seekFilmstripCarousel.hidden, false);
  });

  test(`${mode}: drag overrides keyboard, then returns to hover`, () => {
    const p = player(mode);
    p.api.showKeyboardSeekPreview(80000);
    p.seek.listeners.pointerdown(p.pointer(0.4));
    p.seek.listeners.pointermove(p.pointer(0.5));
    p.seek.listeners.input();
    assert.equal(p.seekPreviewPill.textContent, '40000');
    assert.equal(p.timers.size, 0);
    p.window.listeners.pointerup(p.pointer(0.5));
    p.seek.listeners.change();
    assert.equal(p.seekPreviewPill.textContent, '50000');
    p.seek.listeners.pointerleave();
    assert.equal(p.api.activeSeekPreview(), null);
  });

  for (const count of [1, 3, 5]) test(`${mode}: ${count} centered slots, missing neighbors omitted`, () => {
    const p = player(mode);
    p.state.seekPreviewThumbnailCount = count;
    p.hover(0.5);
    assert.equal(p.previewTiles.filter(tile => tile.style.display === 'block' && tile.style.visibility === 'visible').length, count);
    assert.equal(p.previewTiles[2].style.backgroundPosition, '-720px -0px');
    p.hover(0);
    assert.equal(p.previewTiles.filter(tile => tile.style.display === 'block' && tile.style.visibility === 'visible').length, (count + 1) / 2);
  });

  test(`${mode}: narrow windows scale all five frames and clamp both edges`, () => {
    const p = player(mode, 480);
    p.hover(0);
    const geometry = p.api.seekPreviewGeometry(5, 144, 480, 100);
    assert.ok(geometry.scale < 1);
    assert.ok(geometry.center - geometry.width / 2 >= 16 - 0.001);
    assert.equal(p.previewTiles.filter(tile => tile.style.display === 'block').length, 5);
    assert.equal(Number(p.previewTiles[2].style.zoom), geometry.scale);
    p.hover(1);
    assert.ok(Number.parseFloat(p.codeineSeekPreview.style.left) + geometry.width / 2 <= 464.001);
  });

  test(`${mode}: focused slider keyboard input keeps thumbnails without mouse feedback`, () => {
    const p = player(mode);
    p.seek.listeners.input();
    p.seek.listeners.change();
    assert.equal(p.api.activeSeekPreview().trigger, 'keyboard');
    assert.equal(p.seekFeedback.classes.has('visible'), false);
    assert.equal(p.seekFilmstripCarousel.hidden, false);
    p.expire();
    assert.equal(p.api.activeSeekPreview(), null);
  });

  test(`${mode}: hidden controls retain keyboard thumbnail previews`, () => {
    const p = player(mode);
    p.state.controlsVisible = false;
    p.api.showKeyboardSeekPreview(70000);
    assert.equal(p.seekFilmstripCarousel.hidden, false);
    assert.equal(p.seekFeedback.classes.has('visible'), false);
    p.expire();
    assert.equal(p.api.activeSeekPreview(), null);
  });

  test(`${mode}: fine seek previews every one-second target and resets the timeout`, () => {
    const p = player(mode);
    p.api.fineSeek(true);
    assert.equal(p.api.activeSeekPreview().positionMs, 21000);
    p.api.fineSeek(true);
    assert.equal(p.api.activeSeekPreview().positionMs, 22000);
    assert.equal([...p.timers.values()].filter(timer => timer.delay === 1500).length, 1);
    p.api.fineSeek(false);
    assert.equal(p.api.activeSeekPreview().positionMs, 21000);
  });

  test(`${mode}: source changes, PiP, mini-player and blur clear ownership and timers`, () => {
    for (const update of [s => { s.streamTitle = 'new'; }, s => { s.isInPip = true; }, s => { s.isMiniPlayer = true; }, s => { s.controlsVisible = false; }]) {
      const p = player(mode);
      p.hover(0.2);
      p.api.showKeyboardSeekPreview(80000);
      update(p.state);
      p.api.refreshSeekHover();
      assert.equal(p.api.activeSeekPreview(), null);
      assert.equal(p.timers.size, 0);
      assert.equal(p.seekFeedback.classes.has('visible'), false);
      assert.equal(p.root.classes.has('seek-feedback-active'), false);
    }
    const p = player(mode);
    p.api.showKeyboardSeekPreview(80000);
    p.window.listeners.blur();
    assert.equal(p.timers.size, 0);
    p.state.seekPreviewEnabled = false;
    p.hover(0.4);
    assert.equal(p.seekFeedback.classes.has('visible'), true);
    assert.equal(p.api.seekPreviewCount(4), 5);
  });

  test(`${mode}: master disable cancels keyboard ownership while preserving chapter hover`, () => {
    const p = player(mode);
    p.hover(0.2);
    p.api.showKeyboardSeekPreview(80000);
    p.state.seekPreviewEnabled = false;
    p.api.syncSeekrPreviews();
    assert.equal(p.timers.size, 0);
    assert.equal(p.seekFeedback.classes.has('visible'), true);
    assert.equal(p.api.getCues().length, 0);
    assert.equal(p.seekChapterLabel.hidden, mode === 'official');
  });

  test(`${mode}: exact mouse time survives missing thumbnails and chapters`, () => {
    const p = player(mode);
    p.state.chapters = [];
    p.api.setCues([]);
    p.hover(0.45678);
    assert.equal(p.seekPreviewPill.textContent, '45678');
    assert.equal(p.seekChapterLabel.hidden, true);
    assert.equal(p.seekFeedback.classes.has('visible'), true);
    assert.equal(p.root.classes.has('seek-feedback-active'), true);
    p.seek.listeners.pointerleave();
    assert.equal(p.seekFeedback.classes.has('visible'), false);
    assert.equal(p.root.classes.has('seek-feedback-active'), false);
    p.hover(0);
    assert.equal(p.seekPreviewPill.textContent, '0');
    p.hover(1);
    assert.equal(p.seekPreviewPill.textContent, '100000');
  });

  test(`${mode}: dragging outside retains scrub time until released`, () => {
    const p = player(mode);
    p.seek.listeners.pointerdown(p.pointer(0.4));
    p.seek.listeners.pointermove(p.pointer(0.5));
    p.seek.listeners.input();
    p.seek.listeners.pointerleave();
    assert.equal(p.seekFeedback.classes.has('visible'), true);
    assert.equal(p.seekPreviewPill.textContent, '40000');
    p.window.listeners.pointerup({ clientX: 0, clientY: 0 });
    assert.equal(p.seekFeedback.classes.has('visible'), false);
  });

  test(`${mode}: stale thumbnail responses cannot replace a new content track`, async () => {
    const p = player(mode);
    const pending = [];
    p.context.URL = URL;
    p.context.fetch = () => new Promise(resolve => pending.push(resolve));
    p.state.seekPreviewVttUrl = 'https://example.test/old.vtt';
    p.state.seekrImdbId = 'old';
    p.api.syncSeekrPreviews();
    p.state.seekrImdbId = 'new';
    p.state.seekPreviewVttUrl = 'https://example.test/new.vtt';
    p.api.syncSeekrPreviews();
    const vtt = sheet => `WEBVTT\n\n00:00:00.000 --> 00:00:10.000\n${sheet}#xywh=0,0,144,81\n`;
    pending[1]({ text: () => Promise.resolve(vtt('new.jpg')) });
    await new Promise(resolve => setImmediate(resolve));
    pending[0]({ text: () => Promise.resolve(vtt('old.jpg')) });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(p.api.getCues()[0].sheetUrl, 'https://example.test/new.jpg');
    p.state.seekPreviewVttUrl = 'https://example.test/disabled.vtt';
    p.api.syncSeekrPreviews();
    p.state.seekPreviewEnabled = false;
    p.api.syncSeekrPreviews();
    pending[2]({ text: () => Promise.resolve(vtt('disabled.jpg')) });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(p.api.getCues().length, 0);
  });
}
