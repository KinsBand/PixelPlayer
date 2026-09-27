package com.theveloper.pixelplay.data.youtube

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Buttons on the download notifications: pause / resume / cancel one song, approve / postpone
 * the Wi-Fi bulk download and cancel a running bulk download.
 */
@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: DownloadCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_APPROVE_WIFI_BULK -> coordinator.approveWaitingBulkDownload()
            ACTION_POSTPONE_WIFI_BULK -> coordinator.postponeWaitingBulkDownload()
            ACTION_CANCEL_BULK -> coordinator.cancelBulk()
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_SONG_ID)?.let(coordinator::pause)
            ACTION_RESUME -> intent.getStringExtra(EXTRA_SONG_ID)?.let(coordinator::resume)
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_SONG_ID)?.let(coordinator::cancel)
        }
    }

    companion object {
        const val ACTION_APPROVE_WIFI_BULK = "com.theveloper.pixelplay.download.APPROVE_WIFI_BULK"
        const val ACTION_POSTPONE_WIFI_BULK = "com.theveloper.pixelplay.download.POSTPONE_WIFI_BULK"
        const val ACTION_CANCEL_BULK = "com.theveloper.pixelplay.download.CANCEL_BULK"
        const val ACTION_PAUSE = "com.theveloper.pixelplay.download.PAUSE"
        const val ACTION_RESUME = "com.theveloper.pixelplay.download.RESUME"
        const val ACTION_CANCEL = "com.theveloper.pixelplay.download.CANCEL"
        const val EXTRA_SONG_ID = "song_id"
    }
}
