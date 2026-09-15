package com.example.ui

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
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
    val view = LocalView.current
    val engine = remember(workbook) { workbook.toSpreadsheetWorkbook() }

    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
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
}
