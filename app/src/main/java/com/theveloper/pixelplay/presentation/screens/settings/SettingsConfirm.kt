package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R

/**
 * A pending confirmation.
 *
 * Holding this as one nullable object replaces the four loose `mutableStateOf` variables
 * (title, body, action label, and a lambda kept in state) that the settings root used to
 * carry. A lambda in `mutableStateOf` does not survive configuration change, so the old
 * shape could show a sheet whose confirm button had lost its action after a rotation.
 */
@Immutable
data class ConfirmRequest(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val severity: Severity = Severity.Destructive,
    val onConfirm: () -> Unit
) {
    enum class Severity {
        /** Reversible, or cheap to redo. */
        Warning,

        /** Deletes or rebuilds something, but the user can recreate it. */
        Destructive
    }
}

/**
 * The single confirmation surface for settings.
 *
 * Settings previously had three of these: a bottom sheet on the root screen, a second,
 * separately written bottom sheet for the database rebuild (~60 duplicated lines with
 * different wording), and an AlertDialog for resetting lyrics. Same decision, three
 * different shapes and three different tones of voice.
 *
 * Renders nothing when [request] is null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsConfirmSheet(
    request: ConfirmRequest?,
    onDismiss: () -> Unit
) {
    if (request == null) return

    val accent = when (request.severity) {
        ConfirmRequest.Severity.Warning -> MaterialTheme.colorScheme.primary
        ConfirmRequest.Severity.Destructive -> MaterialTheme.colorScheme.error
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = request.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = request.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1)
                }
                Button(
                    onClick = {
                        request.onConfirm()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(request.confirmLabel, maxLines = 1)
                }
            }
        }
    }
}
