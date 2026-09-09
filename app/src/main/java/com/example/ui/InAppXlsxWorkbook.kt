package com.example.ui

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal data class InAppSheet(
  val name: String,
  val entryName: String,
  val cells: MutableList<MutableList<String>>,
  val formulas: MutableList<MutableList<String?>> = mutableListOf(),
  val styles: MutableList<MutableList<ExcelCellStyle>> = mutableListOf(),
  var hidden: Boolean = false
)

internal class InAppXlsxWorkbook private constructor(
  private val entries: LinkedHashMap<String, ByteArray>,
  val sheets: MutableList<InAppSheet>
) {
  private val merges = mutableMapOf<String, MutableSet<String>>()

  fun setCell(sheetIndex: Int, row: Int, column: Int, value: String) { ensure(sheetIndex,row,column); val s=sheets[sheetIndex];s.cells[row][column]=value;s.formulas[row][column]=value.trim().takeIf{it.startsWith("=")}; }
  fun setStyle(sheetIndex:Int,row:Int,column:Int,style:ExcelCellStyle){ensure(sheetIndex,row,column);sheets[sheetIndex].styles[row][column]=style.copy()}
  fun styleAt(sheetIndex:Int,row:Int,column:Int):ExcelCellStyle{ensure(sheetIndex,row,column);return sheets[sheetIndex].styles[row][column].copy()}
  fun clearCell(sheetIndex:Int,row:Int,column:Int)=setCell(sheetIndex,row,column,"")

  fun insertRow(sheetIndex:Int,at:Int){val s=sheets[sheetIndex];val i=at.coerceIn(0,s.cells.size);val w=s.cells.maxOfOrNull{it.size}?:1;s.cells.add(i,MutableList(w){""});s.formulas.add(i,MutableList(w){null});s.styles.add(i,MutableList(w){ExcelCellStyle()});}
  fun deleteRow(sheetIndex:Int,at:Int){val s=sheets[sheetIndex];if(at !in s.cells.indices)return;s.cells.removeAt(at);if(at<s.formulas.size)s.formulas.removeAt(at);if(at<s.styles.size)s.styles.removeAt(at)}
  fun insertColumn(sheetIndex:Int,at:Int){val s=sheets[sheetIndex];val i=at.coerceAtLeast(0);s.cells.forEachIndexed{r,row->while(row.size<i)row.add("");row.add(i,"");while(s.formulas.size<=r)s.formulas.add(mutableListOf());while(s.styles.size<=r)s.styles.add(mutableListOf());while(s.formulas[r].size<row.size-1)s.formulas[r].add(null);s.formulas[r].add(i,null);while(s.styles[r].size<row.size-1)s.styles[r].add(ExcelCellStyle());s.styles[r].add(i,ExcelCellStyle())}}
  fun deleteColumn(sheetIndex:Int,at:Int){val s=sheets[sheetIndex];s.cells.forEachIndexed{r,row->if(at in row.indices)row.removeAt(at);if(r<s.formulas.size&&at in s.formulas[r].indices)s.formulas[r].removeAt(at);if(r<s.styles.size&&at in s.styles[r].indices)s.styles[r].removeAt(at)}}
  fun merge(sheetIndex:Int,r1:Int,c1:Int,r2:Int,c2:Int){val a=minOf(r1,r2);val b=maxOf(r1,r2);val c=minOf(c1,c2);val d=maxOf(c1,c2);ensure(sheetIndex,b,d);merges.getOrPut(sheets[sheetIndex].entryName){mutableSetOf()}.add("${cellRef(a+1,c+1)}:${cellRef(b+1,d+1)}")}
  fun unmerge(sheetIndex:Int,r1:Int,c1:Int,r2:Int,c2:Int){val a=minOf(r1,r2);val b=maxOf(r1,r2);val c=minOf(c1,c2);val d=maxOf(c1,c2);merges[sheets[sheetIndex].entryName]?.remove("${cellRef(a+1,c+1)}:${cellRef(b+1,d+1)}")}
  fun isMerged(sheetIndex:Int,row:Int,column:Int):Boolean=merges[sheets[sheetIndex].entryName].orEmpty().any{rangeContains(it,row+1,column+1)}

  fun insertSheet(name:String):Int{val base=name.trim().ifBlank{"Sheet ${sheets.size+1}"};val safe=uniqueSheetName(base);val entry="xl/worksheets/sheet${nextSheetNumber()}.xml";entries[entry]=blankSheetXml();sheets.add(InAppSheet(safe,entry,mutableListOf(mutableListOf()),mutableListOf(mutableListOf(null)),mutableListOf(mutableListOf(ExcelCellStyle()))));return sheets.lastIndex}
  fun renameSheet(index:Int,name:String){if(index !in sheets.indices)return;val n=name.trim();if(n.isBlank()||sheets.indices.any{it!=index&&sheets[it].name.equals(n,true)})return;val s=sheets[index];sheets[index]=InAppSheet(n,s.entryName,s.cells,s.formulas,s.styles,s.hidden)}
  fun deleteSheet(index:Int){if(sheets.size<=1||index !in sheets.indices)return;entries.remove(sheets[index].entryName);sheets.removeAt(index)}
  fun duplicateSheet(index:Int):Int{if(index !in sheets.indices)return index;val src=sheets[index];val entry="xl/worksheets/sheet${nextSheetNumber()}.xml";entries[entry]=entries[src.entryName]?.clone()?:blankSheetXml();sheets.add(index+1,InAppSheet(uniqueSheetName(src.name+" Copy"),entry,src.cells.map{it.toMutableList()}.toMutableList(),src.formulas.map{it.toMutableList()}.toMutableList(),src.styles.map{it.map{st->st.copy()}.toMutableList()}.toMutableList(),src.hidden));return index+1}

  fun snapshot():InAppXlsxWorkbook{val e=LinkedHashMap<String,ByteArray>();entries.forEach{(k,v)->e[k]=v.clone()};val s=sheets.map{InAppSheet(it.name,it.entryName,it.cells.map{r->r.toMutableList()}.toMutableList(),it.formulas.map{r->r.toMutableList()}.toMutableList(),it.styles.map{r->r.map{st->st.copy()}.toMutableList()}.toMutableList(),it.hidden)}.toMutableList();val w=InAppXlsxWorkbook(e,s);merges.forEach{(k,v)->w.merges[k]=v.toMutableSet()};return w}
  fun restoreFrom(other:InAppXlsxWorkbook){entries.clear();other.entries.forEach{(k,v)->entries[k]=v.clone()};sheets.clear();sheets.addAll(other.sheets.map{InAppSheet(it.name,it.entryName,it.cells.map{r->r.toMutableList()}.toMutableList(),it.formulas.map{r->r.toMutableList()}.toMutableList(),it.styles.map{r->r.map{st->st.copy()}.toMutableList()}.toMutableList(),it.hidden)});merges.clear();other.merges.forEach{(k,v)->merges[k]=v.toMutableSet()}}

  fun saveTo(target:File){val output=LinkedHashMap(entries);sheets.forEach{s->output[s.entryName]=updateSheet(output[s.entryName]?:blankSheetXml(),s)};materializeStyles(output);updateWorkbookXml(output);ZipOutputStream(FileOutputStream(target)).use{zip->output.forEach{(name,bytes)->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}}}

  private fun parseXml(bytes: ByteArray): Document = parseXmlStatic(bytes)

  private var styleMap: Map<ExcelCellStyle, Int> = emptyMap()

  private fun updateSheet(bytes: ByteArray, sheet: InAppSheet): ByteArray {
    val doc = parseXml(bytes)
    val root = doc.documentElement
    val data = root.getElementsByTagName("sheetData").item(0) as? Element ?: return bytes
    while (data.hasChildNodes()) data.removeChild(data.firstChild)
    val width = sheet.cells.maxOfOrNull { it.size } ?: 1
    for (r in sheet.cells.indices) {
      val row = doc.createElement("row")
      row.setAttribute("r", (r + 1).toString())
      for (c in 0 until width) {
        val v = sheet.cells[r].getOrElse(c) { "" }
        val cell = doc.createElement("c")
        cell.setAttribute("r", cellRef(r + 1, c + 1))
        val st = sheet.styles.getOrNull(r)?.getOrNull(c)
        if (st != null) cell.setAttribute("s", (styleMap[st] ?: 0).toString())
        val f = sheet.formulas.getOrNull(r)?.getOrNull(c)
        if (f != null) {
          val fn = doc.createElement("f")
          fn.appendChild(doc.createTextNode(f.removePrefix("=")))
          cell.appendChild(fn)
          val vv = doc.createElement("v")
          vv.appendChild(doc.createTextNode(InAppExcelFormula.evaluate(f) { ref -> cellNumber(sheet, ref) }?.toString().orEmpty()))
          cell.appendChild(vv)
        } else if (v.isNotEmpty()) {
          val num = v.toDoubleOrNull()
          if (num != null) {
            val vv = doc.createElement("v")
            vv.appendChild(doc.createTextNode(v))
            cell.appendChild(vv)
          } else {
            cell.setAttribute("t", "inlineStr")
            val isNode = doc.createElement("is")
            val t = doc.createElement("t")
            t.appendChild(doc.createTextNode(v))
            isNode.appendChild(t)
            cell.appendChild(isNode)
          }
        }
        row.appendChild(cell)
      }
      data.appendChild(row)
    }
    val ranges = merges[sheet.entryName].orEmpty()
    var mc = root.getElementsByTagName("mergeCells").item(0) as? Element
    if (ranges.isNotEmpty() && mc == null) {
      mc = doc.createElement("mergeCells")
      root.appendChild(mc)
    }
    if (mc != null) {
      while (mc.hasChildNodes()) mc.removeChild(mc.firstChild)
      ranges.forEach { m ->
        val x = doc.createElement("mergeCell")
        x.setAttribute("ref", m)
        mc.appendChild(x)
      }
      mc.setAttribute("count", ranges.size.toString())
    }
    return serializeXml(doc)
  }

  private fun materializeStyles(output: MutableMap<String, ByteArray>) {
    val styles = output["xl/styles.xml"] ?: defaultStylesXml()
    val doc = parseXml(styles)
    val fonts = doc.getElementsByTagName("fonts").item(0) as Element
    val fills = doc.getElementsByTagName("fills").item(0) as Element
    val borders = doc.getElementsByTagName("borders").item(0) as Element
    val xfs = doc.getElementsByTagName("cellXfs").item(0) as Element
    val map = LinkedHashMap<ExcelCellStyle, Int>()
    val all = buildSet<ExcelCellStyle> {
      sheets.forEach { s ->
        s.styles.forEach { row ->
          row.forEach { add(it) }
        }
      }
    }
    var fontCount = fonts.getAttribute("count").toIntOrNull() ?: fonts.getElementsByTagName("font").length
    var fillCount = fills.getAttribute("count").toIntOrNull() ?: fills.getElementsByTagName("fill").length
    var borderCount = borders.getAttribute("count").toIntOrNull() ?: borders.getElementsByTagName("border").length
    var xfCount = xfs.getAttribute("count").toIntOrNull() ?: xfs.getElementsByTagName("xf").length
    all.forEach { st ->
      if (st == ExcelCellStyle()) {
        map[st] = 0
      } else {
        val font = doc.createElement("font")
        font.appendChild(doc.createElement("sz").apply { setAttribute("val", st.fontSize.toString()) })
        font.appendChild(doc.createElement("name").apply { setAttribute("val", st.fontName) })
        if (st.bold) font.appendChild(doc.createElement("b"))
        if (st.italic) font.appendChild(doc.createElement("i"))
        if (st.underline) font.appendChild(doc.createElement("u"))
        st.foreground?.let { font.appendChild(doc.createElement("color").apply { setAttribute("rgb", "FF$it") }) }
        fonts.appendChild(font)

        val fill = doc.createElement("fill")
        val pattern = doc.createElement("patternFill").apply { setAttribute("patternType", if (st.background != null) "solid" else "none") }
        st.background?.let { pattern.appendChild(doc.createElement("fgColor").apply { setAttribute("rgb", "FF$it") }) }
        fill.appendChild(pattern)
        fills.appendChild(fill)

        val border = doc.createElement("border")
        val sides = listOf("left", "right", "top", "bottom")
        if (st.border) {
          sides.forEach { side ->
            val bElem = doc.createElement(side)
            bElem.setAttribute("style", "thin")
            border.appendChild(bElem)
          }
        } else {
          sides.forEach { side ->
            val bElem = doc.createElement(side)
            border.appendChild(bElem)
          }
        }
        borders.appendChild(border)

        val xf = doc.createElement("xf")
        xf.setAttribute("numFmtId", numberFormatId(doc, st.numberFormat).toString())
        xf.setAttribute("fontId", fontCount.toString())
        xf.setAttribute("fillId", fillCount.toString())
        xf.setAttribute("borderId", borderCount.toString())
        xf.setAttribute("xfId", "0")
        if (st.horizontal != "general" || st.vertical != "center" || st.wrap) {
          xf.setAttribute("applyAlignment", "1")
          val alignment = doc.createElement("alignment")
          if (st.horizontal != "general") alignment.setAttribute("horizontal", st.horizontal)
          if (st.vertical != "center") alignment.setAttribute("vertical", st.vertical)
          if (st.wrap) alignment.setAttribute("wrapText", "1")
          xf.appendChild(alignment)
        }
        xfs.appendChild(xf)
        map[st] = xfCount
        fontCount++
        fillCount++
        borderCount++
        xfCount++
      }
    }
    fonts.setAttribute("count", fontCount.toString())
    fills.setAttribute("count", fillCount.toString())
    borders.setAttribute("count", borderCount.toString())
    xfs.setAttribute("count", xfCount.toString())
    styleMap = map
    output["xl/styles.xml"] = serializeXml(doc)
  }

  private fun numberFormatId(doc: Document, format: String?): Int {
    if (format == null) return 0
    return when (format) {
      "0" -> 1
      "0.00" -> 2
      "0%" -> 9
      "0.00%" -> 10
      "yyyy-mm-dd" -> 14
      else -> {
        val n = 164 + doc.getElementsByTagName("numFmt").length
        val root = doc.getElementsByTagName("numFmts").item(0) as? Element
          ?: doc.createElement("numFmts").also { doc.documentElement.insertBefore(it, doc.documentElement.firstChild) }
        val x = doc.createElement("numFmt")
        x.setAttribute("numFmtId", n.toString())
        x.setAttribute("formatCode", format)
        root.appendChild(x)
        root.setAttribute("count", root.getElementsByTagName("numFmt").length.toString())
        n
      }
    }
  }

  private fun updateWorkbookXml(output: MutableMap<String, ByteArray>) {
    val wb = output["xl/workbook.xml"] ?: return
    val doc = parseXml(wb)
    val sp = doc.getElementsByTagName("sheets").item(0) as? Element ?: return
    while (sp.hasChildNodes()) sp.removeChild(sp.firstChild)
    sheets.forEachIndexed { i, s ->
      val e = doc.createElement("sheet")
      e.setAttribute("name", s.name)
      e.setAttribute("sheetId", (i + 1).toString())
      e.setAttribute("r:id", "rId${i + 1}")
      sp.appendChild(e)
    }
    output["xl/workbook.xml"] = serializeXml(doc)
  }

  private fun ensure(si: Int, r: Int, c: Int) {
    val s = sheets[si]
    while (s.cells.size <= r) {
      val w = s.cells.maxOfOrNull { it.size } ?: 0
      s.cells.add(MutableList(w) { "" })
      s.formulas.add(MutableList(w) { null })
      s.styles.add(MutableList(w) { ExcelCellStyle() })
    }
    while (s.cells[r].size <= c) s.cells[r].add("")
    while (s.formulas[r].size <= c) s.formulas[r].add(null)
    while (s.styles[r].size <= c) s.styles[r].add(ExcelCellStyle())
  }

  private fun cellNumber(s: InAppSheet, ref: String): Double? {
    val m = Regex("([A-Z]+)([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(ref.trim()) ?: return null
    val c = m.groupValues[1].uppercase().fold(0) { n, ch -> n * 26 + ch.code - 64 } - 1
    val r = m.groupValues[2].toInt() - 1
    return s.cells.getOrNull(r)?.getOrNull(c)?.toDoubleOrNull()
  }

  private fun rangeContains(range: String, row: Int, col: Int): Boolean {
    val p = range.split(':')
    if (p.size != 2) return false
    val a = parseCellRefCoord(p[0]) ?: return false
    val b = parseCellRefCoord(p[1]) ?: return false
    return row in minOf(a.first, b.first)..maxOf(a.first, b.first) && col in minOf(a.second, b.second)..maxOf(a.second, b.second)
  }

  private fun parseCellRefCoord(ref: String): Pair<Int, Int>? {
    val m = Regex("([A-Z]+)([0-9]+)").matchEntire(ref) ?: return null
    val row = m.groupValues[2].toInt()
    val col = m.groupValues[1].fold(0) { n, ch -> n * 26 + ch.code - 64 }
    return Pair(row, col)
  }

  private fun uniqueSheetName(base: String): String {
    var n = base
    var i = 2
    while (sheets.any { it.name.equals(n, true) }) n = "$base ${i++}"
    return n
  }

  private fun nextSheetNumber(): Int {
    val nums = sheets.mapNotNull { Regex("sheet(\\d+)\\.xml").find(it.entryName)?.groupValues?.get(1)?.toIntOrNull() }
    return (nums.maxOrNull() ?: 0) + 1
  }

  private fun cellRef(row: Int, column: Int): String {
    var n = column
    val sb = StringBuilder()
    while (n > 0) {
      val r = (n - 1) % 26
      sb.append(('A'.code + r).toChar())
      n = (n - 1) / 26
    }
    return sb.reverse().append(row).toString()
  }

  private fun blankSheetXml(): ByteArray =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData/></worksheet>".toByteArray()

  private fun defaultStylesXml(): ByteArray =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><numFmts count=\"0\"/><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs></styleSheet>".toByteArray()

  companion object {
    fun load(file: File): Result<InAppXlsxWorkbook> = runCatching {
      val entries = LinkedHashMap<String, ByteArray>()
      ZipInputStream(FileInputStream(file)).use { z ->
        while (true) {
          val e = z.nextEntry ?: break
          entries[e.name] = z.readBytes()
        }
      }
      val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
      val wb = entries["xl/workbook.xml"] ?: error("Excel workbook metadata not found")
      val rels = entries["xl/_rels/workbook.xml.rels"]
      val relMap = mutableMapOf<String, String>()
      if (rels != null) {
        val d = parseXmlStatic(rels)
        val ns = d.getElementsByTagName("Relationship")
        for (i in 0 until ns.length) {
          val e = ns.item(i) as Element
          relMap[e.getAttribute("Id")] = e.getAttribute("Target").let { if (it.startsWith("/")) it.removePrefix("/") else "xl/$it" }
        }
      }
      val d = parseXmlStatic(wb)
      val ns = d.getElementsByTagName("sheet")
      val sheets = mutableListOf<InAppSheet>()
      for (i in 0 until ns.length) {
        val e = ns.item(i) as Element
        val name = e.getAttribute("name").ifBlank { "Sheet ${i + 1}" }
        val rid = e.getAttribute("r:id").ifBlank { e.getAttribute("id") }
        val entry = relMap[rid] ?: "xl/worksheets/sheet${i + 1}.xml"
        entries[entry]?.let { sheets += parseSheet(name, entry, it, shared, e.getAttribute("state") == "hidden") }
      }
      if (sheets.isEmpty()) error("No Excel worksheets found")
      InAppXlsxWorkbook(entries, sheets)
    }

    private fun parseSharedStrings(b: ByteArray?): List<String> {
      if (b == null) return emptyList()
      val d = parseXmlStatic(b)
      val ns = d.getElementsByTagName("si")
      return buildList {
        for (i in 0 until ns.length) {
          val ts = (ns.item(i) as Element).getElementsByTagName("t")
          add(buildString { for (j in 0 until ts.length) append(ts.item(j).textContent) })
        }
      }
    }

    private fun parseSheet(name: String, entryName: String, b: ByteArray, shared: List<String>, hidden: Boolean): InAppSheet {
      val d = parseXmlStatic(b)
      val rows = d.getElementsByTagName("row")
      val matrix = mutableListOf<MutableList<String>>()
      val fs = mutableListOf<MutableList<String?>>()
      val st = mutableListOf<MutableList<ExcelCellStyle>>()
      var max = 0
      for (i in 0 until rows.length) {
        val re = rows.item(i) as Element
        val rn = re.getAttribute("r").toIntOrNull() ?: (i + 1)
        while (matrix.size < rn) {
          matrix.add(mutableListOf())
          fs.add(mutableListOf())
          st.add(mutableListOf())
        }
        val row = matrix[rn - 1]
        val fr = fs[rn - 1]
        val sr = st[rn - 1]
        val cs = re.getElementsByTagName("c")
        for (j in 0 until cs.length) {
          val c = cs.item(j) as Element
          val ref = c.getAttribute("r")
          val col = ref.filter(Char::isLetter).fold(0) { a, ch -> a * 26 + (ch.uppercaseChar().code - 64) }
          if (col <= 0) continue
          while (row.size < col) row.add("")
          while (fr.size < col) fr.add(null)
          while (sr.size < col) sr.add(ExcelCellStyle())
          fr[col - 1] = c.getElementsByTagName("f").item(0)?.textContent?.let { "=$it" }
          row[col - 1] = when (c.getAttribute("t")) {
            "s" -> c.getElementsByTagName("v").item(0)?.textContent?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "inlineStr" -> c.getElementsByTagName("t").item(0)?.textContent.orEmpty()
            "b" -> if (c.getElementsByTagName("v").item(0)?.textContent == "1") "TRUE" else "FALSE"
            else -> c.getElementsByTagName("v").item(0)?.textContent.orEmpty()
          }
          max = maxOf(max, col)
        }
      }
      matrix.forEach { while (it.size < max) it.add("") }
      fs.forEach { while (it.size < max) it.add(null) }
      st.forEach { while (it.size < max) it.add(ExcelCellStyle()) }
      return InAppSheet(name, entryName, matrix, fs, st, hidden)
    }

    private fun parseXmlStatic(b: ByteArray): Document {
      val h = String(b, Charsets.UTF_8).lowercase(Locale.ROOT)
      require("<!doctype" !in h) { "DOCTYPE declarations are not allowed in Excel XML" }
      val f = DocumentBuilderFactory.newInstance().apply {
        try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
        try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
        try { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
        try { isXIncludeAware = false } catch (_: Exception) {}
        try { isExpandEntityReferences = false } catch (_: Exception) {}
      }
      return f.newDocumentBuilder().parse(ByteArrayInputStream(b))
    }
  }
  private fun serializeXml(d:Document):ByteArray{val o=ByteArrayOutputStream();TransformerFactory.newInstance().newTransformer().apply{setOutputProperty(OutputKeys.ENCODING,"UTF-8");setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,"no")}.transform(DOMSource(d),StreamResult(o));return o.toByteArray()}
}
