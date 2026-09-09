package com.example.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.RealtimeMessageApi
import com.example.model.GroupMessage
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val excelEditorClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
private val excelEditorMain = Handler(Looper.getMainLooper())

private fun downloadExcelForEditor(context: Context, token: String, message: GroupMessage, onSuccess: (File) -> Unit, onError: (String) -> Unit) {
  Thread {
    try {
      val request = Request.Builder().url(RealtimeMessageApi.attachmentUrl(message.groupId, message.id)).header("Authorization", "Bearer $token").get().build()
      excelEditorClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) { excelEditorMain.post { onError("Excel download अयशस्वी (HTTP ${response.code})") }; return@use }
        val target = File.createTempFile("kp_excel_", ".xlsx", context.cacheDir)
        response.body?.byteStream()?.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } } ?: run { target.delete(); excelEditorMain.post { onError("Excel file रिकामी आहे.") }; return@use }
        excelEditorMain.post { onSuccess(target) }
      }
    } catch (e: Exception) { excelEditorMain.post { onError(e.message?.trim().takeUnless { it.isNullOrBlank() } ?: "Excel उघडता आली नाही.") } }
  }.start()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppExcelEditorScreen(
  message: GroupMessage,
  token: String,
  canPublish: Boolean,
  onBack: () -> Unit,
  onPublished: () -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var workbook by remember(message.id, message.excelVersion) { mutableStateOf<InAppXlsxWorkbook?>(null) }
  var loading by remember(message.id, message.excelVersion) { mutableStateOf(true) }
  var saving by remember(message.id) { mutableStateOf(false) }
  var dirty by remember(message.id, message.excelVersion) { mutableStateOf(false) }
  var notice by remember(message.id, message.excelVersion) { mutableStateOf<String?>(null) }
  var selectedSheet by remember(message.id, message.excelVersion) { mutableIntStateOf(0) }
  var externalVersion by remember(message.id) { mutableIntStateOf(message.excelVersion) }
  val horizontal = rememberScrollState()

  LaunchedEffect(message.id, message.excelVersion) {
    loading = true
    notice = null
    downloadExcelForEditor(context, token, message,
      onSuccess = { file ->
        InAppXlsxWorkbook.load(file).onSuccess { loaded -> workbook = loaded; externalVersion = message.excelVersion; loading = false }.onFailure { loading = false; notice = "Excel वाचता आली नाही: ${it.message ?: "अज्ञात त्रुटी"}" }
        file.delete()
      },
      onError = { loading = false; notice = it }
    )
  }

  fun saveWorkbook() {
    val current = workbook ?: return
    saving = true; notice = null
    Thread {
      try {
        val output = File.createTempFile("kp_excel_save_", ".xlsx", context.cacheDir)
        current.saveTo(output)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
        RealtimeMessageApi.saveExcel(context, token, message.groupId, message.id, uri, message.excelVersion,
          onSuccess = { version, _ -> saving = false; dirty = false; externalVersion = version; notice = "बदल सेव्ह झाले • Version $version"; output.delete() },
          onError = { saving = false; notice = it; output.delete() }
        )
      } catch (e: Exception) { saving = false; notice = "Excel सेव्ह करता आली नाही: ${e.message ?: "अज्ञात त्रुटी"}" }
    }.start()
  }

  if (loading) {
    Column(Modifier.fillMaxSize().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Excel उघडत आहे…") }
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Column { Text("Excel Edit & Fill", fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("Version $externalVersion", fontSize = 10.sp, color = Color(0xFF64748B)) } },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { IconButton(enabled = !saving && dirty, onClick = ::saveWorkbook) { Icon(Icons.Default.Save, "Save", tint = if (dirty) Color(0xFF15803D) else Color(0xFF94A3B8)) } }
      )
    }
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8FAFC))) {
      if (notice != null) Surface(Modifier.fillMaxWidth().padding(8.dp), RoundedCornerShape(10.dp), color = Color(0xFFFFF7ED)) { Text(notice.orEmpty(), Modifier.padding(9.dp), fontSize = 11.sp, color = Color(0xFF9A3412)) }
      if (message.excelVersion != externalVersion && dirty) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp), RoundedCornerShape(10.dp), color = Color(0xFFFFF1F2)) {
          Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("दुसऱ्या user ने नवीन Version सेव्ह केली आहे. तुमचे बदल सुरक्षित ठेवण्यासाठी आधी Reload करा.", Modifier.weight(1f), fontSize = 10.sp, color = Color(0xFF9F1239)); IconButton(onClick = onBack) { Icon(Icons.Default.Refresh, "Reload") } }
        }
      }
      val current = workbook
      if (current != null) {
        if (current.sheets.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          current.sheets.forEachIndexed { index, sheet -> FilterChip(selected = selectedSheet == index, onClick = { selectedSheet = index }, label = { Text(sheet.name, fontSize = 11.sp) }) }
        }
        val sheet = current.sheets.getOrNull(selectedSheet)
        if (sheet != null) {
          Row(Modifier.fillMaxWidth().horizontalScroll(horizontal).padding(horizontal = 8.dp)) {
            Column {
              Row {
                Box(Modifier.width(42.dp).height(34.dp).background(Color(0xFFE2E8F0)), contentAlignment = Alignment.Center) { Text("#", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                repeat(sheet.cells.maxOfOrNull { it.size } ?: 1) { col -> Box(Modifier.width(140.dp).height(34.dp).background(Color(0xFFE2E8F0)), contentAlignment = Alignment.Center) { Text(columnName(col + 1), fontWeight = FontWeight.Bold, fontSize = 10.sp) } }
              }
              LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(sheet.cells) { rowIndex, row ->
                  Row {
                    Box(Modifier.width(42.dp).height(48.dp).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) { Text((rowIndex + 1).toString(), fontSize = 9.sp, color = Color(0xFF64748B)) }
                    repeat(sheet.cells.maxOfOrNull { it.size } ?: 1) { col ->
                      val value = row.getOrElse(col) { "" }
                      var cellText by remember(sheet.name, rowIndex, col, value) { mutableStateOf(value) }
                      OutlinedTextField(
                        value = cellText,
                        onValueChange = { cellText = it; if (it != value) { current.setCell(selectedSheet, rowIndex, col, it); dirty = true; notice = null } },
                        modifier = Modifier.width(140.dp).height(48.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Color(0xFFE2E8F0), focusedBorderColor = Color(0xFF0F766E)),
                        shape = RoundedCornerShape(0.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
      if (dirty) Button(onClick = ::saveWorkbook, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text(if (saving) "Saving…" else "Save changes") }
      if (canPublish && !dirty && !saving) OutlinedButton(onClick = { RealtimeMessageApi.publishExcel(token, message.groupId, message.id, onSuccess = onPublished, onError = { notice = it }) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { Text("Publish to Reports") }
    }
  }
}

private fun columnName(number: Int): String { var n = number; val out = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; out.append(('A'.code + r).toChar()); n = (n - 1) / 26 }; return out.reverse().toString() }
