package com.example.excel.engine.model

/**
 * Represents a range of cells in a spreadsheet.
 *
 * Supports:
 * - Single cell: A1
 * - Rectangular range: A1:B5
 * - Entire row: 1:1
 * - Entire column: A:A
 *
 * All operations are based on 0-based indices internally.
 * Ranges are normalized (top-left to bottom-right).
 */
data class CellRange(
    val startRow: Int,
    val endRow: Int,
    val startCol: Int,
    val endCol: Int,
    val isEntireRow: Boolean = false,
    val isEntireColumn: Boolean = false
) {
    init {
        require(startRow <= endRow || isEntireColumn) { "startRow must be <= endRow" }
        require(startCol <= endCol || isEntireRow) { "startCol must be <= endCol" }
    }

    /**
     * Number of rows in this range (or Int.MAX_VALUE for entire column).
     */
    val rowCount: Int
        get() = if (isEntireColumn) Int.MAX_VALUE else endRow - startRow + 1

    /**
     * Number of columns in this range (or Int.MAX_VALUE for entire row).
     */
    val colCount: Int
        get() = if (isEntireRow) Int.MAX_VALUE else endCol - startCol + 1

    /**
     * Check if a cell is contained in this range.
     */
    fun contains(row: Int, col: Int): Boolean {
        return (isEntireRow || (row in startRow..endRow)) &&
               (isEntireColumn || (col in startCol..endCol))
    }

    /**
     * Check if another range intersects with this one.
     */
    fun intersects(other: CellRange): Boolean {
        if (isEntireRow && other.isEntireRow) return true
        if (isEntireColumn && other.isEntireColumn) return true
        if (isEntireRow) return other.startRow <= endRow && other.endRow >= startRow
        if (isEntireColumn) return other.startCol <= endCol && other.endCol >= startCol
        if (other.isEntireRow) return startRow <= other.endRow && endRow >= other.startRow
        if (other.isEntireColumn) return startCol <= other.endCol && endCol >= other.startCol
        return !(endRow < other.startRow || startRow > other.endRow ||
                 endCol < other.startCol || startCol > other.endCol)
    }

    /**
     * Expand this range to include another cell.
     */
    fun expandTo(row: Int, col: Int): CellRange {
        if (isEntireRow && isEntireColumn) return this
        val newStartRow = if (isEntireColumn) startRow else minOf(startRow, row)
        val newEndRow = if (isEntireColumn) endRow else maxOf(endRow, row)
        val newStartCol = if (isEntireRow) startCol else minOf(startCol, col)
        val newEndCol = if (isEntireRow) endCol else maxOf(endCol, col)
        return CellRange(newStartRow, newEndRow, newStartCol, newEndCol, isEntireRow, isEntireColumn)
    }

    /**
     * Iterate over all cells in this range (excluding entire row/column).
     */
    fun cells(): Sequence<CellReference> = sequence {
        if (!isEntireRow && !isEntireColumn) {
            for (r in startRow..endRow) {
                for (c in startCol..endCol) {
                    yield(CellReference(r, c))
                }
            }
        }
    }

    /**
     * Convert to Excel notation if possible.
     * Returns null for entire row/column cases.
     */
    fun toNotation(): String? {
        return when {
            isEntireRow -> "${startRow + 1}:${endRow + 1}"
            isEntireColumn -> "${CellReference(0, startCol).toNotation().takeWhile { it.isLetter() }}:${CellReference(0, endCol).toNotation().takeWhile { it.isLetter() }}"
            startRow == endRow && startCol == endCol -> CellReference(startRow, startCol).toNotation()
            else -> "${CellReference(startRow, startCol).toNotation()}:${CellReference(endRow, endCol).toNotation()}"
        }
    }

    override fun toString(): String = toNotation() ?: "EntireRange"

    companion object {
        /**
         * Parse Excel range notation.
         * Examples: "A1", "A1:B5", "1:1", "A:A"
         * Returns null if notation is invalid.
         */
        fun parse(notation: String): CellRange? {
            val trimmed = notation.trim()
            val parts = trimmed.split(':')

            return when {
                parts.size == 1 -> {
                    // Single cell or entire row/column
                    when {
                        parts[0].all { it.isDigit() } -> {
                            // Entire row: "1"
                            val row = parts[0].toIntOrNull()?.minus(1) ?: return null
                            if (row >= 0) CellRange(row, row, 0, Int.MAX_VALUE, isEntireRow = true)
                            else null
                        }
                        parts[0].all { it.isLetter() } -> {
                            // Entire column: "A"
                            val col = parts[0].fold(0) { n, ch -> n * 26 + ch.uppercaseChar().code - 64 } - 1
                            if (col >= 0) CellRange(0, Int.MAX_VALUE, col, col, isEntireColumn = true)
                            else null
                        }
                        else -> {
                            // Single cell: "A1"
                            CellReference.parse(parts[0])?.let {
                                CellRange(it.row, it.row, it.col, it.col)
                            }
                        }
                    }
                }
                parts.size == 2 -> {
                    val startRef = parts[0].trim()
                    val endRef = parts[1].trim()

                    when {
                        startRef.all { it.isDigit() } && endRef.all { it.isDigit() } -> {
                            // Entire rows: "1:5"
                            val r1 = startRef.toIntOrNull()?.minus(1) ?: return null
                            val r2 = endRef.toIntOrNull()?.minus(1) ?: return null
                            if (r1 >= 0 && r2 >= 0) {
                                CellRange(minOf(r1, r2), maxOf(r1, r2), 0, Int.MAX_VALUE, isEntireRow = true)
                            } else null
                        }
                        startRef.all { it.isLetter() } && endRef.all { it.isLetter() } -> {
                            // Entire columns: "A:C"
                            val c1 = startRef.fold(0) { n, ch -> n * 26 + ch.uppercaseChar().code - 64 } - 1
                            val c2 = endRef.fold(0) { n, ch -> n * 26 + ch.uppercaseChar().code - 64 } - 1
                            if (c1 >= 0 && c2 >= 0) {
                                CellRange(0, Int.MAX_VALUE, minOf(c1, c2), maxOf(c1, c2), isEntireColumn = true)
                            } else null
                        }
                        else -> {
                            // Cell range: "A1:B5"
                            val start = CellReference.parse(startRef) ?: return null
                            val end = CellReference.parse(endRef) ?: return null
                            CellRange(
                                minOf(start.row, end.row),
                                maxOf(start.row, end.row),
                                minOf(start.col, end.col),
                                maxOf(start.col, end.col)
                            )
                        }
                    }
                }
                else -> null
            }
        }
    }
}
