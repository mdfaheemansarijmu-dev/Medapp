package com.example.network

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class AppUpdateConfig(
    val latestVersion: String,
    val minimumVersion: String,
    val forceUpdate: Boolean,
    val apkUrl: String,
    val releaseNotes: String
)

sealed class AppUpdateResult {
    data class UpdateAvailable(val config: AppUpdateConfig, val isForce: Boolean) : AppUpdateResult()
    object UpToDate : AppUpdateResult()
    data class Error(val message: String) : AppUpdateResult()
}

interface UpdateService {
    suspend fun checkForUpdates(context: Context, customUrl: String? = null): AppUpdateResult
    fun getCachedUpdateInfo(context: Context): AppUpdateConfig?
    fun getLastCheckedTime(context: Context): Long
    fun clearCachedUpdate(context: Context)
    fun getCustomUpdateUrl(context: Context): String
    fun setCustomUpdateUrl(context: Context, url: String)
}

class UpdateServiceImpl : UpdateService {

    companion object {
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_LAST_CHECKED = "last_checked_timestamp"
        private const val KEY_CACHED_VERSION = "cached_latest_version"
        private const val KEY_CACHED_MIN_VERSION = "cached_min_version"
        private const val KEY_CACHED_FORCE = "cached_force_update"
        private const val KEY_CACHED_APK_URL = "cached_apk_url"
        private const val KEY_CACHED_RELEASE_NOTES = "cached_release_notes"
    }

    override suspend fun checkForUpdates(context: Context, customUrl: String?): AppUpdateResult = withContext(Dispatchers.IO) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Update last checked timestamp
        val now = System.currentTimeMillis()
        sharedPrefs.edit().putLong(KEY_LAST_CHECKED, now).apply()

        try {
            val apps = com.google.firebase.FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                Log.w("UpdateService", "FirebaseApp not initialized; skipping remote update check.")
                return@withContext getCachedUpdateInfo(context)?.let { cachedConfig ->
                    val installedVersion = BuildConfig.VERSION_NAME
                    if (isVersionNewer(installedVersion, cachedConfig.latestVersion)) {
                        val isForce = cachedConfig.forceUpdate || isVersionNewer(installedVersion, cachedConfig.minimumVersion)
                        AppUpdateResult.UpdateAvailable(cachedConfig, isForce)
                    } else AppUpdateResult.UpToDate
                } ?: AppUpdateResult.UpToDate
            }

            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("app_config").document("update")
            
            var documentSnapshot = try {
                Tasks.await(docRef.get(), 6, TimeUnit.SECONDS)
            } catch (e: Exception) {
                Log.w("UpdateService", "Firestore document fetch notice (using offline cache): ${e.message}")
                null
            }

