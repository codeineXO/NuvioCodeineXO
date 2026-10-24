package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.AnimeUpscalerOption
import com.nuvio.app.features.player.MediaContentType
import com.nuvio.app.features.player.SvpMotionOption
import kotlin.test.Test
import kotlin.test.assertEquals

class NativePlayerClassificationTest {
    @Test fun detectionChangesDriveTheExistingAutomaticEnhancementMatrix() {
        val controller = NativePlayerController(host = NativePlayerHost())
        for (type in listOf(MediaContentType.UNKNOWN, MediaContentType.LIVE_ACTION, MediaContentType.ANIME,
            MediaContentType.CARTOON, MediaContentType.LIVE_ACTION)) {
            controller.setVideoEnhancements(type, AnimeUpscalerOption.AUTO, AnimeUpscalerMode.entries.first(), SvpMotionOption.AUTO)
            assertEquals(type.isAnimation to !type.isAnimation, controller.compute("computeUpscalerState"))
            assertEquals(if (type.isAnimation) true to "anime" else false to "", controller.compute("computeSvpState"))
            controller.setVideoEnhancements(type, AnimeUpscalerOption.AUTO, AnimeUpscalerMode.entries.first(), SvpMotionOption.GLOBAL)
            assertEquals(true to if (type.isAnimation) "anime" else "cinema", controller.compute("computeSvpState"))
        }
    }

    @Test fun newDetectionPreservesManualOverridesAndOffSettings() {
        val controller = NativePlayerController(host = NativePlayerHost())
        val mode = AnimeUpscalerMode.entries.first()
        controller.setAnimeUpscaler(false, mode)
        controller.javaClass.getDeclaredField("overrideSvpProfile").apply { isAccessible = true }.set(controller, "cinema")
        controller.setVideoEnhancements(MediaContentType.ANIME, AnimeUpscalerOption.AUTO, mode, SvpMotionOption.AUTO)
        assertEquals(false to false, controller.compute("computeUpscalerState"))
        assertEquals(true to "cinema", controller.compute("computeSvpState"))
        val fresh = NativePlayerController(host = NativePlayerHost())
        fresh.setVideoEnhancements(MediaContentType.ANIME, AnimeUpscalerOption.OFF, mode, SvpMotionOption.OFF)
        assertEquals(false to false, fresh.compute("computeUpscalerState"))
        assertEquals(false to "", fresh.compute("computeSvpState"))
    }

    private fun NativePlayerController.compute(method: String): Any? = javaClass.getDeclaredMethod(method)
        .apply { isAccessible = true }.invoke(this)
}
