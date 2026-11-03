package io.music_assistant.client.ui.compose.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.location.HaFailure
import io.music_assistant.client.data.location.HaStatus
import io.music_assistant.client.ui.compose.settings.SectionCard
import io.music_assistant.client.ui.compose.settings.SectionTitle
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_ha_address
import musicassistantclient.composeapp.generated.resources.settings_ha_failed_address
import musicassistantclient.composeapp.generated.resources.settings_ha_failed_answer
import musicassistantclient.composeapp.generated.resources.settings_ha_failed_auth
import musicassistantclient.composeapp.generated.resources.settings_ha_failed_timeout
import musicassistantclient.composeapp.generated.resources.settings_ha_failed_unreachable
import musicassistantclient.composeapp.generated.resources.settings_ha_help
import musicassistantclient.composeapp.generated.resources.settings_ha_save
import musicassistantclient.composeapp.generated.resources.settings_ha_status_connected
import musicassistantclient.composeapp.generated.resources.settings_ha_title
import musicassistantclient.composeapp.generated.resources.settings_ha_token
import org.jetbrains.compose.resources.stringResource

/** Lets the speaker list group rooms by floor, using a Home Assistant address and access token. */
@Composable
fun HomeAssistantSection(
    savedAddress: String,
    savedToken: String,
    status: HaStatus,
    onSave: (address: String, token: String) -> Unit,
) {
    var address by remember(savedAddress) { mutableStateOf(savedAddress) }
    var token by remember(savedToken) { mutableStateOf(savedToken) }

    SectionCard {
        SectionTitle(stringResource(Res.string.settings_ha_title))
        Text(
            modifier = Modifier.padding(bottom = 12.dp),
            text = stringResource(Res.string.settings_ha_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(Res.string.settings_ha_address)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(Res.string.settings_ha_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        val statusText = when (status) {
            HaStatus.Idle, HaStatus.Working -> null
            is HaStatus.Connected -> stringResource(Res.string.settings_ha_status_connected, status.speakersPlaced)
            is HaStatus.Failed -> stringResource(
                when (status.reason) {
                    HaFailure.BAD_ADDRESS -> Res.string.settings_ha_failed_address
                    HaFailure.AUTH -> Res.string.settings_ha_failed_auth
                    HaFailure.TIMEOUT -> Res.string.settings_ha_failed_timeout
                    HaFailure.UNREACHABLE -> Res.string.settings_ha_failed_unreachable
                    HaFailure.BAD_ANSWER -> Res.string.settings_ha_failed_answer
                },
            )
        }
        statusText?.let {
            Text(
                modifier = Modifier.padding(bottom = 8.dp),
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (status is HaStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = status != HaStatus.Working,
            onClick = { onSave(address, token) },
        ) {
            Text(stringResource(Res.string.settings_ha_save))
        }
    }
}
