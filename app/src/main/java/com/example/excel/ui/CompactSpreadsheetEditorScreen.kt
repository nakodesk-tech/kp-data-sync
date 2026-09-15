package com.example.excel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.excel.engine.*
import java.util.Locale

private val Green = Color(0xFF107C41)
private val DarkGreen = Color(0xFF0F5132)
private val Header = Color(0xFFF1F5F9)
private val ActiveHeader = Color(0xFFD1E7DD)
private val Grid = Color(0xFFE2E8F0)
private val HeaderBorder = Color(0xFFCBD5E1)
private val Selection = Color(0x1A107C41)
private val RowWidth = 44.dp
private val ColWidth = 98.dp
private val RowHeight = 37.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun CompactSpreadsheetEditorScreen(
    workbook: SpreadsheetWorkbook,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    var sheetIndex by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(CellAddress(0, 0)) }
    var anchor by remember { mutableStateOf(active) }
    var selection by remember { mutableStateOf(CellRange(active, active)) }
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<CellValue?>(null) }
    var requestCellFocus by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<List<List<CellSnap>>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val history = remember { SpreadsheetHistory() }
    val hScroll = rememberScrollState()
    val vScroll = rememberLazyListState()
    val cellFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val sheet = workbook.sheets[sheetIndex.coerceIn(0, workbook.sheets.lastIndex)]
    val engine = remember(workbook, refresh) { FormulaEngine { n -> workbook.sheets.firstOrNull { it.name.equals(n, true) } } }
    val rows = maxOf(60, sheet.maxRow() + 25)
    val cols = maxOf(16, sheet.maxColumn() + 5)

    fun commit() {
        val old = original ?: run { editing = false; return }
        val newValue = sheet.valueAt(active)
        if (old != newValue) {
            sheet.setValue(active, old)
            history.execute(SetCellCommand(sheet, active, newValue))
        }
        original = null
        editing = false
        requestCellFocus = false
        text = raw(sheet.valueAt(active))
        refresh++
    }
    fun draft(value: String) {
        if (!editing) { original = sheet.valueAt(active); editing = true }
        text = value
        sheet.setValue(active, parse(value))
        refresh++
    }
    fun select(address: CellAddress, extend: Boolean = false) {
        commit()
        active = address
        if (extend) selection = CellRange(anchor, address) else { anchor = address; selection = CellRange(address, address) }
        text = raw(sheet.valueAt(address))
        refresh++
    }
    fun edit(address: CellAddress) {
        active = address; anchor = address; selection = CellRange(address, address)
        text = raw(sheet.valueAt(address)); original = sheet.valueAt(address)
        editing = true; requestCellFocus = true; refresh++
    }
    fun finish() { commit(); keyboard?.hide() }
    fun style(transform: (CellStyle) -> CellStyle) { commit(); history.execute(StyleRangeCommand(sheet, selection, transform)); refresh++ }
    fun clear() { commit(); history.execute(ClearRangeCommand(sheet, selection)); text = ""; refresh++ }
    fun copy() { commit(); copied = (selection.top..selection.bottom).map { r -> (selection.left..selection.right).map { c -> val a=CellAddress(r,c); CellSnap(sheet.valueAt(a),sheet.cell(a).style) } }; refresh++ }
    fun paste() { commit(); val data=copied?:return; val edits=mutableListOf<Pair<CellAddress,Pair<CellValue,CellStyle?>>>(); data.forEachIndexed { r,row -> row.forEachIndexed { c,x -> edits += CellAddress(active.row+r,active.column+c) to (x.value to x.style) } }; history.execute(BatchEditCommand(sheet,edits,"Paste cells")); refresh++ }
    fun autoSum() { val r=if(selection.isSingleCell){ val c=active.column; var top=active.row-1; while(top>=0&&sheet.valueAt(CellAddress(top,c)) !is CellValue.Empty)top--; "${CellAddress(top+1,c)}:${CellAddress(active.row-1,c)}" } else selection.toString(); commit(); history.execute(SetCellCommand(sheet,active,CellValue.Formula("=SUM($r)"))); text="=SUM($r)"; refresh++ }

    Surface(Modifier.fillMaxSize().imePadding(), color=Color.White) {
        Column(Modifier.fillMaxSize()) {
            Surface(color=Green,shadowElevation=2.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={finish();onBack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=Color.White)}
                    Column(Modifier.weight(1f)){Text("Excel Editor",color=Color.White,fontWeight=FontWeight.Bold,fontSize=16.sp);Text("${sheet.name} • $selection",color=Color.White.copy(.82f),fontSize=10.sp)}
                    IconButton(onClick={finish();if(history.undo()){text=raw(sheet.valueAt(active));refresh++}}){Icon(Icons.AutoMirrored.Filled.Undo,"Undo",tint=Color.White)}
                    IconButton(onClick={finish();if(history.redo()){text=raw(sheet.valueAt(active));refresh++}}){Icon(Icons.AutoMirrored.Filled.Redo,"Redo",tint=Color.White)}
                    Button(onClick={finish();onSave()},colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Green),contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp),shape=RoundedCornerShape(18.dp)){Icon(Icons.Default.Check,null,Modifier.size(15.dp));Spacer(Modifier.width(3.dp));Text("Save",fontSize=12.sp,fontWeight=FontWeight.Bold)}
                    Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"More",tint=Color.White)};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("Select All")},onClick={commit();selection=CellRange(CellAddress(0,0),CellAddress(rows-1,cols-1));active=CellAddress(0,0);menu=false;refresh++});DropdownMenuItem({Text("Add Sheet")},onClick={workbook.addSheet();menu=false;refresh++});DropdownMenuItem({Text("Clear Selection")},onClick={clear();menu=false})}}
                }
            }

            Surface(color=Color(0xFFF8FAFC),border=BorderStroke(.5.dp,Grid)) {
                Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal=5.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(color=Color.White,shape=RoundedCornerShape(4.dp),border=BorderStroke(1.dp,HeaderBorder)){Text(selection.toString(),Modifier.padding(horizontal=7.dp,vertical=4.dp),fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=11.sp,color=DarkGreen)}
                    Text("fx",fontSize=15.sp,fontWeight=FontWeight.Black,color=Green,Modifier.padding(horizontal=5.dp))
                    TextField(value=text,onValueChange={draft(it)},modifier=Modifier.weight(1f).height(42.dp).onFocusChanged{if(it.isFocused&&!editing){original=sheet.valueAt(active);editing=true}},colors=TextFieldDefaults.colors(focusedContainerColor=Color.White,unfocusedContainerColor=Color.White,focusedIndicatorColor=Green,unfocusedIndicatorColor=Color.Transparent),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={finish()}),placeholder={Text("Enter value or =FORMULA",fontSize=11.sp)})
                    IconButton(onClick={finish()},Modifier.size(32.dp).background(Green,CircleShape)){Icon(Icons.Default.Check,"Apply",tint=Color.White,Modifier.size(17.dp))}
                }
            }

            if(editing||text.startsWith("=")) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=5.dp,vertical=2.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf("Σ SUM","AVERAGE","COUNT","MAX","MIN","IF").forEach{f->Chip(f){draft(if(text.startsWith("="))text+f.replace("Σ ","")+"(" else "=${f.replace("Σ ","")}( ".replace(" ",""))}};Chip("AutoSum"){autoSum()}}

            val s=sheet.cell(active).style
            Surface(color=Color.White,shadowElevation=1.dp){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(38.dp),verticalAlignment=Alignment.CenterVertically){
                Tool("Cut",Icons.Default.ContentCut){copy();clear()};Tool("Copy",Icons.Default.ContentCopy){copy()};Tool("Paste",Icons.Default.ContentPaste,copied!=null){paste()};Tool("Clear",Icons.Default.DeleteOutline){clear()};DividerV();Toggle(Icons.Default.FormatBold,s.bold){style{it.copy(bold=!it.bold)}};Toggle(Icons.Default.FormatItalic,s.italic){style{it.copy(italic=!it.italic)}};Toggle(Icons.Default.FormatUnderlined,s.underline){style{it.copy(underline=!it.underline)}};DividerV();Tool("Left",Icons.Default.FormatAlignLeft){style{it.copy(horizontalAlignment=HorizontalAlignment.Left)}};Tool("Center",Icons.Default.FormatAlignCenter){style{it.copy(horizontalAlignment=HorizontalAlignment.Center)}};Tool("Right",Icons.Default.FormatAlignRight){style{it.copy(horizontalAlignment=HorizontalAlignment.Right)}};Tool("Wrap",Icons.Default.WrapText){style{it.copy(wrapText=!it.wrapText)}};DividerV();Tool("Fill",Icons.Default.FormatColorFill){style{it.copy(fillArgb=0xFFE8F5E9.toInt())}};Tool("Border",Icons.Default.BorderAll){style{it.copy(border=if(it.border==BorderStyle.None)BorderStyle.Thin else BorderStyle.None)}};Tool("+Row",Icons.Default.Add){commit();sheet.insertRow(active.row);refresh++};Tool("-Row",Icons.Default.Remove){commit();sheet.deleteRow(active.row);refresh++};Tool("+Col",Icons.Default.AddBox){commit();sheet.insertColumn(active.column);refresh++};Tool("-Col",Icons.Default.IndeterminateCheckBox){commit();sheet.deleteColumn(active.column);refresh++}
            }}

            Box(Modifier.weight(1f).fillMaxWidth()){
                Column(Modifier.fillMaxSize()){
                    Row(Modifier.fillMaxWidth().height(30.dp).background(Header).border(BorderStroke(.5.dp,HeaderBorder))){Box(Modifier.width(RowWidth).fillMaxHeight(),contentAlignment=Alignment.Center){Text("⊞",fontSize=13.sp)};Row(Modifier.weight(1f).horizontalScroll(hScroll)){repeat(cols){c->Box(Modifier.width(ColWidth).fillMaxHeight().border(BorderStroke(.5.dp,HeaderBorder)).background(if(c in selection.left..selection.right)ActiveHeader else Header).clickable{select(CellAddress(0,c));selection=CellRange(CellAddress(0,c),CellAddress(rows-1,c));refresh++},contentAlignment=Alignment.Center){Text(columnName(c),fontSize=11.sp,fontWeight=FontWeight.Bold)}}}}
                    LazyColumn(state=vScroll,modifier=Modifier.weight(1f)){items((0 until rows).toList(),key={it}){r->Row(Modifier.fillMaxWidth().height(RowHeight)){Box(Modifier.width(RowWidth).fillMaxHeight().background(if(r in selection.top..selection.bottom)ActiveHeader else Header).border(BorderStroke(.5.dp,HeaderBorder)).clickable{select(CellAddress(r,0));selection=CellRange(CellAddress(r,0),CellAddress(r,cols-1));refresh++},contentAlignment=Alignment.Center){Text("${r+1}",fontSize=11.sp,fontWeight=FontWeight.Medium)};Row(Modifier.weight(1f).horizontalScroll(hScroll)){repeat(cols){c->val a=CellAddress(r,c);val active=a==active;val cell=sheet.cell(a);val value=display(sheet,cell,engine);Box(Modifier.width(ColWidth).fillMaxHeight().background(if(active)Color.White else if(selection.contains(a))Selection else cell.style.fillArgb?.let{Color(it)}?:Color.White).border(if(active)BorderStroke(2.dp,Green) else BorderStroke(.5.dp,Grid)).combinedClickable(onClick={select(a)},onLongClick={select(a,true)},onDoubleClick={edit(a)}).padding(horizontal=5.dp),contentAlignment=Alignment.CenterStart){if(editing&&active){TextField(value=text,onValueChange={draft(it)},modifier=Modifier.fillMaxWidth().height(34.dp).focusRequester(cellFocus),colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={finish()}),textStyle=LocalTextStyle.current.copy(fontSize=12.sp,fontWeight=if(cell.style.bold)FontWeight.Bold else FontWeight.Normal))}else{Text(value,Modifier.fillMaxWidth(),fontSize=12.sp,fontWeight=if(cell.style.bold)FontWeight.Bold else FontWeight.Normal,textDecoration=if(cell.style.underline)TextDecoration.Underline else TextDecoration.None,textAlign=when(cell.style.horizontalAlignment){HorizontalAlignment.Center->TextAlign.Center;HorizontalAlignment.Right->TextAlign.End;else->TextAlign.Start})}}}}}}}
                }
            }

            Surface(color=Color(0xFFE2E8F0),shadowElevation=3.dp){Row(Modifier.fillMaxWidth().navigationBarsPadding().height(46.dp),verticalAlignment=Alignment.CenterVertically){Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())){workbook.sheets.forEachIndexed{idx,sh->Surface(color=if(idx==sheetIndex)Color.White else Color(0xFFF1F5F9),border=BorderStroke(.5.dp,if(idx==sheetIndex)Green:Color(0xFFCBD5E1)),modifier=Modifier.clickable{finish();sheetIndex=idx;active=CellAddress(0,0);anchor=active;selection=CellRange(active,active);text=raw(workbook.sheets[idx].valueAt(active));refresh++}){Text(sh.name,Modifier.padding(horizontal=13.dp,vertical=6.dp),fontSize=12.sp,fontWeight=if(idx==sheetIndex)FontWeight.Bold else FontWeight.Medium,color=if(idx==sheetIndex)DarkGreen else Color(0xFF475569))}}};IconButton(onClick={finish();workbook.addSheet();refresh++},modifier=Modifier.size(32.dp).background(Green,CircleShape)){Icon(Icons.Default.Add,"Add Sheet",tint=Color.White)}}}
        }
    }
    LaunchedEffect(requestCellFocus){if(requestCellFocus){cellFocus.requestFocus();keyboard?.show();requestCellFocus=false}}
}

