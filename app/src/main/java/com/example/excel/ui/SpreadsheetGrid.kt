package com.example.excel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.excel.engine.CellAddress
import com.example.excel.engine.CellRange
import com.example.excel.engine.SpreadsheetSheet

private val GridRowHeader = 52.dp
private val GridColumnWidth = 110.dp
private val GridCellHeight = 44.dp

/** Excel-like grid with pinned row/column headers and reliable range selection. */
@Composable
fun SpreadsheetGrid(
    sheet: SpreadsheetSheet,
    selection: CellRange,
    anchor: CellAddress,
    onCellClick: (CellAddress, Boolean) -> Unit,
    onRowClick: (Int) -> Unit,
    onColumnClick: (Int) -> Unit,
    rows: Int = 100,
    columns: Int = 20
) {
    val vertical = rememberLazyListState()
    val horizontal = rememberScrollState()

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(GridRowHeader)) {
            Box(
                Modifier.width(GridRowHeader).height(34.dp)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) { Text("#", fontSize = 11.sp) }
            LazyColumn(state = vertical) {
                items((0 until rows).toList()) { row ->
                    val selected = selection.top == row && selection.bottom == row
                    Box(
                        Modifier.width(GridRowHeader).height(GridCellHeight)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline)
                            .clickable { onRowClick(row) },
                        contentAlignment = Alignment.Center
                    ) { Text((row + 1).toString(), fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }

        Column(Modifier.horizontalScroll(horizontal)) {
            Row(Modifier.height(34.dp)) {
                repeat(columns) { column ->
                    val selected = selection.left == column && selection.right == column
                    Box(
                        Modifier.width(GridColumnWidth).height(34.dp)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline)
                            .clickable { onColumnClick(column) },
                        contentAlignment = Alignment.Center
                    ) { Text(columnName(column), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                }
            }
            LazyColumn(state = vertical) {
                items((0 until rows).toList()) { row ->
                    Row {
                        repeat(columns) { column ->
                            val address = CellAddress(row, column)
                            val selected = selection.contains(address)
                            val value = sheet.valueAt(address)
                            Box(
                                Modifier.width(GridColumnWidth).height(GridCellHeight)
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                                    .border(if (selected) 2.dp else 0.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                    .clickable { onCellClick(address, anchor == address) }
                                    .padding(horizontal = 7.dp),
                                contentAlignment = Alignment.CenterStart
                            ) { Text(cellText(value), fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

private fun cellText(value: com.example.excel.engine.CellValue): String = when (value) {
    com.example.excel.engine.CellValue.Empty -> ""
    is com.example.excel.engine.CellValue.Text -> value.value
    is com.example.excel.engine.CellValue.Number -> value.value.toString().removeSuffix(".0")
    is com.example.excel.engine.CellValue.BooleanValue -> value.value.toString()
    is com.example.excel.engine.CellValue.Formula -> value.expression
    is com.example.excel.engine.CellValue.Error -> value.code
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
