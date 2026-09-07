package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RealtimeChatManager
import com.example.data.RealtimeMessageApi
import com.example.model.*
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityOnBackground
import com.example.ui.theme.HighDensityPrimary
import com.example.ui.theme.HighDensityPrimaryContainer
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@Composable
fun GroupChatScreen(
  group: ChatGroup,
  session: UserSession,
  onBack: () -> Unit
) {
  var messages by remember(group.id) { mutableStateOf<List<GroupMessage>>(emptyList()) }
  var loading by remember(group.id) { mutableStateOf(true) }
  var error by remember(group.id) { mutableStateOf<String?>(null) }
  var input by remember(group.id) { mutableStateOf("") }
  var uploadError by remember(group.id) { mutableStateOf<String?>(null) }
  var uploading by remember(group.id) { mutableStateOf(false) }
  val connectionManager = remember(group.id, session.token) { RealtimeChatManager() }
  val connectionState by connectionManager.state.collectAsState()
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val context = androidx.compose.ui.platform.LocalContext.current

  fun addServerMessage(message: GroupMessage) {
    val normalized = message.copy(isMe = message.senderId == session.id)
    if (messages.any { it.id == normalized.id || (!normalized.clientMessageId.isNullOrBlank() && it.clientMessageId == normalized.clientMessageId) }) return
    messages = messages + normalized
    scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
  }

  DisposableEffect(group.id, session.token) {
    connectionManager.onMessage = { message -> scope.launch { addServerMessage(message) } }
    connectionManager.onError = { message -> scope.launch { uploadError = message } }
    RealtimeMessageApi.getMessageHistory(
      groupId = group.id,
      token = session.token,
      onSuccess = { result ->
        scope.launch {
          val normalized = result.map { it.copy(isMe = it.senderId == session.id) }
          messages = normalized.distinctBy { it.id }
          loading = false
          error = null
          if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
        }
      },
      onError = { message -> scope.launch { error = message; loading = false } }
    )
    connectionManager.connect(group.id, session.token)
    onDispose { connectionManager.disconnect() }
  }

  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    val mime = context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase().orEmpty()
    val messageType = when {
      mime.startsWith("image/") -> "image"
      mime == "application/pdf" -> "pdf"
      mime == "text/csv" || mime == "application/vnd.ms-excel" || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "excel"
      mime.startsWith("audio/") -> "audio"
      else -> ""
    }
    if (messageType.isBlank()) {
      uploadError = "या फाइल प्रकाराचे समर्थन उपलब्ध नाही."
      return@rememberLauncherForActivityResult
    }
    val name = queryDisplayName(context, uri) ?: "attachment"
    uploading = true
    uploadError = null
    RealtimeMessageApi.uploadAttachment(
      context, session.token, group.id, uri, messageType, name,
      onSuccess = { result ->
        scope.launch {
          val sent = connectionManager.sendAttachment(result.messageType, result.attachmentKey, result.fileName, result.mimeType, result.fileSize)
          uploading = false
          if (sent == null) uploadError = "Realtime connection उपलब्ध नाही. फाइल upload झाली आहे, पण message पाठवता आला नाही."
        }
      },
      onError = { message -> scope.launch { uploading = false; uploadError = message } }
    )
  }

  Column(Modifier.fillMaxSize().background(HighDensityBackground)) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
      Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = HighDensityOnBackground) }
        Column(Modifier.weight(1f)) {
          Text(group.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("${scopeLabel(group.scope)} • ${connectionLabel(connectionState)}", fontSize = 10.sp, color = if (connectionState == ChatConnectionState.Connected) Color(0xFF16A34A) else Color(0xFF64748B))
        }
      }
    }

    if (connectionState == ChatConnectionState.Reconnecting || connectionState == ChatConnectionState.Connecting) {
      LinearProgressIndicator(Modifier.fillMaxWidth(), color = HighDensityPrimary)
    }

    if (error != null && messages.isEmpty()) {
      Surface(Modifier.fillMaxWidth().padding(12.dp), RoundedCornerShape(14.dp), color = Color(0xFFFFF7ED)) {
        Text(error.orEmpty(), Modifier.padding(14.dp), color = Color(0xFF9A3412), fontSize = 12.sp)
      }
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(7.dp),
      contentPadding = PaddingValues(vertical = 12.dp)
    ) {
      if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(26.dp), color = HighDensityPrimary) } }
      items(messages, key = { it.id }) { message -> MessageBubble(message, context, session.token) }
    }

    if (uploadError != null) {
      Text(uploadError.orEmpty(), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), color = Color(0xFFB91C1C), fontSize = 11.sp)
    }

    Surface(color = Color.White, tonalElevation = 2.dp) {
      Row(Modifier.fillMaxWidth().imePadding().padding(8.dp), verticalAlignment = Alignment.Bottom) {
        IconButton(enabled = !uploading, onClick = { picker.launch(arrayOf("image/*", "application/pdf", "text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "audio/*")) }) {
          Icon(Icons.Default.AttachFile, "Attachment", tint = HighDensityPrimary)
        }
        OutlinedTextField(
          value = input,
          onValueChange = { input = it; uploadError = null },
          modifier = Modifier.weight(1f),
          placeholder = { Text("संदेश लिहा…") },
          maxLines = 4,
          shape = RoundedCornerShape(20.dp)
        )
        IconButton(enabled = input.isNotBlank(), onClick = {
          val sent = connectionManager.sendText(input)
          if (sent == null) uploadError = "Realtime connection उपलब्ध नाही."
          else input = ""
        }) {
          Icon(Icons.Default.Send, "Send", tint = if (input.isNotBlank()) HighDensityPrimary else Color(0xFF94A3B8))
        }
      }
    }
  }
}

@Composable
private fun MessageBubble(message: GroupMessage, context: Context, token: String) {
  val mine = message.isMe
  Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = if (mine) HighDensityPrimaryContainer else Color.White,
      tonalElevation = 1.dp,
      modifier = Modifier.widthIn(max = 320.dp)
    ) {
      Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
        if (!mine) Text(message.senderName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
        when (message.messageType) {
          "text" -> if (!message.text.isNullOrBlank()) Text(message.text.orEmpty(), color = HighDensityOnBackground, fontSize = 14.sp)
          "link" -> LinkCard(message, context)
          "image" -> AttachmentCard(message, Icons.Default.Image, "Image", context, token)
          "excel" -> AttachmentCard(message, Icons.Default.Description, "Excel / CSV", context, token)
          "pdf" -> AttachmentCard(message, Icons.Default.PictureAsPdf, "PDF", context, token)
          "audio" -> AttachmentCard(message, Icons.Default.Mic, "Audio", context, token)
          else -> AttachmentCard(message, Icons.Default.AttachFile, "Attachment", context, token)
        }
        Spacer(Modifier.height(4.dp))
        Text(formatTime(message.timestamp), fontSize = 9.sp, color = Color(0xFF64748B), modifier = Modifier.align(Alignment.End))
      }
    }
  }
}

@Composable
private fun LinkCard(message: GroupMessage, context: Context) {
  TextButton(onClick = {
    val value = message.linkUrl.orEmpty()
    if (value.startsWith("http://") || value.startsWith("https://")) {
      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
    }
  }, contentPadding = PaddingValues(0.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Default.Link, null, tint = HighDensityPrimary, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(6.dp))
      Text(message.linkUrl.orEmpty(), color = HighDensityPrimary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun AttachmentCard(message: GroupMessage, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, context: Context, token: String) {
  TextButton(onClick = { downloadAndOpen(context, token, message) }, contentPadding = PaddingValues(0.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, null, tint = HighDensityPrimary, modifier = Modifier.size(24.dp))
      Spacer(Modifier.width(8.dp))
      Column {
        Text(label, fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
        Text(message.attachmentName ?: label, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

private fun downloadAndOpen(context: Context, token: String, message: GroupMessage) {
  Thread {
    try {
      val request = Request.Builder()
        .url(RealtimeMessageApi.attachmentUrl(message.groupId, message.id))
        .header("Authorization", "Bearer $token")
        .get().build()
      OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@use
        val body = response.body ?: return@use
        val extension = message.attachmentName?.substringAfterLast('.', "bin") ?: "bin"
        val file = File(context.cacheDir, "chat-${message.id}.$extension")
        FileOutputStream(file).use { output -> body.byteStream().use { input -> input.copyTo(output) } }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(uri, message.mimeType ?: "application/octet-stream")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open attachment"))
      }
    } catch (_: Exception) { }
  }.start()
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
  context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
  }
}.getOrNull()

private fun formatTime(value: String): String = value.replace('T', ' ').takeLast(8).ifBlank { "" }
private fun scopeLabel(scope: String): String = when (scope) { "system" -> "संपूर्ण प्रणाली"; "cluster" -> "केंद्र"; "school" -> "शाळा"; else -> scope }
private fun connectionLabel(state: ChatConnectionState): String = when (state) { ChatConnectionState.Connecting -> "Connecting…"; ChatConnectionState.Connected -> "Connected"; ChatConnectionState.Disconnected -> "Disconnected"; ChatConnectionState.Reconnecting -> "Reconnecting…"; ChatConnectionState.Error -> "Connection error" }
