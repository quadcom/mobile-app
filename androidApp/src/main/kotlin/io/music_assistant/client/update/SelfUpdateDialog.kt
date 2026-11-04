@file:Suppress("MagicNumber") // UI sizes only.

package io.music_assistant.client.update

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.music_assistant.client.R

@Composable
fun SelfUpdateDialog(updater: AppUpdater, activity: Activity) {
    val state by updater.state.collectAsState()
    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text(stringResource(R.string.self_update_title)) },
            text = {
                Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    Text(s.release.name)
                    if (s.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.release.notes)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { updater.install(activity) }) {
                    Text(stringResource(R.string.self_update_install))
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.self_update_later)) }
                    TextButton(onClick = { updater.skip(s.release) }) {
                        Text(stringResource(R.string.self_update_skip))
                    }
                }
            },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.self_update_downloading)) },
            text = {
                val p = s.progress
                if (p != null) {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = updater::dismiss,
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.self_update_close)) }
            },
        )
        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = updater::dismiss,
            text = { Text(stringResource(R.string.self_update_up_to_date)) },
            confirmButton = {
                TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.self_update_ok)) }
            },
        )
        UpdateState.Checking, UpdateState.Installing, UpdateState.Idle -> Unit
    }
}
