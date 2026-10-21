package com.example.elevatorvision.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.elevatorvision.kijun.KijunClassMap
import com.example.elevatorvision.kijun.KijunSiteCache
import com.example.elevatorvision.ui.theme.BackgroundDark
import com.example.elevatorvision.ui.theme.BrandOrange
import com.example.elevatorvision.ui.theme.TextSecondary
import org.json.JSONObject

/**
 * 인식된 부품의 검사기준을 통합검색 사이트(WebView)로 보여주는 바텀시트.
 * 클래스 → 검색어·근거 조항은 KijunClassMap, 사이트 주소·오프라인 저장본은 KijunSiteCache.
 * 기준/표준화/불합격 고르기는 사이트 자체의 출처 칩을 그대로 쓴다.
 */
@Composable
fun KijunSheet(
    className: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val entry = remember(className) {
        KijunClassMap.load(context)
        KijunClassMap.find(className)
    }
    val cache = remember { KijunSiteCache(context) }
    val online = remember { cache.isOnline() }
    val query = entry?.query ?: className

    val info = buildList {
        add("검색어 ‘$query’")
        if (!entry?.clauses.isNullOrEmpty()) add("근거 " + entry!!.clauses.joinToString(" · ") { it.num })
    }.joinToString("  ·  ")

    BottomSheetScaffold(
        visible = true,
        onDismiss = onDismiss,
        eyebrow = "AI 인식 부품 · ${entry?.key ?: className}",
        title = entry?.nameKo ?: className,
        startExpanded = true,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(info, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (!online) {
                StatusPill(text = "오프라인 저장본", icon = Icons.Filled.CloudOff, color = BrandOrange)
            }
        }

        if (!online && !cache.hasIndex()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "인터넷에 연결된 상태에서 한 번 열어야\n오프라인에서도 볼 수 있습니다.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            KijunWebView(
                cache = cache,
                query = query,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KijunWebView(
    cache: KijunSiteCache,
    query: String,
    modifier: Modifier = Modifier
) {
    val bg = BackgroundDark.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(bg)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true   // 사이트가 검색어·기준일·현장용어를 localStorage에 둔다
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = cache.intercept(request)

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(setupScript(query), null)
                    }

                    // 사이트 밖 링크는 외부 브라우저로
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.toString().startsWith(cache.baseUrl)) return false
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                }
                loadUrl(cache.baseUrl + "#q=" + Uri.encode(query))
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        }
    )
}

// 사이트의 앱 연동 API(window.KIJUN): 제목줄 숨김, 앱 다크 테마 색 입히기, 검색 실행
private fun setupScript(query: String): String = """
    (function () {
      var k = window.KIJUN;
      if (!k) return;
      k.embed({ hideHeader: true, hideSub: true });
      k.setTheme({ theme: 'dark', bg: '#0A0E14', surface: '#141A22', fg: '#EDF1F5', muted: '#8E9BAA',
                   line: '#2A3440', accent: '#2E9BF0', accentSoft: '#12293D' });
      k.search(${JSONObject.quote(query)}, 20);
    })();
""".trimIndent()
