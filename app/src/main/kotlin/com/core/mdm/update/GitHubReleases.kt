package com.core.mdm.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads APKs from GitHub releases — the only non-Play source CoreMDM installs from.
 * Every hop (including CDN redirects) is checked against GitHub's own hosts, so a release
 * asset can't bounce the download somewhere else.
 */
object GitHubReleases {

    data class ApkAsset(val tag: String, val name: String, val url: String)

    private val ALLOWED_HOSTS = setOf(
        "api.github.com", "github.com", "codeload.github.com",
        "objects.githubusercontent.com", "release-assets.githubusercontent.com",
    )

    /** Latest release's APK for `owner/repo`, preferring an asset whose name contains [nameHint]. */
    suspend fun latestApk(repo: String, nameHint: String? = null): ApkAsset? = withContext(Dispatchers.IO) {
        val json = JSONObject(get("https://api.github.com/repos/$repo/releases/latest").bufferedReader().use { it.readText() })
        val assets = json.getJSONArray("assets")
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.getString("name").endsWith(".apk", ignoreCase = true) }
        val pick = apks.firstOrNull { nameHint != null && it.getString("name").contains(nameHint, ignoreCase = true) }
            ?: apks.firstOrNull { !it.getString("name").contains("debug", ignoreCase = true) }
            ?: return@withContext null
        ApkAsset(json.getString("tag_name"), pick.getString("name"), pick.getString("browser_download_url"))
    }

    suspend fun download(url: String, dest: File): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        get(url).use { input -> dest.outputStream().use { input.copyTo(it) } }
        dest
    }

    /** Reads package name / version / signers from a downloaded APK without installing it. */
    fun archiveInfo(context: Context, apk: File): PackageInfo? {
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    fun signatures(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            info.signingInfo?.apkContentsSigners else info.signatures
        return sigs.orEmpty().map { it.toCharsString() }.toSet()
    }

    @Suppress("DEPRECATION")
    fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun get(start: String): java.io.InputStream {
        var url = URL(start)
        repeat(6) {
            if (url.protocol != "https" || url.host !in ALLOWED_HOSTS)
                throw IOException("Blocked non-GitHub download host: ${url.host}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
                setRequestProperty("User-Agent", "CoreMDM")
            }
            when (conn.responseCode) {
                in 200..299 -> return conn.inputStream
                301, 302, 303, 307, 308 -> {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect()
                }
                else -> throw IOException("HTTP ${conn.responseCode} from ${url.host}")
            }
        }
        throw IOException("Too many redirects")
    }
}
