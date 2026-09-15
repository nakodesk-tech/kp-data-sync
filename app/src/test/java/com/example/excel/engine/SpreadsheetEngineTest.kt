package com.example.excel.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadsheetEngineTest {
    @Test fun cellReferencesRoundTrip() {
        assertEquals(CellAddress(0, 0), CellAddress.parse("A1"))
        assertEquals(CellAddress(9, 26), CellAddress.parse("AA10"))
        assertEquals("A1", CellAddress(0, 0).toString())
        assertEquals("AA10", CellAddress(9, 26).toString())
    }

    @Test fun rangeNormalizesReverseCoordinates() {
        val range = CellRange(CellAddress(4, 3), CellAddress(1, 1))
        assertEquals(4, range.rowCount)
        assertEquals(3, range.columnCount)
        assertTrue(range.contains(CellAddress(2, 2)))
        assertEquals(12, range.addresses().count())
    }

    @Test fun selectionSupportsCellRangeRowAndColumn() {
        val selection = SelectionModel()
        selection.selectCell(CellAddress(1, 1))
        selection.extendTo(CellAddress(3, 3))
        assertTrue(selection.contains(CellAddress(2, 2)))
        selection.selectRow(4, 9)
        assertTrue(selection.contains(CellAddress(4, 9)))
        selection.selectColumn(2, 9)
        assertTrue(selection.contains(CellAddress(9, 2)))
    }

    @Test fun formulaResolvesDependentCells() {
        val sheet = SpreadsheetSheet("Sheet1")
        sheet.setValue(CellAddress.parse("A1"), CellValue.Number(10.0))
        sheet.setValue(CellAddress.parse("B1"), CellValue.Formula("=A1+2"))
        sheet.setValue(CellAddress.parse("C1"), CellValue.Formula("=B1+1"))
        val engine = FormulaEngine { if (it == "Sheet1") sheet else null }
        assertEquals(CellValue.Number(13.0), engine.evaluate("Sheet1", "=C1"))
    }

    @Test fun formulaDetectsCircularReference() {
        val sheet = SpreadsheetSheet("Sheet1")
        sheet.setValue(CellAddress.parse("A1"), CellValue.Formula("=B1+1"))
        sheet.setValue(CellAddress.parse("B1"), CellValue.Formula("=A1+1"))
        val engine = FormulaEngine { sheet }
        assertEquals(CellValue.Error("#CIRC!"), engine.evaluate("Sheet1", "=A1"))
    }

    @Test fun historyUndoRedoRestoresCell() {
        val sheet = SpreadsheetSheet("Sheet1")
        val history = SpreadsheetHistory()
        val a1 = CellAddress.parse("A1")
        history.execute(SetCellCommand(sheet, a1, CellValue.Number(42.0)))
        assertEquals(CellValue.Number(42.0), sheet.valueAt(a1))
        assertTrue(history.undo())
        assertEquals(CellValue.Empty, sheet.valueAt(a1))
        assertTrue(history.redo())
        assertEquals(CellValue.Number(42.0), sheet.valueAt(a1))
    }
}
