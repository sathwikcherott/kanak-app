package com.kanak.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.kanak.app.KanakApp
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Checks Kanak's public GitHub releases and hands a verified newer APK to Android's downloader. */
internal class UpdateController(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    suspend fun scheduleChecks() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints(NetworkType.CONNECTED))
                .build()
        ).await()
    }

    suspend fun checkAndDownload() = withContext(Dispatchers.IO) {
        val currentVersion = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: return@withContext
        val update = GitHubUpdateChecker.findNewerRelease(currentVersion) ?: return@withContext
        if (preferences.getString(DOWNLOAD_VERSION, null) == update.version) return@withContext

        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("Kanak ${update.version}")
            .setDescription("Update downloaded — tap this notification to install")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, update.assetName)

        val downloadId = context.getSystemService(DownloadManager::class.java).enqueue(request)
        preferences.edit()
            .putString(DOWNLOAD_VERSION, update.version)
            .putLong(DOWNLOAD_ID, downloadId)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "kanak_updates"
        const val DOWNLOAD_VERSION = "download_version"
        const val DOWNLOAD_ID = "download_id"
        const val WORK_NAME = "kanak-update-check"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

internal class UpdateCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as KanakApp).updateController.checkAndDownload()
        Result.success()
    } catch (_: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.success()
    }
}

internal data class AppUpdate(
    val version: String,
    val assetName: String,
    val downloadUrl: String,
)

internal object GitHubUpdateChecker {
    private const val RELEASES_URL = "https://api.github.com/repos/sathwikcherott/kanak-app/releases"
    private const val ASSET_PREFIX = "Kanak-"
    private const val ASSET_SUFFIX = "-debug.apk"
    private const val DOWNLOAD_PREFIX = "https://github.com/sathwikcherott/kanak-app/releases/download/"

    suspend fun findNewerRelease(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val current = Version.parse(currentVersion) ?: return@withContext null
        val releases = JSONArray(fetchReleases())
        (0 until releases.length())
            .asSequence()
            .mapNotNull { index -> releases.optJSONObject(index) }
            .filterNot { it.optBoolean("draft", true) }
            .mapNotNull { release ->
                val version = release.optString("tag_name").removePrefix("v")
                if (Version.parse(version) == null) return@mapNotNull null
                val assets = release.optJSONArray("assets") ?: return@mapNotNull null
                val asset = (0 until assets.length())
                    .asSequence()
                    .mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { candidate ->
                        candidate.optString("name") == "$ASSET_PREFIX$version$ASSET_SUFFIX" &&
                            candidate.optString("browser_download_url").startsWith(DOWNLOAD_PREFIX)
                    } ?: return@mapNotNull null
                AppUpdate(version, asset.optString("name"), asset.optString("browser_download_url"))
            }
            .filter { Version.parse(it.version)!! > current }
            .maxByOrNull { Version.parse(it.version)!! }
    }

    private fun fetchReleases(): String {
        val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Kanak-Update-Checker")
        }
        return try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

internal class Version private constructor(private val parts: List<Int>) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        val length = maxOf(parts.size, other.parts.size)
        return (0 until length)
            .map { index -> (parts.getOrElse(index) { 0 }).compareTo(other.parts.getOrElse(index) { 0 }) }
            .firstOrNull { it != 0 } ?: 0
    }

    companion object {
        fun parse(value: String): Version? {
            val segments = value.split('.')
            if (segments.isEmpty() || segments.any { it.isEmpty() || !it.all(Char::isDigit) || it.toIntOrNull() == null }) return null
            return Version(segments.map { it.toInt() })
        }
    }
}
