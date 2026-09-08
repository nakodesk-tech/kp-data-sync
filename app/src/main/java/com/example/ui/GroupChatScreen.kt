package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupChatScreen(group: ChatGroup, session: UserSession, onBack: () -> Unit) {
  var messages by remember(group.id) { mutableStateOf<List<GroupMessage>>(emptyList()) }
  var loading by remember(group.id) { mutableStateOf(true) }
  var error by remember(group.id) { mutableStateOf<String?>(null) }
  var input by remember(group.id) { mutableStateOf("") }
  var uploadError by remember(group.id) { mutableStateOf<String?>(null) }
  var uploading by remember(group.id) { mutableStateOf(false) }
  var selectedIds by remember(group.id) { mutableStateOf<Set<String>>(emptySet()) }
  var showMediaSheet by remember { mutableStateOf(false) }
  var showEmojiSheet by remember { mutableStateOf(false) }
  var showGroupInfo by remember { mutableStateOf(false) }
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
    connectionManager.onDeleted = { messageId -> scope.launch { messages = messages.map { if (it.id == messageId) it.copy(isDeleted = true, text = null, attachmentKey = null, attachmentName = null, mediaUrl = null) else it }; selectedIds = selectedIds - messageId } }
    connectionManager.onError = { message -> scope.launch { uploadError = message } }
    RealtimeMessageApi.getMessageHistory(group.id, session.token, onSuccess = { result -> scope.launch { messages = result.map { it.copy(isMe = it.senderId == session.id) }.distinctBy { it.id }; loading = false; error = null; if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) } }, onError = { message -> scope.launch { error = message; loading = false } })
    connectionManager.connect(group.id, session.token)
    onDispose { connectionManager.disconnect() }
  }

  fun uploadSelected(uri: Uri) {
    val mime = context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase().orEmpty()
    val messageType = when { mime.startsWith("image/") -> "image"; mime == "application/pdf" -> "pdf"; mime == "text/csv" || mime == "application/vnd.ms-excel" || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "excel"; mime.startsWith("audio/") -> "audio"; else -> "" }
    if (messageType.isBlank()) { uploadError = "या फाइल प्रकाराचे समर्थन उपलब्ध नाही."; return }
    val name = queryDisplayName(context, uri) ?: "attachment"
    uploading = true; uploadError = null
    RealtimeMessageApi.uploadAttachment(context, session.token, group.id, uri, messageType, name, onSuccess = { result -> scope.launch { val sent = connectionManager.sendAttachment(result.messageType, result.attachmentKey, result.fileName, result.mimeType, result.fileSize); uploading = false; if (sent == null) uploadError = "Realtime connection उपलब्ध नाही. फाइल upload झाली आहे, पण message पाठवता आला नाही." } }, onError = { message -> scope.launch { uploading = false; uploadError = message } })
  }

  val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) uploadSelected(uri) }
  val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) uploadSelected(uri) }
  var cameraUri by remember { mutableStateOf<Uri?>(null) }
  val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> cameraUri?.let { uri -> if (ok) uploadSelected(uri) } }

  if (showGroupInfo) { GroupInfoScreen(group, session, onBack = { showGroupInfo = false }); return }

  if (showMediaSheet) ModalBottomSheet(onDismissRequest = { showMediaSheet = false }, containerColor = Color.White, contentColor = HighDensityOnBackground, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { Box(Modifier.padding(top = 8.dp).size(width = 56.dp, height = 5.dp).background(Color(0xFFD1D5DB), RoundedCornerShape(5.dp))) }) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
      Text("मीडिया पाठवा", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = HighDensityOnBackground)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        MediaAction("Gallery", Icons.Default.Image, Color(0xFF1E88E5)) { showMediaSheet = false; galleryPicker.launch("image/*") }
        MediaAction("Camera", Icons.Default.CameraAlt, Color(0xFFE91E63)) { showMediaSheet = false; val file = File.createTempFile("chat-camera-", ".jpg", context.cacheDir); cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); cameraPicker.launch(cameraUri!!) }
        MediaAction("Emoji", Icons.Default.EmojiEmotions, Color(0xFFFBC02D)) { showMediaSheet = false; showEmojiSheet = true }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { Spacer(Modifier.width(4.dp)); MediaAction("Files", Icons.Default.Description, Color(0xFF7E57C2)) { showMediaSheet = false; filePicker.launch(arrayOf("image/*", "application/pdf", "text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "audio/*")) } }
      Spacer(Modifier.height(10.dp))
    }
  }

  if (showEmojiSheet) ModalBottomSheet(onDismissRequest = { showEmojiSheet = false }) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Text("Emoji", fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(12.dp)); val emojis = listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","🙂","🙃","😉","😌","😍","🥰","😘","😎","🤔","👍","👏","🙏","❤️","🎉","🔥","✅","⭐","📚","🏫","📌","💡","🙂","😢","😮","😡"); emojis.chunked(7).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { emoji -> Text(emoji, Modifier.clickable { input += emoji; showEmojiSheet = false }.padding(8.dp), fontSize = 26.sp) } } }; Spacer(Modifier.height(12.dp)) } }

  Column(Modifier.fillMaxSize().background(HighDensityBackground)) {
    Surface(color = Color.White, shadowElevation = 1.dp) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = HighDensityOnBackground) }; if (selectedIds.isNotEmpty()) { Text("${selectedIds.size} निवडले", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground); IconButton(onClick = { val ids = selectedIds.toList(); if (ids.any { !connectionManager.deleteMessage(it) }) uploadError = "Realtime connection उपलब्ध नाही." else selectedIds = emptySet() }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFB91C1C)) } } else { Column(Modifier.weight(1f).clickable { showGroupInfo = true }) { Text(group.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${scopeLabel(group.scope)} • ${connectionLabel(connectionState)}", fontSize = 10.sp, color = if (connectionState == ChatConnectionState.Connected) Color(0xFF16A34A) else Color(0xFF64748B)) }; IconButton(onClick = { showGroupInfo = true }) { Icon(Icons.Default.Info, "Group info", tint = HighDensityPrimary) } } } }
    if (connectionState == ChatConnectionState.Reconnecting || connectionState == ChatConnectionState.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = HighDensityPrimary)
    if (error != null && messages.isEmpty()) Surface(Modifier.fillMaxWidth().padding(12.dp), RoundedCornerShape(14.dp), color = Color(0xFFFFF7ED)) { Text(error.orEmpty(), Modifier.padding(14.dp), color = Color(0xFF9A3412), fontSize = 12.sp) }
    val firstUnreadIndex = messages.indexOfFirst { !it.isMe && !it.isRead }
    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 12.dp)) { if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(26.dp), color = HighDensityPrimary) } }; itemsIndexed(messages, key = { _, message -> message.id }) { index, message -> val date = formatDate(message.timestamp); val showDate = index == 0 || formatDate(messages[index - 1].timestamp) != date; Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { if (showDate) DateDivider(date); if (index == firstUnreadIndex) UnreadDivider(); MessageBubble(message, context, session.token, selectedIds.contains(message.id), onLongPress = { selectedIds = if (selectedIds.contains(message.id)) selectedIds - message.id else selectedIds + message.id }) } } }
    if (uploadError != null) Text(uploadError.orEmpty(), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), color = Color(0xFFB91C1C), fontSize = 11.sp)
    val density = androidx.compose.ui.platform.LocalDensity.current; val imeVisible = WindowInsets.ime.getBottom(density) > 0
    Surface(color = Color.White, tonalElevation = 2.dp, modifier = if (imeVisible) Modifier.imePadding() else Modifier.navigationBarsPadding()) { Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.Bottom) { IconButton(enabled = !uploading, onClick = { showMediaSheet = true }) { Icon(Icons.Default.Add, "Media", tint = HighDensityPrimary) }; OutlinedTextField(value = input, onValueChange = { input = it; uploadError = null }, modifier = Modifier.weight(1f), placeholder = { Text("संदेश लिहा…", color = Color(0xFF64748B)) }, maxLines = 4, shape = RoundedCornerShape(20.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color(0xFF1F2937), unfocusedTextColor = Color(0xFF1F2937), focusedPlaceholderColor = Color(0xFF64748B), unfocusedPlaceholderColor = Color(0xFF64748B), focusedBorderColor = Color(0xFF63D1B1), unfocusedBorderColor = Color(0xFF63D1B1), cursorColor = HighDensityPrimary)); IconButton(enabled = input.isNotBlank(), onClick = { val sent = connectionManager.sendText(input); if (sent == null) uploadError = "Realtime connection उपलब्ध नाही." else input = "" }) { Icon(Icons.Default.Send, "Send", tint = if (input.isNotBlank()) HighDensityPrimary else Color(0xFF94A3B8)) } } }
  }
}

@Composable private fun MediaAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) { Column(Modifier.width(100.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Surface(shape = RoundedCornerShape(32.dp), color = Color(0xFFF1F3F5)) { Box(Modifier.size(width = 96.dp, height = 64.dp), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint, modifier = Modifier.size(32.dp)) } }; Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HighDensityOnBackground) } }
@Composable private fun DateDivider(date: String) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f)); Surface(Modifier.padding(horizontal = 8.dp), RoundedCornerShape(10.dp), color = HighDensityPrimaryContainer) { Text(date, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 9.sp, color = HighDensityPrimary, fontWeight = FontWeight.Bold) }; HorizontalDivider(Modifier.weight(1f)) } }
@Composable private fun UnreadDivider() { Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f), color = HighDensityPrimary); Text("  न वाचलेले संदेश  ", fontSize = 9.sp, color = HighDensityPrimary, fontWeight = FontWeight.Bold); HorizontalDivider(Modifier.weight(1f), color = HighDensityPrimary) } }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun MessageBubble(message: GroupMessage, context: Context, token: String, selected: Boolean, onLongPress: () -> Unit) { val mine = message.isMe; Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) { Surface(shape = RoundedCornerShape(16.dp), color = if (selected) HighDensityPrimaryContainer.copy(alpha = 0.65f) else if (mine) HighDensityPrimaryContainer else Color.White, tonalElevation = 1.dp, modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = { if (selected) onLongPress() }, onLongClick = onLongPress)) { Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) { if (!mine) Text(message.senderName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary); if (message.isDeleted) Text("हा संदेश हटविला आहे.", color = Color(0xFF94A3B8), fontSize = 13.sp) else when (message.messageType) { "text" -> if (!message.text.isNullOrBlank()) Text(message.text.orEmpty(), color = HighDensityOnBackground, fontSize = 14.sp); "link" -> LinkCard(message, context); "image", "pdf" -> AttachmentPreviewCard(message, context, token); "excel" -> AttachmentCard(message, Icons.Default.Description, "Excel / CSV", context, token); "audio" -> AttachmentCard(message, Icons.Default.Mic, "Audio", context, token); else -> AttachmentCard(message, Icons.Default.AttachFile, "Attachment", context, token) }; Spacer(Modifier.height(4.dp)); Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) { Text(formatDateTime(message.timestamp), fontSize = 9.sp, color = Color(0xFF64748B)); if (mine) { Spacer(Modifier.width(4.dp)); Text(if (message.isRead) "✓✓" else "✓", fontSize = 10.sp, color = if (message.isRead) HighDensityPrimary else Color(0xFF64748B), fontWeight = FontWeight.Bold) } } } } } }

@Composable private fun AttachmentPreviewCard(message: GroupMessage, context: Context, token: String) {
  val fileName = message.attachmentName ?: if (message.messageType == "pdf") "document.pdf" else "image"
  val auto = MediaAutoDownloadSettings.shouldAutoDownload(context, message.messageType)
  var file by remember(message.id, auto) { mutableStateOf<File?>(null) }
  LaunchedEffect(message.id, auto) { if (auto) file = downloadAttachmentPreview(context, token, message.id, message.groupId, fileName) }
  if (message.messageType == "image" && file != null) {
    val bitmap = remember(file) { BitmapFactory.decodeFile(file!!.absolutePath) }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = fileName, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)).clickable { downloadAndOpen(context, token, message) }, contentScale = ContentScale.Crop) else AttachmentCard(message, Icons.Default.Image, "Image", context, token)
  } else if (message.messageType == "pdf" && file != null) {
    val bitmap = remember(file) { renderPdfFirstPage(file!!) }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "PDF preview", modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)).clickable { downloadAndOpen(context, token, message) }, contentScale = ContentScale.Crop) else AttachmentCard(message, Icons.Default.PictureAsPdf, "PDF", context, token)
  } else {
    AttachmentCard(message, if (message.messageType == "pdf") Icons.Default.PictureAsPdf else Icons.Default.Image, if (message.messageType == "pdf") "PDF preview" else "Image", context, token)
  }
}

@Composable private fun LinkCard(message: GroupMessage, context: Context) { TextButton(onClick = { val value = message.linkUrl.orEmpty(); if (value.startsWith("http://") || value.startsWith("https://")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }, contentPadding = PaddingValues(0.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Link, null, tint = HighDensityPrimary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(message.linkUrl.orEmpty(), color = HighDensityPrimary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) } } }
@Composable private fun AttachmentCard(message: GroupMessage, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, context: Context, token: String) { TextButton(onClick = { downloadAndOpen(context, token, message) }, contentPadding = PaddingValues(0.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = HighDensityPrimary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Column { Text(label, fontWeight = FontWeight.Bold, color = HighDensityOnBackground); Text(message.attachmentName ?: label, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }

private fun renderPdfFirstPage(file: File): android.graphics.Bitmap? = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { renderer -> if (renderer.pageCount == 0) null else renderer.openPage(0).use { page -> val width = 900; val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1); android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap -> page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) } } } } }.getOrNull()

private fun downloadAndOpen(context: Context, token: String, message: GroupMessage) { Thread { try { val request = Request.Builder().url(RealtimeMessageApi.attachmentUrl(message.groupId, message.id)).header("Authorization", "Bearer $token").get().build(); OkHttpClient().newCall(request).execute().use { response -> if (!response.isSuccessful) return@use; val body = response.body ?: return@use; val extension = message.attachmentName?.substringAfterLast('.', "bin") ?: "bin"; val file = File(context.cacheDir, "chat-${message.id}.$extension"); FileOutputStream(file).use { output -> body.byteStream().use { input -> input.copyTo(output) } }; val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, message.mimeType ?: "application/octet-stream"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(intent, "Open attachment")) } } catch (_: Exception) { } }.start() }
private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching { context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } }.getOrNull()
private fun formatDateTime(value: String): String = if (value.length >= 16) "${formatDate(value)} ${value.substring(11, 16)}" else value.replace('T', ' ').take(16)
private fun formatDate(value: String): String = runCatching { val d = value.substring(0, 10).split('-'); "${d[2]}-${d[1]}-${d[0]}" }.getOrElse { value.take(10) }
private fun scopeLabel(scope: String): String = when (scope) { "system" -> "संपूर्ण प्रणाली"; "cluster" -> "केंद्र"; "school" -> "शाळा"; else -> scope }
private fun connectionLabel(state: ChatConnectionState): String = when (state) { ChatConnectionState.Connecting -> "Connecting…"; ChatConnectionState.Connected -> "Connected"; ChatConnectionState.Disconnected -> "Disconnected"; ChatConnectionState.Reconnecting -> "Reconnecting…"; ChatConnectionState.Error -> "Connection error" }
