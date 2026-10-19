#pragma once
#include <cstdint>
#include <initializer_list>

struct MpvCacheWindow {
    int64_t maxBytes;
    int64_t maxBackBytes;
    double seconds;
};

constexpr MpvCacheWindow playbackBufferWindow(int presetId) {
    constexpr int64_t mib = 1024ll * 1024;
    switch (presetId) {
        case 1: return {512 * mib, 128 * mib, 180.0};
        case 2: return {1024 * mib, 256 * mib, 300.0};
        default: return {256 * mib, 64 * mib, 120.0};
    }
}

constexpr MpvCacheWindow kStreamingCacheWindow = playbackBufferWindow(0);
constexpr MpvCacheWindow kResumeCacheWindow{64ll * 1024 * 1024, 16ll * 1024 * 1024, 30.0};

// Resume preparation takes priority; SVP only overrides the normal forward limit.
constexpr MpvCacheWindow effectivePlaybackBuffer(MpvCacheWindow selected, bool resumePending, bool svpActive) {
    return resumePending ? kResumeCacheWindow : MpvCacheWindow{
        svpActive ? 1024ll * 1024 * 1024 : selected.maxBytes,
        selected.maxBackBytes,
        selected.seconds,
    };
}

// Compile-time policy checks run whenever the Windows bridge is built.
static_assert(playbackBufferWindow(-1).maxBytes == playbackBufferWindow(0).maxBytes);
static_assert(playbackBufferWindow(0).maxBytes == 268435456 && playbackBufferWindow(0).maxBackBytes == 67108864 && playbackBufferWindow(0).seconds == 120);
static_assert(playbackBufferWindow(1).maxBytes == 536870912 && playbackBufferWindow(1).maxBackBytes == 134217728 && playbackBufferWindow(1).seconds == 180);
static_assert(playbackBufferWindow(2).maxBytes == 1073741824 && playbackBufferWindow(2).maxBackBytes == 268435456 && playbackBufferWindow(2).seconds == 300);
constexpr bool playbackBufferTransitionsAreValid() {
    for (int preset = 0; preset < 3; ++preset) {
        const auto selected = playbackBufferWindow(preset);
        for (bool svp : {false, true}) {
            const auto resume = effectivePlaybackBuffer(selected, true, svp);
            if (resume.maxBytes != kResumeCacheWindow.maxBytes || resume.maxBackBytes != kResumeCacheWindow.maxBackBytes || resume.seconds != 30) return false;
            const auto promoted = effectivePlaybackBuffer(selected, false, svp);
            if (promoted.maxBytes != (svp ? 1073741824 : selected.maxBytes) || promoted.maxBackBytes != selected.maxBackBytes || promoted.seconds != selected.seconds) return false;
        }
        const auto restored = effectivePlaybackBuffer(selected, false, false);
        if (restored.maxBytes != selected.maxBytes || restored.maxBackBytes != selected.maxBackBytes || restored.seconds != selected.seconds) return false;
    }
    return true;
}
static_assert(playbackBufferTransitionsAreValid(), "Preset updates, resume promotion and SVP restoration must preserve cache policy");
