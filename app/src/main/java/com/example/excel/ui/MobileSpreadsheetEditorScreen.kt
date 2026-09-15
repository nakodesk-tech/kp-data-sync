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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
private val ColumnWidth = 96.dp
private val CellHeight = 36.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun MobileSpreadsheetEditorScreen(
    workbook: SpreadsheetWorkbook,
    fileName: String = "Schools.xlsx",
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

    var showMoreMenu by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    var showFillSheet by remember { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }
    var showSheetManager by remember { mutableStateOf(false) }
    var renameTargetSheetIndex by remember { mutableStateOf<Int?>(null) }

    var copied by remember { mutableStateOf<List<List<Snap>>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val history = remember { SpreadsheetHistory() }
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberLazyListState()
    val formulaInputFocus = remember { FocusRequester() }
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
        showSuggestions = s.startsWith("=")
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
        showSuggestions = false
        refresh++
    }

    fun startEdit(a: CellAddress) {
        commit()
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
        showSuggestions = false
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

    fun insertFormula(fnName: String) {
        if (!editing) {
            original = sheet.valueAt(active)
            editing = true
        }
        val current = text.trim()
        val rangeStr = if (selection.isSingleCell) {
            val c = active.column
            var t = active.row - 1
            while (t >= 0 && sheet.valueAt(CellAddress(t, c)) !is CellValue.Empty) t--
            val startRow = (t + 1).coerceAtLeast(0)
            val endRow = (active.row - 1).coerceAtLeast(startRow)
            "${CellAddress(startRow, c)}:${CellAddress(endRow, c)}"
        } else {
            selection.toString()
        }

        val formulaStr = if (fnName.equals("AutoSum", true) || fnName.equals("SUM", true)) {
            "=SUM($rangeStr)"
        } else if (current.startsWith("=")) {
            "$current$fnName($rangeStr)"
        } else {
            "=$fnName($rangeStr)"
        }

        text = formulaStr
        sheet.setValue(active, parse(formulaStr))
        showSuggestions = false
        refresh++
    }

    fun mergeOrWrap() {
        commit()
        if (selection.isSingleCell) {
            val st = sheet.cell(active).style
            style { it.copy(wrapText = !it.wrapText) }
        } else {
            // Combine values of selection into top-left cell
            val top = selection.top
            val left = selection.left
            val combined = (selection.top..selection.bottom).flatMap { r ->
                (selection.left..selection.right).mapNotNull { c ->
                    val v = raw(sheet.valueAt(CellAddress(r, c)))
                    v.takeIf { it.isNotBlank() }
                }
            }.joinToString(" ")
            history.execute(ClearRangeCommand(sheet, selection))
            sheet.setValue(CellAddress(top, left), CellValue.Text(combined))
            text = combined
            refresh++
        }
    }

    val activeStyle = sheet.cell(active).style

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Compact App Bar
        Surface(
            color = ExcelGreen,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                        IconButton(onClick = { finish(); onBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "Excel Editor",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = fileName,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (history.canUndo()) Color.White else Color.White.copy(alpha = 0.4f)
                            )
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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (history.canRedo()) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = { finish(); onSave() }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save",
                                tint = Color.White
                            )
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Select All") },
                                    onClick = {
                                        commit()
                                        selection = CellRange(CellAddress(0, 0), CellAddress(rows - 1, cols - 1))
                                        active = CellAddress(0, 0)
                                        showMoreMenu = false
                                        refresh++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Selection") },
                                    onClick = {
                                        clear()
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add Sheet") },
                                    onClick = {
                                        workbook.addSheet()
                                        sheetIndex = workbook.sheets.lastIndex
                                        showMoreMenu = false
                                        refresh++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename Sheet") },
                                    onClick = {
                                        renameTargetSheetIndex = safeSheetIndex
                                        showMoreMenu = false
                                    }
                                )
                                if (workbook.sheets.size > 1) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Sheet", color = Color(0xFFDC2626)) },
                                        onClick = {
                                            workbook.removeSheet(safeSheetIndex)
                                            sheetIndex = 0.coerceAtMost(workbook.sheets.lastIndex)
                                            showMoreMenu = false
                                            refresh++
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Slim Formula Bar
                Surface(
                    color = Color.White,
                    border = BorderStroke(0.5.dp, ExcelGridBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cell Address Pill
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable {
                                finish()
                                showToolsSheet = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selection.toString(),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = ExcelDarkGreen
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = ExcelDarkGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // fx badge
                        Text(
                            text = "fx",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            color = ExcelGreen,
                            modifier = Modifier
                                .clickable { showSuggestions = !showSuggestions }
                                .padding(horizontal = 6.dp)
                        )

                        // Input Box
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, if (editing) ExcelGreen else Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = text,
                                    onValueChange = { draft(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(formulaInputFocus)
                                        .onFocusChanged {
                                            if (it.isFocused && !editing) {
                                                original = sheet.valueAt(active)
                                                editing = true
                                            }
                                        },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 12.sp,
                                        color = Color(0xFF1E293B),
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(ExcelGreen),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { finish() }),
                                    decorationBox = { innerTextField ->
                                        if (text.isEmpty()) {
                                            Text(
                                                text = "fx or value",
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                if (text.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { draft("") }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        // Green Confirm Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(ExcelGreen, CircleShape)
                                .clickable { finish() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirm",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

        // 3. Maximum Sheet Space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
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
                        modifier = Modifier
                            .width(RowHeaderWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⊞",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScroll)
                    ) {
                        repeat(cols) { c ->
                            val isSelectedCol = c in selection.left..selection.right
                            Box(
                                modifier = Modifier
                                    .width(ColumnWidth)
                                    .fillMaxHeight()
                                    .border(BorderStroke(0.5.dp, Color(0xFFCBD5E1)))
                                    .background(if (isSelectedCol) ExcelHeaderGreen else ExcelHeaderBg)
                                    .clickable {
                                        select(CellAddress(0, c))
                                        selection = CellRange(CellAddress(0, c), CellAddress(rows - 1, c))
                                        refresh++
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = col(c),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelectedCol) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelectedCol) ExcelDarkGreen else Color(0xFF334155)
                                )
                                if (isSelectedCol) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(ExcelGreen)
                                    )
                                }
                            }
                        }
                    }
                }

                // Worksheet Rows (Scrollable)
                LazyColumn(
                    state = verticalScroll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items((0 until rows).toList(), key = { it }) { r ->
                        val isSelectedRow = r in selection.top..selection.bottom
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CellHeight)
                        ) {
                            // Row Header
                            Box(
                                modifier = Modifier
                                    .width(RowHeaderWidth)
                                    .fillMaxHeight()
                                    .background(if (isSelectedRow) ExcelHeaderGreen else ExcelHeaderBg)
                                    .border(BorderStroke(0.5.dp, Color(0xFFCBD5E1)))
                                    .clickable {
                                        select(CellAddress(r, 0))
                                        selection = CellRange(CellAddress(r, 0), CellAddress(r, cols - 1))
                                        refresh++
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${r + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelectedRow) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelectedRow) ExcelDarkGreen else Color(0xFF475569)
                                )
                                if (isSelectedRow) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(ExcelGreen)
                                    )
                                }
                            }

                            // Cells in Row
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(horizontalScroll)
                            ) {
                                repeat(cols) { c ->
                                    val a = CellAddress(r, c)
                                    val cell = sheet.cell(a)
                                    val activeCell = a == active
                                    val inRange = selection.contains(a)
                                    val value = display(sheet, cell, engine)
                                    val fillArgb = cell.style.fillArgb

                                    val bg = when {
                                        activeCell -> Color.White
                                        inRange -> ExcelSelectionFill
                                        fillArgb != null -> Color(fillArgb)
                                        else -> Color.White
                                    }
                                    val border = when {
                                        activeCell -> BorderStroke(2.dp, ExcelGreen)
                                        inRange -> BorderStroke(0.5.dp, ExcelGreen)
                                        else -> BorderStroke(0.5.dp, ExcelGridBorder)
                                    }

                                    val textArgb = cell.style.textArgb
                                    Box(
                                        modifier = Modifier
                                            .width(ColumnWidth)
                                            .fillMaxHeight()
                                            .background(bg)
                                            .border(border)
                                            .combinedClickable(
                                                onClick = { select(a) },
                                                onLongClick = { select(a, extend = true) },
                                                onDoubleClick = { startEdit(a) }
                                            )
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = value,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontSize = 12.sp,
                                            fontWeight = if (cell.style.bold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (cell.style.italic) FontStyle.Italic else FontStyle.Normal,
                                            textDecoration = if (cell.style.underline) TextDecoration.Underline else TextDecoration.None,
                                            maxLines = if (cell.style.wrapText) 2 else 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = when (cell.style.horizontalAlignment) {
                                                HorizontalAlignment.Center -> TextAlign.Center
                                                HorizontalAlignment.Right -> TextAlign.End
                                                else -> TextAlign.Start
                                            },
                                            color = if (textArgb != null) Color(textArgb) else Color(0xFF1E293B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Formula Suggestions (On Demand Floating Card)
            if (showSuggestions) {
                Surface(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 4.dp, end = 12.dp)
                        .widthIn(max = 320.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Suggestions",
                                tint = Color(0xFF64748B),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showSuggestions = false }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Formula Suggestions",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ExcelDarkGreen
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "=fx",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ExcelGreen
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE2E8F0))

                        val suggestions = listOf(
                            "SUM" to "Adds numbers in range",
                            "AVERAGE" to "Average of numbers",
                            "COUNT" to "Counts non-empty cells",
                            "IF" to "Logical test condition",
                            "MAX" to "Maximum value",
                            "MIN" to "Minimum value"
                        )

                        suggestions.forEach { (name, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { insertFormula(name) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "fx",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    color = ExcelGreen,
                                    modifier = Modifier.width(22.dp)
                                )
                                Text(
                                    text = name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = desc,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF1F5F9))
                        }
                    }
                }
            }
        }

        // 4. Sheet Tabs (Bottom)
        Surface(
            color = Color(0xFFF1F5F9),
            border = BorderStroke(0.5.dp, Color(0xFFCBD5E1))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showSheetManager = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Sheets Menu",
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    workbook.sheets.forEachIndexed { idx, sh ->
                        val isActiveTab = idx == safeSheetIndex
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(if (isActiveTab) Color.White else Color(0xFFF1F5F9))
                                .combinedClickable(
                                    onClick = {
                                        finish()
                                        sheetIndex = idx
                                        active = CellAddress(0, 0)
                                        anchor = active
                                        selection = CellRange(active, active)
                                        text = raw(workbook.sheets[idx].valueAt(active))
                                        refresh++
                                    },
                                    onLongClick = {
                                        renameTargetSheetIndex = idx
                                    }
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sh.name,
                                fontSize = 12.sp,
                                fontWeight = if (isActiveTab) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActiveTab) ExcelGreen else Color(0xFF64748B)
                            )
                            if (isActiveTab) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(2.5.dp)
                                        .background(ExcelGreen)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = {
                        finish()
                        workbook.addSheet()
                        sheetIndex = workbook.sheets.lastIndex
                        refresh++
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Sheet",
                        tint = ExcelGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 5. Bottom Toolbar (Essential)
        Surface(
            color = Color.White,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, ExcelGridBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomToolItem(
                    icon = Icons.Default.FormatBold,
                    label = "Bold",
                    active = activeStyle.bold,
                    onClick = { style { it.copy(bold = !it.bold) } }
                )
                BottomToolItem(
                    icon = Icons.Default.FormatColorText,
                    label = "Font",
                    active = false,
                    onClick = { showFontSheet = true }
                )
                BottomToolItem(
                    icon = Icons.Default.FormatColorFill,
                    label = "Fill",
                    active = activeStyle.fillArgb != null,
                    onClick = { showFillSheet = true }
                )
                BottomToolItem(
                    icon = Icons.AutoMirrored.Filled.CallMerge,
                    label = "Merge",
                    active = activeStyle.wrapText,
                    onClick = { mergeOrWrap() }
                )
                BottomToolItem(
                    icon = Icons.Default.GridView,
                    label = "Tools",
                    active = false,
                    onClick = { showToolsSheet = true }
                )
                BottomToolItem(
                    icon = Icons.Default.Keyboard,
                    label = "Keyboard",
                    active = editing,
                    onClick = {
                        startEdit(active)
                        formulaInputFocus.requestFocus()
                        keyboard?.show()
                    }
                )
            }
        }
    }

    LaunchedEffect(cellFocusRequest) {
        if (cellFocusRequest) {
            formulaInputFocus.requestFocus()
            keyboard?.show()
            cellFocusRequest = false
        }
    }

    // Font Sheet
    if (showFontSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFontSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Font & Text Styling", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { style { it.copy(bold = !it.bold) } },
                        modifier = Modifier.weight(1f),
                        colors = if (activeStyle.bold) ButtonDefaults.outlinedButtonColors(containerColor = ExcelHeaderGreen) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("B", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (activeStyle.bold) ExcelDarkGreen else Color.Black)
                    }
                    OutlinedButton(
                        onClick = { style { it.copy(italic = !it.italic) } },
                        modifier = Modifier.weight(1f),
                        colors = if (activeStyle.italic) ButtonDefaults.outlinedButtonColors(containerColor = ExcelHeaderGreen) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("I", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (activeStyle.italic) ExcelDarkGreen else Color.Black)
                    }
                    OutlinedButton(
                        onClick = { style { it.copy(underline = !it.underline) } },
                        modifier = Modifier.weight(1f),
                        colors = if (activeStyle.underline) ButtonDefaults.outlinedButtonColors(containerColor = ExcelHeaderGreen) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("U", textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (activeStyle.underline) ExcelDarkGreen else Color.Black)
                    }
                }

                Text("Text Color", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF475569))
                val textColors = listOf(
                    0xFF000000.toInt() to "Black",
                    0xFF107C41.toInt() to "Green",
                    0xFF1D4ED8.toInt() to "Blue",
                    0xFFDC2626.toInt() to "Red",
                    0xFFD97706.toInt() to "Orange",
                    0xFF7C3AED.toInt() to "Purple",
                    0xFF475569.toInt() to "Slate"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    textColors.forEach { (colorVal, _) ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(colorVal), CircleShape)
                                .border(BorderStroke(1.5.dp, Color(0xFFCBD5E1)), CircleShape)
                                .clickable {
                                    style { it.copy(textArgb = colorVal) }
                                    showFontSheet = false
                                }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Fill Sheet
    if (showFillSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFillSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Cell Fill Color", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))

                val fills = listOf(
                    null to "None",
                    0xFFD1E7DD.toInt() to "Excel Green",
                    0xFFE8F5E9.toInt() to "Light Mint",
                    0xFFFEF3C7.toInt() to "Warm Yellow",
                    0xFFDBEAFE.toInt() to "Soft Blue",
                    0xFFFEE2E2.toInt() to "Light Red",
                    0xFFFFEDD5.toInt() to "Light Orange",
                    0xFFF3E8FF.toInt() to "Soft Lavender",
                    0xFFF1F5F9.toInt() to "Light Gray"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    fills.forEach { (colorVal, name) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                style { it.copy(fillArgb = colorVal) }
                                showFillSheet = false
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(colorVal?.let { Color(it) } ?: Color.White, CircleShape)
                                    .border(BorderStroke(1.5.dp, Color(0xFF94A3B8)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (colorVal == null) {
                                    Icon(
                                        imageVector = Icons.Default.FormatColorReset,
                                        contentDescription = "No Fill",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(name, fontSize = 9.sp, color = Color(0xFF64748B))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Tools Sheet
    if (showToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showToolsSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Editing Tools", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))

                // Clipboard
                Text("Clipboard", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF64748B))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetToolButton("Cut", Icons.Default.ContentCut, Modifier.weight(1f)) {
                        copy(); clear(); showToolsSheet = false
                    }
                    SheetToolButton("Copy", Icons.Default.ContentCopy, Modifier.weight(1f)) {
                        copy(); showToolsSheet = false
                    }
                    SheetToolButton("Paste", Icons.Default.ContentPaste, Modifier.weight(1f), enabled = copied != null) {
                        paste(); showToolsSheet = false
                    }
                    SheetToolButton("Clear", Icons.Default.DeleteOutline, Modifier.weight(1f)) {
                        clear(); showToolsSheet = false
                    }
                }

                // Alignment & Text
                Text("Alignment & Wrap", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF64748B))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetToolButton("Left", Icons.AutoMirrored.Filled.FormatAlignLeft, Modifier.weight(1f)) {
                        style { it.copy(horizontalAlignment = HorizontalAlignment.Left) }
                    }
                    SheetToolButton("Center", Icons.Default.FormatAlignCenter, Modifier.weight(1f)) {
                        style { it.copy(horizontalAlignment = HorizontalAlignment.Center) }
                    }
                    SheetToolButton("Right", Icons.AutoMirrored.Filled.FormatAlignRight, Modifier.weight(1f)) {
                        style { it.copy(horizontalAlignment = HorizontalAlignment.Right) }
                    }
                    SheetToolButton("Wrap", Icons.AutoMirrored.Filled.WrapText, Modifier.weight(1f)) {
                        style { it.copy(wrapText = !it.wrapText) }
                    }
                }

                // Borders & Calculations
                Text("Borders & AutoSum", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF64748B))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetToolButton("Border", Icons.Default.BorderAll, Modifier.weight(1f)) {
                        style { it.copy(border = if (it.border == BorderStyle.None) BorderStyle.Thin else BorderStyle.None) }
                    }
                    SheetToolButton("AutoSum", Icons.Default.Functions, Modifier.weight(1f)) {
                        insertFormula("AutoSum"); showToolsSheet = false
                    }
                    SheetToolButton("Average", Icons.Default.Calculate, Modifier.weight(1f)) {
                        insertFormula("AVERAGE"); showToolsSheet = false
                    }
                    SheetToolButton("Count", Icons.Default.Numbers, Modifier.weight(1f)) {
                        insertFormula("COUNT"); showToolsSheet = false
                    }
                }

                // Rows & Columns
                Text("Rows & Columns", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF64748B))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetToolButton("+ Row", Icons.Default.Add, Modifier.weight(1f)) {
                        commit(); sheet.insertRow(active.row); showToolsSheet = false; refresh++
                    }
                    SheetToolButton("- Row", Icons.Default.Remove, Modifier.weight(1f)) {
                        commit(); sheet.deleteRow(active.row); showToolsSheet = false; refresh++
                    }
                    SheetToolButton("+ Col", Icons.Default.AddBox, Modifier.weight(1f)) {
                        commit(); sheet.insertColumn(active.column); showToolsSheet = false; refresh++
                    }
                    SheetToolButton("- Col", Icons.Default.IndeterminateCheckBox, Modifier.weight(1f)) {
                        commit(); sheet.deleteColumn(active.column); showToolsSheet = false; refresh++
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Sheets Manager Modal Bottom Sheet
    if (showSheetManager) {
        ModalBottomSheet(
            onDismissRequest = { showSheetManager = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sheets", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        workbook.addSheet()
                        sheetIndex = workbook.sheets.lastIndex
                        showSheetManager = false
                        refresh++
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ExcelGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Sheet", color = ExcelGreen, fontWeight = FontWeight.Bold)
                    }
                }

                workbook.sheets.forEachIndexed { idx, sh ->
                    val isCurrent = idx == safeSheetIndex
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrent) ExcelHeaderGreen else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isCurrent) ExcelGreen else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                finish()
                                sheetIndex = idx
                                active = CellAddress(0, 0)
                                anchor = active
                                selection = CellRange(active, active)
                                text = raw(workbook.sheets[idx].valueAt(active))
                                showSheetManager = false
                                refresh++
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = if (isCurrent) ExcelDarkGreen else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = sh.name,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) ExcelDarkGreen else Color(0xFF1E293B),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                renameTargetSheetIndex = idx
                                showSheetManager = false
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            }
                            if (workbook.sheets.size > 1) {
                                IconButton(onClick = {
                                    workbook.removeSheet(idx)
                                    sheetIndex = 0.coerceAtMost(workbook.sheets.lastIndex)
                                    showSheetManager = false
                                    refresh++
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Rename Dialog
    renameTargetSheetIndex?.let { targetIdx ->
        val currentSheet = workbook.sheets.getOrNull(targetIdx)
        if (currentSheet != null) {
            var newName by remember(targetIdx) { mutableStateOf(currentSheet.name) }
            AlertDialog(
                onDismissRequest = { renameTargetSheetIndex = null },
                title = { Text("Rename Sheet") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Sheet Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                currentSheet.name = newName.trim()
                                renameTargetSheetIndex = null
                                refresh++
                            }
                        }
                    ) {
                        Text("Rename", color = ExcelGreen, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTargetSheetIndex = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private data class Snap(val value: CellValue, val style: CellStyle)

@Composable
private fun RowScope.BottomToolItem(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) ExcelGreen else Color(0xFF334155),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) ExcelGreen else Color(0xFF475569)
        )
    }
}

@Composable
private fun SheetToolButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (enabled) Color(0xFFF8FAFC) else Color(0xFFF1F5F9),
        border = BorderStroke(0.5.dp, Color(0xFFCBD5E1))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) Color(0xFF334155) else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color(0xFF1E293B) else Color(0xFF94A3B8)
            )
        }
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
