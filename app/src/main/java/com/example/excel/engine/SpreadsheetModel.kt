package com.example.excel.engine

/** Core, Compose-independent spreadsheet model. */
class SpreadsheetWorkbook(
    sheets: List<SpreadsheetSheet> = listOf(SpreadsheetSheet("Sheet1"))
) {
    val sheets: MutableList<SpreadsheetSheet> = sheets.toMutableList()

    init { require(this.sheets.isNotEmpty()) { "A workbook must contain at least one sheet" } }

    fun sheet(index: Int): SpreadsheetSheet = sheets[index]

    fun addSheet(name: String = uniqueSheetName("Sheet")): SpreadsheetSheet {
        val sheet = SpreadsheetSheet(uniqueSheetName(name))
        sheets += sheet
        return sheet
    }

    fun removeSheet(index: Int) {
        require(sheets.size > 1) { "A workbook must contain at least one sheet" }
        sheets.removeAt(index)
    }

    private fun uniqueSheetName(base: String): String {
        val clean = base.trim().ifEmpty { "Sheet" }
        if (sheets.none { it.name.equals(clean, ignoreCase = true) }) return clean
        var n = 2
        while (sheets.any { it.name.equals("$clean$n", ignoreCase = true) }) n++
        return "$clean$n"
    }
}

class SpreadsheetSheet(var name: String) {
    private val cells = linkedMapOf<CellAddress, SpreadsheetCell>()
    private val rowHeights = mutableMapOf<Int, Int>()
    private val columnWidths = mutableMapOf<Int, Int>()
    private val hiddenRows = mutableSetOf<Int>()
    private val hiddenColumns = mutableSetOf<Int>()
    private val mergedRanges = linkedSetOf<CellRange>()

    fun cell(address: CellAddress): SpreadsheetCell = cells.getOrPut(address) { SpreadsheetCell() }
    fun valueAt(address: CellAddress): CellValue = cells[address]?.value ?: CellValue.Empty
    fun setValue(address: CellAddress, value: CellValue) { if (value is CellValue.Empty) cells.remove(address) else cell(address).value = value }
    fun clear(range: CellRange) { cells.keys.removeIf(range::contains) }
    fun cellsIn(range: CellRange): Sequence<Pair<CellAddress, SpreadsheetCell>> = cells.asSequence().filter { range.contains(it.key) }.map { it.key to it.value }

    fun insertRow(row: Int) { require(row >= 0); shiftCells { a -> if (a.row >= row) a.copy(row = a.row + 1) else a } }

    fun deleteRow(row: Int) {
        require(row >= 0)
        cells.keys.toList().forEach { a ->
            when {
                a.row == row -> cells.remove(a)
                a.row > row -> cells.remove(a)?.let { cells[a.copy(row = a.row - 1)] = it }
            }
        }
    }

    fun insertColumn(column: Int) { require(column >= 0); shiftCells { a -> if (a.column >= column) a.copy(column = a.column + 1) else a } }

    fun deleteColumn(column: Int) {
        require(column >= 0)
        cells.keys.toList().forEach { a ->
            when {
                a.column == column -> cells.remove(a)
                a.column > column -> cells.remove(a)?.let { cells[a.copy(column = a.column - 1)] = it }
            }
        }
    }

    fun setRowHeight(row: Int, heightPx: Int) { rowHeights[row] = heightPx.coerceAtLeast(1) }
    fun rowHeight(row: Int): Int = rowHeights[row] ?: 48
    fun setColumnWidth(column: Int, widthPx: Int) { columnWidths[column] = widthPx.coerceAtLeast(1) }
    fun columnWidth(column: Int): Int = columnWidths[column] ?: 120
    fun setRowHidden(row: Int, hidden: Boolean) { if (hidden) hiddenRows += row else hiddenRows -= row }
    fun isRowHidden(row: Int): Boolean = row in hiddenRows
    fun setColumnHidden(column: Int, hidden: Boolean) { if (hidden) hiddenColumns += column else hiddenColumns -= column }
    fun isColumnHidden(column: Int): Boolean = column in hiddenColumns
    fun merge(range: CellRange) { require(!range.isSingleCell); mergedRanges += range }
    fun unmerge(range: CellRange) { mergedRanges.removeIf { it.intersects(range) } }
    fun mergedRanges(): Set<CellRange> = mergedRanges.toSet()

    private fun shiftCells(transform: (CellAddress) -> CellAddress) {
        val copy = cells.entries.map { transform(it.key) to it.value }
        cells.clear()
        copy.forEach { cells[it.first] = it.second }
    }
}

data class SpreadsheetCell(var value: CellValue = CellValue.Empty, var style: CellStyle = CellStyle())

sealed interface CellValue {
    data object Empty : CellValue
    data class Text(val value: String) : CellValue
    data class Number(val value: Double) : CellValue
    data class BooleanValue(val value: Boolean) : CellValue
    data class Formula(val expression: String) : CellValue
    data class Error(val code: String) : CellValue
}

data class CellStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.General,
    val verticalAlignment: VerticalAlignment = VerticalAlignment.Center,
    val wrapText: Boolean = false,
    val numberFormat: String = "General",
    val fillArgb: Int? = null,
    val textArgb: Int? = null,
    val border: BorderStyle = BorderStyle.None
)

enum class HorizontalAlignment { General, Left, Center, Right }
enum class VerticalAlignment { Top, Center, Bottom }
enum class BorderStyle { None, Thin }

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

data class CellAddress(val row: Int, val column: Int) {
    init { require(row >= 0 && column >= 0) }
    override fun toString(): String = columnName(column) + (row + 1)

    companion object {
        fun parse(reference: String): CellAddress {
            val m = Regex("^([A-Za-z]+)([1-9][0-9]*)$").matchEntire(reference.trim())
                ?: error("Invalid cell reference: $reference")
            var column = 0
            m.groupValues[1].uppercase().forEach { column = column * 26 + (it - 'A' + 1) }
            return CellAddress(m.groupValues[2].toInt() - 1, column - 1)
        }
    }
}

data class CellRange(val start: CellAddress, val end: CellAddress) {
    val top: Int get() = minOf(start.row, end.row)
    val bottom: Int get() = maxOf(start.row, end.row)
    val left: Int get() = minOf(start.column, end.column)
    val right: Int get() = maxOf(start.column, end.column)
    val rowCount: Int get() = bottom - top + 1
    val columnCount: Int get() = right - left + 1
    val isSingleCell: Boolean get() = rowCount == 1 && columnCount == 1
    fun contains(address: CellAddress): Boolean = address.row in top..bottom && address.column in left..right
    fun intersects(other: CellRange): Boolean = top <= other.bottom && bottom >= other.top && left <= other.right && right >= other.left
    fun addresses(): Sequence<CellAddress> = sequence { for (r in top..bottom) for (c in left..right) yield(CellAddress(r, c)) }
    override fun toString(): String = "${CellAddress(top, left)}:${CellAddress(bottom, right)}"
}
