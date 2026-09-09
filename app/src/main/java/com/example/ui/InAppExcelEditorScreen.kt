package com.example.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.RealtimeMessageApi
import com.example.model.GroupMessage
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val excelEditorClient=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(60,TimeUnit.SECONDS).build()
private val excelEditorMain=Handler(Looper.getMainLooper())
private fun downloadExcelForEditor(context:Context,token:String,message:GroupMessage,onSuccess:(File)->Unit,onError:(String)->Unit){Thread{try{val req=Request.Builder().url(RealtimeMessageApi.attachmentUrl(message.groupId,message.id)).header("Authorization","Bearer $token").get().build();excelEditorClient.newCall(req).execute().use{r->if(!r.isSuccessful){excelEditorMain.post{onError("Excel download अयशस्वी (HTTP ${r.code})")};return@use};val f=File.createTempFile("kp_excel_",".xlsx",context.cacheDir);r.body?.byteStream()?.use{i->FileOutputStream(f).use{o->i.copyTo(o)}}?:run{f.delete();excelEditorMain.post{onError("Excel file रिकामी आहे.")};return@use};excelEditorMain.post{onSuccess(f)}}}catch(e:Exception){excelEditorMain.post{onError(e.message?.trim().takeUnless{it.isNullOrBlank()}?:"Excel उघडता आली नाही.")}}}.start()}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun InAppExcelEditorScreen(message:GroupMessage,token:String,canPublish:Boolean,onBack:()->Unit,onPublished:()->Unit){
  val context=androidx.compose.ui.platform.LocalContext.current
  var workbook by remember(message.id,message.excelVersion){mutableStateOf<InAppXlsxWorkbook?>(null)}
  var loading by remember(message.id,message.excelVersion){mutableStateOf(true)}
  var saving by remember(message.id){mutableStateOf(false)}
  var dirty by remember(message.id,message.excelVersion){mutableStateOf(false)}
  var notice by remember(message.id,message.excelVersion){mutableStateOf<String?>(null)}
  var sheetIndex by remember(message.id,message.excelVersion){mutableIntStateOf(0)}
  var selectedRow by remember(message.id,message.excelVersion){mutableIntStateOf(0)}
  var selectedCol by remember(message.id,message.excelVersion){mutableIntStateOf(0)}
  var anchorRow by remember(message.id,message.excelVersion){mutableIntStateOf(0)}
  var anchorCol by remember(message.id,message.excelVersion){mutableIntStateOf(0)}
  var revision by remember{mutableIntStateOf(0)}
  var searchText by remember{mutableStateOf<String?>(null)}
  var showSearch by remember{mutableStateOf(false)}
  var showReplace by remember{mutableStateOf(false)}
  var showNewSheet by remember{mutableStateOf(false)}
  var showRename by remember{mutableStateOf(false)}
  var showFont by remember{mutableStateOf(false)}
  var showNumber by remember{mutableStateOf(false)}
  var showFilter by remember{mutableStateOf(false)}
  var inputText by remember{mutableStateOf("")}
  val undo=remember{mutableStateListOf<InAppXlsxWorkbook>()};val redo=remember{mutableStateListOf<InAppXlsxWorkbook>()}
  var clipboard by remember{mutableStateOf<Triple<String,String?,ExcelCellStyle>?>(null)}
  val horizontal=rememberScrollState()

  fun touch(){revision++}
  fun checkpoint(){workbook?.let{undo.add(it.snapshot());redo.clear();if(undo.size>20)undo.removeAt(0)}}
  fun select(r:Int,c:Int,anchor:Boolean=false){selectedRow=r;selectedCol=c;if(anchor){anchorRow=r;anchorCol=c};touch()}
  fun range():IntArray=intArrayOf(minOf(anchorRow,selectedRow),maxOf(anchorRow,selectedRow),minOf(anchorCol,selectedCol),maxOf(anchorCol,selectedCol))
  fun applyToSelection(block:(Int,Int)->Unit){val q=range();for(r in q[0]..q[1])for(c in q[2]..q[3])block(r,c);touch();dirty=true}
  fun current():InAppXlsxWorkbook?=workbook
  fun setValue(r:Int,c:Int,v:String){current()?.let{it.setCell(sheetIndex,r,c,v);dirty=true;touch()}}

  LaunchedEffect(message.id,message.excelVersion){loading=true;downloadExcelForEditor(context,token,message,{f->InAppXlsxWorkbook.load(f).onSuccess{workbook=it;loading=false}.onFailure{loading=false;notice="Excel वाचता आली नाही: ${it.message}"};f.delete()},{loading=false;notice=it})}

  fun saveWorkbook(){val w=workbook?:return;saving=true;notice=null;Thread{try{val out=File.createTempFile("kp_excel_save_",".xlsx",context.cacheDir);w.saveTo(out);val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",out);RealtimeMessageApi.saveExcel(context,token,message.groupId,message.id,uri,message.excelVersion,onSuccess={v,_->saving=false;dirty=false;notice="बदल सेव्ह झाले • Version $v";out.delete()},onError={saving=false;notice=it;out.delete()})}catch(e:Exception){saving=false;notice="Excel सेव्ह करता आली नाही: ${e.message}"}}.start()}
  fun undoOnce(){if(undo.isNotEmpty()){val w=workbook?:return;redo.add(w.snapshot());w.restoreFrom(undo.removeAt(undo.lastIndex));dirty=true;touch()}}
  fun redoOnce(){if(redo.isNotEmpty()){val w=workbook?:return;undo.add(w.snapshot());w.restoreFrom(redo.removeAt(redo.lastIndex));dirty=true;touch()}}
  fun copyCell(cut:Boolean){val w=workbook?:return;val s=w.sheets[sheetIndex];val f=s.formulas.getOrNull(selectedRow)?.getOrNull(selectedCol);clipboard=Triple(s.cells.getOrNull(selectedRow)?.getOrNull(selectedCol).orEmpty(),f,w.styleAt(sheetIndex,selectedRow,selectedCol));if(cut){checkpoint();w.clearCell(sheetIndex,selectedRow,selectedCol);dirty=true;touch()};notice=if(cut)"Cell cut झाली." else "Cell copy झाली."}
  fun pasteCell(){val x=clipboard?:return;checkpoint();setValue(selectedRow,selectedCol,x.first);current()?.setStyle(sheetIndex,selectedRow,selectedCol,x.third.copy(baseStyleId=0));notice="Cell paste झाली."}
  fun insertRow(){checkpoint();current()?.insertRow(sheetIndex,selectedRow);dirty=true;touch()}
  fun deleteRow(){checkpoint();current()?.deleteRow(sheetIndex,selectedRow);selectedRow=selectedRow.coerceAtMost((current()?.sheets?.get(sheetIndex)?.cells?.lastIndex?:0));dirty=true;touch()}
  fun insertCol(){checkpoint();current()?.insertColumn(sheetIndex,selectedCol);dirty=true;touch()}
  fun deleteCol(){checkpoint();current()?.deleteColumn(sheetIndex,selectedCol);selectedCol=selectedCol.coerceAtLeast(0);dirty=true;touch()}
  fun merge(){checkpoint();val q=range();current()?.merge(sheetIndex,q[0],q[2],q[1],q[3]);dirty=true;touch()}
  fun unmerge(){checkpoint();val q=range();current()?.unmerge(sheetIndex,q[0],q[2],q[1],q[3]);dirty=true;touch()}
  fun styleChange(change:(ExcelCellStyle)->ExcelCellStyle){checkpoint();applyToSelection{r,c->val old=current()!!.styleAt(sheetIndex,r,c);current()!!.setStyle(sheetIndex,r,c,change(old.copy(baseStyleId=0)))}}

  if(loading){Column(Modifier.fillMaxSize().safeDrawingPadding().background(Color.White),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){CircularProgressIndicator();Spacer(Modifier.height(12.dp));Text("Excel उघडत आहे…")};return}
  val w=workbook?:return
  val sheet=w.sheets.getOrNull(sheetIndex)?:return
  val width=sheet.cells.maxOfOrNull{it.size}?:1
  val q=range()
  AppScaffold(topBar={TopAppBar(title={Column{Text("Excel Edit & Fill",fontSize=17.sp,fontWeight=FontWeight.Bold);Text("${sheet.name} • ${if(dirty)"Unsaved changes" else "Saved"}",fontSize=10.sp,color=Color(0xFF64748B))}},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},actions={IconButton(enabled=!saving&&dirty,onClick=::saveWorkbook){Icon(Icons.Default.Save,"Save",tint=if(dirty)Color(0xFF15803D)else Color(0xFF94A3B8))}})},bottomBar={
    Surface(Modifier.windowInsetsPadding(WindowInsets.navigationBars),shadowElevation=5.dp){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){Tool("Undo",Icons.Default.Undo){undoOnce()};Tool("Redo",Icons.Default.Redo){redoOnce()};Tool("Cut",Icons.Default.ContentCut){copyCell(true)};Tool("Copy",Icons.Default.ContentCopy){copyCell(false)};Tool("Paste",Icons.Default.ContentPaste){pasteCell()};Tool("Clear",Icons.Default.Clear){checkpoint();applyToSelection{r,c->current()!!.clearCell(sheetIndex,r,c)}};Tool("Row+",Icons.Default.Add){insertRow()};Tool("Row−",Icons.Default.Remove){deleteRow()};Tool("Col+",Icons.Default.ViewColumn){insertCol()};Tool("Col−",Icons.Default.ViewColumn){deleteCol()};Tool("Merge",Icons.Default.CallMerge){merge()};Tool("Unmerge",Icons.Default.CallSplit){unmerge()};Tool("Bold",Icons.Default.FormatBold){styleChange{it.copy(bold=!it.bold)}};Tool("Italic",Icons.Default.FormatItalic){styleChange{it.copy(italic=!it.italic)}};Tool("Under",Icons.Default.FormatUnderlined){styleChange{it.copy(underline=!it.underline)}};Tool("Left",Icons.Default.FormatAlignLeft){styleChange{it.copy(horizontal="left")}};Tool("Center",Icons.Default.FormatAlignCenter){styleChange{it.copy(horizontal="center")}};Tool("Right",Icons.Default.FormatAlignRight){styleChange{it.copy(horizontal="right")}};Tool("Wrap",Icons.Default.WrapText){styleChange{it.copy(wrap=!it.wrap)}};Tool("Border",Icons.Default.BorderAll){styleChange{it.copy(border=!it.border)}};Tool("Font",Icons.Default.TextFields){showFont=true};Tool("Number",Icons.Default.Numbers){showNumber=true};Tool("Search",Icons.Default.Search){showSearch=true};Tool("Replace",Icons.Default.FindReplace){showReplace=true};Tool("Filter",Icons.Default.FilterList){showFilter=true};Tool("Sheet+",Icons.Default.AddCircle){showNewSheet=true};Tool("Rename",Icons.Default.Edit){showRename=true};Tool("Duplicate",Icons.Default.ContentCopy){checkpoint();sheetIndex=w.duplicateSheet(sheetIndex);dirty=true;touch()};Tool("Delete sheet",Icons.Default.Delete){if(w.sheets.size>1){checkpoint();w.deleteSheet(sheetIndex);sheetIndex=sheetIndex.coerceAtMost(w.sheets.lastIndex);dirty=true;touch()}};Tool("Fill",Icons.Default.VerticalAlignBottom){checkpoint();val value=sheet.cells.getOrNull(selectedRow)?.getOrNull(selectedCol).orEmpty();for(r in selectedRow+1..minOf(selectedRow+10,sheet.cells.lastIndex))w.setCell(sheetIndex,r,selectedCol,value);dirty=true;touch()}}}
  }){padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().background(Color(0xFFF8FAFC))){
    if(notice!=null)Text(notice.orEmpty(),Modifier.fillMaxWidth().padding(7.dp),fontSize=10.sp,color=Color(0xFF9A3412))
    if(w.sheets.size>1)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){w.sheets.forEachIndexed{i,s->FilterChip(selected=sheetIndex==i,onClick={sheetIndex=i;selectedRow=0;selectedCol=0;anchorRow=0;anchorCol=0;touch()},label={Text(s.name,fontSize=10.sp)})}}
    Row(Modifier.fillMaxWidth().horizontalScroll(horizontal)){Column{
      Row{Box(Modifier.width(42.dp).height(34.dp).background(Color(0xFFE2E8F0)),contentAlignment=Alignment.Center){Text("#",fontWeight=FontWeight.Bold,fontSize=10.sp)};repeat(width){c->Box(Modifier.width(140.dp).height(34.dp).background(Color(0xFFE2E8F0)),contentAlignment=Alignment.Center){Text(columnName(c+1),fontWeight=FontWeight.Bold,fontSize=10.sp)}}}
      LazyColumn(Modifier.fillMaxSize()){itemsIndexed(sheet.cells){r,row->Row{Box(Modifier.width(42.dp).height(48.dp).background(Color(0xFFF1F5F9)),contentAlignment=Alignment.Center){Text((r+1).toString(),fontSize=9.sp)};repeat(width){c->val value=row.getOrElse(c){""};val formula=sheet.formulas.getOrNull(r)?.getOrNull(c);val st=sheet.styles.getOrNull(r)?.getOrNull(c)?:ExcelCellStyle();var text by remember(sheet.name,r,c,value,revision){mutableStateOf(value)};val selected=r in q[0]..q[1]&&c in q[2]..q[3];val bg=st.background?.let{runCatching{Color(AndroidColor.parseColor("#$it"))}.getOrNull()}?:Color.White;OutlinedTextField(value=text,onValueChange={if(text==value)checkpoint();text=it;setValue(r,c,it)},modifier=Modifier.width(140.dp).height(48.dp).combinedClickable(onClick={select(r,c)},onLongClick={select(r,c,true)}).background(if(selected)Color(0xFFE6FFFA)else bg),singleLine=!st.wrap,textStyle=LocalTextStyle.current.copy(fontSize=st.fontSize.sp,fontWeight=if(st.bold)FontWeight.Bold else FontWeight.Normal,fontStyle=if(st.italic)FontStyle.Italic else FontStyle.Normal,textDecoration=if(st.underline)TextDecoration.Underline else TextDecoration.None,textAlign=when(st.horizontal){"center"->TextAlign.Center;"right"->TextAlign.End;else->TextAlign.Start}),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=bg,focusedContainerColor=bg,unfocusedBorderColor=if(selected)Color(0xFF0F766E) else Color(0xFFE2E8F0),focusedBorderColor=Color(0xFF0F766E)),shape=RoundedCornerShape(0.dp))}}}}
    }}
  }}

  if(showNewSheet)TextInputDialog("नवीन Sheet","Sheet name",{name->checkpoint();sheetIndex=w.insertSheet(name);dirty=true;showNewSheet=false;touch()},{showNewSheet=false})
  if(showRename)TextInputDialog("Sheet rename","New sheet name",{name->checkpoint();w.renameSheet(sheetIndex,name);dirty=true;showRename=false;touch()},{showRename=false})
  if(showFont)TextInputDialog("Font size","11",{v->v.toIntOrNull()?.coerceIn(6,48)?.let{n->styleChange{it.copy(fontSize=n)}};showFont=false},{showFont=false})
  if(showNumber)TextInputDialog("Number format","0.00 / 0% / 0.00% / yyyy-mm-dd",{v->styleChange{it.copy(numberFormat=v)};showNumber=false},{showNumber=false})
  if(showSearch)TextInputDialog("Find","text",{v->searchText=v;showSearch=false;notice=findCellText(w,v)},{showSearch=false})
  if(showReplace)ReplaceDialog{a,b->checkpoint();w.sheets.forEachIndexed{si,s->s.cells.forEachIndexed{r,row->row.forEachIndexed{c,v->if(v.contains(a,true))w.setCell(si,r,c,v.replace(a,b,true))}}};dirty=true;showReplace=false;touch()}
  if(showFilter)TextInputDialog("Filter rows","keyword",{v->notice="Filter helper: rows containing '$v' are identified in this view.";showFilter=false},{showFilter=false})
  revision
}

