package io.music_assistant.client.update

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import co.touchlab.kermit.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import io.music_assistant.client.R
import io.music_assistant.client.utils.AppUpdateCheck
import io.music_assistant.client.utils.HttpClientFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private const val CHECK_COOLDOWN_MS = 15 * 60 * 1000L
private const val PREFS = "self_update"
private const val KEY_LAST_CHECK = "last_check"
private const val KEY_SKIPPED_TAG = "skipped_tag"
private const val APK_FILE = "update.apk"
private const val COPY_BUFFER_BYTES = 64 * 1024
private const val CANT_REPLACE =
    "This build can't replace the installed app. Install it once by hand, then updates will work again."
private const val GENERIC_FAILURE = "The update could not be installed. Try again in a moment."

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: UpdateRelease) : UpdateState
    data class Downloading(val release: UpdateRelease, val progress: Float?) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Checks GitHub for a newer build of this app and installs it through PackageInstaller. */
class AppUpdater(
    private val context: Context,
    private val httpClientFactory: HttpClientFactory,
) : AppUpdateCheck {
    private val log = Logger.withTag("AppUpdater")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    // Created on first use so a disabled build never builds a client.
    private val client by lazy { httpClientFactory.create() }

    val enabled: Boolean = context.resources.getBoolean(R.bool.self_update_enabled)
    override val available: Boolean get() = enabled

    fun checkOnStart() {
        if (!enabled || job?.isActive == true || _state.value !is UpdateState.Idle) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < CHECK_COOLDOWN_MS) return
        check(manual = false)
    }

    override fun checkNow() {
        if (!enabled || job?.isActive == true) return
        check(manual = true)
    }

    fun skip(release: UpdateRelease) {
        prefs.edit().putString(KEY_SKIPPED_TAG, release.tag).apply()
        _state.value = UpdateState.Idle
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing) {
            _state.value = UpdateState.Idle
        }
    }

    private fun check(manual: Boolean) {
        _state.value = if (manual) UpdateState.Checking else UpdateState.Idle
        job = scope.launch {
            try {
                val release = fetchLatest()
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                // A manual check ignores the skipped build, so "Check for updates" always tells the truth.
                val skipped = if (manual) null else prefs.getString(KEY_SKIPPED_TAG, null)
                _state.value = when {
                    release != null && isNewer(release.versionCode, installedCode(), skipped, release.tag) ->
                        UpdateState.Available(release)
                    manual -> UpdateState.UpToDate
                    else -> UpdateState.Idle
                }
            } catch (e: Exception) {
                log.w(e) { "Update check failed" }
                _state.value = if (manual) {
                    UpdateState.Failed("Could not check for updates. Check your connection and try again.")
                } else {
                    UpdateState.Idle
                }
            }
        }
    }

    private fun installedCode(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    private suspend fun fetchLatest(): UpdateRelease? {
        val repo = context.getString(R.string.self_update_repo)
        val response = client.get("https://api.github.com/repos/$repo/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }
        val json = JSONObject(response.bodyAsText())
        val tag = json.optString("tag_name")
        val code = versionCodeFromTag(tag) ?: return null
        val array = json.optJSONArray("assets")
        val assets = (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val a = array?.optJSONObject(i) ?: return@mapNotNull null
            ReleaseAsset(
                name = a.optString("name"),
                url = a.optString("browser_download_url"),
                size = a.optLong("size", -1L).takeIf { it >= 0 },
            )
        }
        val asset = pickApkAsset(assets) ?: return null
        return UpdateRelease(tag, json.optString("name").ifBlank { tag }, code, json.optString("body"), asset)
    }

    fun install(activity: Activity) {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.Failed(
                "Allow this app to install updates on the screen that just opened, then tap Install again.",
            )
            openInstallSettings(activity)
            return
        }
        job = scope.launch {
            try {
                val apk = download(release)
                _state.value = UpdateState.Installing
                commit(apk)
            } catch (e: Exception) {
                log.e(e) { "Update install failed" }
                _state.value = UpdateState.Failed(GENERIC_FAILURE)
            }
        }
    }

    private fun openInstallSettings(activity: Activity) {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Some builds leave out the per-app page; the next intent is the fallback.
            }
        }
    }

    private suspend fun download(release: UpdateRelease): File {
        val file = File(context.cacheDir, APK_FILE)
        _state.value = UpdateState.Downloading(release, null)
        client.prepareGet(release.asset.url).execute { response ->
            val total = (response.contentLength() ?: release.asset.size)?.takeIf { it > 0 }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var done = 0L
            file.outputStream().use { out ->
                while (true) {
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    _state.value = UpdateState.Downloading(release, total?.let { done.toFloat() / it })
                }
            }
        }
        return file
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Skips the confirmation when this app installed itself; otherwise the system asks anyway.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite(APK_FILE, 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            // The installer fills in extras, so the intent must be mutable on Android 12+.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val statusIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java),
                flags,
            )
            session.commit(statusIntent.intentSender)
        }
    }

    fun onInstallStatus(status: Int, message: String?) {
        if (status == PackageInstaller.STATUS_SUCCESS) return
        log.w { "Install ended with status $status: $message" }
        _state.value = UpdateState.Failed(
            when (status) {
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                -> CANT_REPLACE
                PackageInstaller.STATUS_FAILURE_ABORTED -> "The update was cancelled. Tap Install to try again."
                else -> GENERIC_FAILURE
            },
        )
    }
}
