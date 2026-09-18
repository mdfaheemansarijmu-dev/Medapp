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
import java.io.FileInputStream

object DownloadManagerHelper {
    private const val TAG = "DownloadManagerHelper"
    private const val PREFS_NAME = "download_manager_prefs"
    private const val KEY_LAST_DOWNLOAD_ID = "last_download_id"
    private const val KEY_LAST_FILE_NAME = "last_download_file_name"

    /**
     * Enqueues an APK download using Android's system DownloadManager.
     * Uses the app's external files directory to avoid Android 10+ Scoped Storage access restrictions.
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

            // Clean up existing APK files in app directory to avoid conflicts
            try {
                val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (targetDir != null) {
                    val existingFile = File(targetDir, fileName)
                    if (existingFile.exists()) {
                        existingFile.delete()
                    }
                }
                val cacheDir = File(context.cacheDir, "updates")
                if (cacheDir.exists()) {
                    val cacheFile = File(cacheDir, fileName)
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete old APK file: ${e.message}")
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("MedPulse Update")
                val desc = if (cleanVersion.isNotEmpty()) "Downloading MedPulse v$cleanVersion..." else "Downloading MedPulse update..."
                setDescription(desc)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // Save to app's external files dir (fully accessible by app & FileProvider on Android 10+ without storage permissions)
                try {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set destination in external files dir, falling back to public downloads: ${e.message}")
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }

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
     * Checks if Unknown App Sources permission is granted on Android 8.0+.
     * If not, prompts the user and opens the system settings screen.
     */
    fun checkAndRequestInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Log.w(TAG, "Requesting unknown sources permission")
                Toast.makeText(
                    context,
                    "Please allow MedPulse to install apps, then tap Install",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    val manageIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(manageIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch ACTION_MANAGE_UNKNOWN_APP_SOURCES", e)
                }
                return false
            }
        }
        return true
    }

    /**
     * Installs an APK file using FileProvider and ACTION_VIEW Intent.
     */
    fun installApk(context: Context, file: File): Boolean {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Cannot install: File does not exist or empty at ${file.absolutePath}")
            Toast.makeText(context, "Update file not found or corrupted", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            if (!checkAndRequestInstallPermission(context)) {
                return false
            }

            val authority = "${context.packageName}.provider"
            val apkUri = FileProvider.getUriForFile(context, authority, file)
            Log.d(TAG, "Prompting package installer for URI: $apkUri (File: ${file.absolutePath}, ${file.length()} bytes)")

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            Toast.makeText(context, "Error starting installation: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    /**
     * Installs an APK directly from a content URI (e.g. from DownloadManager).
     */
    fun installApkFromUri(context: Context, uri: Uri): Boolean {
        try {
            if (!checkAndRequestInstallPermission(context)) {
                return false
            }

            Log.d(TAG, "Prompting package installer for content URI: $uri")
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error installing from URI: $uri", e)
            return false
        }
    }

    /**
     * Resolves downloaded APK from a DownloadManager downloadId and triggers installation.
     */
    fun installApkFromDownloadId(context: Context, downloadId: Long): Boolean {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedFileName = prefs.getString(KEY_LAST_FILE_NAME, "MedPulse-Update.apk") ?: "MedPulse-Update.apk"

        // 1. Check if the file is in context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (extDir != null) {
            val appFile = File(extDir, savedFileName)
            if (appFile.exists() && appFile.length() > 0L) {
                Log.d(TAG, "Found file in app external files dir: ${appFile.absolutePath}")
                return installApk(context, appFile)
            }
        }

        // 2. Try copying via downloadManager.openDownloadedFile(downloadId) into context.cacheDir/updates
        try {
            downloadManager.openDownloadedFile(downloadId)?.use { pfd ->
                val inputStream = FileInputStream(pfd.fileDescriptor)
                val updateDir = File(context.cacheDir, "updates")
                updateDir.mkdirs()
                val cacheFile = File(updateDir, savedFileName)
                cacheFile.outputStream().use { out ->
                    inputStream.copyTo(out)
                }
                if (cacheFile.exists() && cacheFile.length() > 0L) {
                    Log.d(TAG, "Copied file from DownloadManager to cache: ${cacheFile.absolutePath} (${cacheFile.length()} bytes)")
                    return installApk(context, cacheFile)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "openDownloadedFile failed or not available for ID #$downloadId: ${e.message}")
        }

        // 3. Try downloadManager.getUriForDownloadedFile(downloadId)
        try {
            val contentUri = downloadManager.getUriForDownloadedFile(downloadId)
            if (contentUri != null) {
                Log.d(TAG, "Attempting install directly with contentUri: $contentUri")
                val installed = installApkFromUri(context, contentUri)
                if (installed) return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "getUriForDownloadedFile failed for ID #$downloadId: ${e.message}")
        }

        // 4. Try public Downloads folder fallback
        try {
            val publicFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                savedFileName
            )
            if (publicFile.exists() && publicFile.length() > 0L) {
                val updateDir = File(context.cacheDir, "updates")
                updateDir.mkdirs()
                val cacheFile = File(updateDir, savedFileName)
                try {
                    publicFile.inputStream().use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (cacheFile.exists() && cacheFile.length() > 0L) {
                        return installApk(context, cacheFile)
                    }
                } catch (_: Exception) {}
                return installApk(context, publicFile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public downloads check failed: ${e.message}")
        }

        // 5. Look for any existing .apk in cacheDir/updates
        val updateDir = File(context.cacheDir, "updates")
        if (updateDir.exists()) {
            val apks = updateDir.listFiles { _, name -> name.endsWith(".apk") }
            val latest = apks?.maxByOrNull { it.lastModified() }
            if (latest != null && latest.length() > 0L) {
                return installApk(context, latest)
            }
        }

        Log.e(TAG, "Could not locate or read downloaded APK for ID #$downloadId")
        Toast.makeText(context, "Could not locate downloaded update file. Please tap Install Update.", Toast.LENGTH_SHORT).show()
        return false
    }

    /**
     * Resolves the latest downloaded APK file and triggers installation.
     */
    fun installLatestDownloadedApk(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastId = prefs.getLong(KEY_LAST_DOWNLOAD_ID, -1L)
        if (lastId != -1L) {
            val installed = installApkFromDownloadId(context, lastId)
            if (installed) return true
        }

        val savedFileName = prefs.getString(KEY_LAST_FILE_NAME, "MedPulse-Update.apk") ?: "MedPulse-Update.apk"
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (extDir != null) {
            val file = File(extDir, savedFileName)
            if (file.exists() && file.length() > 0L) {
                return installApk(context, file)
            }
        }

        val updateDir = File(context.cacheDir, "updates")
        if (updateDir.exists()) {
            val apks = updateDir.listFiles { _, name -> name.endsWith(".apk") }
            val latest = apks?.maxByOrNull { it.lastModified() }
            if (latest != null && latest.length() > 0L) {
                return installApk(context, latest)
            }
        }

        Toast.makeText(context, "No downloaded update found. Please download again.", Toast.LENGTH_SHORT).show()
        return false
    }
}
