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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.excel.engine.*
import java.util.Locale

// Excel brand colors & styling
private val ExcelGreen = Color(0xFF107C41)
private val ExcelDarkGreen = Color(0xFF0F5132)
private val ExcelSelectionFill = Color(0x1A107C41)
private val ExcelHeaderActive = Color(0xFFD1E7DD)
private val ExcelGridBorder = Color(0xFFE2E8F0)
private val ExcelHeaderBg = Color(0xFFF1F5F9)
private val ExcelHeaderBorder = Color(0xFFCBD5E1)

private val RowHeaderWidth = 46.dp
private val ColumnWidth = 98.dp
private val CellHeight = 38.dp
private val HeaderHeight = 32.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpreadsheetEditorScreen(
    workbook: SpreadsheetWorkbook = remember { SpreadsheetWorkbook() },
    onBack: () -> Unit = {},
    onSave: () -> Unit = {}
) {
    var activeSheet by remember { mutableIntStateOf(0) }
    var activeCell by remember { mutableStateOf(CellAddress(0, 0)) }
    var anchorCell by remember { mutableStateOf(CellAddress(0, 0)) }
    var selection by remember { mutableStateOf(CellRange(activeCell, activeCell)) }
    var formulaText by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var moreMenu by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showTextColorPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showNewSheetDialog by remember { mutableStateOf(false) }
    var showQuickEditDialog by remember { mutableStateOf(false) }
    var noticeText by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf<List<List<SpreadsheetCellSnapshot>>?>(null) }
    val history = remember { SpreadsheetHistory() }

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberLazyListState()

    val safeSheetIndex = activeSheet.coerceIn(0, workbook.sheets.lastIndex.coerceAtLeast(0))
    val sheet = workbook.sheets[safeSheetIndex]

    val formulaEngine = remember(workbook, refresh) {
        FormulaEngine { name -> workbook.sheets.firstOrNull { it.name.equals(name, true) } }
    }

    val totalRows = remember(sheet, refresh) { maxOf(60, sheet.maxRow() + 25) }
    val totalCols = remember(sheet, refresh) { maxOf(16, sheet.maxColumn() + 5) }

    fun notify(msg: String) {
        noticeText = msg
    }

    fun selectCell(address: CellAddress, extend: Boolean = false) {
        activeCell = address
        if (extend) {
            selection = CellRange(anchorCell, address)
        } else {
            anchorCell = address
            selection = CellRange(address, address)
        }
        formulaText = rawValueText(sheet.valueAt(address))
        refresh++
    }

    fun editCell(address: CellAddress, value: CellValue) {
        history.execute(SetCellCommand(sheet, address, value))
        formulaText = rawValueText(sheet.valueAt(address))
        refresh++
    }

    fun copySelection() {
        copied = (selection.top..selection.bottom).map { r ->
            (selection.left..selection.right).map { c ->
                val addr = CellAddress(r, c)
                SpreadsheetCellSnapshot(sheet.valueAt(addr), sheet.cell(addr).style)
            }
        }
        notify("Copied ${selection.rowCount}×${selection.columnCount} cells")
    }

    fun cutSelection() {
        copySelection()
        val edits = selection.addresses().map { it to (CellValue.Empty to null) }.toList()
        history.execute(BatchEditCommand(sheet, edits, "Cut $selection"))
        formulaText = ""
        notify("Cut $selection")
        refresh++
    }

    fun pasteSelection() {
        val data = copied ?: return
        val edits = mutableListOf<Pair<CellAddress, Pair<CellValue, CellStyle?>>>()
        data.forEachIndexed { rOffset, row ->
            row.forEachIndexed { cOffset, item ->
                val targetAddr = CellAddress(activeCell.row + rOffset, activeCell.column + cOffset)
                edits.add(targetAddr to (item.value to item.style))
            }
        }
        history.execute(BatchEditCommand(sheet, edits, "Paste cells"))
        formulaText = rawValueText(sheet.valueAt(activeCell))
        notify("Pasted ${data.size}×${data.firstOrNull()?.size ?: 0} cells")
        refresh++
    }

    fun clearSelection() {
        history.execute(ClearRangeCommand(sheet, selection))
        formulaText = ""
        refresh++
    }

    fun applyStyle(transform: (CellStyle) -> CellStyle) {
        history.execute(StyleRangeCommand(sheet, selection, transform))
        refresh++
    }

    fun applyAutoSum() {
        val sumRange = if (selection.isSingleCell) {
            // Find numbers directly above
            val col = activeCell.column
            var top = activeCell.row - 1
            while (top >= 0 && sheet.valueAt(CellAddress(top, col)) !is CellValue.Empty) {
                top--
            }
            val startRow = top + 1
            val endRow = (activeCell.row - 1).coerceAtLeast(startRow)
            if (startRow <= endRow) "${CellAddress(startRow, col)}:${CellAddress(endRow, col)}" else "A1:${activeCell}"
        } else {
            "${CellAddress(selection.top, selection.left)}:${CellAddress(selection.bottom, selection.right)}"
        }
        val formula = "=SUM($sumRange)"
        editCell(activeCell, CellValue.Formula(formula))
        formulaText = formula
        notify("Inserted $formula")
    }

    // Auto calculate status for selected range (Excel classic)
    val autoCalc = remember(selection, activeSheet, refresh) {
        if (selection.rowCount <= 1 && selection.columnCount <= 1) null
        else {
            var count = 0
            var numCount = 0
            var sum = 0.0
            selection.addresses().forEach { addr ->
                val cell = sheet.cell(addr)
                if (cell.value !is CellValue.Empty) count++
                val num = when (val v = cell.value) {
                    is CellValue.Number -> v.value
                    is CellValue.Formula -> (formulaEngine.evaluate(sheet.name, v.expression) as? CellValue.Number)?.value
                    is CellValue.Text -> v.value.toDoubleOrNull()
                    else -> null
                }
                if (num != null) {
                    numCount++
                    sum += num
                }
            }
            if (numCount > 0) {
                val avg = sum / numCount
                Triple(formatNumber(sum), formatNumber(avg), count)
            } else if (count > 0) {
                Triple("-", "-", count)
            } else null
        }
    }

    Surface(modifier = Modifier.fillMaxSize().imePadding(), color = Color.White) {
        Column(Modifier.fillMaxSize()) {
            // 1. Excel Green Header Bar
            Surface(
                color = ExcelGreen,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Excel Editor", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        Text("${sheet.name} • ${selection}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    }

                    IconButton(onClick = { if (history.undo()) { refresh++; formulaText = rawValueText(sheet.valueAt(activeCell)) } }, enabled = history.canUndo()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (history.canUndo()) Color.White else Color.White.copy(alpha = 0.4f))
                    }

                    IconButton(onClick = { if (history.redo()) { refresh++; formulaText = rawValueText(sheet.valueAt(activeCell)) } }, enabled = history.canRedo()) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (history.canRedo()) Color.White else Color.White.copy(alpha = 0.4f))
                    }

                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ExcelGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Select All") },
                                leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                onClick = {
                                    selection = CellRange(CellAddress(0, 0), CellAddress(totalRows - 1, totalCols - 1))
                                    activeCell = CellAddress(0, 0)
                                    moreMenu = false
                                    refresh++
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add New Sheet") },
                                leadingIcon = { Icon(Icons.Default.AddCircleOutline, null) },
                                onClick = { moreMenu = false; showNewSheetDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename Sheet") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { moreMenu = false; showRenameDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Selection") },
                                leadingIcon = { Icon(Icons.Default.Clear, null) },
                                onClick = { clearSelection(); moreMenu = false }
                            )
                        }
                    }
                }
            }

            // Notice strip if active
            if (noticeText != null) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.fillMaxWidth().clickable { noticeText = null }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(noticeText.orEmpty(), fontSize = 12.sp, color = Color(0xFF92400E), modifier = Modifier.weight(1f))
                        Text("✕", fontSize = 11.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. Excel Formula Bar (Name Box + fx + Expression Field)
            Surface(
                color = Color(0xFFF8FAFC),
                tonalElevation = 1.dp,
                border = BorderStroke(0.5.dp, ExcelGridBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Excel Name Box (A1 or A1:B3)
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, ExcelHeaderBorder),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = selection.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = ExcelDarkGreen
                            )
                        }

                        // fx label
                        Text(
                            text = "fx",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            color = ExcelGreen,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        // Input field
                        TextField(
                            value = formulaText,
                            onValueChange = { formulaText = it },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedIndicatorColor = ExcelGreen,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            placeholder = { Text("Enter value or =FORMULA", fontSize = 12.sp) }
                        )

                        if (formulaText.isNotEmpty()) {
                            IconButton(onClick = { formulaText = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Clear, "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }

                        IconButton(
                            onClick = {
                                editCell(activeCell, parseInput(formulaText))
                            },
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(34.dp)
                                .background(ExcelGreen, CircleShape)
                        ) {
                            Icon(Icons.Default.Check, "Apply", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Quick formula chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormulaChip("Σ SUM") {
                            if (!formulaText.startsWith("=")) formulaText = "=SUM(" else formulaText += "SUM("
                        }
                        FormulaChip("AVERAGE") {
                            if (!formulaText.startsWith("=")) formulaText = "=AVERAGE(" else formulaText += "AVERAGE("
                        }
                        FormulaChip("COUNT") {
                            if (!formulaText.startsWith("=")) formulaText = "=COUNT(" else formulaText += "COUNT("
                        }
                        FormulaChip("MAX") {
                            if (!formulaText.startsWith("=")) formulaText = "=MAX(" else formulaText += "MAX("
                        }
                        FormulaChip("MIN") {
                            if (!formulaText.startsWith("=")) formulaText = "=MIN(" else formulaText += "MIN("
                        }
                        FormulaChip("IF") {
                            if (!formulaText.startsWith("=")) formulaText = "=IF(" else formulaText += "IF("
                        }
                        FormulaChip("AutoSum") { applyAutoSum() }
                    }
                }
            }

            // 3. Excel Formatting Ribbon (Scrollable Toolbar)
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                val activeCellStyle = sheet.cell(activeCell).style
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    RibbonButton("Cut", Icons.Default.ContentCut) { cutSelection() }
                    RibbonButton("Copy", Icons.Default.ContentCopy) { copySelection() }
                    RibbonButton("Paste", Icons.Default.ContentPaste, enabled = copied != null) { pasteSelection() }
                    RibbonButton("Clear", Icons.Default.DeleteOutline) { clearSelection() }

                    RibbonDivider()

                    RibbonToggle("B", Icons.Default.FormatBold, active = activeCellStyle.bold) {
                        applyStyle { it.copy(bold = !it.bold) }
                    }
                    RibbonToggle("I", Icons.Default.FormatItalic, active = activeCellStyle.italic) {
                        applyStyle { it.copy(italic = !it.italic) }
                    }
                    RibbonToggle("U", Icons.Default.FormatUnderlined, active = activeCellStyle.underline) {
                        applyStyle { it.copy(underline = !it.underline) }
                    }

                    RibbonDivider()

                    RibbonButton("Left", Icons.AutoMirrored.Filled.FormatAlignLeft) {
                        applyStyle { it.copy(horizontalAlignment = HorizontalAlignment.Left) }
                    }
                    RibbonButton("Center", Icons.Default.FormatAlignCenter) {
                        applyStyle { it.copy(horizontalAlignment = HorizontalAlignment.Center) }
                    }
                    RibbonButton("Right", Icons.AutoMirrored.Filled.FormatAlignRight) {
                        applyStyle { it.copy(horizontalAlignment = HorizontalAlignment.Right) }
                    }
                    RibbonToggle("Wrap", Icons.AutoMirrored.Filled.WrapText, active = activeCellStyle.wrapText) {
                        applyStyle { it.copy(wrapText = !it.wrapText) }
                    }

                    RibbonDivider()

                    RibbonButton("Fill", Icons.Default.FormatColorFill) { showColorPicker = true }
                    RibbonButton("Color", Icons.Default.FormatColorText) { showTextColorPicker = true }
                    RibbonToggle("Border", Icons.Default.BorderAll, active = activeCellStyle.border == BorderStyle.Thin) {
                        applyStyle { it.copy(border = if (it.border == BorderStyle.Thin) BorderStyle.None else BorderStyle.Thin) }
                    }

                    RibbonDivider()

                    RibbonButton("+ Row", Icons.Default.Add) {
                        sheet.insertRow(activeCell.row)
                        refresh++
                        notify("Inserted row at ${activeCell.row + 1}")
                    }
                    RibbonButton("- Row", Icons.Default.Remove) {
                        sheet.deleteRow(activeCell.row)
                        refresh++
                        notify("Deleted row ${activeCell.row + 1}")
                    }
                    RibbonButton("+ Col", Icons.Default.AddBox) {
                        sheet.insertColumn(activeCell.column)
                        refresh++
                        notify("Inserted column at ${columnName(activeCell.column)}")
                    }
                    RibbonButton("- Col", Icons.Default.IndeterminateCheckBox) {
                        sheet.deleteColumn(activeCell.column)
                        refresh++
                        notify("Deleted column ${columnName(activeCell.column)}")
                    }
                }
            }

            // 4. Unified Spreadsheet Grid (PINNED HEADERS, ZERO DESYNC!)
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
                Column(Modifier.fillMaxSize()) {
                    // Pinned Top Column Headers Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HeaderHeight)
                            .background(ExcelHeaderBg)
                            .border(BorderStroke(0.5.dp, ExcelHeaderBorder))
                    ) {
                        // Top-left corner (#) - Click to Select All
                        Box(
                            modifier = Modifier
                                .width(RowHeaderWidth)
                                .fillMaxHeight()
                                .background(ExcelHeaderBg)
                                .border(BorderStroke(0.5.dp, ExcelHeaderBorder))
                                .clickable {
                                    selection = CellRange(CellAddress(0, 0), CellAddress(totalRows - 1, totalCols - 1))
                                    activeCell = CellAddress(0, 0)
                                    refresh++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⊞", fontSize = 14.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        }

                        // Horizontally scrolling Column Letters (A, B, C...)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScroll)
                        ) {
                            repeat(totalCols) { colIndex ->
                                val isColSelected = colIndex in selection.left..selection.right
                                val isColActive = colIndex == activeCell.column
                                Box(
                                    modifier = Modifier
                                        .width(ColumnWidth)
                                        .fillMaxHeight()
                                        .background(if (isColSelected) ExcelHeaderActive else ExcelHeaderBg)
                                        .border(BorderStroke(0.5.dp, ExcelHeaderBorder))
                                        .clickable {
                                            selection = CellRange(CellAddress(0, colIndex), CellAddress(totalRows - 1, colIndex))
                                            activeCell = CellAddress(0, colIndex)
                                            formulaText = rawValueText(sheet.valueAt(activeCell))
                                            refresh++
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = columnName(colIndex),
                                        fontWeight = if (isColActive || isColSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isColSelected) ExcelDarkGreen else Color(0xFF334155)
                                    )
                                    if (isColSelected) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(2.5.dp)
                                                .background(ExcelGreen)
                                                .align(Alignment.BottomCenter)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Worksheet Rows - Single Unified LazyColumn!
                    LazyColumn(
                        state = verticalScroll,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        items((0 until totalRows).toList(), key = { it }) { rowIndex ->
                            val isRowSelected = rowIndex in selection.top..selection.bottom
                            val isRowActive = rowIndex == activeCell.row

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(CellHeight)
                            ) {
                                // Pinned Row Number (1, 2, 3...)
                                Box(
                                    modifier = Modifier
                                        .width(RowHeaderWidth)
                                        .fillMaxHeight()
                                        .background(if (isRowSelected) ExcelHeaderActive else ExcelHeaderBg)
                                        .border(BorderStroke(0.5.dp, ExcelHeaderBorder))
                                        .clickable {
                                            selection = CellRange(CellAddress(rowIndex, 0), CellAddress(rowIndex, totalCols - 1))
                                            activeCell = CellAddress(rowIndex, 0)
                                            formulaText = rawValueText(sheet.valueAt(activeCell))
                                            refresh++
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${rowIndex + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = if (isRowActive || isRowSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isRowSelected) ExcelDarkGreen else Color(0xFF334155)
                                    )
                                    if (isRowSelected) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight()
                                                .width(2.5.dp)
                                                .background(ExcelGreen)
                                                .align(Alignment.CenterEnd)
                                        )
                                    }
                                }

                                // Cells row, synchronized with horizontal scroll
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(horizontalScroll)
                                ) {
                                    repeat(totalCols) { colIndex ->
                                        val addr = CellAddress(rowIndex, colIndex)
                                        val isActive = rowIndex == activeCell.row && colIndex == activeCell.column
                                        val isSelected = selection.contains(addr)
                                        val cell = sheet.cell(addr)
                                        val style = cell.style

                                        val (displayText, isError) = getDisplayValue(sheet, cell, formulaEngine)

                                        val cellBg = when {
                                            isActive -> Color.White
                                            isSelected -> ExcelSelectionFill
                                            style.fillArgb != null -> Color(style.fillArgb)
                                            else -> Color.White
                                        }

                                        val cellBorder = when {
                                            isActive -> BorderStroke(2.dp, ExcelGreen)
                                            isSelected -> BorderStroke(0.5.dp, ExcelGreen.copy(alpha = 0.6f))
                                            style.border == BorderStyle.Thin -> BorderStroke(0.5.dp, Color(0xFF64748B))
                                            else -> BorderStroke(0.5.dp, ExcelGridBorder)
                                        }

                                        val align = when (style.horizontalAlignment) {
                                            HorizontalAlignment.Left -> TextAlign.Start
                                            HorizontalAlignment.Center -> TextAlign.Center
                                            HorizontalAlignment.Right -> TextAlign.End
                                            HorizontalAlignment.General -> if (isNumeric(displayText)) TextAlign.End else TextAlign.Start
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(ColumnWidth)
                                                .fillMaxHeight()
                                                .background(cellBg)
                                                .border(cellBorder)
                                                .combinedClickable(
                                                    onClick = { selectCell(addr, extend = false) },
                                                    onLongClick = { selectCell(addr, extend = true) },
                                                    onDoubleClick = {
                                                        selectCell(addr, extend = false)
                                                        showQuickEditDialog = true
                                                    }
                                                )
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = displayText,
                                                modifier = Modifier.fillMaxWidth(),
                                                fontSize = 12.sp,
                                                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                                                textDecoration = if (style.underline) TextDecoration.Underline else TextDecoration.None,
                                                textAlign = align,
                                                color = when {
                                                    isError -> Color(0xFFDC2626)
                                                    style.textArgb != null -> Color(style.textArgb)
                                                    else -> Color(0xFF0F172A)
                                                },
                                                maxLines = if (style.wrapText) Int.MAX_VALUE else 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Excel Auto-Calculate Status Bar (when multiple cells selected)
            if (autoCalc != null) {
                Surface(
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(0.5.dp, ExcelGridBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVERAGE: ${autoCalc.second}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ExcelDarkGreen
                        )
                        Text(
                            text = "COUNT: ${autoCalc.third}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ExcelDarkGreen
                        )
                        Text(
                            text = "SUM: ${autoCalc.first}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExcelGreen
                        )
                    }
                }
            }

            // 6. Excel Worksheet Bottom Tabs
            Surface(
                color = Color(0xFFE2E8F0),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(44.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        workbook.sheets.forEachIndexed { index, s ->
                            val isTabActive = index == safeSheetIndex
                            Surface(
                                color = if (isTabActive) Color.White else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                border = BorderStroke(0.5.dp, if (isTabActive) ExcelGreen else Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .clickable {
                                        activeSheet = index
                                        activeCell = CellAddress(0, 0)
                                        anchorCell = CellAddress(0, 0)
                                        selection = CellRange(activeCell, activeCell)
                                        formulaText = rawValueText(workbook.sheets[index].valueAt(activeCell))
                                        refresh++
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = s.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isTabActive) ExcelDarkGreen else Color(0xFF475569)
                                    )
                                    if (isTabActive && workbook.sheets.size > 1) {
                                        IconButton(
                                            onClick = {
                                                workbook.removeSheet(index)
                                                activeSheet = 0
                                                refresh++
                                                notify("Deleted sheet")
                                            },
                                            modifier = Modifier.size(18.dp).padding(start = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "Close sheet", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { showNewSheetDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .background(ExcelGreen, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, "Add Sheet", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Dialogs
    if (showQuickEditDialog) {
        var input by remember { mutableStateOf(formulaText) }
        AlertDialog(
            onDismissRequest = { showQuickEditDialog = false },
            title = { Text("Edit Cell ${activeCell}") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Value or Formula") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editCell(activeCell, parseInput(input))
                    showQuickEditDialog = false
                }) { Text("OK", fontWeight = FontWeight.Bold, color = ExcelGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showQuickEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewSheetDialog) {
        var sheetName by remember { mutableStateOf("Sheet${workbook.sheets.size + 1}") }
        AlertDialog(
            onDismissRequest = { showNewSheetDialog = false },
            title = { Text("Add Worksheet") },
            text = {
                OutlinedTextField(
                    value = sheetName,
                    onValueChange = { sheetName = it },
                    label = { Text("Sheet Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newSheet = workbook.addSheet(sheetName)
                    activeSheet = workbook.sheets.indexOf(newSheet)
                    showNewSheetDialog = false
                    notify("Added sheet ${newSheet.name}")
                    refresh++
                }) { Text("Create", fontWeight = FontWeight.Bold, color = ExcelGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showNewSheetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(sheet.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
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
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        sheet.name = newName.trim()
                        showRenameDialog = false
                        refresh++
                    }
                }) { Text("Rename", fontWeight = FontWeight.Bold, color = ExcelGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showColorPicker) {
        val colors = listOf(
            null to "No Fill",
            0xFFFEF08A.toInt() to "Yellow",
            0xFFBBF7D0.toInt() to "Green",
            0xFFBFDBFE.toInt() to "Blue",
            0xFFFED7AA.toInt() to "Orange",
            0xFFFECDD3.toInt() to "Pink",
            0xFFE9D5FF.toInt() to "Purple",
            0xFFE2E8F0.toInt() to "Gray"
        )
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("Fill Color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { (colorInt, name) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .background(colorInt?.let { Color(it) } ?: Color.White, CircleShape)
                                            .border(1.dp, Color.Gray, CircleShape)
                                            .clickable {
                                                applyStyle { it.copy(fillArgb = colorInt) }
                                                showColorPicker = false
                                            }
                                    )
                                    Text(name, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showColorPicker = false }) { Text("Cancel") } }
        )
    }

    if (showTextColorPicker) {
        val textColors = listOf(
            0xFF0F172A.toInt() to "Black",
            0xFF15803D.toInt() to "Green",
            0xFF1D4ED8.toInt() to "Blue",
            0xFFDC2626.toInt() to "Red",
            0xFF7E22CE.toInt() to "Purple",
            0xFFD97706.toInt() to "Amber"
        )
        AlertDialog(
            onDismissRequest = { showTextColorPicker = false },
            title = { Text("Text Color") },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    textColors.forEach { (colorInt, name) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(colorInt), CircleShape)
                                    .border(1.dp, Color.LightGray, CircleShape)
                                    .clickable {
                                        applyStyle { it.copy(textArgb = colorInt) }
                                        showTextColorPicker = false
                                    }
                            )
                            Text(name, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTextColorPicker = false }) { Text("Cancel") } }
        )
    }
}

private data class SpreadsheetCellSnapshot(val value: CellValue, val style: CellStyle)

@Composable
private fun RibbonButton(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Color(0xFF334155) else Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RibbonToggle(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) ExcelHeaderActive else Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.size(36.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = if (active) ExcelDarkGreen else Color(0xFF334155), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RibbonDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(22.dp)
            .background(Color(0xFFE2E8F0))
    )
}

@Composable
private fun FormulaChip(text: String, onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ExcelDarkGreen,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun getDisplayValue(
    sheet: SpreadsheetSheet,
    cell: SpreadsheetCell,
    formulaEngine: FormulaEngine
): Pair<String, Boolean> {
    return when (val v = cell.value) {
        CellValue.Empty -> "" to false
        is CellValue.Text -> v.value to false
        is CellValue.Number -> formatNumber(v.value) to false
        is CellValue.BooleanValue -> (if (v.value) "TRUE" else "FALSE") to false
        is CellValue.Formula -> {
            try {
                when (val eval = formulaEngine.evaluate(sheet.name, v.expression)) {
                    is CellValue.Number -> formatNumber(eval.value) to false
                    is CellValue.Text -> eval.value to false
                    is CellValue.BooleanValue -> (if (eval.value) "TRUE" else "FALSE") to false
                    is CellValue.Error -> eval.code to true
                    CellValue.Empty -> "" to false
                    else -> v.expression to false
                }
            } catch (_: Exception) {
                "#VALUE!" to true
            }
        }
        is CellValue.Error -> v.code to true
    }
}

private fun rawValueText(value: CellValue): String = when (value) {
    CellValue.Empty -> ""
    is CellValue.Text -> value.value
    is CellValue.Number -> formatNumber(value.value)
    is CellValue.BooleanValue -> if (value.value) "TRUE" else "FALSE"
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

private fun formatNumber(num: Double): String {
    return if (num % 1.0 == 0.0) {
        num.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", num).trimEnd('0').trimEnd('.')
    }
}

private fun isNumeric(str: String): Boolean = str.toDoubleOrNull() != null

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