private data class CellSnap(val value:CellValue,val style:CellStyle)
@Composable private fun Tool(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,enabled:Boolean=true,onClick:()->Unit){IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(36.dp)){Icon(icon,label,tint=if(enabled)Color(0xFF334155) else Color.LightGray,modifier=Modifier.size(18.dp))}}
@Composable private fun Toggle(icon:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean,onClick:()->Unit){Surface(color=if(active)ActiveHeader else Color.Transparent,modifier=Modifier.size(36.dp)){IconButton(onClick=onClick){Icon(icon,null,tint=if(active)DarkGreen else Color(0xFF334155),modifier=Modifier.size(18.dp))}}}
@Composable private fun DividerV(){Box(Modifier.padding(horizontal=3.dp).width(1.dp).height(22.dp).background(Grid))}
@Composable private fun Chip(text:String,onClick:()->Unit){Surface(shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,HeaderBorder),modifier=Modifier.combinedClickable(onClick=onClick,onLongClick={}),color=Color.White){Text(text,Modifier.padding(horizontal=8.dp,vertical=3.dp),fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=DarkGreen)}}
private fun display(sheet:SpreadsheetSheet,cell:SpreadsheetCell,engine:FormulaEngine):String=when(val v=cell.value){CellValue.Empty->"";is CellValue.Text->v.value;is CellValue.Number->fmt(v.value);is CellValue.BooleanValue->if(v.value)"TRUE" else "FALSE";is CellValue.Formula->when(val e=engine.evaluate(sheet.name,v.expression)){is CellValue.Number->fmt(e.value);is CellValue.Text->e.value;is CellValue.BooleanValue->if(e.value)"TRUE" else "FALSE";is CellValue.Error->e.code;else->v.expression};is CellValue.Error->v.code}
private fun raw(v:CellValue):String=when(v){CellValue.Empty->"";is CellValue.Text->v.value;is CellValue.Number->fmt(v.value);is CellValue.BooleanValue->if(v.value)"TRUE" else "FALSE";is CellValue.Formula->v.expression;is CellValue.Error->v.code}
private fun parse(s:String):CellValue{val v=s.trim();if(v.isEmpty())return CellValue.Empty;if(v.startsWith("="))return CellValue.Formula(v);v.toDoubleOrNull()?.let{return CellValue.Number(it)};if(v.equals("true",true))return CellValue.BooleanValue(true);if(v.equals("false",true))return CellValue.BooleanValue(false);return CellValue.Text(v)}
private fun fmt(n:Double)=if(n%1.0==0.0)n.toLong().toString() else String.format(Locale.US,"%.2f",n).trimEnd('0').trimEnd('.')
private fun columnName(c:Int):String{var n=c+1;val b=StringBuilder();while(n>0){val r=(n-1)%26;b.append(('A'.code+r).toChar());n=(n-1)/26};return b.reverse().toString()}
