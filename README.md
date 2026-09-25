<div align="center">

  <img src="composeApp/src/commonMain/composeResources/drawable/app_logo_wordmark.png" alt="Nuvio" width="300" />
  <br />
  <br />

  <h1>Nuvio Desktop — CodeineXO Edition</h1>

  <p>
    An enhanced edition of Nuvio Desktop with a custom player UI, smoother playback, native streaming, deep subtitle styling, and independent profile storage.
  </p>

  <p>
    <a href="https://github.com/codeineXO/NuvioCodeineXO/releases/latest">
      <img src="https://img.shields.io/github/v/release/codeineXO/NuvioCodeineXO?style=for-the-badge&logo=github&color=blue" alt="Latest Release" />
    </a>
    <a href="https://github.com/codeineXO/NuvioCodeineXO/tree/NuvioCodeineXO">
      <img src="https://img.shields.io/badge/Branch-NuvioCodeineXO-green.svg?style=for-the-badge&logo=git" alt="Branch" />
    </a>
    <a href="https://github.com/codeineXO/NuvioCodeineXO/blob/NuvioCodeineXO/LICENSE">
      <img src="https://img.shields.io/badge/License-GPL--3.0-orange.svg?style=for-the-badge" alt="License" />
    </a>
  </p>

</div>

---

## ✨ What's Changed From Dev

### 🌟 New Features

- **🎬 CodeineXO Player UI:**
  - **Dedicated UI Layout:** Brand-new default player layout designed for clean, unobstructed viewing, with the original **Official UI** kept 100% intact and selectable anytime in **Settings > Playback > Player UI**.
  - **Clean & Centered Top Bar:** Centered media title and episode name; removed top-right fullscreen and PiP clutter.
  - **Streamlined Left Controls:** Play/Pause, Next Episode, and Volume slider grouped together without redundant seek buttons.
  - **Logical Control Order:** Right-side controls grouped logically: `Episode` → `Source` → `Subtitles` → `Audio` → `Speed` → `PiP` → `Fit` → `Fullscreen`.
  - **Buffer Progress Bar:** Faint white fill on the seekbar track displays buffered stream progress in real time.
  - **Flanked Timestamps:** Current playback position on the left and total duration on the right directly beside the seekbar.
  - **Dynamic Theme Accent:** Active seekbar progress fill automatically matches your selected app theme color.
  - **Lowered Spacing:** Positioned lower with an 8px bottom clearance to prevent video and subtitle obstruction.

- **🎨 Deep Subtitle Customization:**
  - **Custom Color Picker:** Pick exact colors for text, border outlines, and background boxes using full color pickers.
  - **10 Clean Fonts:** Switch between Arial, Segoe UI, Trebuchet MS, Impact, Georgia, and more.
  - **Outline & Shadow Effects:** Choose between classic borders, drop shadows, soft glow halos, or background boxes with an adjustable thickness slider.
  - **Live Preview:** Subtitle styling updates instantly while the video plays without needing a restart.

- **🚀 Faster P2P Streaming Engine:**
  - Built-in NuvioEngine backend integrated alongside TorrServer for direct peer-to-peer torrent and debrid streaming.

- **🎮 Richer Discord RPC & Active Now Card:**
  - Displays movie or show posters and episode thumbnails directly on Discord, complete with live browsing status ("Choosing Stream") and customizable privacy modes.
  - Full support for Discord's "Active Now" game card sub-panel with elapsed session timers and high-res artwork.


### ⚡ Improvements & Optimizations

- **⚡ Faster Streaming by Default:** Initial setup defaults to the "Fast" streaming profile for faster buffering and less wait time.
- **⏩ Stutter-Free Seeking & Skip Intro:** Fixed timeline scrubbing glitches and routed large seeks (>3s) plus intro/outro skips through keyframe seeking to eliminate buffering stalls, audio desyncs, and frame snaps.
- **📁 Safe & Isolated Profile:** Runs out of its own dedicated `NuvioCodeineXO` data folder so your settings, cache, and watch progress never clash with the original Nuvio app.
- **🧹 Clean Windows Uninstaller:** Interactive uninstaller prompt allows you to choose whether to delete or preserve personal app data, settings, and cache upon removal.
- **🔄 Dedicated In-App Updater:** Built-in updater configured directly for `codeineXO/NuvioCodeineXO` repository releases for seamless desktop updates.

---

## 📦 Download & Installation

Get the latest version from the **[Releases Page](https://github.com/codeineXO/NuvioCodeineXO/releases/latest)**:

| Format | Description | Link |
| :--- | :--- | :--- |
| **Windows Installer (.msi)** | Recommended. Standalone installer with bundled runtime, shortcuts, and easy upgrades. | **[Download](https://github.com/codeineXO/NuvioCodeineXO/releases/latest)** |

---

## ⚖️ Disclaimer

Nuvio is a client-side media browser and player for user-provided sources and extensions. It does not host, store, or distribute any media content.
