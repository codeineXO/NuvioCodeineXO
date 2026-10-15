# VapourSynth SVP Frame Interpolation Profile: Cinema & Live Action
# Tuned for film: algo 21, subpixel precision pel 2, 50% block overlap, blend: false to eliminate ghosting on film grain.
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
        super_clip = core.mv.Super(c, pel=2, hpad=16, vpad=16)
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
    # Ensure compatible YUV format with high-quality error diffusion to preserve film grain
    if clip.format.id != vs.YUV420P8:
        try:
            clip = core.resize.Bicubic(clip, format=vs.YUV420P8, dither_type="error_diffusion")
        except Exception:
            clip = core.resize.Bicubic(clip, format=vs.YUV420P8)

    has_svp = hasattr(core, 'svp1') and hasattr(core, 'svp2')

    if has_svp:
        try:
            super_params = "{pel: 2, scale: {up: 0}, gpu: 1}"
            blk = 32 if (clip.width >= 1920 or clip.height >= 1080) else 16
            analyse_params = (
                "{{block: {{w: {0}, overlap: 2}}, "
                "main: {{search: {{type: 4, distance: -4, sort: true}}, "
                "penalty: {{lambda: 20.0, plevel: 1.1}}}}, "
                "special: {{sad: 2000}}}}"
            ).format(blk)

            smooth_params = (
                "{{rate: {{num: {0}, den: 1, abs: true}}, "
                "algo: 21, "
                "mask: {{cover: 80}}, "
                "scene: {{mode: 1, blend: false}}}}"
            ).format(target_fps)

            super_clip = core.svp1.Super(clip, super_params)
            vectors = core.svp1.Analyse(super_clip["clip"], super_clip["data"], clip, analyse_params)
            clip = core.svp2.SmoothFps(clip, super_clip["clip"], super_clip["data"], vectors["clip"], vectors["data"], smooth_params)
        except Exception:
            clip = interpolate_mvtools(clip)
    else:
        clip = interpolate_mvtools(clip)

    clip.set_output()
