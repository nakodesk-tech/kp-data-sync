package com.example.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.excel.engine.SpreadsheetWorkbook
import com.example.excel.ui.SpreadsheetEditorScreen
import java.io.File

@Composable
internal fun InAppExcelEditorV2Screen(
    workbook: InAppXlsxWorkbook,
    onBack: () -> Unit,
    onSaved: (File) -> Unit
) {
    val context = LocalContext.current
    val engine = remember(workbook) { workbook.toSpreadsheetWorkbook() }
    SpreadsheetEditorScreen(
        workbook = engine,
        onBack = onBack,
        onSave = {
            runCatching {
                engine.applyToXlsx(workbook)
                File.createTempFile("kp_excel_edited_", ".xlsx", context.cacheDir).also { workbook.saveTo(it) }
            }.onSuccess(onSaved)
        }
    )
}