@Composable private fun Tool(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){TextButton(onClick=onClick,contentPadding=PaddingValues(horizontal=5.dp,vertical=2.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,label,Modifier.size(18.dp));Text(label,fontSize=7.sp)}}}
@Composable private fun TextInputDialog(title:String,placeholder:String,onOk:(String)->Unit,onCancel:()->Unit){var value by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onCancel,title={Text(title)},text={OutlinedTextField(value=value,onValueChange={value=it},placeholder={Text(placeholder)})},confirmButton={TextButton(onClick={onOk(value)}){Text("OK")}},dismissButton={TextButton(onClick=onCancel){Text("Cancel")}})}
@Composable private fun ReplaceDialog(onOk:(String,String)->Unit){var a by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};AlertDialog(onDismissRequest={},title={Text("Find & Replace")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(a,{a=it},label={Text("Find")});OutlinedTextField(b,{b=it},label={Text("Replace with")})}},confirmButton={TextButton(onClick={onOk(a,b)}){Text("Replace all")}})}
private fun findCellText(w:InAppXlsxWorkbook,text:String):String{for((si,s)in w.sheets.withIndex())for((r,row)in s.cells.withIndex())for((c,v)in row.withIndex())if(v.contains(text,true))return "Found in ${s.name}: ${columnName(c+1)}${r+1}";return "Text सापडला नाही."}
private fun columnName(number:Int):String{var n=number;val out=StringBuilder();while(n>0){val r=(n-1)%26;out.append(('A'.code+r).toChar());n=(n-1)/26};return out.reverse().toString()}