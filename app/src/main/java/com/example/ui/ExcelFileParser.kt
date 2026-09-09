package com.example.ui

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

internal object ExcelFileParser {
  fun parse(resolver: ContentResolver, uri: Uri): Result<List<ImportRow>> = runCatching {
    resolver.openInputStream(uri)?.use { input ->
      ZipInputStream(BufferedInputStream(input)).use { zip ->
        var sharedStrings = emptyList<String>()
        var sheetXml: ByteArray? = null
        while (true) {
          val entry = zip.nextEntry ?: break
          val bytes = zip.readBytes()
          when (entry.name) {
            "xl/sharedStrings.xml" -> sharedStrings = parseSharedStrings(bytes)
            "xl/worksheets/sheet1.xml" -> sheetXml = bytes
          }
        }
        parseSheet(sheetXml ?: error("Workbook sheet1 not found"), sharedStrings)
      }
    } ?: error("Unable to open Excel file")
  }

  private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val parser = Xml.newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }
    val result = mutableListOf<String>()
    var inSi = false
    var inText = false
    val text = StringBuilder()
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
      when (parser.eventType) {
        XmlPullParser.START_TAG -> when (parser.name) {
          "si" -> { inSi = true; text.setLength(0) }
          "t" -> if (inSi) inText = true
        }
        XmlPullParser.TEXT -> if (inText) text.append(parser.text)
        XmlPullParser.END_TAG -> when (parser.name) {
          "t" -> inText = false
          "si" -> { if (inSi) result += text.toString(); inSi = false }
        }
      }
      parser.next()
    }
    return result
  }

  private fun parseSheet(bytes: ByteArray, shared: List<String>): List<ImportRow> {
    val parser = Xml.newPullParser().apply { setInput(bytes.inputStream(), "UTF-8") }
    val rows = mutableListOf<Pair<Int, Map<String, String>>>()
    var rowNumber = 0
    var rowCells = mutableMapOf<String, String>()
    var cellRef = ""
    var cellType = ""
    var capture = false
    val value = StringBuilder()
    var inRow = false
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
      when (parser.eventType) {
        XmlPullParser.START_TAG -> when (parser.name) {
          "row" -> { inRow = true; rowCells = mutableMapOf(); rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rows.size + 1) }
          "c" -> { cellRef = parser.getAttributeValue(null, "r").orEmpty(); cellType = parser.getAttributeValue(null, "t").orEmpty(); value.setLength(0) }
          "v", "t" -> if (cellRef.isNotBlank()) capture = true
        }
        XmlPullParser.TEXT -> if (capture) value.append(parser.text)
        XmlPullParser.END_TAG -> when (parser.name) {
          "v", "t" -> capture = false
          "c" -> {
            if (cellRef.isNotBlank()) {
              val raw = value.toString()
              val resolved = if (cellType == "s") raw.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty() else raw
              rowCells[columnLetters(cellRef)] = resolved.trim()
            }
            cellRef = ""
          }
          "row" -> if (inRow) { rows += rowNumber to rowCells.toMap(); inRow = false }
        }
      }
      parser.next()
    }
    val nonEmpty = rows.filter { it.second.values.any(String::isNotBlank) }
    if (nonEmpty.isEmpty()) return emptyList()
    val headerPair = nonEmpty.first()
    val headerMap = headerPair.second.entries
      .mapNotNull { (column, header) -> normalizeHeader(header).takeIf(String::isNotBlank)?.let { column to it } }
      .toMap()
    return nonEmpty.drop(1).map { (num, cells) ->
      ImportRow(num, cells.mapNotNull { (column, cellValue) -> headerMap[column]?.let { key -> key to cellValue } }.toMap())
    }.filter { it.values.values.any(String::isNotBlank) }
  }

  private fun normalizeHeader(value: String): String {
    val compact = value.trim().lowercase().replace("/", " ").replace("-", " ").replace("_", " ").replace(Regex("\\s+"), " ")
    return when (compact) {
      "school name", "school", "शाळेचे नाव", "शाळा" -> "school_name"
      "udise", "udise code", "udise school code", "udise / school code" -> "udise_code"
      "school code", "user school code" -> "school_code"
      "cluster name", "centre name", "center name", "केंद्र", "केंद्राचे नाव" -> "cluster_name"
      "cluster code", "centre code", "center code", "केंद्र कोड" -> "cluster_code"
      "taluka", "तालुका" -> "taluka"
      "district", "जिल्हा" -> "district"
      "hm name", "headmaster", "headmaster name", "मुख्याध्यापक" -> "hm_name"
      "hm mobile", "headmaster mobile", "मोबाईल" -> "hm_mobile"
      "mobile", "mobile number", "user mobile", "मोबाइल नंबर", "मोबाईल नंबर" -> "mobile"
      "school type", "शाळेचा प्रकार" -> "school_type"
      "is active", "active", "status", "सक्रिय" -> "is_active"
      "name", "user name", "full name", "नाव" -> "name"
      "email", "email id", "ईमेल" -> "email"
      "role", "भूमिका" -> "role"
      "user school" -> "school_name"
      "address", "पत्ता" -> "address"
      "password", "पासवर्ड" -> "password"
      else -> compact.replace(" ", "_")
    }
  }

  private fun columnLetters(ref: String): String = ref.takeWhile { it.isLetter() }.uppercase()
}
