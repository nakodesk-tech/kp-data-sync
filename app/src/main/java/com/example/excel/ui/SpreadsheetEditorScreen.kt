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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.excel.engine.CellAddress
import com.example.excel.engine.CellRange
import com.example.excel.engine.CellStyle
import com.example.excel.engine.CellValue
import com.example.excel.engine.SpreadsheetWorkbook

private const val DEFAULT_ROWS = 40
private const val DEFAULT_COLUMNS = 12
private val rowHeaderWidth = 52.dp
private val columnWidth = 110.dp
private val cellHeight = 44.dp

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
    var formulaText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val sheet = workbook.sheet(activeSheet)
    val horizontal = rememberScrollState()

    fun select(cell: CellAddress) {
        activeCell = cell
        selection = CellRange(cell, cell)
        formulaText = valueText(sheet.valueAt(cell))
        editing = false
    }

    Surface(modifier = Modifier.fillMaxSize()) {
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
                    IconButton(onClick = { refresh++; onSave() }) { Icon(Icons.Default.Check, "Save") }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "More") }
                }
            )

            // Compact Excel-style command strip: the common actions stay visible.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton("Undo", Icons.Default.Undo)
                ToolButton("Redo", Icons.Default.Redo)
                ToolButton("Copy", Icons.Default.ContentCopy)
                ToolButton("Paste", Icons.Default.ContentPaste)
                ToolButton("Bold", Icons.Default.FormatBold) {
                    updateStyle(sheet, selection) { it.copy(bold = !it.bold) }; refresh++
                }
                ToolButton("Italic", Icons.Default.FormatItalic) {
                    updateStyle(sheet, selection) { it.copy(italic = !it.italic) }; refresh++
                }
                ToolButton("Underline", Icons.Default.FormatUnderlined) {
                    updateStyle(sheet, selection) { it.copy(underline = !it.underline) }; refresh++
                }
                Spacer(Modifier.width(4.dp))
                ActionText("Merge") { sheet.merge(selection); refresh++ }
                ActionText("Clear") { sheet.clear(selection); formulaText = ""; refresh++ }
                ActionText("+") { sheet.insertRow(selection.top); refresh++ }
                ActionText("−") { sheet.deleteRow(selection.top); refresh++ }
            }

            // Formula/value bar, deliberately kept directly under the command strip.
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(74.dp).border(1.dp, MaterialTheme.colorScheme.outline).padding(8.dp)) {
                    Text(selection.toString(), fontSize = 13.sp)
                }
                Text("fx", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                TextField(
                    value = formulaText,
                    onValueChange = { formulaText = it; editing = true },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Enter value or formula") },
                    leadingIcon = if (editing) null else ({ Icon(Icons.Default.Close, null, Modifier.clickable { formulaText = "" }) })
                )
                IconButton(onClick = {
                    sheet.setValue(activeCell, parseInput(formulaText));
                    editing = false; refresh++
                }) { Icon(Icons.Default.Check, "Apply") }
            }

            // Horizontally scrollable worksheet. Row numbers and column letters remain obvious.
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.width(rowHeaderWidth)) {
                    Box(Modifier.height(34.dp).fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline))
                    LazyColumn {
                        items((0 until DEFAULT_ROWS).toList()) { r ->
                            val selected = selection.top == r && selection.bottom == r
                            Box(
                                Modifier.height(cellHeight).fillMaxWidth()
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                    .clickable {
                                        selection = CellRange(CellAddress(r, 0), CellAddress(r, DEFAULT_COLUMNS - 1)); activeCell = CellAddress(r, 0)
                                    }, contentAlignment = Alignment.Center
                            ) { Text("${r + 1}", fontSize = 12.sp) }
                        }
                    }
                }
                Column(Modifier.horizontalScroll(horizontal).weight(1f)) {
                    Row(Modifier.height(34.dp)) {
                        repeat(DEFAULT_COLUMNS) { c ->
                            Box(Modifier.width(columnWidth).fillMaxWidth().border(0.5.dp, MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) {
                                Text(columnName(c), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                    LazyColumn {
                        items((0 until DEFAULT_ROWS).toList()) { r ->
                            Row {
                                repeat(DEFAULT_COLUMNS) { c ->
                                    val address = CellAddress(r, c)
                                    val selected = selection.contains(address)
                                    val cell = sheet.cell(address)
                                    Box(
                                        Modifier.width(columnWidth).height(cellHeight)
                                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                                            .border(if (selected) 2.dp else 0.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                            .clickable { select(address) }
                                            .padding(horizontal = 7.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(valueText(cell.value), fontSize = 13.sp, fontWeight = if (cell.style.bold) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sheet tabs: familiar spreadsheet navigation without hiding the worksheet.
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                workbook.sheets.forEachIndexed { index, tab ->
                    Surface(
                        tonalElevation = if (index == activeSheet) 3.dp else 0.dp,
                        modifier = Modifier.padding(end = 6.dp).clickable { activeSheet = index; select(CellAddress(0, 0)) }
                    ) { Text(tab.name, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp) }
                }
                IconButton(onClick = { workbook.addSheet(); refresh++ }) { Icon(Icons.Default.Add, "Add sheet") }
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val keepRefresh = refresh
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp).clickable(onClick = onClick)) {
        IconButton(onClick = onClick) { Icon(icon, label) }
        Text(label, fontSize = 9.sp)
    }
}

@Composable
private fun ActionText(label: String, onClick: () -> Unit) {
    Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp).clickable(onClick = onClick), fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

private fun updateStyle(sheet: com.example.excel.engine.SpreadsheetSheet, range: CellRange, transform: (CellStyle) -> CellStyle) {
    range.addresses().forEach { address ->
        val cell = sheet.cell(address)
        cell.style = transform(cell.style)
    }
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
    while (n > 0) {
        val r = (n - 1) % 26
        out.append(('A'.code + r).toChar())
        n = (n - 1) / 26
    }
    return out.reverse().toString()
}
