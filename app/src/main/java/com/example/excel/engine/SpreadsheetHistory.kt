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

class StyleRangeCommand(
    private val sheet: SpreadsheetSheet,
    private val range: CellRange,
    private val transform: (CellStyle) -> CellStyle,
    override val description: String = "Format $range"
) : SpreadsheetCommand {
    private val backup = mutableMapOf<CellAddress, CellStyle>()
    private var captured = false

    override fun execute() {
        if (!captured) {
            range.addresses().forEach { address -> backup[address] = sheet.cell(address).style }
            captured = true
        }
        range.addresses().forEach { address ->
            sheet.cell(address).style = transform(sheet.cell(address).style)
        }
    }

    override fun undo() {
        backup.forEach { (address, style) -> sheet.cell(address).style = style }
    }
}

class BatchEditCommand(
    private val sheet: SpreadsheetSheet,
    private val edits: List<Pair<CellAddress, Pair<CellValue, CellStyle?>>>,
    override val description: String = "Batch edit"
) : SpreadsheetCommand {
    private val backup = mutableMapOf<CellAddress, Pair<CellValue, CellStyle>>()
    private var captured = false

    override fun execute() {
        if (!captured) {
            edits.forEach { (address, _) ->
                backup[address] = sheet.valueAt(address) to sheet.cell(address).style
            }
            captured = true
        }
        edits.forEach { (address, pair) ->
            sheet.setValue(address, pair.first)
            pair.second?.let { sheet.cell(address).style = it }
        }
    }

    override fun undo() {
        backup.forEach { (address, pair) ->
            sheet.setValue(address, pair.first)
            sheet.cell(address).style = pair.second
        }
    }
}

