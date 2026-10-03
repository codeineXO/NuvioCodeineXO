<div align="center">

  <h1>NuvioCodeineXO Desktop</h1>
  
  <p>
    An enhanced edition of Nuvio Desktop with Anime4K shaders, a custom player UI, smoother playback, native streaming, deep subtitle styling, and independent profile storage.
  </p>

  <a href="https://github.com/codeineXO/NuvioCodeineXO/releases/latest">
    <img src="https://placehold.co/300x60/2ea44f/ffffff.png?text=DOWNLOAD" alt="DOWNLOAD" />
  </a>

  <p><sub>Click the button above → find the <b>.msi</b> file under <b>Assets</b> and download it.</sub></p>

</div>

---

## ✨ Features & Fork Highlights

Here is the breakdown of features and improvements on **NuvioCodeineXO** that are not present in official Nuvio Desktop:

### 🎬 Player & Playback
* **Anime Upscaler Shaders (Anime4K):** Integrated MPV post-processing shader pipelines (`Anime4K Fast`, `Anime4K Sharp HQ`, and `Line Recovery`) switchable in-player on the fly.
* **CodeineXO Player UI & Standalone PiP:** Alternate modern player layout with natural text shadows, faint buffer progress bar, soft-blurred panels with black tint, episode rating badges, and a custom borderless, resizable Picture-in-Picture mode.
* **Live Network & Stream Stats:** Real-time overlay showing download speed, seeders, and peers directly on the player for both P2P and HTTP streams.
* **Precision Seeking & Stutter Fixes:** 5-second default seek, `Shift` + `Arrow keys` for 1-second micro-seeking, keyframe-routed intro skips and seeking to eliminate audio desyncs / buffer stalls, and custom MPV streaming cache tuning.

### 🎨 Deep Subtitle Customization
* **Granular Subtitle Styling:** Custom hex color picker for text, outline, and background box (with adjustable box opacity).
* **Typography & Effects:** 10 curated clean fonts, outline thickness stepper, soft blur glow, and shadow effects that update live without player reload.

### ⚡ P2P Engine & Core Performance
* **NuvioEngine-Only Streaming:** Fully dropped external TorrServer dependency in favor of the native NuvioEngine backend with configurable persistent torrent caching and faster mid-file resume on Windows.
* **OpenGL Hardware Acceleration:** OpenGL Skiko renderer enabled by default on desktop for smoother 60+ FPS animations and reduced rendering overhead.

### ✨ Visuals, UX & Settings
* **Dedicated CodeineXO Settings:** Consolidated fork options into a dedicated settings page with full search indexing and mirrored playback controls.
* **CodeineXO Aura & Glass Aesthetics:** Ambient interactive background glow with dark translucent glass styling across cards and surfaces.
* **UX Enhancements:** Drag-to-scroll on season/filter tabs, hero ratings with fallback support, sidebar exit button, copy addon manifest link shortcut, and fullscreen state preservation when navigating back from the player.
* **Enhanced Discord Rich Presence:** Richer RPC displaying movie/series posters, episode thumbnails, and granular presence status (Active Now card, "Choosing Stream", and toggle to hide idle browsing).

### 🪟 Windows Desktop Isolation & Installer
* **Isolated Environment:** Runs entirely out of dedicated `NuvioCodeineXO` data directories without conflicting with or overwriting official Nuvio settings or shortcuts.
* **Custom Installer & In-App Updater:** Dedicated Windows MSI installer with 3D icon, an interactive uninstall prompt asking whether to retain or wipe data, and a silent background updater tracking the fork’s GitHub releases.

---

## ⚖️ Disclaimer

Nuvio is a client-side media browser and player for user-provided sources and extensions. It does not host, store, or distribute any media content.
