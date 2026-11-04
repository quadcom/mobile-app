package io.music_assistant.client.update

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateReleaseTest {
    private fun asset(name: String) = ReleaseAsset(name, "https://example.invalid/$name", null)

    @Test
    fun tagWithDigitsParses() {
        assertEquals(1234L, versionCodeFromTag("volmod-1234"))
    }

    @Test
    fun tagWithWrongPrefixIsRejected() {
        assertNull(versionCodeFromTag("v1234"))
        assertNull(versionCodeFromTag("1234"))
    }

    @Test
    fun tagWithNonDigitsIsRejected() {
        assertNull(versionCodeFromTag("volmod-12a4"))
        assertNull(versionCodeFromTag("volmod-"))
        assertNull(versionCodeFromTag("volmod--5"))
    }

    @Test
    fun arm64ApkIsPreferred() {
        val picked = pickApkAsset(listOf(asset("app-universal.apk"), asset("app-arm64-v8a.apk")))
        assertEquals("app-arm64-v8a.apk", picked?.name)
    }

    @Test
    fun anyApkIsTheFallback() {
        val picked = pickApkAsset(listOf(asset("notes.txt"), asset("app-universal.apk")))
        assertEquals("app-universal.apk", picked?.name)
    }

    @Test
    fun noApkGivesNull() {
        assertNull(pickApkAsset(listOf(asset("notes.txt"))))
        assertNull(pickApkAsset(emptyList()))
    }

    @Test
    fun newerReleaseIsOffered() {
        assertTrue(isNewer(11, 10, null, "volmod-11"))
    }

    @Test
    fun sameOrOlderReleaseIsNotOffered() {
        assertFalse(isNewer(10, 10, null, "volmod-10"))
        assertFalse(isNewer(9, 10, null, "volmod-9"))
    }

    @Test
    fun skippedTagIsNotOffered() {
        assertFalse(isNewer(11, 10, "volmod-11", "volmod-11"))
        assertTrue(isNewer(12, 10, "volmod-11", "volmod-12"))
    }
}
