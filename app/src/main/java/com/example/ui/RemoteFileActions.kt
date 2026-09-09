package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.RealtimeMessageApi
import com.example.data.ReportsApi
import com.example.data.ExcelReport
import com.example.model.GroupMessage
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val remoteFileClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
private val mainHandler = Handler(Looper.getMainLooper())
private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private fun safeFileName(value: String, fallback: String): String = value.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { fallback }

private fun downloadRemoteToFile(context: Context, token: String, url: String, fileName: String, onSuccess: (File) -> Unit, onError: (String) -> Unit) {
  Thread {
    try {
      val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
      remoteFileClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) { mainHandler.post { onError("फाइल डाउनलोड अयशस्वी (HTTP ${response.code})") }; return@use }
        val target = File(context.cacheDir, "remote_${System.currentTimeMillis()}_${safeFileName(fileName, "file")}")
        response.body?.byteStream()?.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        mainHandler.post { onSuccess(target) }
      }
    } catch (e: Exception) { mainHandler.post { onError(e.message?.trim().takeUnless { it.isNullOrBlank() } ?: "फाइल डाउनलोड करता आली नाही.") } }
  }.start()
}

private fun downloadRemoteToUri(token: String, url: String, destination: Uri, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
  Thread {
    try {
      val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
      remoteFileClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) { mainHandler.post { onError("फाइल डाउनलोड अयशस्वी (HTTP ${response.code})") }; return@use }
        val body = response.body ?: run { mainHandler.post { onError("फाइल रिकामी आहे.") }; return@use }
        context.contentResolver.openOutputStream(destination)?.use { output -> body.byteStream().use { input -> input.copyTo(output) } } ?: run { mainHandler.post { onError("फाइल सेव्ह करण्यासाठी जागा उपलब्ध नाही.") }; return@use }
        mainHandler.post { onSuccess() }
      }
    } catch (e: Exception) { mainHandler.post { onError(e.message?.trim().takeUnless { it.isNullOrBlank() } ?: "फाइल डाउनलोड करता आली नाही.") } }
  }.start()
}

private fun openLocalFile(context: Context, file: File, mimeType: String?): Boolean = runCatching {
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mimeType ?: "application/octet-stream"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
  context.startActivity(Intent.createChooser(intent, "फाइल उघडा")); true
}.getOrDefault(false)

