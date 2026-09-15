package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
    val engine = remember(workbook) { workbook.toSpreadsheetWorkbook() }
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
