package com.example.ui

import com.example.excel.engine.BorderStyle
import com.example.excel.engine.CellAddress
import com.example.excel.engine.CellRange
import com.example.excel.engine.CellStyle
import com.example.excel.engine.CellValue
import com.example.excel.engine.HorizontalAlignment
import com.example.excel.engine.SpreadsheetWorkbook
import com.example.excel.engine.VerticalAlignment

internal fun InAppXlsxWorkbook.toSpreadsheetWorkbook(): SpreadsheetWorkbook {
    val result = SpreadsheetWorkbook()
    result.sheets.clear()
    sheets.forEach { source ->
        val target = com.example.excel.engine.SpreadsheetSheet(source.name)
        source.cells.forEachIndexed { r, row ->
            row.forEachIndexed { c, raw ->
                val formula = source.formulas.getOrNull(r)?.getOrNull(c)
                val value = when {
                    formula != null -> CellValue.Formula(formula)
                    raw.isBlank() -> CellValue.Empty
                    raw.equals("TRUE", true) -> CellValue.BooleanValue(true)
                    raw.equals("FALSE", true) -> CellValue.BooleanValue(false)
                    raw.toDoubleOrNull() != null -> CellValue.Number(raw.toDouble())
                    else -> CellValue.Text(raw)
                }
                if (value !is CellValue.Empty || source.styles.getOrNull(r)?.getOrNull(c) != ExcelCellStyle()) {
                    val s = source.styles.getOrNull(r)?.getOrNull(c) ?: ExcelCellStyle()
                    target.setValue(CellAddress(r, c), value)
                    target.cell(CellAddress(r, c)).style = s.toEngineStyle()
                }
            }
        }
        result.sheets += target
    }
    if (result.sheets.isEmpty()) result.sheets += com.example.excel.engine.SpreadsheetSheet("Sheet1")
    return result
}

internal fun SpreadsheetWorkbook.applyToXlsx(target: InAppXlsxWorkbook) {
    sheets.forEachIndexed { si, source ->
        if (si >= target.sheets.size) target.insertSheet(source.name)
        val destination = target.sheets[si]
        destination.name.takeIf { it != source.name }?.let { target.renameSheet(si, source.name) }
        source.cellsIn(CellRange(CellAddress(0, 0), CellAddress(9999, 999))) .forEach { (address, cell) ->
            val value = when (val v = cell.value) {
                CellValue.Empty -> ""
                is CellValue.Text -> v.value
                is CellValue.Number -> v.value.toString()
                is CellValue.BooleanValue -> if (v.value) "TRUE" else "FALSE"
                is CellValue.Formula -> v.expression
                is CellValue.Error -> "#${v.code}"
            }
            target.setCell(si, address.row, address.column, value)
            target.setStyle(si, address.row, address.column, cell.style.toXlsxStyle())
        }
    }
}

private fun ExcelCellStyle.toEngineStyle(): CellStyle = CellStyle(
    bold = bold,
    italic = italic,
    underline = underline,
    horizontalAlignment = when (horizontal.lowercase()) {
        "left" -> HorizontalAlignment.Left
        "center" -> HorizontalAlignment.Center
        "right" -> HorizontalAlignment.Right
        else -> HorizontalAlignment.General
    },
    verticalAlignment = when (vertical.lowercase()) {
        "top" -> VerticalAlignment.Top
        "bottom" -> VerticalAlignment.Bottom
        else -> VerticalAlignment.Center
    },
    wrapText = wrap,
    numberFormat = numberFormat ?: "General",
    fillArgb = background?.let(::hexArgb),
    textArgb = foreground?.let(::hexArgb),
    border = if (border) BorderStyle.Thin else BorderStyle.None
)

private fun CellStyle.toXlsxStyle(): ExcelCellStyle = ExcelCellStyle(
    bold = bold,
    italic = italic,
    underline = underline,
    fontSize = 11,
    fontName = "Calibri",
    horizontal = when (horizontalAlignment) {
        HorizontalAlignment.Left -> "left"
        HorizontalAlignment.Center -> "center"
        HorizontalAlignment.Right -> "right"
        HorizontalAlignment.General -> "general"
    },
    vertical = when (verticalAlignment) {
        VerticalAlignment.Top -> "top"
        VerticalAlignment.Bottom -> "bottom"
        VerticalAlignment.Center -> "center"
    },
    background = fillArgb?.let(::argbHex),
    foreground = textArgb?.let(::argbHex),
    wrap = wrapText,
    border = border == BorderStyle.Thin,
    numberFormat = numberFormat.takeUnless { it == "General" }
)

private fun hexArgb(value: String): Int = (0xFF000000.toInt() or value.removePrefix("#").takeLast(6).toLong(16).toInt())
private fun argbHex(value: Int): String = "%06X".format(value and 0x00FFFFFF)