            // Automatically seed config in Firestore if it doesn't exist
            if (documentSnapshot != null && !documentSnapshot.exists()) {
                val seedConfig = hashMapOf(
                    "latestVersion" to "1.0",
                    "minimumVersion" to "1.0",
                    "forceUpdate" to false,
                    "apkUrl" to "https://raw.githubusercontent.com/faheem-ansari/student-planner-app/main/app-release.apk",
                    "releaseNotes" to "• All systems fully updated and running production"
                )
                try {
                    Tasks.await(docRef.set(seedConfig), 5, TimeUnit.SECONDS)
                    documentSnapshot = Tasks.await(docRef.get(), 5, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e("UpdateService", "Error seeding Firestore update document", e)
                }
            }

            val config = if (documentSnapshot != null && documentSnapshot.exists()) {
                val latest = documentSnapshot.getString("latestVersion") ?: "1.0"
                AppUpdateConfig(
                    latestVersion = latest,
                    minimumVersion = documentSnapshot.getString("minimumVersion") ?: "1.0",
                    forceUpdate = documentSnapshot.getBoolean("forceUpdate") ?: false,
                    apkUrl = documentSnapshot.getString("apkUrl") ?: "https://raw.githubusercontent.com/faheem-ansari/student-planner-app/main/app-release.apk",
                    releaseNotes = documentSnapshot.getString("releaseNotes") ?: ""
                )
            } else {
                getCachedUpdateInfo(context) ?: AppUpdateConfig(
                    latestVersion = "1.0",
                    minimumVersion = "1.0",
                    forceUpdate = false,
                    apkUrl = "https://raw.githubusercontent.com/faheem-ansari/student-planner-app/main/app-release.apk",
                    releaseNotes = "• All systems fully updated and running production"
                )
            }

            // Cache the update info
            sharedPrefs.edit()
                .putString(KEY_CACHED_VERSION, config.latestVersion)
                .putString(KEY_CACHED_MIN_VERSION, config.minimumVersion)
                .putBoolean(KEY_CACHED_FORCE, config.forceUpdate)
                .putString(KEY_CACHED_APK_URL, config.apkUrl)
                .putString(KEY_CACHED_RELEASE_NOTES, config.releaseNotes)
                .apply()

            val installedVersion = BuildConfig.VERSION_NAME

            if (isVersionNewer(installedVersion, config.latestVersion)) {
                val isForce = config.forceUpdate || isVersionNewer(installedVersion, config.minimumVersion)
                AppUpdateResult.UpdateAvailable(config, isForce)
            } else {
                AppUpdateResult.UpToDate
            }
        } catch (e: Exception) {
            Log.e("UpdateService", "Error checking for updates via Firestore", e)
            
            // On network error, let's see if we have a cached newer version we can still notify about offline
            val cachedConfig = getCachedUpdateInfo(context)
            if (cachedConfig != null) {
                val installedVersion = BuildConfig.VERSION_NAME
                if (isVersionNewer(installedVersion, cachedConfig.latestVersion)) {
                    val isForce = cachedConfig.forceUpdate || isVersionNewer(installedVersion, cachedConfig.minimumVersion)
                    return@withContext AppUpdateResult.UpdateAvailable(cachedConfig, isForce)
                }
            }
            
            AppUpdateResult.UpToDate
        }
    }

    override fun getCachedUpdateInfo(context: Context): AppUpdateConfig? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val latestVersion = sharedPrefs.getString(KEY_CACHED_VERSION, null) ?: return null
        val minVersion = sharedPrefs.getString(KEY_CACHED_MIN_VERSION, "1.0") ?: "1.0"
        val force = sharedPrefs.getBoolean(KEY_CACHED_FORCE, false)
        val apkUrl = sharedPrefs.getString(KEY_CACHED_APK_URL, "") ?: ""
        val releaseNotes = sharedPrefs.getString(KEY_CACHED_RELEASE_NOTES, "") ?: ""

        return AppUpdateConfig(
            latestVersion = latestVersion,
            minimumVersion = minVersion,
            forceUpdate = force,
            apkUrl = apkUrl,
            releaseNotes = releaseNotes
        )
    }

    override fun getLastCheckedTime(context: Context): Long {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getLong(KEY_LAST_CHECKED, 0L)
    }

    override fun clearCachedUpdate(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .remove(KEY_CACHED_VERSION)
            .remove(KEY_CACHED_MIN_VERSION)
            .remove(KEY_CACHED_FORCE)
            .remove(KEY_CACHED_APK_URL)
            .remove(KEY_CACHED_RELEASE_NOTES)
            .apply()
    }

    override fun getCustomUpdateUrl(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString("custom_update_url", "") ?: ""
    }

    override fun setCustomUpdateUrl(context: Context, url: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (url.isBlank()) {
            sharedPrefs.edit().remove("custom_update_url").apply()
        } else {
            sharedPrefs.edit().putString("custom_update_url", url.trim()).apply()
        }
    }

    private fun isVersionNewer(installed: String, latest: String): Boolean {
        val installedClean = installed.takeWhile { it.isDigit() || it == '.' }
        val latestClean = latest.takeWhile { it.isDigit() || it == '.' }

        val installedParts = installedClean.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(installedParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val installedPart = installedParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }
            if (latestPart > installedPart) return true
            if (installedPart > latestPart) return false
        }
        return false
    }
}
