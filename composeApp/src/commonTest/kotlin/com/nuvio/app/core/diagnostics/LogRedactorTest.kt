package com.nuvio.app.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogRedactorTest {
    @Test
    fun masksShortUserIdsInAddonPaths() {
        val out = LogRedactor.redact(
            "GET https://nuvio.gglab.cyou/usr_93bdff379f561914/subtitles/series/tt12343534%3A1%3A9/x.json " +
                "and https://cncverse.dpdns.org/u/p_2bwcebc6l9w9/subtitles/series/tt1.json",
        )
        assertFalse("93bdff379f561914" in out, out)
        assertFalse("2bwcebc6l9w9" in out, out)
        assertTrue("/subtitles/series/tt12343534%3A1%3A9/" in out, out)
    }

    @Test
    fun keepsReadableEncodedFolderNames() {
        val url = "http://172.16.50.10/DHAKA-FLIX-10/Anime%20%26%20Cartoon%20TV%20Series/Jujutsu%20Kaisen%20%28TV%20Series%29/" +
            "Season%201/%5BMsM%5D%20Jujutsu%20Kaisen%20S01E09.mkv"
        assertEquals(url, LogRedactor.redact(url))
    }

    @Test
    fun keepsMediaIdsAndMasksConfigBlobs() {
        val out = LogRedactor.redact(
            "https://api.seekr.tv/series/95479/s01e09/1435000/thumbnails.vtt?sig=abcdef123 " +
                "https://pengu.uk/eyJhcGlLZXkiOiJhYmMxMjMiLCJkZWJyaWQiOiJ4eXo5ODcifQ/meta/series/tt0121220.json",
        )
        assertTrue("/series/95479/s01e09/1435000/" in out, out)
        assertTrue("sig=***" in out, out)
        assertFalse("eyJhcGlLZXki" in out, out)
        assertTrue("/meta/series/tt0121220.json" in out, out)
    }

    @Test
    fun masksClientIdQueryParam() {
        val out = LogRedactor.redact(
            "GET https://api.simkl.com/search/id?imdb=tt0445883&client_id=dc20e0db975583b15096267cee79cd23&app-name=nuvio",
        )
        assertTrue("client_id=***&app-name=nuvio" in out, out)
        assertTrue("imdb=tt0445883" in out, out)
    }
}
