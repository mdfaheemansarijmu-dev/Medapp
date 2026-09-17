package com.example.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object DownloadManagerHelper {
    private const val TAG = "DownloadManagerHelper"
    private const val PREFS_NAME = "download_manager_prefs"
    private const val KEY_LAST_DOWNLOAD_ID = "last_download_id"
    private const val KEY_LAST_FILE_NAME = "last_download_file_name"

    /**
     * Enqueues an APK download using Android's system DownloadManager.
     * Saves to Environment.DIRECTORY_DOWNLOADS.
     */
    fun downloadApk(
        context: Context,
        apkUrl: String,
        version: String = ""
    ): Long {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cleanVersion = version.trim().removePrefix("v").removePrefix("V")
            val fileName = if (cleanVersion.isNotEmpty()) {
                "MedPulse-v$cleanVersion.apk"
            } else {
                "MedPulse-Update.apk"
            }

            // Remove existing file if present to avoid download collisions
            try {
                val existingFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                if (existingFile.exists()) {
                    existingFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete old APK file: ${e.message}")
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("MedPulse Update")
                val desc = if (cleanVersion.isNotEmpty()) "Downloading MedPulse v$cleanVersion..." else "Downloading MedPulse update..."
                setDescription(desc)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Enqueued APK download #$downloadId for $apkUrl -> $fileName")

            // Store the download details in SharedPreferences
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_DOWNLOAD_ID, downloadId)
                .putString(KEY_LAST_FILE_NAME, fileName)
                .apply()

            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
            return downloadId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start APK download", e)
            Toast.makeText(context, "Failed to start download: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return -1L
        }
    }

    /**
     * Installs an APK file using FileProvider and ACTION_VIEW Intent.
     */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            Log.e(TAG, "Cannot install: File does not exist at ${file.absolutePath}")
            Toast.makeText(context, "Update file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Check for Unknown App Sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w(TAG, "Requesting unknown sources permission")
                    Toast.makeText(
                        context,
                        "Please allow MedPulse to install packages from this source",
                        Toast.LENGTH_LONG
                    ).show()
                    val manageIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(manageIntent)
                }
            }

            val authority = "${context.packageName}.provider"
            val apkUri = FileProvider.getUriForFile(context, authority, file)
            Log.d(TAG, "Prompting package installer for URI: $apkUri with authority $authority")

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            Toast.makeText(context, "Error starting installation: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Resolves downloaded APK from a DownloadManager downloadId and triggers installation.
     */
    fun installApkFromDownloadId(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        var handled = false
        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                // Try resolving via local URI column
                val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUriString = if (localUriIndex >= 0) cursor.getString(localUriIndex) else null

                if (!localUriString.isNullOrBlank()) {
                    val localUri = Uri.parse(localUriString)
                    val filePath = localUri.path
                    if (filePath != null) {
                        val file = File(filePath)
                        if (file.exists()) {
                            installApk(context, file)
                            handled = true
                        }
                    }
                }
            }
            cursor.close()
        }

        if (!handled) {
            // Fallback: Check last known filename in Environment.DIRECTORY_DOWNLOADS
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val fileName = prefs.getString(KEY_LAST_FILE_NAME, "MedPulse-Update.apk") ?: "MedPulse-Update.apk"
            val fallbackFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (fallbackFile.exists()) {
                installApk(context, fallbackFile)
            } else {
                Log.w(TAG, "Could not locate downloaded APK file for ID #$downloadId")
            }
        }
    }
}
