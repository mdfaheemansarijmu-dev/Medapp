package com.example.network

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUpdateConfig(
    val latestVersion: String,
    val minimumVersion: String,
    val forceUpdate: Boolean,
    val apkUrl: String,
    val releaseNotes: String,
    val releaseTag: String = latestVersion
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
    fun getGitHubOwner(context: Context): String
    fun getGitHubRepo(context: Context): String
    fun setGitHubRepoConfig(context: Context, owner: String, repo: String)
}

class UpdateServiceImpl : UpdateService {

    companion object {
        private const val TAG = "UpdateService"
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_LAST_CHECKED = "last_checked_timestamp"
        private const val KEY_CACHED_VERSION = "cached_latest_version"
        private const val KEY_CACHED_MIN_VERSION = "cached_min_version"
        private const val KEY_CACHED_FORCE = "cached_force_update"
        private const val KEY_CACHED_APK_URL = "cached_apk_url"
        private const val KEY_CACHED_RELEASE_NOTES = "cached_release_notes"
        private const val KEY_CACHED_TAG = "cached_release_tag"
    }

    override suspend fun checkForUpdates(context: Context, customUrl: String?): AppUpdateResult = withContext(Dispatchers.IO) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        sharedPrefs.edit().putLong(KEY_LAST_CHECKED, now).apply()

        val owner = GitHubUpdateClient.getRepoOwner(context)
        val repo = GitHubUpdateClient.getRepoName(context)
        val currentVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }

        Log.d(TAG, "Checking for updates from GitHub repo $owner/$repo (installed: $currentVersion)...")

        // 1. Primary: Fetch latest release from GitHub API
        val gitHubResult = GitHubUpdateClient.fetchLatestRelease(owner, repo)
        if (gitHubResult.isSuccess) {
            val release = gitHubResult.getOrNull()
            if (release != null) {
                val isNewer = GitHubUpdateClient.isUpdateAvailable(currentVersion, release.tagName)
                val apkUrl = release.apkDownloadUrl ?: release.htmlUrl
                val notes = if (release.body.isNotBlank()) {
                    release.body
                } else {
                    "Release ${release.tagName} is available on GitHub with performance improvements and updates."
                }

                val config = AppUpdateConfig(
                    latestVersion = release.cleanVersionName,
                    minimumVersion = "1.0",
                    forceUpdate = false,
                    apkUrl = apkUrl,
                    releaseNotes = notes,
                    releaseTag = release.tagName
                )

                // Cache the fetched config
                cacheUpdateInfo(sharedPrefs, config)

                return@withContext if (isNewer) {
                    Log.i(TAG, "New update available on GitHub: ${release.tagName} (installed: $currentVersion)")
                    AppUpdateResult.UpdateAvailable(config, isForce = false)
                } else {
                    Log.i(TAG, "MedPulse is up to date (${currentVersion} >= ${release.tagName})")
                    AppUpdateResult.UpToDate
                }
            }
        }

        val failureError = gitHubResult.exceptionOrNull()?.message ?: "Unable to fetch releases from GitHub"
        Log.w(TAG, "GitHub API check failed: $failureError")

        // 2. Fallback: Check cached update info if available
        val cachedConfig = getCachedUpdateInfo(context)
        if (cachedConfig != null) {
            if (GitHubUpdateClient.isUpdateAvailable(currentVersion, cachedConfig.releaseTag)) {
                return@withContext AppUpdateResult.UpdateAvailable(cachedConfig, isForce = false)
            }
        }

        AppUpdateResult.Error(failureError)
    }

    private fun cacheUpdateInfo(prefs: android.content.SharedPreferences, config: AppUpdateConfig) {
        prefs.edit()
            .putString(KEY_CACHED_VERSION, config.latestVersion)
            .putString(KEY_CACHED_MIN_VERSION, config.minimumVersion)
            .putBoolean(KEY_CACHED_FORCE, config.forceUpdate)
            .putString(KEY_CACHED_APK_URL, config.apkUrl)
            .putString(KEY_CACHED_RELEASE_NOTES, config.releaseNotes)
            .putString(KEY_CACHED_TAG, config.releaseTag)
            .apply()
    }

    override fun getCachedUpdateInfo(context: Context): AppUpdateConfig? {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val latestVersion = sharedPrefs.getString(KEY_CACHED_VERSION, null) ?: return null
        val minVersion = sharedPrefs.getString(KEY_CACHED_MIN_VERSION, "1.0") ?: "1.0"
        val force = sharedPrefs.getBoolean(KEY_CACHED_FORCE, false)
        val apkUrl = sharedPrefs.getString(KEY_CACHED_APK_URL, "") ?: ""
        val releaseNotes = sharedPrefs.getString(KEY_CACHED_RELEASE_NOTES, "") ?: ""
        val releaseTag = sharedPrefs.getString(KEY_CACHED_TAG, latestVersion) ?: latestVersion

        return AppUpdateConfig(
            latestVersion = latestVersion,
            minimumVersion = minVersion,
            forceUpdate = force,
            apkUrl = apkUrl,
            releaseNotes = releaseNotes,
            releaseTag = releaseTag
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
            .remove(KEY_CACHED_TAG)
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

    override fun getGitHubOwner(context: Context): String {
        return GitHubUpdateClient.getRepoOwner(context)
    }

    override fun getGitHubRepo(context: Context): String {
        return GitHubUpdateClient.getRepoName(context)
    }

    override fun setGitHubRepoConfig(context: Context, owner: String, repo: String) {
        GitHubUpdateClient.setRepoConfig(context, owner, repo)
    }
}
