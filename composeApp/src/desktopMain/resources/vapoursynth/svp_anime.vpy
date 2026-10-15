# VapourSynth SVP Frame Interpolation Profile: Anime & Animation
# Tuned for 2D animation: algo 13, dynamic block sizing, scene change protection, YUV420P8 ordered dithering.
# Includes automatic fallback to MVTools if SVPflow plugins are unavailable.

import vapoursynth as vs
core = vs.core

# Input clip from mpv
clip = video_in

target_fps = 60

# Check current FPS; bypass if already high frame rate
current_fps = 0.0
if clip.fps_num > 0 and clip.fps_den > 0:
    current_fps = float(clip.fps_num) / float(clip.fps_den)

def interpolate_mvtools(c):
    try:
        if not hasattr(core, 'mv'):
            return c
        super_clip = core.mv.Super(c, pel=1, hpad=16, vpad=16)
        blk = 32 if (c.width >= 1920 or c.height >= 1080) else 16
        ovl = blk // 2
        bwd = core.mv.Analyse(super_clip, isb=True, blksize=blk, overlap=ovl, search=3)
        fwd = core.mv.Analyse(super_clip, isb=False, blksize=blk, overlap=ovl, search=3)
        return core.mv.FlowFPS(c, super_clip, mvbw=bwd, mvfw=fwd, num=target_fps, den=1)
    except Exception:
        return c

if current_fps >= 55.0:
    clip.set_output()
else:
    # Ensure 8-bit YUV420 with ordered dithering for clean anime line rendering and optimal SVP performance
    if clip.format.id != vs.YUV420P8:
        try:
            clip = core.resize.Bicubic(clip, format=vs.YUV420P8, dither_type="ordered")
        except Exception:
            clip = core.resize.Bicubic(clip, format=vs.YUV420P8)

    has_svp = hasattr(core, 'svp1') and hasattr(core, 'svp2')

    if has_svp:
        try:
            # Dynamic block sizing based on resolution
            w = clip.width
            h = clip.height
            if w >= 3840 or h >= 2160:
                block_size = 32
            elif w >= 1920 or h >= 1080:
                block_size = 32
            else:
                block_size = 16

            super_params = "{scale: {up: 0}, gpu: 1}"
            analyse_params = (
                "{{block: {{w: {0}, overlap: 2}}, "
                "main: {{search: {{type: 4, distance: -4, sort: true}}, "
                "penalty: {{lambda: 40.0, plevel: 1.3}}}}, "
                "special: {{sad: 2000}}}}"
            ).format(block_size)

            smooth_params = (
                "{{rate: {{num: {0}, den: 1, abs: true}}, "
                "algo: 13, "
                "mask: {{cover: 80}}, "
                "scene: {{mode: 1, blend: false, limits: {{m1: 1800, m2: 2400, scene: 4000}}}}}}"
            ).format(target_fps)

            super_clip = core.svp1.Super(clip, super_params)
            vectors = core.svp1.Analyse(super_clip["clip"], super_clip["data"], clip, analyse_params)
            clip = core.svp2.SmoothFps(clip, super_clip["clip"], super_clip["data"], vectors["clip"], vectors["data"], smooth_params)
        except Exception:
            clip = interpolate_mvtools(clip)
    else:
        clip = interpolate_mvtools(clip)

    clip.set_output()
