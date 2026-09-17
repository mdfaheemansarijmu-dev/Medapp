package com.example.network

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
    val assets: List<GitHubReleaseAsset>
) {
    /**
     * Extracts browser_download_url of the first APK asset in the release.
     */
    val apkDownloadUrl: String?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.browserDownloadUrl
            ?: assets.firstOrNull()?.browserDownloadUrl

    /**
     * Normalized version string stripped of 'v' / 'V' prefix.
     */
    val cleanVersionName: String
        get() = tagName.trim().removePrefix("v").removePrefix("V")
}

data class GitHubReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long,
    val contentType: String
)
