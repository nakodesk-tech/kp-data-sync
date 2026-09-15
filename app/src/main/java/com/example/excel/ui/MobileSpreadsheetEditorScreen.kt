package com.example.excel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.excel.engine.*
import java.util.Locale

private val ExcelGreen = Color(0xFF107C41)
private val ExcelDarkGreen = Color(0xFF0F5132)
private val ExcelHeaderGreen = Color(0xFFD1E7DD)
private val ExcelHeaderBg = Color(0xFFF1F5F9)
private val ExcelGridBorder = Color(0xFFE2E8F0)
private val ExcelSelectionFill = Color(0x1A107C41)

private val RowHeaderWidth = 44.dp
private val ColumnWidth = 98.dp
private val CellHeight = 37.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun MobileSpreadsheetEditorScreen(
    workbook: SpreadsheetWorkbook,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    var sheetIndex by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(CellAddress(0, 0)) }
    var anchor by remember { mutableStateOf(active) }
    var selection by remember { mutableStateOf(CellRange(active, active)) }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<CellValue?>(null) }
    var cellFocusRequest by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<List<List<Snap>>?>(null) }
    var rename by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    val history = remember { SpreadsheetHistory() }
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberLazyListState()
    val cellFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val safeSheetIndex = sheetIndex.coerceIn(0, workbook.sheets.lastIndex.coerceAtLeast(0))
    val sheet = workbook.sheets[safeSheetIndex]
    val engine = remember(workbook, refresh) {
        FormulaEngine { name -> workbook.sheets.firstOrNull { it.name.equals(name, true) } }
    }
    val rows = maxOf(60, sheet.maxRow() + 25)
    val cols = maxOf(16, sheet.maxColumn() + 5)

    fun commit() {
        val old = original ?: run {
            editing = false
            return
        }
        val nv = sheet.valueAt(active)
        if (old != nv) {
            sheet.setValue(active, old)
            history.execute(SetCellCommand(sheet, active, nv))
        }
        original = null
        editing = false
        cellFocusRequest = false
        text = raw(sheet.valueAt(active))
        refresh++
    }

    fun draft(s: String) {
        if (!editing) {
            original = sheet.valueAt(active)
            editing = true
        }
        text = s
        sheet.setValue(active, parse(s))
        refresh++
    }

    fun select(a: CellAddress, extend: Boolean = false) {
        commit()
        active = a
        if (extend) {
            selection = CellRange(anchor, a)
        } else {
            anchor = a
            selection = CellRange(a, a)
        }
        text = raw(sheet.valueAt(a))
        refresh++
    }

    fun startEdit(a: CellAddress) {
        active = a
        anchor = a
        selection = CellRange(a, a)
        text = raw(sheet.valueAt(a))
        original = sheet.valueAt(a)
        editing = true
        cellFocusRequest = true
        refresh++
    }

    fun finish() {
        commit()
        keyboard?.hide()
    }

    fun style(f: (CellStyle) -> CellStyle) {
        commit()
        history.execute(StyleRangeCommand(sheet, selection, f))
        refresh++
    }

    fun clear() {
        commit()
        history.execute(ClearRangeCommand(sheet, selection))
        text = ""
        refresh++
    }

    fun copy() {
        commit()
        copied = (selection.top..selection.bottom).map { r ->
            (selection.left..selection.right).map { c ->
                val a = CellAddress(r, c)
                Snap(sheet.valueAt(a), sheet.cell(a).style)
            }
        }
    }

    fun paste() {
        commit()
        val d = copied ?: return
        val edits = mutableListOf<Pair<CellAddress, Pair<CellValue, CellStyle?>>>()
        d.forEachIndexed { r, row ->
            row.forEachIndexed { c, x ->
                edits += CellAddress(active.row + r, active.column + c) to (x.value to x.style)
            }
        }
        history.execute(BatchEditCommand(sheet, edits, "Paste cells"))
        refresh++
    }

    fun autoSum() {
        val r = if (selection.isSingleCell) {
            val c = active.column
            var t = active.row - 1
            while (t >= 0 && sheet.valueAt(CellAddress(t, c)) !is CellValue.Empty) t--
            "${CellAddress(t + 1, c)}:${CellAddress(active.row - 1, c)}"
        } else selection.toString()
        commit()
        history.execute(SetCellCommand(sheet, active, CellValue.Formula("=SUM($r)")))
        text = "=SUM($r)"
        refresh++
    }

    Surface(
        modifier = Modifier.fillMaxSize().imePadding(),
        color = Color.White
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top App Bar
            Surface(
                color = ExcelGreen,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { finish(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Excel Editor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${sheet.name} • $selection", color = Color.White.copy(0.82f), fontSize = 10.sp)
                    }
                    IconButton(
                        onClick = {
                            finish()
                            if (history.undo()) {
                                text = raw(sheet.valueAt(active))
                                refresh++
                            }
                        },
                        enabled = history.canUndo()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (history.canUndo()) Color.White else Color.White.copy(0.4f))
                    }
                    IconButton(
                        onClick = {
                            finish()
                            if (history.redo()) {
                                text = raw(sheet.valueAt(active))
                                refresh++
                            }
                        },
                        enabled = history.canRedo()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (history.canRedo()) Color.White else Color.White.copy(0.4f))
                    }
                    Button(
                        onClick = { finish(); onSave() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ExcelGreen),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box {
                        IconButton(onClick = { more = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text("Select All") },
                                onClick = {
                                    commit()
                                    selection = CellRange(CellAddress(0, 0), CellAddress(rows - 1, cols - 1))
                                    active = CellAddress(0, 0)
                                    more = false
                                    refresh++
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Sheet") },
                                onClick = {
                                    workbook.addSheet()
                                    more = false
                                    refresh++
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename Sheet") },
                                onClick = {
                                    more = false
                                    rename = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Selection") },
                                onClick = {
                                    clear()
                                    more = false
                                }
                            )
                        }
                    }
                }
            }

            // Formula Bar
            Surface(
                color = Color(0xFFF8FAFC),
                border = BorderStroke(0.5.dp, ExcelGridBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Text(
                            text = selection.toString(),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ExcelDarkGreen
                        )
                    }
                    Text(
                        text = "fx",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = ExcelGreen,
                        modifier = Modifier.padding(horizontal = 5.dp)
                    )
                    TextField(
                        value = text,
                        onValueChange = { draft(it) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .onFocusChanged {
                                if (it.isFocused && !editing) {
                                    original = sheet.valueAt(active)
                                    editing = true
                                }
                            },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = ExcelGreen,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finish() }),
                        placeholder = { Text("Enter value or =FORMULA", fontSize = 11.sp) }
                    )
                    IconButton(
                        onClick = { finish() },
                        modifier = Modifier
                            .size(32.dp)
                            .background(ExcelGreen, CircleShape)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply", tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // Quick Formula Chips
            if (editing || text.startsWith("=")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf("Σ SUM", "AVERAGE", "COUNT", "MAX", "MIN", "IF").forEach { f ->
                        Chip(f) {
                            draft(if (text.startsWith("=")) text + f.replace("Σ ", "") + "(" else "=${f.replace("Σ ", "")}(")
                        }
                    }
                    Chip("AutoSum") { autoSum() }
                }
            }

            // Format Bar
            val st = sheet.cell(active).style
            Surface(
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .height(38.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    T("Cut", Icons.Default.ContentCut) { copy(); clear() }
                    T("Copy", Icons.Default.ContentCopy) { copy() }
                    T("Paste", Icons.Default.ContentPaste, enabled = copied != null) { paste() }
                    T("Clear", Icons.Default.DeleteOutline) { clear() }
                    D()
                    F(Icons.Default.FormatBold, st.bold) { style { it.copy(bold = !it.bold) } }
                    F(Icons.Default.FormatItalic, st.italic) { style { it.copy(italic = !it.italic) } }
                    F(Icons.Default.FormatUnderlined, st.underline) { style { it.copy(underline = !it.underline) } }
                    D()
                    T("Left", Icons.AutoMirrored.Filled.FormatAlignLeft) { style { it.copy(horizontalAlignment = HorizontalAlignment.Left) } }
                    T("Center", Icons.Default.FormatAlignCenter) { style { it.copy(horizontalAlignment = HorizontalAlignment.Center) } }
                    T("Right", Icons.AutoMirrored.Filled.FormatAlignRight) { style { it.copy(horizontalAlignment = HorizontalAlignment.Right) } }
                    T("Wrap", Icons.AutoMirrored.Filled.WrapText) { style { it.copy(wrapText = !it.wrapText) } }
                    D()
                    T("Fill", Icons.Default.FormatColorFill) { style { it.copy(fillArgb = 0xFFE8F5E9.toInt()) } }
                    T("Border", Icons.Default.BorderAll) {
                        style { it.copy(border = if (it.border == BorderStyle.None) BorderStyle.Thin else BorderStyle.None) }
                    }
                    T("+Row", Icons.Default.Add) { commit(); sheet.insertRow(active.row); refresh++ }
                    T("-Row", Icons.Default.Remove) { commit(); sheet.deleteRow(active.row); refresh++ }
                    T("+Col", Icons.Default.AddBox) { commit(); sheet.insertColumn(active.column); refresh++ }
                    T("-Col", Icons.Default.IndeterminateCheckBox) { commit(); sheet.deleteColumn(active.column); refresh++ }
                }
            }

            // Grid
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                    // Column Headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(ExcelHeaderBg)
                            .border(BorderStroke(0.5.dp, Color(0xFFCBD5E1)))
                    ) {
                        Box(
                            modifier = Modifier.width(RowHeaderWidth).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⊞", fontSize = 13.sp)
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScroll)
                        ) {
                            repeat(cols) { c ->
                                val isSelected = c in selection.left..selection.right
                                Box(
                                    modifier = Modifier
                                        .width(ColumnWidth)
                                        .fillMaxHeight()
                                        .border(BorderStroke(0.5.dp, Color(0xFFCBD5E1)))
                                        .background(if (isSelected) ExcelHeaderGreen else ExcelHeaderBg)
                                        .clickable {
                                            select(CellAddress(0, c))
                                            selection = CellRange(CellAddress(0, c), CellAddress(rows - 1, c))
                                            refresh++
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(col(c), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Worksheet Rows
                    LazyColumn(
                        state = verticalScroll,
                        modifier = Modifier.weight(1f)
                    ) {
                        items((0 until rows).toList(), key = { it }) { r ->
                            val isRowSelected = r in selection.top..selection.bottom
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(CellHeight)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(RowHeaderWidth)
                                        .fillMaxHeight()
                                        .background(if (isRowSelected) ExcelHeaderGreen else ExcelHeaderBg)
                                        .border(BorderStroke(0.5.dp, Color(0xFFCBD5E1)))
                                        .clickable {
                                            select(CellAddress(r, 0))
                                            selection = CellRange(CellAddress(r, 0), CellAddress(r, cols - 1))
                                            refresh++
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${r + 1}", fontSize = 11.sp)
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(horizontalScroll)
                                ) {
                                    repeat(cols) { c ->
                                        val a = CellAddress(r, c)
                                        val cell = sheet.cell(a)
                                        val activeCell = a == active
                                        val value = display(sheet, cell, engine)
                                        val fillArgb = cell.style.fillArgb
                                        val bg = when {
                                            activeCell -> Color.White
                                            selection.contains(a) -> ExcelSelectionFill
                                            fillArgb != null -> Color(fillArgb)
                                            else -> Color.White
                                        }
                                        val border = when {
                                            activeCell -> BorderStroke(2.dp, ExcelGreen)
                                            else -> BorderStroke(0.5.dp, ExcelGridBorder)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(ColumnWidth)
                                                .fillMaxHeight()
                                                .background(bg)
                                                .border(border)
                                                .combinedClickable(
                                                    onClick = { select(a) },
                                                    onLongClick = { select(a, true) },
                                                    onDoubleClick = { startEdit(a) }
                                                )
                                                .padding(horizontal = 5.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (editing && activeCell) {
                                                TextField(
                                                    value = text,
                                                    onValueChange = { draft(it) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(34.dp)
                                                        .focusRequester(cellFocus),
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent
                                                    ),
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(onDone = { finish() }),
                                                    textStyle = LocalTextStyle.current.copy(
                                                        fontSize = 12.sp,
                                                        fontWeight = if (cell.style.bold) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                )
                                            } else {
                                                Text(
                                                    text = value,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    fontSize = 12.sp,
                                                    fontWeight = if (cell.style.bold) FontWeight.Bold else FontWeight.Normal,
                                                    textDecoration = if (cell.style.underline) TextDecoration.Underline else TextDecoration.None,
                                                    textAlign = when (cell.style.horizontalAlignment) {
                                                        HorizontalAlignment.Center -> TextAlign.Center
                                                        HorizontalAlignment.Right -> TextAlign.End
                                                        else -> TextAlign.Start
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Sheets Bar
            Surface(
                color = Color(0xFFE2E8F0),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(46.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        workbook.sheets.forEachIndexed { idx, sh ->
                            val isActiveTab = idx == safeSheetIndex
                            Surface(
                                color = if (isActiveTab) Color.White else ExcelHeaderBg,
                                border = BorderStroke(0.5.dp, if (isActiveTab) ExcelGreen else Color(0xFFCBD5E1)),
                                modifier = Modifier.clickable {
                                    finish()
                                    sheetIndex = idx
                                    active = CellAddress(0, 0)
                                    anchor = active
                                    selection = CellRange(active, active)
                                    text = raw(workbook.sheets[idx].valueAt(active))
                                    refresh++
                                }
                            ) {
                                Text(
                                    text = sh.name,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = if (isActiveTab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActiveTab) ExcelDarkGreen else Color(0xFF475569)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            finish()
                            workbook.addSheet()
                            refresh++
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(ExcelGreen, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Sheet", tint = Color.White)
                    }
                }
            }
        }
    }

    LaunchedEffect(cellFocusRequest) {
        if (cellFocusRequest) {
            cellFocus.requestFocus()
            keyboard?.show()
            cellFocusRequest = false
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(sheet.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename Sheet") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Sheet Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            sheet.name = name.trim()
                            rename = false
                            refresh++
                        }
                    }
                ) {
                    Text("Rename", color = ExcelGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rename = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class Snap(val value: CellValue, val style: CellStyle)

@Composable
private fun T(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Color(0xFF334155) else Color.LightGray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun F(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) ExcelHeaderGreen else Color.Transparent,
        modifier = Modifier.size(36.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) ExcelDarkGreen else Color(0xFF334155),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun D() {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .width(1.dp)
            .height(22.dp)
            .background(ExcelGridBorder)
    )
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        color = Color.White,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = ExcelDarkGreen
        )
    }
}

private fun display(s: SpreadsheetSheet, c: SpreadsheetCell, e: FormulaEngine): String = when (val v = c.value) {
    CellValue.Empty -> ""
    is CellValue.Text -> v.value
    is CellValue.Number -> fmt(v.value)
    is CellValue.BooleanValue -> if (v.value) "TRUE" else "FALSE"
    is CellValue.Formula -> when (val x = e.evaluate(s.name, v.expression)) {
        is CellValue.Number -> fmt(x.value)
        is CellValue.Text -> x.value
        is CellValue.BooleanValue -> if (x.value) "TRUE" else "FALSE"
        is CellValue.Error -> x.code
        else -> v.expression
    }
    is CellValue.Error -> v.code
}

private fun raw(v: CellValue): String = when (v) {
    CellValue.Empty -> ""
    is CellValue.Text -> v.value
    is CellValue.Number -> fmt(v.value)
    is CellValue.BooleanValue -> if (v.value) "TRUE" else "FALSE"
    is CellValue.Formula -> v.expression
    is CellValue.Error -> v.code
}

private fun parse(s: String): CellValue {
    val v = s.trim()
    if (v.isEmpty()) return CellValue.Empty
    if (v.startsWith("=")) return CellValue.Formula(v)
    v.toDoubleOrNull()?.let { return CellValue.Number(it) }
    if (v.equals("true", true)) return CellValue.BooleanValue(true)
    if (v.equals("false", true)) return CellValue.BooleanValue(false)
    return CellValue.Text(v)
}

private fun fmt(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')

private fun col(c: Int): String {
    var n = c + 1
    val b = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        b.append(('A'.code + r).toChar())
        n = (n - 1) / 26
    }
    return b.reverse().toString()
}
