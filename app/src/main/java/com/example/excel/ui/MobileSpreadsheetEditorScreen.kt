package com.example.excel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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

private val G=Color(0xFF107C41); private val DG=Color(0xFF0F5132); private val HG=Color(0xFFD1E7DD)
private val HB=Color(0xFFF1F5F9); private val GB=Color(0xFFE2E8F0); private val Sel=Color(0x1A107C41)
private val RH=44.dp; private val CW=98.dp; private val CH=37.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun MobileSpreadsheetEditorScreen(workbook: SpreadsheetWorkbook,onBack:()->Unit,onSave:()->Unit){
    var si by remember{mutableIntStateOf(0)}; var active by remember{mutableStateOf(CellAddress(0,0))}; var anchor by remember{mutableStateOf(active)}
    var selection by remember{mutableStateOf(CellRange(active,active))}; var text by remember{mutableStateOf("")}; var editing by remember{mutableStateOf(false)}
    var original by remember{mutableStateOf<CellValue?>(null)}; var cellFocusRequest by remember{mutableStateOf(false)}; var more by remember{mutableStateOf(false)}
    var copied by remember{mutableStateOf<List<List<Snap>>?>(null)}; var rename by remember{mutableStateOf(false)}; var refresh by remember{mutableIntStateOf(0)}
    val history=remember{SpreadsheetHistory()}; val h=rememberScrollState(); val v=rememberLazyListState(); val cellFocus=remember{FocusRequester()}; val kb=LocalSoftwareKeyboardController.current
    val sheet=workbook.sheets[si.coerceIn(0,workbook.sheets.lastIndex)]; val engine=remember(workbook,refresh){FormulaEngine{n->workbook.sheets.firstOrNull{it.name.equals(n,true)}}}
    val rows=maxOf(60,sheet.maxRow()+25); val cols=maxOf(16,sheet.maxColumn()+5)

    fun commit(){val old=original?:run{editing=false;return};val nv=sheet.valueAt(active);if(old!=nv){sheet.setValue(active,old);history.execute(SetCellCommand(sheet,active,nv))};original=null;editing=false;cellFocusRequest=false;text=raw(sheet.valueAt(active));refresh++}
    fun draft(s:String){if(!editing){original=sheet.valueAt(active);editing=true};text=s;sheet.setValue(active,parse(s));refresh++}
    fun select(a:CellAddress,extend:Boolean=false){commit();active=a;if(extend)selection=CellRange(anchor,a)else{anchor=a;selection=CellRange(a,a)};text=raw(sheet.valueAt(a));refresh++}
    fun startEdit(a:CellAddress){active=a;anchor=a;selection=CellRange(a,a);text=raw(sheet.valueAt(a));original=sheet.valueAt(a);editing=true;cellFocusRequest=true;refresh++}
    fun finish(){commit();kb?.hide()}
    fun style(f:(CellStyle)->CellStyle){commit();history.execute(StyleRangeCommand(sheet,selection,f));refresh++}
    fun clear(){commit();history.execute(ClearRangeCommand(sheet,selection));text="";refresh++}
    fun copy(){commit();copied=(selection.top..selection.bottom).map{r->(selection.left..selection.right).map{c->val a=CellAddress(r,c);Snap(sheet.valueAt(a),sheet.cell(a).style)}}}
    fun paste(){commit();val d=copied?:return;val edits=mutableListOf<Pair<CellAddress,Pair<CellValue,CellStyle?>>>();d.forEachIndexed{r,row->row.forEachIndexed{c,x->edits+=CellAddress(active.row+r,active.column+c) to (x.value to x.style)}};history.execute(BatchEditCommand(sheet,edits,"Paste cells"));refresh++}
    fun autoSum(){val r=if(selection.isSingleCell){val c=active.column;var t=active.row-1;while(t>=0&&sheet.valueAt(CellAddress(t,c)) !is CellValue.Empty)t--;"${CellAddress(t+1,c)}:${CellAddress(active.row-1,c)}"}else selection.toString();commit();history.execute(SetCellCommand(sheet,active,CellValue.Formula("=SUM($r)")));text="=SUM($r)";refresh++}

    Surface(Modifier.fillMaxSize().imePadding(),Color.White){Column(Modifier.fillMaxSize()){
        Surface(G,shadowElevation=2.dp){Row(Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal=5.dp),Alignment.CenterVertically){
            IconButton({finish();onBack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=Color.White)}
            Column(Modifier.weight(1f)){Text("Excel Editor",color=Color.White,fontWeight=FontWeight.Bold,fontSize=16.sp);Text("${sheet.name} • $selection",color=Color.White.copy(.82f),fontSize=10.sp)}
            IconButton({finish();if(history.undo()){text=raw(sheet.valueAt(active));refresh++}}){Icon(Icons.AutoMirrored.Filled.Undo,"Undo",tint=Color.White)}
            IconButton({finish();if(history.redo()){text=raw(sheet.valueAt(active));refresh++}}){Icon(Icons.AutoMirrored.Filled.Redo,"Redo",tint=Color.White)}
            Button({finish();onSave()},colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=G),contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp),shape=RoundedCornerShape(18.dp)){Icon(Icons.Default.Check,null,Modifier.size(15.dp));Spacer(Modifier.width(3.dp));Text("Save",fontSize=12.sp,fontWeight=FontWeight.Bold)}
            Box{IconButton({more=true}){Icon(Icons.Default.MoreVert,"More",tint=Color.White)};DropdownMenu(more,{more=false}){
                DropdownMenuItem({Text("Select All")},onClick={commit();selection=CellRange(CellAddress(0,0),CellAddress(rows-1,cols-1));active=CellAddress(0,0);more=false;refresh++})
                DropdownMenuItem({Text("Add Sheet")},onClick={workbook.addSheet();more=false;refresh++})
                DropdownMenuItem({Text("Rename Sheet")},onClick={more=false;rename=true})
                DropdownMenuItem({Text("Clear Selection")},onClick={clear();more=false})
            }}
        }}

        Surface(Color(0xFFF8FAFC),border=BorderStroke(.5.dp,GB)){Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal=5.dp,vertical=2.dp),Alignment.CenterVertically){
            Surface(Color.White,shape=RoundedCornerShape(4.dp),border=BorderStroke(1.dp,Color(0xFFCBD5E1))){Text(selection.toString(),Modifier.padding(horizontal=7.dp,vertical=4.dp),fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Bold,fontSize=11.sp,color=DG)}
            Text("fx",fontSize=15.sp,fontWeight=FontWeight.Black,color=G,modifier=Modifier.padding(horizontal=5.dp))
            TextField(value=text,onValueChange={draft(it)},modifier=Modifier.weight(1f).height(42.dp).onFocusChanged{if(it.isFocused&&!editing){original=sheet.valueAt(active);editing=true}},colors=TextFieldDefaults.colors(focusedContainerColor=Color.White,unfocusedContainerColor=Color.White,focusedIndicatorColor=G,unfocusedIndicatorColor=Color.Transparent),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={finish()}),placeholder={Text("Enter value or =FORMULA",fontSize=11.sp)})
            IconButton({finish()},Modifier.size(32.dp).background(G,CircleShape)){Icon(Icons.Default.Check,"Apply",tint=Color.White,Modifier.size(17.dp))}
        }}

        if(editing||text.startsWith("="))Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=5.dp,vertical=2.dp),Arrangement.spacedBy(5.dp)){
            listOf("Σ SUM","AVERAGE","COUNT","MAX","MIN","IF").forEach{f->Chip(f){draft(if(text.startsWith("="))text+f.replace("Σ ","")+"(" else "=${f.replace("Σ ","")}(" )}}
            Chip("AutoSum"){autoSum()}
        }

        val st=sheet.cell(active).style
        Surface(Color.White,shadowElevation=1.dp){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(38.dp),Alignment.CenterVertically){
            T("Cut",Icons.Default.ContentCut){copy();clear()};T("Copy",Icons.Default.ContentCopy){copy()};T("Paste",Icons.Default.ContentPaste,copied!=null){paste()};T("Clear",Icons.Default.DeleteOutline){clear()};D()
            F(Icons.Default.FormatBold,st.bold){style{it.copy(bold=!it.bold)}};F(Icons.Default.FormatItalic,st.italic){style{it.copy(italic=!it.italic)}};F(Icons.Default.FormatUnderlined,st.underline){style{it.copy(underline=!it.underline)}};D()
            T("Left",Icons.Default.FormatAlignLeft){style{it.copy(horizontalAlignment=HorizontalAlignment.Left)}};T("Center",Icons.Default.FormatAlignCenter){style{it.copy(horizontalAlignment=HorizontalAlignment.Center)}};T("Right",Icons.Default.FormatAlignRight){style{it.copy(horizontalAlignment=HorizontalAlignment.Right)}};T("Wrap",Icons.Default.WrapText){style{it.copy(wrapText=!it.wrapText)}};D()
            T("Fill",Icons.Default.FormatColorFill){style{it.copy(fillArgb=0xFFE8F5E9.toInt())}};T("Border",Icons.Default.BorderAll){style{it.copy(border=if(it.border==BorderStyle.None)BorderStyle.Thin else BorderStyle.None)}};T("+Row",Icons.Default.Add){commit();sheet.insertRow(active.row);refresh++};T("-Row",Icons.Default.Remove){commit();sheet.deleteRow(active.row);refresh++};T("+Col",Icons.Default.AddBox){commit();sheet.insertColumn(active.column);refresh++};T("-Col",Icons.Default.IndeterminateCheckBox){commit();sheet.deleteColumn(active.column);refresh++}
        }}

        Box(Modifier.weight(1f).fillMaxWidth()){Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth().height(30.dp).background(HB).border(BorderStroke(.5.dp,Color(0xFFCBD5E1)))){Box(Modifier.width(RH).fillMaxHeight(),contentAlignment=Alignment.Center){Text("⊞",fontSize=13.sp)};Row(Modifier.weight(1f).horizontalScroll(h)){repeat(cols){c->Box(Modifier.width(CW).fillMaxHeight().border(BorderStroke(.5.dp,Color(0xFFCBD5E1))).background(if(c in selection.left..selection.right)HG else HB).clickable{select(CellAddress(0,c));selection=CellRange(CellAddress(0,c),CellAddress(rows-1,c));refresh++},contentAlignment=Alignment.Center){Text(col(c),fontSize=11.sp,fontWeight=FontWeight.Bold)}}}}
            LazyColumn(state=v,modifier=Modifier.weight(1f)){items((0 until rows).toList(),key={it}){r->Row(Modifier.fillMaxWidth().height(CH)){Box(Modifier.width(RH).fillMaxHeight().background(if(r in selection.top..selection.bottom)HG else HB).border(BorderStroke(.5.dp,Color(0xFFCBD5E1))).clickable{select(CellAddress(r,0));selection=CellRange(CellAddress(r,0),CellAddress(r,cols-1));refresh++},contentAlignment=Alignment.Center){Text("${r+1}",fontSize=11.sp)};Row(Modifier.weight(1f).horizontalScroll(h)){repeat(cols){c->val a=CellAddress(r,c);val cell=sheet.cell(a);val activeCell=a==active;val value=display(sheet,cell,engine);Box(Modifier.width(CW).fillMaxHeight().background(if(activeCell)Color.White else if(selection.contains(a))Sel else cell.style.fillArgb?.let{Color(it)}?:Color.White).border(if(activeCell)BorderStroke(2.dp,G) else BorderStroke(.5.dp,GB)).combinedClickable(onClick={select(a)},onLongClick={select(a,true)},onDoubleClick={startEdit(a)}).padding(horizontal=5.dp),contentAlignment=Alignment.CenterStart){if(editing&&activeCell){TextField(value=text,onValueChange={draft(it)},modifier=Modifier.fillMaxWidth().height(34.dp).focusRequester(cellFocus),colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={finish()}),textStyle=LocalTextStyle.current.copy(fontSize=12.sp,fontWeight=if(cell.style.bold)FontWeight.Bold else FontWeight.Normal))}else Text(value,Modifier.fillMaxWidth(),fontSize=12.sp,fontWeight=if(cell.style.bold)FontWeight.Bold else FontWeight.Normal,textDecoration=if(cell.style.underline)TextDecoration.Underline else TextDecoration.None,textAlign=when(cell.style.horizontalAlignment){HorizontalAlignment.Center->TextAlign.Center;HorizontalAlignment.Right->TextAlign.End;else->TextAlign.Start})}}}}}}
        }}

        Surface(Color(0xFFE2E8F0),shadowElevation=3.dp){Row(Modifier.fillMaxWidth().navigationBarsPadding().height(46.dp),Alignment.CenterVertically){Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())){workbook.sheets.forEachIndexed{idx,sh->Surface(color=if(idx==si)Color.White else HB,border=BorderStroke(.5.dp,if(idx==si)G else Color(0xFFCBD5E1)),modifier=Modifier.clickable{finish();si=idx;active=CellAddress(0,0);anchor=active;selection=CellRange(active,active);text=raw(workbook.sheets[idx].valueAt(active));refresh++}){Text(sh.name,Modifier.padding(horizontal=13.dp,vertical=6.dp),fontSize=12.sp,fontWeight=if(idx==si)FontWeight.Bold else FontWeight.Medium,color=if(idx==si)DG else Color(0xFF475569))}}};IconButton({finish();workbook.addSheet();refresh++},Modifier.size(32.dp).background(G,CircleShape)){Icon(Icons.Default.Add,"Add Sheet",tint=Color.White)}}}
    }}
    LaunchedEffect(cellFocusRequest){if(cellFocusRequest){cellFocus.requestFocus();kb?.show();cellFocusRequest=false}}
    if(rename){var name by remember{mutableStateOf(sheet.name)};AlertDialog(onDismissRequest={rename=false},title={Text("Rename Sheet")},text={OutlinedTextField(name,{name=it},label={Text("Sheet Name")},singleLine=true)},confirmButton={TextButton({if(name.isNotBlank()){sheet.name=name.trim();rename=false;refresh++}}){Text("Rename",color=G,fontWeight=FontWeight.Bold)}},dismissButton={TextButton({rename=false}){Text("Cancel")}})}
}

