package com.example.elevatorvision

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.elevatorvision.kijun.KijunSiteCache
import com.example.elevatorvision.ui.KijunWebView
import com.example.elevatorvision.ui.theme.TextSecondary

// 검사기준 검색: 앱 화면을 따로 두지 않고 통합검색 사이트를 전체 화면으로 그대로 띄운다.
// 뒤로가기는 사이트 안에서 먼저 되돌리고, 더 갈 곳이 없으면 첫 화면으로.
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cache = remember { KijunSiteCache(context) }
    val canShow = remember { cache.isOnline() || cache.hasIndex() }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        if (canShow) {
            KijunWebView(
                cache = cache,
                query = null,
                modifier = Modifier.fillMaxSize(),
                onWebView = { webView = it }
            )
        } else {
            Text(
                "인터넷에 연결된 상태에서 한 번 열어야\n오프라인에서도 볼 수 있습니다.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    // 화면 전환 BackHandler보다 나중에 등록돼서 뒤로가기를 먼저 받는다
    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }
}
