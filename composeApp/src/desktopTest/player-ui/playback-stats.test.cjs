const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../../desktopMain/resources/player-ui/controls.js'), 'utf8');
function player() {
  const elements = new Map();
  const get = id => {
    if (!elements.has(id)) elements.set(id, {
      dataset: {}, hidden: true, textContent: '', removeAttribute(name) { delete this[name]; },
    });
    return elements.get(id);
  };
  const context = { state: {}, document: { getElementById: get }, statSubtitleTrack: get('statSubtitleTrack'),
    setText: (element, text) => { element.textContent = text; } };
  vm.runInNewContext(source.slice(source.indexOf('const renderPlaybackStatsMetadata ='),
    source.indexOf('const renderPlaybackStats =')) + '\nglobalThis.render = renderPlaybackStatsMetadata;', context);
  return { context, get, render: context.render };
}

test('stats metadata updates, hides absent rows, and uses safe text for descriptions', () => {
  const p = player();
  p.context.state.playbackStatsMetadata = { subtitleName: 'Addon French', subtitleLanguage: 'French',
    subtitleSource: 'Addon', languageLabel: 'Language', streamDescription: '<b>Title</b>\nFull description' };
  p.render();
  assert.equal(p.get('statSubtitleTrack').textContent, 'Addon French');
  assert.equal(p.get('statSubtitleLanguageRow').hidden, false);
  assert.equal(p.get('statStreamDescription').textContent, '<b>Title</b>\nFull description');
  assert.equal(p.get('statPlaybackEngineRow').hidden, true);
  p.context.state.playbackStatsMetadata = null;
  p.render();
  assert.equal(p.get('statSubtitleSourceRow').hidden, true);
  assert.equal(p.get('statStreamDescriptionRow').hidden, true);
});

test('failed provider logos stay hidden until the source changes', () => {
  const p = player();
  p.context.state.playbackStatsMetadata = { providerLogo: 'https://example.test/a.png' };
  p.render();
  const logo = p.get('statProviderLogo');
  assert.equal(logo.hidden, false);
  logo.onerror();
  p.render();
  assert.equal(logo.hidden, true);
  p.context.state.playbackStatsMetadata.providerLogo = 'https://example.test/b.png';
  p.render();
  assert.equal(logo.hidden, false);
  p.context.state.playbackStatsMetadata = null;
  p.render();
  assert.equal(logo.hidden, true);
  assert.equal(logo.src, undefined);
});

test('one resize handler preserves PiP cleanup and refreshes active modal layout', () => {
  assert.equal(source.match(/window\.nuvioNativeResizeEnded =/g).length, 1);
  assert.equal(source.match(/window\.nuvioNativeViewportChanged =/g).length, 1);
  const calls = [];
  const context = {
    window: { clearTimeout: () => calls.push('clearTimer') },
    root: { classList: { remove: name => calls.push(name) } },
    document: { body: { style: { cursor: 'nwse-resize' } } },
    pipPointerDown: true, nativeViewportTimer: 1, activeModal: 'chapters',
    clearSuppressNextRootClick: () => calls.push('clearClick'),
    noteChromeActivity: () => calls.push('chrome'),
    skipPrompt: { classList: { contains: () => true } },
    syncSkipPromptPlacement: () => calls.push('skip'),
    renderActiveModal: (...args) => calls.push(args),
  };
  vm.runInNewContext(source.slice(source.indexOf('window.nuvioNativeResizeEnded ='),
    source.indexOf('const renderOpeningOverlay =')), context);
  context.window.nuvioNativeResizeEnded();
  assert.equal(context.pipPointerDown, false);
  assert.equal(context.document.body.style.cursor, '');
  assert.deepEqual(calls.slice(0, 5), ['clearTimer', 'clearClick', 'native-resizing', 'chrome', 'skip']);
  assert.deepEqual(Array.from(calls[5]), [false, true]);
});
