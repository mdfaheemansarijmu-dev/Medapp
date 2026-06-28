package com.example.network

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class AppUpdateConfig(
    val latestVersion: String,
    val minimumSupportedVersion: String,
    val updateTitle: String,
    val updateMessage: String,
    val downloadUrl: String,
    val forceUpdate: Boolean
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
}

class UpdateServiceImpl : UpdateService {

    companion object {
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_LAST_CHECKED = "last_checked_timestamp"
        private const val KEY_CACHED_VERSION = "cached_latest_version"
        private const val KEY_CACHED_MIN_VERSION = "cached_min_version"
        private const val KEY_CACHED_TITLE = "cached_update_title"
        private const val KEY_CACHED_MESSAGE = "cached_update_message"
        private const val KEY_CACHED_URL = "cached_download_url"
        private const val KEY_CACHED_FORCE = "cached_force_update"

        const val DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/faheem-ansari/student-planner-app/main/update.json"
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun checkForUpdates(context: Context, customUrl: String?): AppUpdateResult = withContext(Dispatchers.IO) {
        val urlToFetch = customUrl ?: DEFAULT_UPDATE_URL
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Update last checked timestamp
        val now = System.currentTimeMillis()
        sharedPrefs.edit().putLong(KEY_LAST_CHECKED, now).apply()

        try {
            val request = Request.Builder()
                .url(urlToFetch)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext AppUpdateResult.Error(
                        "Update file not found (404). Please upload 'update.json' to your repository (or server) to activate live updates."
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext AppUpdateResult.Error("Server returned code ${response.code}")
                }

                val bodyString = response.body?.string()
                    ?: return@withContext AppUpdateResult.Error("Empty response body")

                val adapter = moshi.adapter(AppUpdateConfig::class.java)
                val config = adapter.fromJson(bodyString)
                    ?: return@withContext AppUpdateResult.Error("Failed to parse update JSON")

                // Cache the update info
                sharedPrefs.edit()
                    .putString(KEY_CACHED_VERSION, config.latestVersion)
                    .putString(KEY_CACHED_MIN_VERSION, config.minimumSupportedVersion)
                    .putString(KEY_CACHED_TITLE, config.updateTitle)
                    .putString(KEY_CACHED_MESSAGE, config.updateMessage)
                    .putString(KEY_CACHED_URL, config.downloadUrl)
                    .putBoolean(KEY_CACHED_FORCE, config.forceUpdate)
                    .apply()

                val installedVersion = BuildConfig.VERSION_NAME
                
                if (isVersionNewer(installedVersion, config.latestVersion)) {
                    // Check if forced update is required based on forceUpdate flag OR if installed version is below minimumSupportedVersion
                    val isForce = config.forceUpdate || isVersionNewer(installedVersion, config.minimumSupportedVersion)
                    AppUpdateResult.UpdateAvailable(config, isForce)
                } else {
                    AppUpdateResult.UpToDate
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateService", "Error checking for updates", e)
            
            // On network error, let's see if we have a cached newer version we can still notify about offline
            val cachedConfig = getCachedUpdateInfo(context)
            if (cachedConfig != null) {
                val installedVersion = BuildConfig.VERSION_NAME
                if (isVersionNewer(installedVersion, cachedConfig.latestVersion)) {
                    val isForce = cachedConfig.forceUpdate || isVersionNewer(installedVersion, cachedConfig.minimumSupportedVersion)
                    return@withContext AppUpdateResult.UpdateAvailable(cachedConfig, isForce)
                }
            }
            
            AppUpdateResult.Error(e.message ?: "Unknown networking error")
        }
    }

    override fun getCachedUpdateInfo(context: Context): AppUpdateConfig? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val latestVersion = sharedPrefs.getString(KEY_CACHED_VERSION, null) ?: return null
        val minVersion = sharedPrefs.getString(KEY_CACHED_MIN_VERSION, "1.0.0") ?: "1.0.0"
        val title = sharedPrefs.getString(KEY_CACHED_TITLE, "New Update Available") ?: "New Update Available"
        val message = sharedPrefs.getString(KEY_CACHED_MESSAGE, "") ?: ""
        val url = sharedPrefs.getString(KEY_CACHED_URL, "") ?: ""
        val force = sharedPrefs.getBoolean(KEY_CACHED_FORCE, false)

        return AppUpdateConfig(
            latestVersion = latestVersion,
            minimumSupportedVersion = minVersion,
            updateTitle = title,
            updateMessage = message,
            downloadUrl = url,
            forceUpdate = force
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
            .remove(KEY_CACHED_TITLE)
            .remove(KEY_CACHED_MESSAGE)
            .remove(KEY_CACHED_URL)
            .remove(KEY_CACHED_FORCE)
            .apply()
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
