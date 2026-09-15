package com.example.excel.engine

/** Compose-independent selection state used by the future spreadsheet UI. */
class SelectionModel {
    var activeCell: CellAddress = CellAddress(0, 0)
        private set
    var anchorCell: CellAddress = CellAddress(0, 0)
        private set

    private val ranges = linkedSetOf<CellRange>()

    init { ranges += CellRange(activeCell, activeCell) }

    fun selectCell(address: CellAddress) {
        activeCell = address
        anchorCell = address
        ranges.clear()
        ranges += CellRange(address, address)
    }

    fun extendTo(address: CellAddress) {
        activeCell = address
        ranges.clear()
        ranges += CellRange(anchorCell, address)
    }

    fun selectRow(row: Int, maxColumn: Int) {
        require(row >= 0 && maxColumn >= 0)
        anchorCell = CellAddress(row, 0)
        activeCell = CellAddress(row, maxColumn)
        ranges.clear()
        ranges += CellRange(anchorCell, activeCell)
    }

    fun selectColumn(column: Int, maxRow: Int) {
        require(column >= 0 && maxRow >= 0)
        anchorCell = CellAddress(0, column)
        activeCell = CellAddress(maxRow, column)
        ranges.clear()
        ranges += CellRange(anchorCell, activeCell)
    }

    fun selectAll(maxRow: Int, maxColumn: Int) {
        require(maxRow >= 0 && maxColumn >= 0)
        anchorCell = CellAddress(0, 0)
        activeCell = CellAddress(maxRow, maxColumn)
        ranges.clear()
        ranges += CellRange(anchorCell, activeCell)
    }

    fun addRange(range: CellRange) { ranges += range }

    fun ranges(): List<CellRange> = ranges.toList()

    fun contains(address: CellAddress): Boolean = ranges.any { it.contains(address) }
}
