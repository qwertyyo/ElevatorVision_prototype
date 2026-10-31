package com.example.elevatorvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.example.elevatorvision.kijun.KijunClassMap
import com.example.elevatorvision.kijun.KijunSiteCache
import com.example.elevatorvision.ui.theme.ElevatorVisionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 다크 테마 전용 앱이므로 상태바/내비게이션바 아이콘을 항상 밝은 색으로 고정
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        // 카메라 프리뷰 등 콘텐츠가 상태바/내비게이션바 뒤까지 완전히 채워지도록 엣지투엣지 활성화
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ElevatorVisionTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

/* ======================= Simple Nav ======================= */

private sealed class Screen {
    object Home : Screen()
    object Camera : Screen()
    object Search : Screen()
    object Storage : Screen()
    data class StorageDetail(val sessionId: String) : Screen()
}

@Composable
private fun AppRoot() {
    // 저장소는 첫 화면에서도, 촬영 화면에서도 들어가므로 뒤로가기는 들어온 순서대로 되돌린다
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    val appContext = LocalContext.current.applicationContext
    remember { KijunClassMap.load(appContext) }

    // 검사기준 통합검색 오프라인 저장본: 앱을 켤 때 Wi-Fi면 사이트 본문·그림을 미리 받아 둔다
    // (모바일 데이터에서는 받지 않고, 열어 본 화면만 그때그때 저장된다)
    val cache = remember { KijunSiteCache(appContext) }
    var kijunStatus by remember {
        mutableStateOf(if (cache.hasIndex()) KijunStatus.SAVED else KijunStatus.NONE)
    }
    LaunchedEffect(Unit) {
        val fresh = withContext(Dispatchers.IO) { cache.prefetchAll() }
        kijunStatus = when {
            fresh -> KijunStatus.LATEST
            cache.hasIndex() -> KijunStatus.SAVED
            else -> KijunStatus.NONE
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val s = stack.last()) {
        Screen.Home -> HomeScreen(
            kijunStatus = kijunStatus,
            onStartLive = { push(Screen.Camera) },
            onOpenSearch = { push(Screen.Search) },
            onOpenStorage = { push(Screen.Storage) },
            onOpenSession = { id -> push(Screen.StorageDetail(id)) }
        )
        Screen.Camera -> CameraScreen(
            onBack = { pop() },
            onOpenStorage = { push(Screen.Storage) }
        )
        Screen.Search -> SearchScreen(onBack = { pop() })
        Screen.Storage -> StorageScreen(
            onBack = { pop() },
            onOpenDetail = { id -> push(Screen.StorageDetail(id)) }
        )
        is Screen.StorageDetail -> StorageDetailScreen(
            sessionId = s.sessionId,
            onBack = { pop() }
        )
    }
}
