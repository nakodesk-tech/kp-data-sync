package com.example.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

internal data class ExcelCellStyle(
  var bold: Boolean = false,
  var italic: Boolean = false,
  var underline: Boolean = false,
  var fontSize: Int = 11,
  var fontName: String = "Calibri",
  var horizontal: String = "general",
  var vertical: String = "center",
  var background: String? = null,
  var foreground: String? = null,
  var wrap: Boolean = false,
  var border: Boolean = false,
  var numberFormat: String? = null,
  var hidden: Boolean = false
)

internal data class ExcelCellSnapshot(
  val sheet: Int,
  val row: Int,
  val column: Int,
  val value: String,
  val formula: String?,
  val style: ExcelCellStyle
)

internal object InAppExcelFormula {
  private val refPattern = Regex("\\b([A-Z]{1,3}[0-9]+)\\b")

  fun evaluate(formula: String, lookup: (String) -> Double?): Double? {
    val f = formula.trim().removePrefix("=").trim()
    if (f.isBlank()) return null
    val fn = Regex("(?i)^(SUM|MIN|MAX|AVERAGE|COUNT)\\((.*)\\)$").matchEntire(f)
    if (fn != null) {
      val values = expandArgs(fn.groupValues[2], lookup)
      return when (fn.groupValues[1].uppercase()) {
        "SUM" -> values.sum()
        "MIN" -> values.minOrNull()
        "MAX" -> values.maxOrNull()
        "AVERAGE" -> values.takeIf { it.isNotEmpty() }?.average()
        "COUNT" -> values.size.toDouble()
        else -> null
      }
    }
    var expr = f.replace("%", "/100")
    val refs = refPattern.findAll(expr).map { it.value }.distinct().toList()
    refs.forEach { ref -> lookup(ref)?.let { expr = expr.replace(Regex("\\b${Regex.escape(ref)}\\b"), it.toString()) } }
    if (Regex("[^0-9+\\-*/(). %]+", RegexOption.IGNORE_CASE).containsMatchIn(expr)) return null
    return SimpleExpressionParser(expr).parse()
  }

  private fun expandArgs(raw: String, lookup: (String) -> Double?): List<Double> {
    return raw.split(',').flatMap { arg ->
      val part = arg.trim()
      if (part.contains(':')) {
        val ends = part.split(':')
        if (ends.size == 2) rangeRefs(ends[0], ends[1]).mapNotNull(lookup) else emptyList()
      } else {
        part.toDoubleOrNull()?.let { listOf(it) } ?: lookup(part)?.let { listOf(it) }.orEmpty()
      }
    }
  }

  private fun rangeRefs(start: String, end: String): List<String> {
    val a = Regex("([A-Z]+)([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(start.trim()) ?: return emptyList()
    val b = Regex("([A-Z]+)([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(end.trim()) ?: return emptyList()
    fun col(s: String): Int = s.uppercase().fold(0) { n, c -> n * 26 + c.code - 64 }
    fun name(n: Int): String { var x=n; val out=StringBuilder(); while(x>0){val r=(x-1)%26;out.append(('A'.code+r).toChar());x=(x-1)/26};return out.reverse().toString() }
    val c1=col(a.groupValues[1]); val c2=col(b.groupValues[1]); val r1=a.groupValues[2].toInt(); val r2=b.groupValues[2].toInt()
    return buildList { for (r in minOf(r1,r2)..maxOf(r1,r2)) for (c in minOf(c1,c2)..maxOf(c1,c2)) add("${name(c)}$r") }
  }
}

private class SimpleExpressionParser(private val input: String) {
  private var p = 0
  fun parse(): Double? = runCatching { val v = expression(); skip(); if (p != input.length) error("trailing") else v }.getOrNull()
  private fun expression(): Double { var v=term(); while(true){skip(); if(match('+'))v+=term() else if(match('-'))v-=term() else return v} }
  private fun term(): Double { var v=factor(); while(true){skip(); if(match('*'))v*=factor() else if(match('/')){val d=factor(); if(d==0.0)error("divide") else v/=d} else return v} }
  private fun factor(): Double { skip(); if(match('+'))return factor(); if(match('-'))return -factor(); if(match('(')){val v=expression();skip();if(!match(')'))error("paren");return v}; val s=p; while(p<input.length&&(input[p].isDigit()||input[p]=='.'))p++; if(s==p)error("number"); return input.substring(s,p).toDouble() }
  private fun skip(){while(p<input.length&&input[p].isWhitespace())p++}
  private fun match(c:Char):Boolean{if(p<input.length&&input[p]==c){p++;return true};return false}
}

internal fun colorHex(color: Color): String = "%02X%02X%02X".format((color.red*255).roundToInt(), (color.green*255).roundToInt(), (color.blue*255).roundToInt())
