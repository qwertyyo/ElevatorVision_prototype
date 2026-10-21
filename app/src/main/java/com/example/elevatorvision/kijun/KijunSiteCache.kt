package com.example.elevatorvision.kijun

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.elevatorvision.R
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 검사기준 통합검색 사이트의 오프라인 저장본.
 *
 * - 사이트 원본은 kijun 저장소 하나뿐이고, 이 앱 저장소에는 복사본을 두지 않는다.
 *   대신 앱이 실행 중에 사이트 주소에서 파일을 받아 폰 안(filesDir/kijun_site)에만 보관한다.
 * - WebView 요청을 가로채서, 인터넷이 되면 사이트에 확인(ETag 조건부 요청)하고 새 내용이면 저장,
 *   인터넷이 안 되거나 사이트에 못 닿으면 저장본으로 보여준다.
 * - Wi-Fi(무제한 네트워크)일 때는 prefetchAll()로 본문·그림 전체를 미리 받아 둔다.
 */
class KijunSiteCache(context: Context) {

    private val appContext = context.applicationContext
    val baseUrl: String = appContext.getString(R.string.kijun_base_url).let {
        if (it.endsWith("/")) it else "$it/"
    }
    private val root = File(appContext.filesDir, "kijun_site")
    private val metaDir = File(root, ".meta")

    fun hasIndex(): Boolean = File(root, INDEX).exists()

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isUnmetered(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        return isOnline() && !cm.isActiveNetworkMetered
    }

    /** 사이트 주소 안의 상대 경로. 사이트 밖 주소(글꼴 서버, 현장용어 서버 등)면 null. */
    private fun relPath(url: String): String? {
        if (!url.startsWith(baseUrl)) return null
        val p = url.removePrefix(baseUrl).substringBefore('#').substringBefore('?')
        if (p.contains("..")) return null
        return p.ifEmpty { INDEX }
    }

    /** WebViewClient.shouldInterceptRequest 에서 부른다 (WebView 백그라운드 스레드). */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.method != "GET") return null
        val rel = relPath(request.url.toString()) ?: return null
        val file = File(root, rel)

        if (isOnline()) {
            fetch(rel)
        }
        return if (file.exists()) response(file) else null   // null = WebView가 평소대로 처리(실패 화면)
    }

    /**
     * 사이트 전체(본문 + 그림 + 수식 글꼴)를 받아 둔다. Wi-Fi에서만, 이미 있는 그림은 다시 받지 않는다.
     * 본문(index.html)은 조건부 요청이라 바뀌지 않았으면 거의 비용이 없다.
     */
    fun prefetchAll() {
        if (!isUnmetered()) return
        try {
            fetch(INDEX)
            fetch("config.json")
            val indexFile = File(root, INDEX)
            if (!indexFile.exists()) return
            val html = indexFile.readText()

            val wanted = mutableSetOf<String>()
            FIG_RE.findAll(html).forEach { wanted += "fig/${it.value}" }
            STD_RE.findAll(html).forEach { wanted += "stdimg/${it.value}" }
            wanted += KATEX_FILES

            fetch(KATEX_CSS)
            File(root, KATEX_CSS).takeIf { it.exists() }?.readText()?.let { css ->
                FONT_RE.findAll(css).forEach { wanted += "katex/${it.groupValues[1]}" }
            }

            for (rel in wanted) {
                if (!File(root, rel).exists()) fetch(rel)
            }

            // 사이트에서 빠진 그림은 저장본에서도 지운다
            for (dir in listOf("fig", "stdimg")) {
                File(root, dir).listFiles()?.forEach { f ->
                    if ("$dir/${f.name}" !in wanted) {
                        f.delete()
                        File(metaDir, "$dir/${f.name}.etag").delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "prefetch failed", e)
        }
    }

    // WebView는 여러 스레드로 동시에 요청하므로, 같은 파일끼리만 순서를 지키고 다른 파일은 동시에 받는다
    private val locks = ConcurrentHashMap<String, Any>()

    /** 한 파일을 조건부로 받아 저장. 200이면 새로 저장, 304면 그대로. 실패해도 저장본은 남겨 둔다. */
    private fun fetch(rel: String): Boolean = synchronized(locks.getOrPut(rel) { Any() }) {
        fetchLocked(rel)
    }

    private fun fetchLocked(rel: String): Boolean {
        val file = File(root, rel)
        val etagFile = File(metaDir, "$rel.etag")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(baseUrl + rel).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 15_000
                if (file.exists() && etagFile.exists()) {
                    setRequestProperty("If-None-Match", etagFile.readText())
                }
            }
            when (conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> true
                HttpURLConnection.HTTP_OK -> {
                    file.parentFile?.mkdirs()
                    val tmp = File(file.parentFile, file.name + ".part")
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                    val etag = conn.getHeaderField("ETag")
                    if (etag != null) {
                        etagFile.parentFile?.mkdirs()
                        etagFile.writeText(etag)
                    } else {
                        etagFile.delete()
                    }
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed: $rel (${e.javaClass.simpleName})")
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun response(file: File): WebResourceResponse {
        val mime = when (file.extension.lowercase()) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "webp" -> "image/webp"
            "png" -> "image/png"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
        val encoding = if (mime.startsWith("text/") || mime.endsWith("json") || mime.endsWith("javascript")) "utf-8" else null
        return WebResourceResponse(mime, encoding, FileInputStream(file))
    }

    companion object {
        private const val TAG = "KijunSiteCache"
        private const val INDEX = "index.html"
        private const val KATEX_CSS = "katex/katex.min.css"
        private val KATEX_FILES = listOf(KATEX_CSS, "katex/katex.min.js")

        // 본문 데이터 안에 그림 파일 이름이 들어 있다 (연혁집 그림 fig/, 표준화 그림 stdimg/)
        private val FIG_RE = Regex("""he[ls]_p\d+_\d+\.webp""")
        private val STD_RE = Regex("""std-\d+_[0-9a-f]+\.webp""")
        private val FONT_RE = Regex("""url\((fonts/[^)]+\.woff2)\)""")
    }
}