private data class Snap(val value:CellValue,val style:CellStyle)
@Composable private fun T(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,enabled:Boolean=true,onClick:()->Unit){IconButton(onClick,enabled,Modifier.size(36.dp)){Icon(icon,label,tint=if(enabled)Color(0xFF334155) else Color.LightGray,Modifier.size(18.dp))}}
@Composable private fun F(icon:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean,onClick:()->Unit){Surface(color=if(active)HG else Color.Transparent,Modifier.size(36.dp)){IconButton(onClick){Icon(icon,null,tint=if(active)DG else Color(0xFF334155),Modifier.size(18.dp))}}}
@Composable private fun D(){Box(Modifier.padding(horizontal=3.dp).width(1.dp).height(22.dp).background(GB))}
@Composable private fun Chip(text:String,onClick:()->Unit){Surface(shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Color(0xFFCBD5E1)),color=Color.White,modifier=Modifier.clickable(onClick=onClick)){Text(text,Modifier.padding(horizontal=8.dp,vertical=3.dp),fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=DG)}}
private fun display(s:SpreadsheetSheet,c:SpreadsheetCell,e:FormulaEngine):String=when(val v=c.value){CellValue.Empty->"";is CellValue.Text->v.value;is CellValue.Number->fmt(v.value);is CellValue.BooleanValue->if(v.value)"TRUE" else "FALSE";is CellValue.Formula->when(val x=e.evaluate(s.name,v.expression)){is CellValue.Number->fmt(x.value);is CellValue.Text->x.value;is CellValue.BooleanValue->if(x.value)"TRUE" else "FALSE";is CellValue.Error->x.code;else->v.expression};is CellValue.Error->v.code}
private fun raw(v:CellValue)=when(v){CellValue.Empty->"";is CellValue.Text->v.value;is CellValue.Number->fmt(v.value);is CellValue.BooleanValue->if(v.value)"TRUE" else "FALSE";is CellValue.Formula->v.expression;is CellValue.Error->v.code}
private fun parse(s:String):CellValue{val v=s.trim();if(v.isEmpty())return CellValue.Empty;if(v.startsWith("="))return CellValue.Formula(v);v.toDoubleOrNull()?.let{return CellValue.Number(it)};if(v.equals("true",true))return CellValue.BooleanValue(true);if(v.equals("false",true))return CellValue.BooleanValue(false);return CellValue.Text(v)}
private fun fmt(n:Double)=if(n%1.0==0.0)n.toLong().toString()else String.format(Locale.US,"%.2f",n).trimEnd('0').trimEnd('.')
private fun col(c:Int):String{var n=c+1;val b=StringBuilder();while(n>0){val r=(n-1)%26;b.append(('A'.code+r).toChar());n=(n-1)/26};return b.reverse().toString()}
