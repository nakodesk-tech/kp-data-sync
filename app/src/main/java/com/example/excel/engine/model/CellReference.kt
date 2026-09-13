package com.example.excel.engine.model

/**
 * Represents a cell reference in a spreadsheet (e.g., A1, B2, Z99).
 *
 * This class handles conversion between cell notation and row/column indices.
 * Row and column indices are 0-based internally.
 * Cell notation (A1, B2, etc.) is human-readable (1-based).
 */
data class CellReference(
    val row: Int,
    val col: Int
) {
    init {
        require(row >= 0) { "Row must be non-negative, got $row" }
        require(col >= 0) { "Column must be non-negative, got $col" }
    }

    /**
     * Convert to Excel notation (e.g., "A1", "B2", "Z99", "AA1").
     */
    fun toNotation(): String {
        var c = col + 1
        val sb = StringBuilder()
        while (c > 0) {
            val r = (c - 1) % 26
            sb.append(('A'.code + r).toChar())
            c = (c - 1) / 26
        }
        return sb.reverse().append(row + 1).toString()
    }

    override fun toString(): String = toNotation()

    companion object {
        /**
         * Parse Excel notation to CellReference.
         * Examples: "A1" -> CellReference(0, 0), "B2" -> CellReference(1, 1)
         * Returns null if notation is invalid.
         */
        fun parse(notation: String): CellReference? {
            val m = Regex("([A-Z]+)([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(notation.trim())
                ?: return null
            val colStr = m.groupValues[1].uppercase()
            val rowStr = m.groupValues[2]

            val col = colStr.fold(0) { n, ch -> n * 26 + ch.code - 64 } - 1
            val row = rowStr.toIntOrNull()?.minus(1) ?: return null

            return if (row >= 0 && col >= 0) CellReference(row, col) else null
        }
    }
}
