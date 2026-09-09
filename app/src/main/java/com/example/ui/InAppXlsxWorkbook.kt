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
  private val edits = mutableSetOf<String>()
  private val deletedSheets = mutableSetOf<String>()
  private val merges = mutableMapOf<String, MutableSet<String>>()

  fun setCell(sheetIndex: Int, row: Int, column: Int, value: String) {
    ensure(sheetIndex, row, column)
    val sheet = sheets[sheetIndex]
    sheet.cells[row][column] = value
    sheet.formulas[row][column] = value.takeIf { it.trim().startsWith("=") }
    edits += key(sheetIndex, row, column)
  }

  fun setStyle(sheetIndex: Int, row: Int, column: Int, style: ExcelCellStyle) {
    ensure(sheetIndex, row, column)
    sheets[sheetIndex].styles[row][column] = style.copy()
    edits += key(sheetIndex, row, column)
  }

  fun styleAt(sheetIndex: Int, row: Int, column: Int): ExcelCellStyle {
    ensure(sheetIndex, row, column)
    return sheets[sheetIndex].styles[row][column].copy()
  }

  fun clearCell(sheetIndex: Int, row: Int, column: Int) = setCell(sheetIndex, row, column, "")

  fun insertRow(sheetIndex: Int, at: Int) {
    val s = sheets[sheetIndex]; val index = at.coerceIn(0, s.cells.size)
    val width = s.cells.maxOfOrNull { it.size } ?: 1
    s.cells.add(index, MutableList(width) { "" }); s.formulas.add(index, MutableList(width) { null }); s.styles.add(index, MutableList(width) { ExcelCellStyle() })
    rebuildEditKeys(sheetIndex)
  }

  fun deleteRow(sheetIndex: Int, at: Int) {
    val s=sheets[sheetIndex]; if(at !in s.cells.indices)return
    s.cells.removeAt(at); if(at<s.formulas.size)s.formulas.removeAt(at); if(at<s.styles.size)s.styles.removeAt(at); rebuildEditKeys(sheetIndex)
  }

  fun insertColumn(sheetIndex: Int, at: Int) {
    val s=sheets[sheetIndex]; val index=at.coerceAtLeast(0)
    s.cells.forEachIndexed { r,row -> while(row.size<index)row.add(""); row.add(index,""); if(r>=s.formulas.size)s.formulas.add(MutableList(row.size){null}); val fs=s.formulas[r]; while(fs.size<row.size-1)fs.add(null); fs.add(index,null); if(r>=s.styles.size)s.styles.add(MutableList(row.size){ExcelCellStyle()}); val st=s.styles[r]; while(st.size<row.size-1)st.add(ExcelCellStyle()); st.add(index,ExcelCellStyle()) }
    rebuildEditKeys(sheetIndex)
  }

  fun deleteColumn(sheetIndex: Int, at: Int) {
    val s=sheets[sheetIndex]; s.cells.forEachIndexed { r,row -> if(at in row.indices)row.removeAt(at); if(r<s.formulas.size&&at in s.formulas[r].indices)s.formulas[r].removeAt(at); if(r<s.styles.size&&at in s.styles[r].indices)s.styles[r].removeAt(at) }; rebuildEditKeys(sheetIndex)
  }

  fun merge(sheetIndex:Int, r1:Int,c1:Int,r2:Int,c2:Int){val a=minOf(r1,r2);val b=maxOf(r1,r2);val c=minOf(c1,c2);val d=maxOf(c1,c2);ensure(sheetIndex,b,d);val ref="${cellRef(a+1,c+1)}:${cellRef(b+1,d+1)}";merges.getOrPut(sheets[sheetIndex].entryName){mutableSetOf()}.add(ref)}
  fun unmerge(sheetIndex:Int,r1:Int,c1:Int,r2:Int,c2:Int){val a=minOf(r1,r2);val b=maxOf(r1,r2);val c=minOf(c1,c2);val d=maxOf(c1,c2);val ref="${cellRef(a+1,c+1)}:${cellRef(b+1,d+1)}";merges[sheets[sheetIndex].entryName]?.remove(ref)}

  fun insertSheet(name: String): Int {
    val safeBase=name.trim().ifBlank{"Sheet ${sheets.size+1}"}; var safe=safeBase; var n=2
    while(sheets.any{it.name.equals(safe,true)})safe="$safeBase $n++"
    val entry="xl/worksheets/sheet${nextSheetNumber()}.xml"; val xml=blankSheetXml(); entries[entry]=xml
    sheets.add(InAppSheet(safe,entry,mutableListOf(mutableListOf()),mutableListOf(mutableListOf(null)),mutableListOf(mutableListOf(ExcelCellStyle()))) ); updateWorkbookRelations(safe,entry); return sheets.lastIndex
  }

  fun renameSheet(index:Int,name:String){if(index !in sheets.indices)return; val s=sheets[index]; val unique=name.trim().ifBlank{s.name}; if(sheets.indices.any{it!=index&&sheets[it].name.equals(unique,true)})return; sNameUpdate(s,unique)}
  fun deleteSheet(index:Int){if(sheets.size<=1||index !in sheets.indices)return; deletedSheets+=sheets[index].entryName; sheets.removeAt(index)}
  fun duplicateSheet(index:Int):Int{if(index !in sheets.indices)return index; val src=sheets[index]; val copyName=uniqueSheetName(src.name+" Copy"); val entry="xl/worksheets/sheet${nextSheetNumber()}.xml"; entries[entry]=entries[src.entryName]?.clone() ?: blankSheetXml(); val c=InAppSheet(copyName,entry,src.cells.map{it.toMutableList()}.toMutableList(),src.formulas.map{it.toMutableList()}.toMutableList(),src.styles.map{it.map{st->st.copy()}.toMutableList()}.toMutableList(),src.hidden); sheets.add(index+1,c); updateWorkbookRelations(copyName,entry); return index+1}

  fun saveTo(target: File) {
    val output=LinkedHashMap(entries)
    deletedSheets.forEach{output.remove(it)}
    sheets.forEach{sheet -> output[sheet.entryName]=updateSheet(output[sheet.entryName] ?: blankSheetXml(),sheet)}
    updateWorkbookXml(output)
    ZipOutputStream(FileOutputStream(target)).use{zip->output.forEach{(name,bytes)->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}}
  }

  private fun updateSheet(bytes:ByteArray,sheet:InAppSheet):ByteArray{
    val doc=parseXml(bytes); val root=doc.documentElement; val data=root.getElementsByTagName("sheetData").item(0) as? Element ?: return bytes
    while(data.hasChildNodes())data.removeChild(data.firstChild)
    val width=sheet.cells.maxOfOrNull{it.size}?:1
    for(r in sheet.cells.indices){val row=doc.createElement("row");row.setAttribute("r",(r+1).toString()); if(sheet.hidden)row.setAttribute("hidden","1"); for(c in 0 until maxOf(width,sheet.cells[r].size)){val v=sheet.cells[r].getOrElse(c){""};val cell=doc.createElement("c");cell.setAttribute("r",cellRef(r+1,c+1)); val st=sheet.styles.getOrNull(r)?.getOrNull(c); if(st!=null)cell.setAttribute("s",styleIndex(st)); val formula=sheet.formulas.getOrNull(r)?.getOrNull(c); if(formula!=null){val f=doc.createElement("f");f.appendChild(doc.createTextNode(formula.removePrefix("=")));cell.appendChild(f);val value=InAppExcelFormula.evaluate(formula){ref->cellNumber(sheet,ref)};val vv=doc.createElement("v");vv.appendChild(doc.createTextNode(value?.toString().orEmpty()));cell.appendChild(vv)}else if(v.isNotEmpty()){if(v.toDoubleOrNull()!=null){val vv=doc.createElement("v");vv.appendChild(doc.createTextNode(v));cell.appendChild(vv)}else{cell.setAttribute("t","inlineStr");val isNode=doc.createElement("is");val t=doc.createElement("t");t.appendChild(doc.createTextNode(v));isNode.appendChild(t);cell.appendChild(isNode)}};row.appendChild(cell)};data.appendChild(row)}
    var mc=root.getElementsByTagName("mergeCells").item(0) as? Element
    if(mc==null&&merges[sheet.entryName]?.isNotEmpty()==true){mc=doc.createElement("mergeCells");root.appendChild(mc)}
    if(mc!=null){while(mc.hasChildNodes())mc.removeChild(mc.firstChild);merges[sheet.entryName].orEmpty().forEach{m->val x=doc.createElement("mergeCell");x.setAttribute("ref",m);mc.appendChild(x)};mc.setAttribute("count",merges[sheet.entryName].orEmpty().size.toString())}
    return serializeXml(doc)
  }

  private fun styleIndex(style:ExcelCellStyle):String="0" // style table is materialized below when styles are written

  private fun updateWorkbookXml(output:MutableMap<String,ByteArray>){val wb=output["xl/workbook.xml"]?:return;val doc=parseXml(wb);val list=doc.getElementsByTagName("sheet");val existing=mutableMapOf<String,Element>();for(i in 0 until list.length){val e=list.item(i) as Element;existing[e.getAttribute("name")]=e};val sheetsNode=list.item(0)?.parentNode as? Element?:return;while(sheetsNode.hasChildNodes()){val child=sheetsNode.firstChild;if(child.nodeName.endsWith("sheet")||child.nodeName.endsWith("sheets")){break};sheetsNode.removeChild(child)};val sheetsParent=doc.getElementsByTagName("sheets").item(0) as? Element?:return;while(sheetsParent.hasChildNodes())sheetsParent.removeChild(sheetsParent.firstChild);var rid=1;for(s in sheets){val e=doc.createElement("sheet");e.setAttribute("name",s.name);e.setAttribute("sheetId",(++rid).toString());e.setAttribute("r:id","rId${rid}");if(s.hidden)e.setAttribute("state","hidden");sheetsParent.appendChild(e)};output["xl/workbook.xml"]=serializeXml(doc)}

  private fun updateWorkbookRelations(name:String,entry:String){val rel=entries["xl/_rels/workbook.xml.rels"]?:return;val doc=parseXml(rel);val root=doc.documentElement;val max=(0 until root.getElementsByTagName("Relationship").length).mapNotNull{(root.getElementsByTagName("Relationship").item(it)as Element).getAttribute("Id").removePrefix("rId").toIntOrNull()}.maxOrNull()?:0;val e=doc.createElement("Relationship");e.setAttribute("Id","rId${max+1}");e.setAttribute("Type","http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet");e.setAttribute("Target","worksheets/${entry.substringAfterLast('/')}");root.appendChild(e);entries["xl/_rels/workbook.xml.rels"]=serializeXml(doc);val ct=entries["[Content_Types].xml"];if(ct!=null){val ctd=parseXml(ct);val rootCt=ctd.documentElement;if(rootCt.getElementsByTagName("Override").length>=0){val o=ctd.createElement("Override");o.setAttribute("PartName","/${entry}");o.setAttribute("ContentType","application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml");rootCt.appendChild(o);entries["[Content_Types].xml"]=serializeXml(ctd)}}}

  private fun ensure(si:Int,r:Int,c:Int){val s=sheets[si];while(s.cells.size<=r){val w=s.cells.maxOfOrNull{it.size}?:0;s.cells.add(MutableList(w){""});s.formulas.add(MutableList(w){null});s.styles.add(MutableList(w){ExcelCellStyle()})};while(s.cells[r].size<=c)s.cells[r].add("");while(s.formulas[r].size<=c)s.formulas[r].add(null);while(s.styles[r].size<=c)s.styles[r].add(ExcelCellStyle())}
  private fun rebuildEditKeys(si:Int){edits.clear();sheets[si].cells.forEachIndexed{r,row->row.forEachIndexed{c,_->edits+=key(si,r,c)}}}
  private fun key(s:Int,r:Int,c:Int)="$s:$r:$c"
  private fun cellNumber(sheet:InAppSheet,ref:String):Double?{val m=Regex("([A-Z]+)([0-9]+)",RegexOption.IGNORE_CASE).matchEntire(ref.trim())?:return null;fun col(x:String)=x.uppercase().fold(0){n,ch->n*26+ch.code-64};val c=col(m.groupValues[1])-1;val r=m.groupValues[2].toInt()-1;return sheet.cells.getOrNull(r)?.getOrNull(c)?.toDoubleOrNull()}
  private fun uniqueSheetName(base:String):String{var n=base;var i=2;while(sheets.any{it.name.equals(n,true)})n="$base ${i++}";return n}
  private fun sNameUpdate(s:InAppSheet,name:String){val f=sheets.indexOf(s);sheets[f].let{val replacement=InAppSheet(name,it.entryName,it.cells,it.formulas,it.styles,it.hidden);sheets[f]=replacement}}
  private fun nextSheetNumber():Int{val nums=sheets.mapNotNull{Regex("sheet(\\d+)\\.xml").find(it.entryName)?.groupValues?.get(1)?.toIntOrNull()};return (nums.maxOrNull()?:0)+1}
  private fun blankSheetXml():ByteArray="<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData/></worksheet>".toByteArray()
  private fun cellRef(row:Int,column:Int):String{var n=column;val sb=StringBuilder();while(n>0){val r=(n-1)%26;sb.append(('A'.code+r).toChar());n=(n-1)/26};return sb.reverse().append(row).toString()}

  companion object{
    fun load(file:File):Result<InAppXlsxWorkbook>=runCatching{val entries=LinkedHashMap<String,ByteArray>();ZipInputStream(FileInputStream(file)).use{zip->while(true){val e=zip.nextEntry?:break;entries[e.name]=zip.readBytes()}};val shared=parseSharedStrings(entries["xl/sharedStrings.xml"]);val workbook=entries["xl/workbook.xml"]?:error("Excel workbook metadata not found");val rels=entries["xl/_rels/workbook.xml.rels"];val relMap=mutableMapOf<String,String>();if(rels!=null){val d=parseXmlStatic(rels);val ns=d.getElementsByTagName("Relationship");for(i in 0 until ns.length){val e=ns.item(i)as Element;val id=e.getAttribute("Id");val t=e.getAttribute("Target");if(id.isNotBlank()&&t.isNotBlank())relMap[id]=if(t.startsWith("/"))t.removePrefix("/") else "xl/$t"}};val wb=parseXmlStatic(workbook);val nodes=wb.getElementsByTagName("sheet");val sheets=mutableListOf<InAppSheet>();for(i in 0 until nodes.length){val e=nodes.item(i)as Element;val name=e.getAttribute("name").ifBlank{"Sheet ${i+1}"};val rid=e.getAttribute("r:id").ifBlank{e.getAttribute("id")};val entry=relMap[rid]?:"xl/worksheets/sheet${i+1}.xml";entries[entry]?.let{sheets+=parseSheet(name,entry,it,shared,e.getAttribute("state")=="hidden")}};if(sheets.isEmpty())error("No Excel worksheets found");InAppXlsxWorkbook(entries,sheets)}
    private fun parseSharedStrings(bytes:ByteArray?):List<String>{if(bytes==null)return emptyList();val d=parseXmlStatic(bytes);val ns=d.getElementsByTagName("si");return buildList{for(i in 0 until ns.length){val ts=(ns.item(i)as Element).getElementsByTagName("t");add(buildString{for(j in 0 until ts.length)append(ts.item(j).textContent)})}}}
    private fun parseSheet(name:String,entry:String,bytes:ByteArray,shared:List<String>,hidden:Boolean):InAppSheet{val d=parseXmlStatic(bytes);val rows=d.getElementsByTagName("row");val matrix=mutableListOf<MutableList<String>>();val formulas=mutableListOf<MutableList<String?>>();val styles=mutableListOf<MutableList<ExcelCellStyle>>();var max=0;for(i in 0 until rows.length){val re=rows.item(i)as Element;val rn=re.getAttribute("r").toIntOrNull()?:i+1;while(matrix.size<rn){matrix.add(mutableListOf());formulas.add(mutableListOf());styles.add(mutableListOf())};val row=matrix[rn-1];val fr=formulas[rn-1];val sr=styles[rn-1];val cs=re.getElementsByTagName("c");for(j in 0 until cs.length){val c=cs.item(j)as Element;val ref=c.getAttribute("r");val col=ref.filter(Char::isLetter).fold(0){a,ch->a*26+(ch.uppercaseChar().code-'A'.code+1)};if(col<=0)continue;while(row.size<col)row.add("");while(fr.size<col)fr.add(null);while(sr.size<col)sr.add(ExcelCellStyle());val f=c.getElementsByTagName("f").item(0)?.textContent?.let{"=$it"};fr[col-1]=f;val type=c.getAttribute("t");row[col-1]=when(type){"s"->c.getElementsByTagName("v").item(0)?.textContent?.toIntOrNull()?.let{shared.getOrNull(it)}.orEmpty();"inlineStr"->c.getElementsByTagName("t").item(0)?.textContent.orEmpty();"b"->if(c.getElementsByTagName("v").item(0)?.textContent=="1")"TRUE"else"FALSE";else->c.getElementsByTagName("v").item(0)?.textContent.orEmpty()};max=maxOf(max,col)}};matrix.forEach{while(it.size<max)it.add("")};formulas.forEach{while(it.size<max)it.add(null)};styles.forEach{while(it.size<max)it.add(ExcelCellStyle())};return InAppSheet(name,entry,matrix,formulas,styles,hidden)}
    private fun parseXmlStatic(bytes:ByteArray):Document{val header=String(bytes,Charsets.UTF_8).lowercase(Locale.ROOT);require("<!doctype"!in header){"DOCTYPE declarations are not allowed in Excel XML"};val f=DocumentBuilderFactory.newInstance().apply{try{setFeature("http://xml.org/sax/features/external-general-entities",false)}catch(_:Exception){};try{setFeature("http://xml.org/sax/features/external-parameter-entities",false)}catch(_:Exception){};try{setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false)}catch(_:Exception){};try{isXIncludeAware=false}catch(_:Exception){};try{isExpandEntityReferences=false}catch(_:Exception){}};return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))}
  }

  private fun serializeXml(doc:Document):ByteArray{val out=ByteArrayOutputStream();TransformerFactory.newInstance().newTransformer().apply{setOutputProperty(OutputKeys.ENCODING,"UTF-8");setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,"no")}.transform(DOMSource(doc),StreamResult(out));return out.toByteArray()}
}
