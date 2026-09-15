package com.example.excel.engine

interface SpreadsheetCommand {
    val description: String
    fun execute()
    fun undo()
}

class SpreadsheetHistory(private val limit: Int = 100) {
    private val undoStack = ArrayDeque<SpreadsheetCommand>()
    private val redoStack = ArrayDeque<SpreadsheetCommand>()

    fun execute(command: SpreadsheetCommand) {
        command.execute()
        undoStack.addLast(command)
        redoStack.clear()
        while (undoStack.size > limit) undoStack.removeFirst()
    }

    fun undo(): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        command.undo()
        redoStack.addLast(command)
        return true
    }

    fun redo(): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        command.execute()
        undoStack.addLast(command)
        return true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun clear() { undoStack.clear(); redoStack.clear() }
}

class SetCellCommand(
    private val sheet: SpreadsheetSheet,
    private val address: CellAddress,
    private val newValue: CellValue,
    override val description: String = "Edit cell $address"
) : SpreadsheetCommand {
    private var oldValue: CellValue? = null

    override fun execute() {
        if (oldValue == null) oldValue = sheet.valueAt(address)
        sheet.setValue(address, newValue)
    }

    override fun undo() {
        sheet.setValue(address, oldValue ?: CellValue.Empty)
    }
}

class ClearRangeCommand(
    private val sheet: SpreadsheetSheet,
    private val range: CellRange
) : SpreadsheetCommand {
    private val backup = mutableMapOf<CellAddress, SpreadsheetCell>()
    private var captured = false

    override val description: String = "Clear $range"

    override fun execute() {
        if (!captured) {
            sheet.cellsIn(range).forEach { (address, cell) -> backup[address] = cell.copy(style = cell.style) }
            captured = true
        }
        sheet.clear(range)
    }

    override fun undo() {
        backup.forEach { (address, cell) ->
            sheet.cell(address).value = cell.value
            sheet.cell(address).style = cell.style
        }
    }
}
