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
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.excel.engine.BorderStyle
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
private val rowHeaderWidth = 48.dp
private val columnWidth = 110.dp
private val cellHeight = 40.dp
private val headerHeight = 32.dp

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
    var toolsMenu by remember { mutableStateOf(false) }
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
        copied = selection.addresses().map { a ->
            SpreadsheetCellSnapshot(sheet.valueAt(a), sheet.cell(a).style)
        }.toList().chunked(selection.columnCount)
    }

    fun pasteSelection() {
        val data = copied ?: return
        data.forEachIndexed { r, row -> row.forEachIndexed { c, item ->
            val target = CellAddress(activeCell.row + r, activeCell.column + c)
            editCell(target, item.value)
            sheet.cell(target).style = item.style
        } }
        refresh++
    }

    fun setAlignment(alignment: HorizontalAlignment) {
        updateStyle(sheet, selection) { it.copy(horizontalAlignment = alignment) }
        refresh++
    }

    fun toggleWrap() {
        updateStyle(sheet, selection) { it.copy(wrapText = !it.wrapText) }
        refresh++
    }

    fun toggleBorder() {
        updateStyle(sheet, selection) {
            it.copy(border = if (it.border == BorderStyle.None) BorderStyle.Thin else BorderStyle.None)
        }
        refresh++
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            TopAppBar(
                title = {
                    Column {
                        Text("${sheet.name}.xlsx", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${selection}  •  ${if (rangeMode) "Range select" else "Ready"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { if (history.undo()) refresh++ }, enabled = history.canUndo()) { Icon(Icons.Default.Undo, "Undo") }
                    IconButton(onClick = { if (history.redo()) refresh++ }, enabled = history.canRedo()) { Icon(Icons.Default.Redo, "Redo") }
                    IconButton(onClick = onSave) { Icon(Icons.Default.Check, "Save") }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("Select all") }, onClick = {
                                selection = CellRange(CellAddress(0, 0), CellAddress(DEFAULT_ROWS - 1, DEFAULT_COLUMNS - 1)); activeCell = CellAddress(0, 0); moreMenu = false; refresh++
                            })
                            DropdownMenuItem(text = { Text("Add sheet") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { workbook.addSheet(); moreMenu = false; refresh++ })
                            DropdownMenuItem(text = { Text("Insert row above") }, onClick = { sheet.insertRow(selection.top); moreMenu = false; refresh++ })
                            DropdownMenuItem(text = { Text("Delete selected row") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { sheet.deleteRow(selection.top); moreMenu = false; refresh++ })
                            DropdownMenuItem(text = { Text("Insert column left") }, onClick = { sheet.insertColumn(selection.left); moreMenu = false; refresh++ })
                            DropdownMenuItem(text = { Text("Delete selected column") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { sheet.deleteColumn(selection.left); moreMenu = false; refresh++ })
                        }
                    }
                }
            )

            Row(
                Modifier.fillMaxWidth().height(40.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                workbook.sheets.forEachIndexed { index, tab ->
                    val selected = index == activeSheet
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(end = 4.dp).clickable {
                            activeSheet = index; activeCell = CellAddress(0, 0); selection = CellRange(activeCell, activeCell); formulaText = ""; rangeMode = false; refresh++
                        }
                    ) {
                        Text(tab.name, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
                IconButton(onClick = { workbook.addSheet(); refresh++ }) { Icon(Icons.Default.Add, "Add sheet") }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(72.dp).height(48.dp).border(1.dp, MaterialTheme.colorScheme.outline).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    Text(selection.toString(), fontSize = 12.sp, maxLines = 1)
                }
                Text("fx", Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextField(
                    value = formulaText,
                    onValueChange = { formulaText = it },
                    modifier = Modifier.weight(1f).height(54.dp),
                    singleLine = true,
                    placeholder = { Text("Enter text or formula", fontSize = 13.sp) }
                )
                IconButton(onClick = { editCell(activeCell, parseInput(formulaText)); rangeMode = false }) { Icon(Icons.Default.Check, "Apply") }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (rangeMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { rangeMode = !rangeMode }
                ) { Text(if (rangeMode) "Range: ON" else "Range select", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.width(8.dp))
                Text(if (rangeMode) "Tap the second cell" else "Tap a cell to select", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // One vertical list keeps row numbers and spreadsheet rows perfectly synchronized.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.width(rowHeaderWidth)) {
                    Box(Modifier.height(headerHeight).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).border(.5.dp, MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) { Text("#", fontSize = 11.sp) }
                    Spacer(Modifier.height(0.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(Modifier.height(headerHeight).horizontalScroll(horizontal)) {
                        repeat(DEFAULT_COLUMNS) { c ->
                            Box(Modifier.width(columnWidth).height(headerHeight).background(MaterialTheme.colorScheme.surfaceVariant).border(.5.dp, MaterialTheme.colorScheme.outline).clickable {
                                selection = CellRange(CellAddress(0, c), CellAddress(DEFAULT_ROWS - 1, c)); activeCell = CellAddress(0, c); rangeMode = false; refresh++
                            }, contentAlignment = Alignment.Center) { Text(columnName(c), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    LazyColumn {
                        items((0 until DEFAULT_ROWS).toList()) { r ->
                            Row {
                                val rowSelected = selection.top <= r && r <= selection.bottom
                                Box(
                                    Modifier.width(rowHeaderWidth).height(cellHeight)
                                        .background(if (rowSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                        .border(.5.dp, MaterialTheme.colorScheme.outline)
                                        .clickable { selection = CellRange(CellAddress(r, 0), CellAddress(r, DEFAULT_COLUMNS - 1)); activeCell = CellAddress(r, 0); rangeMode = false; refresh++ },
                                    contentAlignment = Alignment.Center
                                ) { Text("${r + 1}", fontSize = 11.sp) }
                                Row(Modifier.horizontalScroll(horizontal)) {
                                    repeat(DEFAULT_COLUMNS) { c ->
                                        val address = CellAddress(r, c)
                                        val selected = selection.contains(address)
                                        val cell = sheet.cell(address)
                                        val align = when (cell.style.horizontalAlignment) {
                                            HorizontalAlignment.Left, HorizontalAlignment.General -> TextAlign.Start
                                            HorizontalAlignment.Center -> TextAlign.Center
                                            HorizontalAlignment.Right -> TextAlign.End
                                        }
                                        val fill = cell.style.fillArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.surface
                                        val textColor = cell.style.textArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface
                                        Box(
                                            Modifier.width(columnWidth).height(cellHeight)
                                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .13f) else fill)
                                                .border(if (selected) 2.dp else if (cell.style.border == BorderStyle.Thin) 1.dp else .5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                                .clickable { selectCell(address) }
                                                .padding(horizontal = 7.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                valueText(cell.value),
                                                Modifier.fillMaxWidth(),
                                                fontSize = 12.sp,
                                                color = textColor,
                                                fontWeight = if (cell.style.bold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (cell.style.italic) FontStyle.Italic else FontStyle.Normal,
                                                textDecoration = if (cell.style.underline) TextDecoration.Underline else TextDecoration.None,
                                                textAlign = align,
                                                maxLines = if (cell.style.wrapText) 2 else 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                BottomTool("B", "Bold") { updateStyle(sheet, selection) { it.copy(bold = !it.bold) }; refresh++ }
                BottomTool("A", "Font") { toolsMenu = true }
                BottomTool("▣", "Fill") { updateStyle(sheet, selection) { it.copy(fillArgb = if (it.fillArgb == null) 0xFFE8F5E9.toInt() else null) }; refresh++ }
                BottomTool("↔", "Merge") {
                    if (selection.isSingleCell) toolsMenu = true else { sheet.merge(selection); refresh++ }
                }
                BottomTool("☷", "Tools") { toolsMenu = true }
                BottomTool("⌨", "Keyboard") { /* formula bar receives focus when tapped */ }
            }

            DropdownMenu(expanded = toolsMenu, onDismissRequest = { toolsMenu = false }) {
                DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { copySelection(); toolsMenu = false })
                DropdownMenuItem(text = { Text("Paste") }, leadingIcon = { Icon(Icons.Default.ContentPaste, null) }, enabled = copied != null, onClick = { pasteSelection(); toolsMenu = false })
                DropdownMenuItem(text = { Text("Left align") }, leadingIcon = { Icon(Icons.Default.FormatAlignLeft, null) }, onClick = { setAlignment(HorizontalAlignment.Left); toolsMenu = false })
                DropdownMenuItem(text = { Text("Center align") }, leadingIcon = { Icon(Icons.Default.FormatAlignCenter, null) }, onClick = { setAlignment(HorizontalAlignment.Center); toolsMenu = false })
                DropdownMenuItem(text = { Text("Right align") }, leadingIcon = { Icon(Icons.Default.FormatAlignRight, null) }, onClick = { setAlignment(HorizontalAlignment.Right); toolsMenu = false })
                DropdownMenuItem(text = { Text("Wrap text") }, onClick = { toggleWrap(); toolsMenu = false })
                DropdownMenuItem(text = { Text("Border") }, onClick = { toggleBorder(); toolsMenu = false })
                DropdownMenuItem(text = { Text("Merge cells") }, leadingIcon = { Icon(Icons.Default.MergeType, null) }, enabled = !selection.isSingleCell, onClick = { sheet.merge(selection); toolsMenu = false; refresh++ })
                DropdownMenuItem(text = { Text("Unmerge cells") }, onClick = { sheet.unmerge(selection); toolsMenu = false; refresh++ })
                DropdownMenuItem(text = { Text("Delete contents") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { sheet.clear(selection); toolsMenu = false; refresh++ })
            }
        }
    }
}

private data class SpreadsheetCellSnapshot(val value: CellValue, val style: CellStyle)

@Composable
private fun BottomTool(label: String, title: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).clickable(onClick = onClick)) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(title, fontSize = 10.sp, maxLines = 1)
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
