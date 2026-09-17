package com.example.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GitHubUpdateClient {
    private const val TAG = "GitHubUpdateClient"
    private const val PREFS_NAME = "github_update_prefs"
    private const val KEY_OWNER = "github_owner"
    private const val KEY_REPO = "github_repo"

    // Default repository for MedPulse
    const val DEFAULT_OWNER = "faheem-ansari"
    const val DEFAULT_REPO = "student-planner-app"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun getRepoOwner(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_OWNER, DEFAULT_OWNER) ?: DEFAULT_OWNER
    }

    fun getRepoName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
    }

    fun setRepoConfig(context: Context, owner: String, repo: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_OWNER, owner.trim().ifEmpty { DEFAULT_OWNER })
            .putString(KEY_REPO, repo.trim().ifEmpty { DEFAULT_REPO })
            .apply()
    }

    /**
     * Fetches the latest release from the GitHub API.
     * Endpoint: https://api.github.com/repos/{owner}/{repo}/releases/latest
     */
    suspend fun fetchLatestRelease(
        owner: String,
        repo: String
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
            Log.d(TAG, "Fetching latest release from: $url")

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MedPulse-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                val errorMsg = "GitHub API returned HTTP ${response.code}: ${response.message}"
                Log.w(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val release = parseReleaseJson(responseBody)
            Log.d(TAG, "Successfully fetched GitHub release: tag=${release.tagName}, apkUrl=${release.apkDownloadUrl}")
            Result.success(release)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest GitHub release", e)
            Result.failure(e)
        }
    }

    /**
     * Parses the JSON payload returned by the GitHub Release API.
     */
    fun parseReleaseJson(jsonString: String): GitHubRelease {
        val json = JSONObject(jsonString)
        val tagName = json.optString("tag_name", "")
        val name = json.optString("name", tagName)
        val body = json.optString("body", "")
        val htmlUrl = json.optString("html_url", "")
        val publishedAt = json.optString("published_at", "")

        val assetsList = mutableListOf<GitHubReleaseAsset>()
        val assetsArray = json.optJSONArray("assets")
        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val assetObj = assetsArray.optJSONObject(i) ?: continue
                val assetName = assetObj.optString("name", "")
                val downloadUrl = assetObj.optString("browser_download_url", "")
                val size = assetObj.optLong("size", 0L)
                val contentType = assetObj.optString("content_type", "")

                assetsList.add(
                    GitHubReleaseAsset(
                        name = assetName,
                        browserDownloadUrl = downloadUrl,
                        size = size,
                        contentType = contentType
                    )
                )
            }
        }

        return GitHubRelease(
            tagName = tagName,
            name = name,
            body = body,
            htmlUrl = htmlUrl,
            publishedAt = publishedAt,
            assets = assetsList
        )
    }

    /**
     * Compares installed version with the release tag name.
     * e.g., current = "1.1", tag = "v1.2" -> returns true (update available).
     */
    fun isUpdateAvailable(currentVersionName: String, releaseTagName: String): Boolean {
        val cleanCurrent = currentVersionName.trim().removePrefix("v").removePrefix("V")
        val cleanRelease = releaseTagName.trim().removePrefix("v").removePrefix("V")

        val currentDigits = cleanCurrent.takeWhile { it.isDigit() || it == '.' }
        val releaseDigits = cleanRelease.takeWhile { it.isDigit() || it == '.' }

        val currentParts = currentDigits.split(".").mapNotNull { it.toIntOrNull() }
        val releaseParts = releaseDigits.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(currentParts.size, releaseParts.size)
        for (i in 0 until maxLength) {
            val curr = currentParts.getOrElse(i) { 0 }
            val rel = releaseParts.getOrElse(i) { 0 }
            if (rel > curr) return true
            if (curr > rel) return false
        }

        // If numeric prefix is identical, check if release tag has newer build tag or different non-empty string
        return false
    }
}
