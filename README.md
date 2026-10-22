<div align="center">

  <h1>NuvioCodeineXO Desktop</h1>
  
  <p>
    An enhanced edition of Nuvio Desktop with Anime4K shaders, 60 FPS smooth motion interpolation, anime filler markers, seek preview, deep subtitle styling, TV Style keyboard navigation, watch party feature, minimal player UI, better discord rpc, UI optimizations, better navigation layout, faster and smoother playback .
  </p>

  <a href="https://github.com/codeineXO/NuvioCodeineXO/releases/latest">
    <img src="https://placehold.co/300x60/2ea44f/ffffff.png?text=DOWNLOAD" alt="DOWNLOAD" />
  </a>

  <p><sub>Click the button above → find the <b>.exe</b> file under <b>Assets</b> and download it.</sub></p>

</div>

---

## ✨ Features & Fork Highlights

Here is the breakdown of features and improvements on **NuvioCodeineXO** that are not present in official Nuvio Desktop:

### 🌸 Anime Features
* **Anime Upscaler Shaders (Anime4K):** Integrated MPV post-processing shader pipelines (`Anime4K Fast`, `Anime4K Sharp HQ`, and `Line Recovery`) switchable in-player on the fly.
* **Anime Filler Markers:** Automatic anime filler detection that highlights episodes with Mixed Canon and Anime Filler badges across episode lists, quick drawers, and player overlays. Option to hide and skip pure filler episodes is included. 

### 🎬 Player & Playback
* **Motion Smoothing (Frame Interpolation):** Converts videos into fluid high-framerate playback (60fps+) in real time. Automatically applies tailored smoothness for anime versus live-action movies and TV shows, switchable on the fly (`Off`, `Anime Only`, `All Media`) from the player Enhancements menu.
* **Seek Previews (Seekr.tv):** Netflix-style 5-frame animated seek preview carousel on hover and scrub across Compose and Desktop MPV WebView2 chrome, powered by the Seekr API with WebVTT cue parsing and bounded sprite sheet caching.
* **In Player Playback Statistics Overlay:** View Stats for Nerds in player with all kinds of statistics.
* **In-App Floating Mini Player:** Draggable in-app picture-in-picture overlay that lets you navigate menus and browse catalogs without interrupting playback, featuring instant play/pause, progress tracking, and one-click expand.
* **CodeineXO Player UI & Quick Drawer:** Alternate modern player layout with natural text shadows, faint buffer progress bar, sliding quick drawer, soft-blurred panels with black tint, and episode rating badges.
* **Streamlined In-Player Controls:** Right-click to cycle audio/subtitle tracks instantly, toggle between elapsed and remaining time, "show seekbar while seeking" toggle, rounded track styling, and refined Picture-in-Picture (PiP) controls.
* **Live Network & Stream Stats:** Real-time overlay showing download speed, seeders, and peers directly on the player for both P2P and HTTP streams.
* **Audio Night Mode (Dynamic Range Compression):** Real-time loudness normalization filter accessible in the player audio panel and settings to boost soft dialogue while reducing loud dynamic peaks.
* **Precision Seeking & Stutter Fixes:** Smooth precision seeking, 10-second seek, 3-second seek and  1-second micro-seeking and optimized streaming buffer cache.

### 🎨 Subtitle Customizations
* **Granular Subtitle Styling:** Custom hex color picker for text, outline, and background box (with adjustable box opacity).
* **Typography & Outline Styles:** 10 curated clean fonts, outline thickness stepper, and selectable outline effects (`Classic Outline`, `Glow`, `Halo`, `Background Box`, and `None`) that update live without player reload.

### ✨ Visuals, UX & Settings
* **CodeineXO Aura & Glass Aesthetics:** Ambient interactive background glow with dark translucent glass styling across cards and surfaces.
* **Keyboard Navigation:** Use keyboard arrow keys, enter, backspace and escape for navigating posters, settings, pages etc. 
* **Scroll Posters Layout:** Dedicated home screen browsing option enabling direct mouse-wheel scrolling over media rows and carousels for effortless navigation.
* **Dedicated CodeineXO Settings:** Consolidated fork options into a dedicated settings page with full search indexing, Seekr API key integration, and mirrored playback controls.
* **Sharp Vector Badges & Ratings:** Crisp vector SVG rating badges across catalog cards, hero headers, and episode rows for high-DPI clarity.
* **Catalog Navigation & Polish:** Drag-to-scroll and directional arrow buttons on carousels, sidebar quick exit button, and copy addon manifest link shortcuts.
* **Enhanced Discord Rich Presence:** Richer Discord RPC with customizable activity types ("Watching" vs. "Playing"), uncropped full portrait posters, small badge display for episode thumbnails, active mini-player tracking, and interactive artwork linking directly to IMDb.
* **Addon & Plugin Management:** One-click bulk refresh for addons and plugins, plus master switches to enable or disable entire plugin sources.

### ⚡ P2P Engine & Core Performance
* **NuvioEngine-Only Streaming:** Fully dropped external TorrServer dependency in favor of native NuvioEngine backend (v0.1.5) with configurable persistent torrent caching, optimized HTTP stream buffering, audio channel routing fixes, and fast mid-file resume.

### 🪟 Windows Desktop Isolation & Installer
* **Isolated Environment:** Runs entirely out of dedicated `NuvioCodeineXO` data directories without conflicting with or overwriting official Nuvio settings or shortcuts.
* **Custom Installer & In-App Updater:** Dedicated Windows .exe installer, an interactive uninstall prompt asking whether to retain or wipe data, and an updater tracking the fork’s GitHub latest releases.

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
| :--- | :--- |
| `Space` / `K` | Play / Pause *(hold Space to boost speed)* |
| `F` / `F11` | Toggle Fullscreen |
| `←` / `→` | Seek backward / forward (3s) |
| `J` / `L` | Seek backward / forward (10s) |
| `Shift` + `←` / `→` or `J` / `L` | 1-second micro-seek |
| `↑` / `↓` | Volume up / down |
| `M` | Mute / Unmute audio |
| `P` | Toggle Picture-in-Picture |
| `B` | Quick cycle audio track |
| `V` | Toggle subtitles on / off |
| `G` / `H` | Adjust subtitle delay (-100ms / +100ms) |
| `Shift` + `<` / `>` | Adjust playback speed (`/` to reset to 1x) |
| `Shift` + `N` | Play next episode |
| `E` | Open episode selector drawer |
| `Q` | Open stream sources drawer |
| `A` / `S` | Open audio / subtitle selection panels |
| `Right-Click` on Audio / Subtitle button | Instantly cycle through audio or subtitle tracks without opening menus |
| `I`  | Show / Hide Playback Stats for Nerds |

---

## ⚖️ Disclaimer

Nuvio is a client-side media browser and player for user-provided sources and extensions. It does not host, store, or distribute any media content.
