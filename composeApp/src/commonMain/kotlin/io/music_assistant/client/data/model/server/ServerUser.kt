package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerUser(
    @SerialName("preferences") val preferences: ServerUserPreferences? = null,
)

@Serializable
data class ServerUserPreferences(
    @SerialName("sidebar.shortcuts") val shortcuts: List<String>? = null,
    // Chapter-based progress/navigation for audiobooks & podcasts; the web
    // frontend owns the settings toggle. Absent means the default (true).
    @SerialName("audiobook_chapter_progress") val audiobookChapterProgress: Boolean? = null,
    // Volume slider behaviour; the web frontend owns the settings. Absent means the defaults
    // (absolute slider, haptics on).
    @SerialName("volume_slider_mode") val volumeSliderMode: String? = null,
    @SerialName("volume_haptics") val volumeHaptics: Boolean? = null,
) {
    /** Resolved chapter gate; an absent field means the server default. */
    val chapterProgressEnabled: Boolean get() = audiobookChapterProgress != false

    /** Relative volume drag instead of absolute; any value but "relative" keeps the default. */
    val relativeVolume: Boolean get() = volumeSliderMode == "relative"

    /** Resolved haptics gate; an absent field means the default (on). */
    val volumeHapticsEnabled: Boolean get() = volumeHaptics != false
}

/** The public face of a user (`music_assistant_models.auth.UserSummary`), server 2.11. */
@Serializable
data class ServerUserSummary(
    @SerialName("user_id") val userId: String,
    @SerialName("username") val username: String,
    @SerialName("display_name") val displayName: String? = null,
) {
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}
