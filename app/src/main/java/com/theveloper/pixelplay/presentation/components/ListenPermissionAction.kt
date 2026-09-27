package com.theveloper.pixelplay.presentation.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.recognition.ambient.AmbientListeningService

/** Shared by Queue and Settings so every start has a visible, permission-gated origin. */
@Composable
internal fun rememberListenAction(): (Boolean) -> Unit {
    val context = LocalContext.current
    val start = {
        try {
            ContextCompat.startForegroundService(context, Intent(context, AmbientListeningService::class.java))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not start Listen. Keep PixelPlayer open and try again.", Toast.LENGTH_LONG).show()
        }
        Unit
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start()
        else Toast.makeText(context, "Microphone access is needed to hear song mentions.", Toast.LENGTH_LONG).show()
    }
    return { enabled ->
        if (!enabled) runCatching {
            context.startService(Intent(context, AmbientListeningService::class.java)
                .setAction(AmbientListeningService.ACTION_STOP))
        }.onFailure { context.stopService(Intent(context, AmbientListeningService::class.java)) }
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
