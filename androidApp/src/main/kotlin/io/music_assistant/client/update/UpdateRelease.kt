package io.music_assistant.client.update

private const val TAG_PREFIX = "volmod-"
private const val PREFERRED_ABI = "arm64-v8a"
private const val APK_SUFFIX = ".apk"

data class ReleaseAsset(val name: String, val url: String, val size: Long?)

data class UpdateRelease(
    val tag: String,
    val name: String,
    val versionCode: Long,
    val notes: String,
    val asset: ReleaseAsset,
)

/** Release tags carry the build's versionCode, so no version string has to be compared. */
fun versionCodeFromTag(tag: String): Long? {
    if (!tag.startsWith(TAG_PREFIX)) return null
    val digits = tag.removePrefix(TAG_PREFIX)
    if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
    return digits.toLongOrNull()
}

fun pickApkAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
    val apks = assets.filter { it.name.endsWith(APK_SUFFIX, ignoreCase = true) }
    return apks.firstOrNull { it.name.contains(PREFERRED_ABI) } ?: apks.firstOrNull()
}

fun isNewer(releaseCode: Long, installedCode: Long, skippedTag: String?, tag: String): Boolean =
    releaseCode > installedCode && tag != skippedTag
