package com.example.excel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.excel.engine.CellAddress
import com.example.excel.engine.CellRange
import com.example.excel.engine.CellStyle
import com.example.excel.engine.CellValue
import com.example.excel.engine.HorizontalAlignment
import com.example.excel.engine.SpreadsheetHistory
import com.example.excel.engine.SpreadsheetSheet
import com.example.excel.engine.SpreadsheetWorkbook
import com.example.excel.engine.SetCellCommand

private const val DEFAULT_ROWS = 100
private const val DEFAULT_COLUMNS = 20
private val rowHeaderWidth = 52.dp
private val columnWidth = 110.dp
private val cellHeight = 42.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetEditorScreen(
    workbook: SpreadsheetWorkbook = remember { SpreadsheetWorkbook() },
    onBack: () -> Unit = {},
    onSave: () -> Unit = {}
) {
    var activeSheet by remember { mutableIntStateOf(0) }
    var activeCell by remember { mutableStateOf(CellAddress(0, 0)) }
    var selection by remember { mutableStateOf(CellRange(activeCell, activeCell)) }
    var rangeMode by remember { mutableStateOf(false) }
    var formulaText by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var moreMenu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<List<List<SpreadsheetCellSnapshot>>?>(null) }
    val history = remember { SpreadsheetHistory() }
    val horizontal = rememberScrollState()
    val sheet = workbook.sheet(activeSheet)
    @Suppress("UNUSED_VARIABLE") val redraw = refresh

    fun selectCell(address: CellAddress) {
        activeCell = address
        selection = if (rangeMode) CellRange(selection.start, address) else CellRange(address, address)
        formulaText = valueText(sheet.valueAt(address))
        refresh++
    }

    fun editCell(address: CellAddress, value: CellValue) {
        history.execute(SetCellCommand(sheet, address, value))
        refresh++
    }

    fun copySelection() {
        copied = selection.addresses().map { address ->
            listOf(SpreadsheetCellSnapshot(sheet.valueAt(address), sheet.cell(address).style))
        }.toList().chunked(selection.columnCount)
    }

    fun pasteSelection() {
        val data = copied ?: return
        data.forEachIndexed { r, row -> row.forEachIndexed { c, item ->
            editCell(CellAddress(activeCell.row + r, activeCell.column + c), item.value)
            sheet.cell(CellAddress(activeCell.row + r, activeCell.column + c)).style = item.style
        } }
        refresh++
    }

    Surface(Modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = {
                    Column {
                        Text("Excel", fontWeight = FontWeight.SemiBold)
                        Text("${sheet.name} • ${selection}", fontSize = 11.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { onSave() }) { Icon(Icons.Default.Check, "Save") }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("Select all") }, leadingIcon = { Icon(Icons.Default.SelectAll, null) }, onClick = { selection = CellRange(CellAddress(0, 0), CellAddress(DEFAULT_ROWS - 1, DEFAULT_COLUMNS - 1)); activeCell = CellAddress(0, 0); moreMenu = false; refresh++ })
                            DropdownMenuItem(text = { Text("Insert row") }, onClick = { sheet.insertRow(selection.top); moreMenu = false; refresh++ })
                            DropdownMenuItem(text = { Text("Delete row") }, onClick = { sheet.deleteRow(selection.top); moreMenu = false; refresh++ })
                            DropdownMenuItem(text = { Text("Add sheet") }, onClick = { workbook.addSheet(); moreMenu = false; refresh++ })
                        }
                    }
                }
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton("Undo", Icons.Default.Undo, enabled = history.canUndo()) { if (history.undo()) refresh++ }
                ToolButton("Redo", Icons.Default.Redo, enabled = history.canRedo()) { if (history.redo()) refresh++ }
                ToolButton("Copy", Icons.Default.ContentCopy, enabled = true) { copySelection() }
                ToolButton("Paste", Icons.Default.ContentPaste, enabled = copied != null) { pasteSelection() }
                ToolButton("Bold", Icons.Default.FormatBold) { updateStyle(sheet, selection) { it.copy(bold = !it.bold) }; refresh++ }
                ToolButton("Italic", Icons.Default.FormatItalic) { updateStyle(sheet, selection) { it.copy(italic = !it.italic) }; refresh++ }
                ToolButton("Underline", Icons.Default.FormatUnderlined) { updateStyle(sheet, selection) { it.copy(underline = !it.underline) }; refresh++ }
                ToolButton("Left", Icons.Default.FormatAlignLeft) { updateStyle(sheet, selection) { it.copy(horizontalAlignment = HorizontalAlignment.Left) }; refresh++ }
                ToolButton("Center", Icons.Default.FormatAlignCenter) { updateStyle(sheet, selection) { it.copy(horizontalAlignment = HorizontalAlignment.Center) }; refresh++ }
                ToolButton("Right", Icons.Default.FormatAlignRight) { updateStyle(sheet, selection) { it.copy(horizontalAlignment = HorizontalAlignment.Right) }; refresh++ }
            }

            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(78.dp).border(1.dp, MaterialTheme.colorScheme.outline).padding(8.dp)) { Text(selection.toString(), fontSize = 13.sp) }
                Text("fx", Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                TextField(value = formulaText, onValueChange = { formulaText = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Enter value or formula") })
                IconButton(onClick = { editCell(activeCell, parseInput(formulaText)); rangeMode = false }) { Icon(Icons.Default.Check, "Apply") }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(tonalElevation = if (rangeMode) 3.dp else 0.dp, modifier = Modifier.clickable { rangeMode = !rangeMode }) {
                    Text(if (rangeMode) "Range: ON — tap cells" else "Range select", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(8.dp))
                Text("Tap a cell to select. Turn Range select ON, then tap the second cell.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.width(rowHeaderWidth)) {
                    Box(Modifier.height(34.dp).fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) { Text("#", fontSize = 12.sp) }
                    LazyColumn {
                        items((0 until DEFAULT_ROWS).toList()) { r ->
                            val selected = selection.top <= r && r <= selection.bottom
                            Box(Modifier.height(cellHeight).fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant).border(0.5.dp, MaterialTheme.colorScheme.outline).clickable {
                                selection = CellRange(CellAddress(r, 0), CellAddress(r, DEFAULT_COLUMNS - 1)); activeCell = CellAddress(r, 0); rangeMode = false; refresh++
                            }, contentAlignment = Alignment.Center) { Text("${r + 1}", fontSize = 12.sp) }
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(Modifier.height(34.dp).horizontalScroll(horizontal)) {
                        repeat(DEFAULT_COLUMNS) { c ->
                            Box(Modifier.width(columnWidth).height(34.dp).border(0.5.dp, MaterialTheme.colorScheme.outline).clickable {
                                selection = CellRange(CellAddress(0, c), CellAddress(DEFAULT_ROWS - 1, c)); activeCell = CellAddress(0, c); rangeMode = false; refresh++
                            }, contentAlignment = Alignment.Center) { Text(columnName(c), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                        }
                    }
                    LazyColumn {
                        items((0 until DEFAULT_ROWS).toList()) { r ->
                            Row {
                                Row(Modifier.horizontalScroll(horizontal)) {
                                    repeat(DEFAULT_COLUMNS) { c ->
                                        val address = CellAddress(r, c)
                                        val selected = selection.contains(address)
                                        val cell = sheet.cell(address)
                                        val align = when (cell.style.horizontalAlignment) { HorizontalAlignment.Left, HorizontalAlignment.General -> TextAlign.Start; HorizontalAlignment.Center -> TextAlign.Center; HorizontalAlignment.Right -> TextAlign.End }
                                        Box(Modifier.width(columnWidth).height(cellHeight).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surface).border(if (selected) 2.dp else .5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline).clickable { selectCell(address) }.padding(horizontal = 7.dp), contentAlignment = Alignment.CenterStart) {
                                            Text(valueText(cell.value), modifier = Modifier.fillMaxWidth(), fontSize = 13.sp, fontWeight = if (cell.style.bold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (cell.style.italic) FontStyle.Italic else FontStyle.Normal, textDecoration = if (cell.style.underline) TextDecoration.Underline else TextDecoration.None, textAlign = align)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                workbook.sheets.forEachIndexed { index, tab ->
                    Surface(tonalElevation = if (index == activeSheet) 3.dp else 0.dp, modifier = Modifier.padding(end = 6.dp).clickable { activeSheet = index; activeCell = CellAddress(0, 0); selection = CellRange(activeCell, activeCell); formulaText = ""; rangeMode = false; refresh++ }) {
                        Text(tab.name, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp)
                    }
                }
                IconButton(onClick = { workbook.addSheet(); refresh++ }) { Icon(Icons.Default.Add, "Add sheet") }
            }
        }
    }
}

private data class SpreadsheetCellSnapshot(val value: CellValue, val style: CellStyle)

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(54.dp)) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) }
        Text(label, fontSize = 8.sp)
    }
}

private fun updateStyle(sheet: SpreadsheetSheet, range: CellRange, transform: (CellStyle) -> CellStyle) {
    range.addresses().forEach { address -> sheet.cell(address).style = transform(sheet.cell(address).style) }
}

private fun valueText(value: CellValue): String = when (value) {
    CellValue.Empty -> ""
    is CellValue.Text -> value.value
    is CellValue.Number -> value.value.toString().removeSuffix(".0")
    is CellValue.BooleanValue -> value.value.toString()
    is CellValue.Formula -> value.expression
    is CellValue.Error -> value.code
}

private fun parseInput(input: String): CellValue {
    val value = input.trim()
    if (value.isEmpty()) return CellValue.Empty
    if (value.startsWith("=")) return CellValue.Formula(value)
    value.toDoubleOrNull()?.let { return CellValue.Number(it) }
    if (value.equals("true", true)) return CellValue.BooleanValue(true)
    if (value.equals("false", true)) return CellValue.BooleanValue(false)
    return CellValue.Text(value)
}

private fun columnName(column: Int): String {
    var n = column + 1
    val out = StringBuilder()
    while (n > 0) { val r = (n - 1) % 26; out.append(('A'.code + r).toChar()); n = (n - 1) / 26 }
    return out.reverse().toString()
}
