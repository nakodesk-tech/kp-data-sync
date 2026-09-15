package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private val v2RowHeader = 52.dp
private val v2ColumnWidth = 112.dp
private val v2CellHeight = 42.dp
private const val V2_ROWS = 120
private const val V2_COLS = 24

@Composable
fun InAppExcelEditorV2Screen(
    workbook: InAppXlsxWorkbook,
    onBack: () -> Unit,
    onSaved: (File) -> Unit
) {
    var sheetIndex by remember { mutableIntStateOf(0) }
    var activeRow by remember { mutableIntStateOf(0) }
    var activeCol by remember { mutableIntStateOf(0) }
    var anchorRow by remember { mutableIntStateOf(0) }
    var anchorCol by remember { mutableIntStateOf(0) }
    var rangeMode by remember { mutableStateOf(false) }
    var formulaBar by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val horizontal = rememberScrollState()
    val sheet = workbook.sheets[sheetIndex]
    @Suppress("UNUSED_VARIABLE") val redraw = refresh

    fun bounds(): IntArray = intArrayOf(minOf(activeRow, anchorRow), minOf(activeCol, anchorCol), maxOf(activeRow, anchorRow), maxOf(activeCol, anchorCol))
    fun selected(r: Int, c: Int): Boolean { val b = bounds(); return r in b[0]..b[2] && c in b[1]..b[3] }
    fun select(r: Int, c: Int) {
        if (rangeMode) { activeRow = r; activeCol = c } else { activeRow = r; activeCol = c; anchorRow = r; anchorCol = c }
        formulaBar = sheet.cells.getOrNull(r)?.getOrNull(c).orEmpty()
        refresh++
    }
    fun applyValue() {
        workbook.setCell(sheetIndex, activeRow, activeCol, formulaBar)
        dirty = true
        rangeMode = false
        refresh++
    }
    fun style(transform: (ExcelCellStyle) -> ExcelCellStyle) {
        val b = bounds()
        for (r in b[0]..b[2]) for (c in b[1]..b[3]) workbook.setStyle(sheetIndex, r, c, transform(workbook.styleAt(sheetIndex, r, c)))
        dirty = true
        refresh++
    }
    fun save() {
        if (saving) return
        saving = true
        val target = File.createTempFile("edited_excel_", ".xlsx")
        Thread {
            runCatching { workbook.saveTo(target) }
                .onSuccess { onSaved(target) }
                .onFailure { target.delete(); saving = false }
        }.start()
    }

    Surface(Modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { Column { Text("Excel", fontWeight = FontWeight.SemiBold); Text("${sheet.name} • ${cellRef(activeRow, activeCol)}", fontSize = 11.sp) } },
                navigationIcon = { IconButton(onClick = onBack, enabled = !saving) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { save() }, enabled = dirty && !saving) { Icon(if (saving) Icons.Default.HourglassTop else Icons.Default.Save, "Save") } }
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                V2Tool("Undo", Icons.Default.Undo, false) {}
                V2Tool("Redo", Icons.Default.Redo, false) {}
                V2Tool("Bold", Icons.Default.FormatBold) { style { it.copy(bold = !it.bold) } }
                V2Tool("Italic", Icons.Default.FormatItalic) { style { it.copy(italic = !it.italic) } }
                V2Tool("Underline", Icons.Default.FormatUnderlined) { style { it.copy(underline = !it.underline) } }
                V2Tool("Left", Icons.Default.FormatAlignLeft) { style { it.copy(horizontal = "left") } }
                V2Tool("Center", Icons.Default.FormatAlignCenter) { style { it.copy(horizontal = "center") } }
                V2Tool("Right", Icons.Default.FormatAlignRight) { style { it.copy(horizontal = "right") } }
                V2Tool("Border", Icons.Default.BorderAll) { style { it.copy(border = !it.border) } }
                V2Tool("Clear", Icons.Default.Delete) { workbook.clearCell(sheetIndex, activeRow, activeCol); formulaBar = ""; dirty = true; refresh++ }
            }
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(76.dp).border(1.dp, MaterialTheme.colorScheme.outline).padding(8.dp)) { Text(cellRef(activeRow, activeCol), fontSize = 13.sp) }
                Text("fx", Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                TextField(formulaBar, { formulaBar = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Enter value or formula") })
                IconButton(onClick = { applyValue() }) { Icon(Icons.Default.Check, "Apply") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(tonalElevation = if (rangeMode) 3.dp else 0.dp, modifier = Modifier.clickable { rangeMode = !rangeMode }) { Text(if (rangeMode) "Range ON" else "Range select", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Text(if (rangeMode) "Tap the second cell to finish the range." else "Tap a cell. Use Range select for a two-cell or rectangular range.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.width(v2RowHeader)) {
                    Box(Modifier.height(34.dp).fillMaxWidth().border(.5.dp, MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) { Text("#", fontSize = 12.sp) }
                    LazyColumn {
                        items((0 until V2_ROWS).toList()) { r ->
                            Box(Modifier.height(v2CellHeight).fillMaxWidth().background(if (r in bounds()[0]..bounds()[2]) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant).border(.5.dp, MaterialTheme.colorScheme.outline).clickable { activeRow = r; anchorRow = r; anchorCol = 0; activeCol = 0; rangeMode = false; refresh++ }, contentAlignment = Alignment.Center) { Text("${r + 1}", fontSize = 12.sp) }
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(Modifier.height(34.dp).horizontalScroll(horizontal)) {
                        repeat(V2_COLS) { c -> Box(Modifier.width(v2ColumnWidth).height(34.dp).border(.5.dp, MaterialTheme.colorScheme.outline).clickable { activeCol = c; anchorCol = c; anchorRow = 0; activeRow = 0; rangeMode = false; refresh++ }, contentAlignment = Alignment.Center) { Text(columnName(c), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) } }
                    }
                    LazyColumn {
                        items((0 until V2_ROWS).toList()) { r ->
                            Row(Modifier.horizontalScroll(horizontal)) {
                                repeat(V2_COLS) { c ->
                                    val isSelected = selected(r, c)
                                    val raw = sheet.cells.getOrNull(r)?.getOrNull(c).orEmpty()
                                    val display = if (raw.startsWith("=")) workbook.displayValue(sheetIndex, r, c) else raw
                                    val st = sheet.styles.getOrNull(r)?.getOrNull(c) ?: ExcelCellStyle()
                                    val align = when (st.horizontal) { "left" -> TextAlign.Start; "center" -> TextAlign.Center; "right" -> TextAlign.End; else -> TextAlign.Start }
                                    Box(Modifier.width(v2ColumnWidth).height(v2CellHeight).background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surface).border(if (isSelected) 2.dp else .5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline).clickable { select(r, c) }.padding(horizontal = 7.dp), contentAlignment = Alignment.CenterStart) {
                                        Text(display, Modifier.fillMaxWidth(), fontSize = st.fontSize.sp, fontWeight = if (st.bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (st.italic) FontStyle.Italic else FontStyle.Normal, textDecoration = if (st.underline) TextDecoration.Underline else TextDecoration.None, textAlign = align, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceVariant).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                workbook.sheets.forEachIndexed { i, s -> Surface(tonalElevation = if (i == sheetIndex) 3.dp else 0.dp, modifier = Modifier.padding(end = 6.dp).clickable { sheetIndex = i; activeRow = 0; activeCol = 0; anchorRow = 0; anchorCol = 0; formulaBar = ""; refresh++ }) { Text(s.name, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp) } }
                IconButton(onClick = { sheetIndex = workbook.insertSheet("Sheet ${workbook.sheets.size + 1}"); activeRow = 0; activeCol = 0; anchorRow = 0; anchorCol = 0; dirty = true; refresh++ }) { Icon(Icons.Default.Add, "Add sheet") }
            }
        }
    }
}

@Composable
private fun V2Tool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) }; Text(label, fontSize = 8.sp) }
}

private fun cellRef(row: Int, col: Int): String = columnName(col) + (row + 1)

private fun columnName(column: Int): String {
    var n = column + 1
    val out = StringBuilder()
    while (n > 0) { val r = (n - 1) % 26; out.append(('A'.code + r).toChar()); n = (n - 1) / 26 }
    return out.reverse().toString()
}
