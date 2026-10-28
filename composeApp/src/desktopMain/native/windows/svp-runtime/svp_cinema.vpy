# VapourSynth SVP Frame Interpolation Profile: Cinema & Live Action
# Real-time SVPFlow 60 FPS motion interpolation tuned for film / live action

import vapoursynth as vs
core = vs.core

clip = video_in

src_fps = 23.976
if 'container_fps' in globals() and isinstance(container_fps, (int, float)) and container_fps > 0.1:
    src_fps = float(container_fps)
elif clip.fps_num > 0 and clip.fps_den > 0:
    src_fps = float(clip.fps_num) / float(clip.fps_den)

if abs(src_fps - 23.810) < 0.01:
    src_fps = 23.976

target_fps = 60

if src_fps >= 55.0:
    clip.set_output()
else:
    if clip.format.id != vs.YUV420P8:
        try:
            clip = core.resize.Bicubic(clip, format=vs.YUV420P8, dither_type="ordered")
        except Exception:
            clip = core.resize.Bicubic(clip, format=vs.YUV420P8)

    w = clip.width
    h = clip.height
    block_size = 32 if (w >= 1920 or h >= 1080) else 16

    super_p = "{pel:1,scale:{up:2},gpu:1,full:false,rc:false}"
    analyse_p = "{block:{w:" + str(block_size) + ",overlap:2},main:{search:{coarse:{distance:-10,bad:{sad:1800}},type:2,distance:-10}},refine:[{thsad:250}]}"
    smooth_p = "{rate:{num:" + str(target_fps) + ",den:1,abs:true},algo:23,mask:{area:100,cover:80},scene:{mode:3,blend:true,limits:{m1:1200,m2:2400,scene:3600,zero:100,blocks:35}},block:false}"

    try:
        super_clip = core.svp1.Super(clip, super_p)
        vectors = core.svp1.Analyse(super_clip["clip"], super_clip["data"], clip, analyse_p)
        smooth = core.svp2.SmoothFps(clip, super_clip["clip"], super_clip["data"], vectors["clip"], vectors["data"], smooth_p, src=clip, fps=src_fps)
        smooth = core.std.AssumeFPS(smooth, fpsnum=target_fps, fpsden=1)
        smooth.set_output()
    except Exception as e:
        try:
            import os
            log_path = os.path.expandvars(r"%APPDATA%\NuvioCodeineXO\svp.log")
            with open(log_path, "a", encoding="utf-8") as f:
                f.write(f"CINEMA INTERPOLATION ERROR: {e}\n")
        except Exception:
            pass
        clip.set_output()
