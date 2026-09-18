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
    const val DEFAULT_OWNER = "mdfaheemansarijmu-dev"
    const val DEFAULT_REPO = "Medapp"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun getRepoOwner(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_OWNER, null)
        if (saved.isNullOrBlank() || saved == "faheem-ansari") {
            prefs.edit().putString(KEY_OWNER, DEFAULT_OWNER).apply()
            return DEFAULT_OWNER
        }
        return saved
    }

    fun getRepoName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_REPO, null)
        if (saved.isNullOrBlank() || saved == "student-planner-app") {
            prefs.edit().putString(KEY_REPO, DEFAULT_REPO).apply()
            return DEFAULT_REPO
        }
        return saved
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
     * Tries /releases/latest first, and falls back to /releases list if latest returns 404 (e.g. pre-releases).
     */
    suspend fun fetchLatestRelease(
        owner: String,
        repo: String
    ): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val cleanOwner = owner.trim().ifEmpty { DEFAULT_OWNER }
            val cleanRepo = repo.trim().ifEmpty { DEFAULT_REPO }
            val latestUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/latest"
            Log.d(TAG, "Fetching latest release from: $latestUrl")

            val request = Request.Builder()
                .url(latestUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MedPulse-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val release = parseReleaseJson(responseBody)
                Log.d(TAG, "Successfully fetched GitHub release: tag=${release.tagName}, apkUrl=${release.apkDownloadUrl}")
                return@withContext Result.success(release)
            }

            // If /releases/latest returns 404 or fails, fall back to /releases list
            Log.d(TAG, "Checking /releases endpoint for $cleanOwner/$cleanRepo (latest returned code ${response.code})")
            val listUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases?per_page=10"
            val listRequest = Request.Builder()
                .url(listUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MedPulse-Android-App")
                .build()

            val listResponse = httpClient.newCall(listRequest).execute()
            val listBody = listResponse.body?.string()

            if (listResponse.isSuccessful && !listBody.isNullOrBlank()) {
                val releasesArray = org.json.JSONArray(listBody)
                for (i in 0 until releasesArray.length()) {
                    val obj = releasesArray.optJSONObject(i) ?: continue
                    val isDraft = obj.optBoolean("draft", false)
                    if (!isDraft) {
                        val release = parseReleaseJson(obj.toString())
                        Log.d(TAG, "Successfully found published release in list: tag=${release.tagName}")
                        return@withContext Result.success(release)
                    }
                }
            }

            val errorMsg = if (response.code == 404) {
                "No published releases found for GitHub repository $cleanOwner/$cleanRepo."
            } else {
                "GitHub API returned HTTP ${response.code}: ${response.message}"
            }
            Log.w(TAG, errorMsg)
            Result.failure(Exception(errorMsg))
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
