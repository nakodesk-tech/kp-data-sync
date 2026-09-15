package com.example.ui

import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.excel.ui.MobileSpreadsheetEditorScreen
import java.io.File

@Composable
internal fun InAppExcelEditorV2Screen(
    workbook: InAppXlsxWorkbook,
    fileName: String? = null,
    onBack: () -> Unit,
    onSaved: (File) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val engine = remember(workbook) { workbook.toSpreadsheetWorkbook() }

    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        onDispose {}
    }

    val composeStatus = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var activity: ComponentActivity? = null
    var c = context
    while (c is ContextWrapper) {
        if (c is ComponentActivity) {
            activity = c
            break
        }
        c = c.baseContext
    }
    val activityInsets = activity?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
    val activityStatusPx = activityInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
    val activityStatus = with(density) { activityStatusPx.toDp() }
    val resStatusId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    val resStatus = if (resStatusId > 0) with(density) { context.resources.getDimensionPixelSize(resStatusId).toDp() } else 24.dp
    val statusBarHeight = when {
        composeStatus > 0.dp -> composeStatus
        activityStatus > 0.dp -> activityStatus
        resStatus > 0.dp -> resStatus
        else -> 24.dp
    }

    val composeNav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val activityNavPx = activityInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    val activityNav = with(density) { activityNavPx.toDp() }
    val resNavId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    val resNav = if (resNavId > 0) with(density) { context.resources.getDimensionPixelSize(resNavId).toDp() } else 0.dp
    val navBarHeight = when {
        composeNav > 0.dp -> composeNav
        activityNav > 0.dp -> activityNav
        resNav > 0.dp -> resNav
        else -> 48.dp
    }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val effectiveBottomPadding = if (imeBottom > 0.dp) imeBottom else navBarHeight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF107C41))
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight)
                .background(Color(0xFF107C41))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
        ) {
            MobileSpreadsheetEditorScreen(
                workbook = engine,
                fileName = fileName ?: "Schools.xlsx",
                onBack = onBack,
                onSave = {
                    runCatching {
                        engine.applyToXlsx(workbook)
                        File.createTempFile("kp_excel_edited_", ".xlsx", context.cacheDir).also { workbook.saveTo(it) }
                    }.onSuccess(onSaved)
                }
            )
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(effectiveBottomPadding)
                .background(Color.White)
        )
    }
}
