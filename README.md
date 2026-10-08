<div align="center">

  <h1>NuvioCodeineXO Desktop</h1>
  
  <p>
    An enhanced edition of Nuvio Desktop with Anime4K shaders, seek previews, deep subtitle styling, minimal player UI, better discord rpc, UI optimizations, faster and smoother playback.
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
* **Seek Previews (Seekr.tv):** Netflix-style 5-frame animated seek preview carousel on hover and scrub across Compose and Desktop MPV WebView2 chrome, powered by the Seekr API with WebVTT cue parsing and bounded sprite sheet caching.
* **CodeineXO Player UI & Quick Drawer:** Alternate modern player layout with natural text shadows, faint buffer progress bar, sliding quick drawer, soft-blurred panels with black tint, and episode rating badges.
* **Streamlined In-Player Controls:** Right-click to cycle audio/subtitle tracks instantly, toggle between elapsed and remaining time, "show seekbar while seeking" toggle, rounded track styling, and refined Picture-in-Picture (PiP) controls.
* **Live Network & Stream Stats:** Real-time overlay showing download speed, seeders, and peers directly on the player for both P2P and HTTP streams.
* **Audio Night Mode (Dynamic Range Compression):** Real-time loudness normalization filter accessible in the player audio panel and settings to boost soft dialogue while reducing loud dynamic peaks.
* **Precision Seeking & Stutter Fixes:** Smooth precision seeking, `Shift` + `Arrow keys` for 1-second micro-seeking, keyframe-routed intro skips to prevent audio desyncs, and optimized streaming buffer cache.

### 🎨 Subtitle Customizations
* **Granular Subtitle Styling:** Custom hex color picker for text, outline, and background box (with adjustable box opacity).
* **Typography & Effects:** 10 curated clean fonts, outline thickness stepper, soft blur glow, and shadow effects that update live without player reload.

### ⚡ P2P Engine & Core Performance
* **NuvioEngine-Only Streaming:** Fully dropped external TorrServer dependency in favor of native NuvioEngine backend (v0.1.4) with configurable persistent torrent caching, optimized HTTP stream buffering, audio channel routing fixes, and fast mid-file resume.

### ✨ Visuals, UX & Settings
* **Dedicated CodeineXO Settings:** Consolidated fork options into a dedicated settings page with full search indexing, Seekr API key integration, and mirrored playback controls.
* **CodeineXO Aura & Glass Aesthetics:** Ambient interactive background glow with dark translucent glass styling across cards and surfaces.
* **UX Enhancements:** Scroll navigation arrow buttons and drag-to-scroll on season/episode carousels, hero ratings with fallback support, sidebar exit button, copy addon manifest link shortcut, etc.
* **Enhanced Discord Rich Presence:** Richer Discord RPC displaying movie/series posters, episode thumbnails, and granular presence status.

### 🪟 Windows Desktop Isolation & Installer
* **Isolated Environment:** Runs entirely out of dedicated `NuvioCodeineXO` data directories without conflicting with or overwriting official Nuvio settings or shortcuts.
* **Custom Installer & In-App Updater:** Dedicated Windows MSI installer, an interactive uninstall prompt asking whether to retain or wipe data, and a silent background updater tracking the fork’s GitHub releases.

---

## ⚖️ Disclaimer

Nuvio is a client-side media browser and player for user-provided sources and extensions. It does not host, store, or distribute any media content.
