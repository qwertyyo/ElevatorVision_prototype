package com.example.elevatorvision.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.elevatorvision.ui.theme.DetectionAccent
import com.example.elevatorvision.ui.theme.OfflineBg
import com.example.elevatorvision.ui.theme.OfflineFg
import com.example.elevatorvision.ui.theme.TextSecondary
import org.json.JSONObject

/**
 * 부품의 검사기준을 통합검색 사이트(WebView)로 보여주는 바텀시트.
 * 클래스 → 검색어·근거 조항은 KijunClassMap, 사이트 주소·오프라인 저장본은 KijunSiteCache.
 * 기준/표준화/불합격 고르기는 사이트 자체의 출처 칩을 그대로 쓴다.
 *
 * className 이 클래스 키가 아니면(검색 화면에서 직접 친 말) 그 말 그대로 검색한다.
 * siblings 가 2개 이상이면 "이 사진의 다른 부품" 칩 줄을 보여 주고, 고르면 onSelectSibling 으로 알려 준다
 * (페이지를 새로 열지 않고 검색어만 바꾼다).
 */
@Composable
fun KijunSheet(
    className: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    siblings: List<String> = emptyList(),
    selectedSibling: Int = -1,
    onSelectSibling: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val entry = remember(className) {
        KijunClassMap.load(context)
        KijunClassMap.find(className)
    }
    val cache = remember { KijunSiteCache(context) }
    val online = remember { cache.isOnline() }
    val query = entry?.query ?: className

    BottomSheetScaffold(
        visible = true,
        onDismiss = onDismiss,
        eyebrow = eyebrow ?: "AI 인식 부품 · ${entry?.key ?: className}",
        eyebrowColor = DetectionAccent,
        title = entry?.nameKo ?: className,
        startExpanded = true,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InfoChip("검색어 ‘$query’")
            if (!entry?.clauses.isNullOrEmpty()) {
                InfoChip("근거 " + entry!!.clauses.joinToString(" · ") { it.num })
            }
            if (!online) InfoChip("오프라인 저장본", background = OfflineBg, color = OfflineFg)
        }

        if (siblings.size > 1) {
            Text(
                "이 사진의 다른 부품",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                siblings.forEachIndexed { i, label ->
                    PillChip(label, selected = i == selectedSibling, onClick = { onSelectSibling(i) })
                }
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
                    .clip(RoundedCornerShape(14.dp))
            )
        }
    }
}

/**
 * 통합검색 사이트 WebView. query 가 있으면 앱 안에 끼워 넣는 모양(제목줄 숨김·앱 테마)으로 그 말을 검색하고,
 * null 이면 사이트를 그대로 띄운다(검사기준 검색 화면). onWebView 로 뒤로가기용 WebView 를 넘겨준다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun KijunWebView(
    cache: KijunSiteCache,
    query: String?,
    modifier: Modifier = Modifier,
    onWebView: (WebView?) -> Unit = {}
) {
    val bg = BackgroundDark.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                tag = query   // 지금 보여 줄 검색어. 페이지가 다 열린 뒤에도 이 값으로 검색한다
                setBackgroundColor(bg)
                // 끼워 넣는 모양은 페이지가 다 열린 뒤 스크립트로 꾸미므로, 그 전에 사이트 검색창·밝은 화면이
                // 잠깐 비치지 않게 꾸밈이 끝날 때까지 감춰 둔다
                if (query != null) visibility = View.INVISIBLE
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true   // 사이트가 검색어·기준일·현장용어를 localStorage에 둔다
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = cache.intercept(request)

                    override fun onPageFinished(view: WebView, url: String) {
                        (view.tag as String?)?.let {
                            view.evaluateJavascript(setupScript(it)) { view.visibility = View.VISIBLE }
                        }
                    }

                    // 사이트 밖 링크는 외부 브라우저로
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.toString().startsWith(cache.baseUrl)) return false
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                }
                loadUrl(if (query == null) cache.baseUrl else cache.baseUrl + "#q=" + Uri.encode(query))
                onWebView(this)
            }
        },
        update = { webView ->
            // 시트 안에서 다른 부품을 고르면 페이지는 그대로 두고 검색어만 바꾼다
            if (query != null && webView.tag != query) {
                webView.tag = query
                webView.evaluateJavascript(searchScript(query), null)
            }
        },
        onRelease = { webView ->
            onWebView(null)
            webView.stopLoading()
            webView.destroy()
        }
    )
}

// 사이트의 앱 연동 API(window.KIJUN): 제목줄 숨김, 앱 다크 테마 색 입히기, 검색 실행.
// 인식한 부품의 기준만 보여 주는 자리라 검색창·필터 칩·건수 줄·"↑ 검색" 버튼도 숨기고 결과 카드만 남긴다
// (사이트는 고치지 않고 앱이 스타일만 덧붙인다).
private fun setupScript(query: String): String = """
    (function () {
      var k = window.KIJUN;
      if (!k) return;
      k.embed({ hideHeader: true, hideSub: true });
      if (!document.getElementById('ev-cards-only')) {
        var st = document.createElement('style');
        st.id = 'ev-cards-only';
        st.textContent = 'header.top, #status, .totop { display: none !important; } main { padding-top: 0 !important; }';
        document.head.appendChild(st);
      }
      k.setTheme({ theme: 'dark', bg: '#0A0E14', surface: '#141A22', fg: '#EDF1F5', muted: '#8E9BAA',
                   line: '#2A3440', accent: '#2E9BF0', accentSoft: '#12293D' });
      k.search(${JSONObject.quote(query)}, 20);
    })();
""".trimIndent()

private fun searchScript(query: String): String =
    "window.KIJUN && window.KIJUN.search(${JSONObject.quote(query)}, 20);"
