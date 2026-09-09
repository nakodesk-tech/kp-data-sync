package com.example.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

internal data class ExcelCellStyle(
  var bold:Boolean=false,var italic:Boolean=false,var underline:Boolean=false,var fontSize:Int=11,var fontName:String="Calibri",
  var horizontal:String="general",var vertical:String="center",var background:String?=null,var foreground:String?=null,var wrap:Boolean=false,
  var border:Boolean=false,var numberFormat:String?=null,var hidden:Boolean=false,var baseStyleId:Int=0
)
internal data class ExcelCellSnapshot(val sheet:Int,val row:Int,val column:Int,val value:String,val formula:String?,val style:ExcelCellStyle)
internal object InAppExcelFormula{
  private val ref=Regex("\\b([A-Z]{1,3}[0-9]+)\\b")
  fun evaluate(formula:String,lookup:(String)->Double?):Double?{val f=formula.trim().removePrefix("=").trim();if(f.isBlank())return null;val fn=Regex("(?i)^(SUM|MIN|MAX|AVERAGE|COUNT)\\((.*)\\)$").matchEntire(f);if(fn!=null){val v=args(fn.groupValues[2],lookup);return when(fn.groupValues[1].uppercase()){"SUM"->v.sum();"MIN"->v.minOrNull();"MAX"->v.maxOrNull();"AVERAGE"->v.takeIf{it.isNotEmpty()}?.average();"COUNT"->v.size.toDouble();else->null}};var e=f.replace("%","/100");ref.findAll(e).map{it.value}.distinct().forEach{r->lookup(r)?.let{x->e=e.replace(Regex("\\b${Regex.escape(r)}\\b"),x.toString())}};if(Regex("[^0-9+\\-*/(). %]+",RegexOption.IGNORE_CASE).containsMatchIn(e))return null;return Expr(e).parse()}
  private fun args(raw:String,l:(String)->Double?):List<Double>{return raw.split(',').flatMap{a->val p=a.trim();if(p.contains(':')){val q=p.split(':');if(q.size==2)range(q[0],q[1]).mapNotNull(l)else emptyList()}else p.toDoubleOrNull()?.let{listOf(it)}?:l(p)?.let{listOf(it)}.orEmpty()}}
  private fun range(a: String, b: String): List<String> {
    val x = Regex("([A-Z]+)([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(a.trim()) ?: return emptyList()
    val y = Regex("([A-Z]+)([0-9]+)", RegexOption.IGNORE_CASE).matchEntire(b.trim()) ?: return emptyList()
    val c1 = colIndex(x.groupValues[1])
    val c2 = colIndex(y.groupValues[1])
    val r1 = x.groupValues[2].toInt()
    val r2 = y.groupValues[2].toInt()
    return buildList {
      for (r in minOf(r1, r2)..maxOf(r1, r2)) {
        for (col in minOf(c1, c2)..maxOf(c1, c2)) {
          add("${colName(col)}$r")
        }
      }
    }
  }

  private fun colIndex(s: String): Int = s.uppercase().fold(0) { n, ch -> n * 26 + ch.code - 64 }

  private fun colName(v0: Int): String {
    var v = v0
    val o = StringBuilder()
    while (v > 0) {
      val r = (v - 1) % 26
      o.append(('A'.code + r).toChar())
      v = (v - 1) / 26
    }
    return o.reverse().toString()
  }
}
private class Expr(private val s:String){private var p=0;fun parse():Double?=runCatching{val v=e();skip();if(p!=s.length)error("tail");v}.getOrNull();private fun e():Double{var v=t();while(true){skip();if(m('+'))v+=t()else if(m('-'))v-=t()else return v}};private fun t():Double{var v=f();while(true){skip();if(m('*'))v*=f()else if(m('/')){val d=f();if(d==0.0)error("divide");v/=d}else return v}};private fun f():Double{skip();if(m('+'))return f();if(m('-'))return-f();if(m('(')){val v=e();skip();if(!m(')'))error("paren");return v};val q=p;while(p<s.length&&(s[p].isDigit()||s[p]=='.'))p++;if(q==p)error("number");return s.substring(q,p).toDouble()};private fun skip(){while(p<s.length&&s[p].isWhitespace())p++};private fun m(c:Char)=if(p<s.length&&s[p]==c){p++;true}else false}
internal fun colorHex(color:Color):String="%02X%02X%02X".format((color.red*255).roundToInt(),(color.green*255).roundToInt(),(color.blue*255).roundToInt())
