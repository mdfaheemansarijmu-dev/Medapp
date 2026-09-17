package com.example.receivers

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.utils.DownloadManagerHelper

/**
 * BroadcastReceiver that listens for DownloadManager.ACTION_DOWNLOAD_COMPLETE.
 * When the APK download finishes, it triggers the Android Package Installer via FileProvider.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UpdateInstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            Log.d(TAG, "Download completed broadcast received for ID #$downloadId")

            if (downloadId != -1L) {
                DownloadManagerHelper.installApkFromDownloadId(context, downloadId)
            }
        }
    }
}
