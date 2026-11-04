package io.music_assistant.client.utils

/** Lets shared UI offer a manual update check; only bound on platforms that can self-update. */
interface AppUpdateCheck {
    val available: Boolean

    fun checkNow()
}
