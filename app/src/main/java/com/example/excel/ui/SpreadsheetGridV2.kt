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
import com.example.excel.engine.CellValue
import com.example.excel.engine.SpreadsheetSheet

private val RowHeaderWidth = 52.dp
private val ColumnWidth = 110.dp
private val CellHeight = 44.dp
private val HeaderHeight = 34.dp

/**
 * Spreadsheet grid layout: one vertical list owns all rows, so row numbers and
 * cells can never drift apart while scrolling. The horizontal scroll is shared
 * by the column header and all worksheet columns.
 */
@Composable
fun SpreadsheetGridV2(
    sheet: SpreadsheetSheet,
    selection: CellRange,
    onCellClick: (CellAddress) -> Unit,
    onRowClick: (Int) -> Unit,
    onColumnClick: (Int) -> Unit,
    rows: Int = 100,
    columns: Int = 20
) {
    val horizontal = rememberScrollState()

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(RowHeaderWidth)) {
            Box(
                Modifier.width(RowHeaderWidth).height(HeaderHeight)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) { Text("#", fontSize = 11.sp) }

            LazyColumn {
                items((0 until rows).toList()) { row ->
                    val selected = selection.top == row && selection.bottom == row
                    Box(
                        Modifier.width(RowHeaderWidth).height(CellHeight)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline)
                            .clickable { onRowClick(row) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text((row + 1).toString(), fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Column(Modifier.horizontalScroll(horizontal)) {
            Row(Modifier.height(HeaderHeight)) {
                repeat(columns) { column ->
                    val selected = selection.left == column && selection.right == column
                    Box(
                        Modifier.width(ColumnWidth).height(HeaderHeight)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline)
                            .clickable { onColumnClick(column) },
                        contentAlignment = Alignment.Center
                    ) { Text(columnName(column), fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                }
            }

            // The worksheet body is one LazyColumn; row headers use the same item index.
            LazyColumn {
                items((0 until rows).toList()) { row ->
                    Row {
                        repeat(columns) { column ->
                            val address = CellAddress(row, column)
                            val selected = selection.contains(address)
                            val value = sheet.valueAt(address)
                            Box(
                                Modifier.width(ColumnWidth).height(CellHeight)
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                                    .border(if (selected) 2.dp else 0.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                    .clickable { onCellClick(address) }
                                    .padding(horizontal = 7.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(cellText(value), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cellText(value: CellValue): String = when (value) {
    CellValue.Empty -> ""
    is CellValue.Text -> value.value
    is CellValue.Number -> value.value.toString().removeSuffix(".0")
    is CellValue.BooleanValue -> value.value.toString()
    is CellValue.Formula -> value.expression
    is CellValue.Error -> value.code
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
