package dev.ririxi.bunnylock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Release(
    val version: String,
    val notes: String,
    val url: String,
)

object Updates {
    // Set a valid GitHub releases API endpoint here to re-enable update discovery.
    private const val LATEST_RELEASE_API_URL = ""
    private const val TIMEOUT_MS = 10_000

    private val COMMIT_LINK = Regex("""\s*\(\[[0-9a-f]{7,40}\]\([^)]*\)\)""")
    private val HEADING_MARK = Regex("""^#+\s+""", RegexOption.MULTILINE)

    suspend fun newerThan(installed: String): Release? =
        withContext(Dispatchers.IO) {
            if (!isValidHttpUrl(LATEST_RELEASE_API_URL)) return@withContext null
            val latest = JSONObject(fetch(LATEST_RELEASE_API_URL))
            val version = latest.getString("tag_name").removePrefix("v")
            if (!isNewer(version, installed)) return@withContext null
            val assets = latest.optJSONArray("assets") ?: JSONArray()
            val apk = List(assets.length()) { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
            val notes = if (latest.isNull("body")) "" else latest.getString("body")
            val url = apk?.optString("browser_download_url")?.takeIf(::isValidHttpUrl)
                ?: latest.optString("html_url").takeIf(::isValidHttpUrl)
                ?: return@withContext null
            Release(
                version = version,
                notes = notes.replace(COMMIT_LINK, "").replace(HEADING_MARK, "").trim(),
                url = url,
            )
        }

    private fun isValidHttpUrl(value: String): Boolean =
        runCatching { java.net.URI(value) }
            .getOrNull()
            ?.let { it.isAbsolute && it.scheme in setOf("https", "http") && !it.host.isNullOrBlank() }
            ?: false

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "GitHub answered with status ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(
        version: String,
        installed: String,
    ): Boolean {
        val newNumbers = numbersOf(version)
        val installedNumbers = numbersOf(installed)
        for (index in 0 until maxOf(newNumbers.size, installedNumbers.size)) {
            val difference = newNumbers.getOrElse(index) { 0 } - installedNumbers.getOrElse(index) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }

    private fun numbersOf(version: String): List<Int> = version.split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
