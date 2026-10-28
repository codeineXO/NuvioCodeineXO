# Bundled Windows SVP validation

Validated locally on 2026-10-28. No application version change, commit, push,
deployment, or installer package was made. Existing/relocated legacy runtime
directories were not used or removed.

## Automated checks

- `:composeApp:compileKotlinDesktop --quiet --build-cache`: passed.
- `:composeApp:desktopTest --quiet --build-cache`: final run passed, 2,098 tests,
  2,097 passed and one skipped. Eight SVP tests cover nested installation, reuse,
  missing/corrupt/unlisted files, interrupted preparation, manifest validation,
  preservation of existing versions, native bootstrap ordering, bytecode-free
  initialization, and offline recovery after an initial failed availability check.
- A preceding full run hit an intermittent failure in the unchanged
  `PluginRuntimeDesktopTest` concurrent scraper test (QuickJS `closed` field null).
  The subsequent complete run passed. No plugin-runtime code was changed.
- `:composeApp:createReleaseDistributable --quiet --build-cache`: passed.
- Git attributes preserve byte-identical manifest assets across checkouts; `.pyd`
  files in the curated payload are explicitly exempted from Python cache ignores.

## Packaged-resource checks

Headless probes loaded the release distribution's JARs using a host JDK, from a
temporary working directory with empty APPDATA/LOCALAPPDATA and PATH restricted to
Windows System32. The packaged bridge prepared its own runtime and validated the
VSScript API on two successive launches without writing bytecode. A probe masking
python312.dll left SVP unavailable while the native bridge still initialized;
unmasking it and retrying recovered availability without restarting the process.

The payload was also extracted from the release JAR into an isolated directory.
A source-archive python.exe served only as a test harness, not a shipped file.
Both unchanged profiles converted a 320x180, 24 FPS synthetic clip to 60 FPS and
rendered frames 0, 1, 30, then 5. Plugins were discovered locally, with no bytecode
files generated. No SVP Manager process was present during the final checks.

## Measured size

- 49 manifest-listed files; 50 packaged entries including manifest.sha256.
- Uncompressed packaged resources: 25,892,745 bytes (25.89 MB).
- Compressed resource data inside the release JAR: 12,373,745 bytes (12.37 MB),
  excluding ZIP entry metadata. This is not an installer-size measurement.
- One activated runtime cache: 25,888,532 bytes (25.89 MB).
- App resource data plus one extracted cache: approximately 38.26 MB. Earlier
  cached versions and the user's legacy runtime, if retained, are additional.

## Remaining release acceptance

These checks do not substitute for testing the packaged application on a clean
Windows VM, GUI playback of real anime/live-action content, SVP toggling and
seeking, long sessions, visual artifacts/watermarks, or different GPU drivers.
Check those before release. No clean Windows VM was available in this session.

SVPFlow2 is proprietary. The upstream notices describe Windows licensing and
SVP Manager requirements; successful local rendering does not establish public
redistribution rights or remove vendor restrictions. See the bundled
`svp-runtime/PROVENANCE.md` and `svp-runtime/licenses/svp_libraries.txt` and confirm
applicable distribution rights before publishing.