@Composable
fun GroupFileActions(message: GroupMessage, token: String, canPublish: Boolean, onDeleted: () -> Unit) {
  val context = LocalContext.current
  val isExcel = message.messageType == "excel"
  val isXlsx = isExcel && message.mimeType == XLSX_MIME
  val isPublished = message.excelStatus == "published"
  var busy by remember(message.id) { mutableStateOf(false) }
  var notice by remember(message.id) { mutableStateOf<String?>(null) }
  var localPublished by remember(message.id) { mutableStateOf(isPublished) }
  var showEditor by remember(message.id) { mutableStateOf(false) }
  var legacyEditingFile by remember(message.id) { mutableStateOf<File?>(null) }

  val downloadUrl = RealtimeMessageApi.attachmentUrl(message.groupId, message.id)
  val downloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(message.mimeType ?: "application/octet-stream")) { uri ->
    if (uri != null) { busy = true; downloadRemoteToUri(token, downloadUrl, uri, context, { busy = false; notice = "फाइल डाउनलोड झाली." }, { busy = false; notice = it }) }
  }
  val legacyEditLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    val file = legacyEditingFile ?: return@rememberLauncherForActivityResult
    busy = true
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    RealtimeMessageApi.saveExcel(context, token, message.groupId, message.id, uri, message.excelVersion,
      onSuccess = { version, _ -> busy = false; notice = "बदल सेव्ह झाले • Version $version"; file.delete(); legacyEditingFile = null },
      onError = { busy = false; notice = it; file.delete(); legacyEditingFile = null }
    )
  }

  if (showEditor && isXlsx && !localPublished) {
    Dialog(onDismissRequest = { if (!busy) showEditor = false }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
      // Dialogs are separate windows, so apply the same permanent navigation-bar
      // safety rule explicitly here as well as at the application root.
      Surface(Modifier.fillMaxSize().navigationBarsPadding(), color = Color.White) {
        InAppExcelEditorScreen(message, token, canPublish, { if (!busy) showEditor = false }, { showEditor = false; localPublished = true; notice = "ही फाइल Reports मध्ये प्रकाशित झाली." })
      }
    }
  }

  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    message.text?.takeIf { it.startsWith("Sent from Reports By ") }?.let { originTag ->
      Surface(Modifier.wrapContentWidth(), color = Color(0xFFEAF2FF), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) { Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF2563EB), modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text(originTag, fontSize = 8.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    }
    if (localPublished) Surface(Modifier.fillMaxWidth(), color = Color(0xFFEAF7EE), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) { Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null, tint = Color(0xFF15803D), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("ही फाइल Reports मध्ये प्रकाशित आहे. सामान्य users आता बदल करू शकत नाहीत.", fontSize = 9.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold) } }
    if (notice != null) Text(notice.orEmpty(), fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 2.dp))
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF0F766E))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      FileAction(Icons.Default.Visibility, "View", Color(0xFF10B981)) { if (!busy) { busy = true; downloadRemoteToFile(context, token, downloadUrl, message.attachmentName ?: "file", { file -> busy = false; if (!openLocalFile(context, file, message.mimeType)) notice = "ही फाइल उघडण्यासाठी योग्य app उपलब्ध नाही." }, { busy = false; notice = it }) } }
      FileAction(Icons.Default.Download, "Download", Color(0xFF2563EB)) { if (!busy) downloadPicker.launch(message.attachmentName ?: if (isExcel) "data.xlsx" else "document.pdf") }
      if (isXlsx && !localPublished) FileAction(Icons.Default.Edit, "Edit & Fill Data", Color(0xFF7C3AED)) { if (!busy) { notice = null; showEditor = true } }
      if (isExcel && !isXlsx && !localPublished) FileAction(Icons.Default.Edit, "Edit & Fill Data", Color(0xFF7C3AED)) {
        if (!busy) {
          busy = true
          downloadRemoteToFile(context, token, downloadUrl, message.attachmentName ?: "data.xls", { file ->
            busy = false; legacyEditingFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_EDIT).apply { setDataAndType(uri, message.mimeType ?: "application/octet-stream"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            runCatching { legacyEditLauncher.launch(intent) }.onFailure { legacyEditingFile?.delete(); legacyEditingFile = null; notice = "Excel edit करण्यासाठी योग्य app उपलब्ध नाही." }
          }, { busy = false; notice = it })
        }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      if (!localPublished && canPublish) FileAction(Icons.Default.Publish, "Publish", Color(0xFFEA580C)) { if (!busy) { busy = true; RealtimeMessageApi.publishExcel(token, message.groupId, message.id, { busy = false; localPublished = true; notice = "ही फाइल Reports मध्ये प्रकाशित झाली." }, { busy = false; notice = it }) } }
      FileAction(Icons.Default.Delete, "Delete", Color(0xFFDC2626)) { if (!busy) { busy = true; RealtimeMessageApi.deleteMessage(token, message.groupId, message.id, { busy = false; onDeleted() }, { busy = false; notice = it }) } }
    }
  }
}

@Composable
fun ReportEditAction(report: ExcelReport, token: String, enabled: Boolean, onSaved: (Int) -> Unit, onError: (String) -> Unit) {
  val context = LocalContext.current
  var editingFile by remember(report.id) { mutableStateOf<File?>(null) }
  var busy by remember(report.id) { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    val file = editingFile ?: return@rememberLauncherForActivityResult
    busy = true
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    RealtimeMessageApi.saveExcel(context, token, report.groupId, report.id, uri, report.version,
      onSuccess = { version, _ -> busy = false; onSaved(version); file.delete(); editingFile = null },
      onError = { busy = false; onError(it); file.delete(); editingFile = null }
    )
  }
  FileAction(Icons.Default.Edit, if (busy) "Saving..." else "Edit", Color(0xFF2563EB)) {
    if (enabled && !busy) {
      busy = true
      val url = ReportsApi.reportDownloadUrl(report.id)
      downloadRemoteToFile(context, token, url, report.fileName, { file ->
        busy = false; editingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_EDIT).apply { setDataAndType(uri, report.mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        runCatching { launcher.launch(intent) }.onFailure { editingFile?.delete(); editingFile = null; busy = false; onError("Excel edit करण्यासाठी योग्य app उपलब्ध नाही.") }
      }, { busy = false; onError(it) })
    }
  }
}

@Composable
private fun FileAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) { Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { Icon(icon, label, tint = tint, modifier = Modifier.size(19.dp)); Text(label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
