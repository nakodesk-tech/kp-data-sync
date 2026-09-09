package com.example.ui

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal data class InAppSheet(val name: String, val entryName: String, val cells: List<MutableList<String>>) 

internal class InAppXlsxWorkbook private constructor(
  private val entries: LinkedHashMap<String, ByteArray>,
  val sheets: List<InAppSheet>
) {
  private val edits = mutableMapOf<String, MutableMap<String, String>>()

  fun setCell(sheetIndex: Int, row: Int, column: Int, value: String) {
    val sheet = sheets.getOrNull(sheetIndex) ?: return
    while (sheet.cells.size <= row) sheet.cells.add(mutableListOf())
    while (sheet.cells[row].size <= column) sheet.cells[row].add("")
    sheet.cells[row][column] = value
    edits.getOrPut(sheet.entryName) { mutableMapOf() }[cellRef(row + 1, column + 1)] = value
  }

  fun saveTo(target: File) {
    val outputEntries = LinkedHashMap(entries)
    edits.forEach { (entryName, changed) ->
      val xml = outputEntries[entryName] ?: return@forEach
      outputEntries[entryName] = updateSheet(xml, changed)
    }
    ZipOutputStream(FileOutputStream(target)).use { zip ->
      outputEntries.forEach { (name, bytes) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
  }

  private fun updateSheet(bytes: ByteArray, changed: Map<String, String>): ByteArray {
    val doc = parseXml(bytes)
    val root = doc.documentElement
    val sheetData = root.getElementsByTagName("sheetData").item(0) as? Element ?: return bytes
    changed.forEach { (ref, value) ->
      val rowNumber = ref.filter(Char::isDigit).toIntOrNull() ?: return@forEach
      var row = findRow(sheetData, rowNumber)
      if (row == null) {
        row = doc.createElement("row")
        row.setAttribute("r", rowNumber.toString())
        sheetData.appendChild(row)
      }
      var cell = findCell(row, ref)
      if (cell == null) {
        cell = doc.createElement("c")
        cell.setAttribute("r", ref)
        row.appendChild(cell)
      }
      while (cell.hasChildNodes()) cell.removeChild(cell.firstChild)
      cell.setAttribute("t", "inlineStr")
      val isNode = doc.createElement("is")
      val textNode = doc.createElement("t")
      if (value.startsWith(" ") || value.endsWith(" ")) textNode.setAttribute("xml:space", "preserve")
      textNode.appendChild(doc.createTextNode(value))
      isNode.appendChild(textNode)
      cell.appendChild(isNode)
    }
    return serializeXml(doc)
  }

  private fun findRow(sheetData: Element, number: Int): Element? {
    val rows = sheetData.getElementsByTagName("row")
    for (i in 0 until rows.length) if ((rows.item(i) as Element).getAttribute("r").toIntOrNull() == number) return rows.item(i) as Element
    return null
  }

  private fun findCell(row: Element, ref: String): Element? {
    val cells = row.getElementsByTagName("c")
    for (i in 0 until cells.length) if ((cells.item(i) as Element).getAttribute("r") == ref) return cells.item(i) as Element
    return null
  }

  private fun parseXml(bytes: ByteArray): Document {
    val f = DocumentBuilderFactory.newInstance().apply {
      setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      setFeature("http://xml.org/sax/features/external-general-entities", false)
      setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      isXIncludeAware = false
      isExpandEntityReferences = false
    }
    return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
  }

  private fun serializeXml(doc: Document): ByteArray {
    val out = ByteArrayOutputStream()
    val transformer = TransformerFactory.newInstance().newTransformer().apply {
      setOutputProperty(OutputKeys.ENCODING, "UTF-8")
      setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
    }
    transformer.transform(DOMSource(doc), StreamResult(out))
    return out.toByteArray()
  }

  private fun cellRef(row: Int, column: Int): String {
    var n = column
    val sb = StringBuilder()
    while (n > 0) { val r = (n - 1) % 26; sb.append(('A'.code + r).toChar()); n = (n - 1) / 26 }
    return sb.reverse().append(row).toString()
  }

  companion object {
    fun load(file: File): Result<InAppXlsxWorkbook> = runCatching {
      val entries = LinkedHashMap<String, ByteArray>()
      ZipInputStream(FileInputStream(file)).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          entries[entry.name] = zip.readBytes()
        }
      }
      val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
      val workbook = entries["xl/workbook.xml"] ?: error("Excel workbook metadata not found")
      val rels = entries["xl/_rels/workbook.xml.rels"]
      val relMap = mutableMapOf<String, String>()
      if (rels != null) {
        val doc = parseXmlStatic(rels)
        val nodes = doc.getElementsByTagName("Relationship")
        for (i in 0 until nodes.length) {
          val e = nodes.item(i) as Element
          val id = e.getAttribute("Id")
          val target = e.getAttribute("Target")
          if (id.isNotBlank() && target.isNotBlank()) relMap[id] = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
        }
      }
      val wbDoc = parseXmlStatic(workbook)
      val sheetNodes = wbDoc.getElementsByTagName("sheet")
      val sheets = mutableListOf<InAppSheet>()
      for (i in 0 until sheetNodes.length) {
        val e = sheetNodes.item(i) as Element
        val name = e.getAttribute("name").ifBlank { "Sheet ${i + 1}" }
        val rid = e.getAttribute("r:id").ifBlank { e.getAttribute("id") }
        val entryName = relMap[rid] ?: "xl/worksheets/sheet${i + 1}.xml"
        val xml = entries[entryName] ?: continue
        sheets += parseSheet(name, entryName, xml, shared)
      }
      if (sheets.isEmpty()) error("No Excel worksheets found")
      InAppXlsxWorkbook(entries, sheets)
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
      if (bytes == null) return emptyList()
      val doc = parseXmlStatic(bytes)
      val nodes = doc.getElementsByTagName("si")
      return buildList {
        for (i in 0 until nodes.length) {
          val ts = (nodes.item(i) as Element).getElementsByTagName("t")
          val value = buildString { for (j in 0 until ts.length) append(ts.item(j).textContent) }
          add(value)
        }
      }
    }

    private fun parseSheet(name: String, entryName: String, bytes: ByteArray, shared: List<String>): InAppSheet {
      val doc = parseXmlStatic(bytes)
      val rows = doc.getElementsByTagName("row")
      val matrix = mutableListOf<MutableList<String>>()
      var maxColumn = 0
      for (i in 0 until rows.length) {
        val rowElement = rows.item(i) as Element
        val rowNumber = rowElement.getAttribute("r").toIntOrNull() ?: (i + 1)
        while (matrix.size < rowNumber) matrix.add(mutableListOf())
        val row = matrix[rowNumber - 1]
        val cells = rowElement.getElementsByTagName("c")
        for (j in 0 until cells.length) {
          val cell = cells.item(j) as Element
          val ref = cell.getAttribute("r")
          val col = ref.filter(Char::isLetter).fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar().code - 'A'.code + 1) }
          if (col <= 0) continue
          while (row.size < col) row.add("")
          val type = cell.getAttribute("t")
          val value = when (type) {
            "s" -> cell.getElementsByTagName("v").item(0)?.textContent?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "inlineStr" -> cell.getElementsByTagName("t").item(0)?.textContent.orEmpty()
            "b" -> if (cell.getElementsByTagName("v").item(0)?.textContent == "1") "TRUE" else "FALSE"
            else -> cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
          }
          row[col - 1] = value
          if (col > maxColumn) maxColumn = col
        }
      }
      matrix.forEach { while (it.size < maxColumn) it.add("") }
      return InAppSheet(name, entryName, matrix)
    }

    private fun parseXmlStatic(bytes: ByteArray): Document {
      val f = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
      }
      return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
  }
}
