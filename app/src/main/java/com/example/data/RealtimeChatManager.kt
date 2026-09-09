package com.example.data

import com.example.model.ChatConnectionState
import com.example.model.GroupMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RealtimeChatManager(
  private val httpClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
) {
  private val _state = MutableStateFlow(ChatConnectionState.Disconnected)
  val state: StateFlow<ChatConnectionState> = _state.asStateFlow()
  private var socket: WebSocket? = null
  private var groupId: String? = null
  private var token: String? = null
  private var intentionalClose = false
  private var reconnectAttempt = 0
  private var reconnectThread: Thread? = null
  private val knownIds = LinkedHashSet<String>()
  private val knownClientIds = LinkedHashSet<String>()

  var onMessage: ((GroupMessage) -> Unit)? = null
  var onDeleted: ((String) -> Unit)? = null
  var onError: ((String) -> Unit)? = null

  fun connect(groupId: String, token: String) { disconnect(); this.groupId = groupId; this.token = token; intentionalClose = false; reconnectAttempt = 0; connectNow() }

  private fun connectNow() {
    val currentGroup = groupId ?: return
    val currentToken = token ?: return
    _state.value = if (reconnectAttempt == 0) ChatConnectionState.Connecting else ChatConnectionState.Reconnecting
    val request = Request.Builder().url("${RealtimeMessageApi.BASE_URL.replaceFirst("https://", "wss://")}/api/messages/$currentGroup/realtime").header("Authorization", "Bearer $currentToken").build()
    socket = httpClient.newWebSocket(request, listener)
  }

  private val listener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) { reconnectAttempt = 0; _state.value = ChatConnectionState.Connecting }
    override fun onMessage(webSocket: WebSocket, text: String) {
      try {
        val root = JSONObject(text)
        when (root.optString("type")) {
          "connected" -> _state.value = ChatConnectionState.Connected
          "message" -> root.optJSONObject("data")?.let { parseIncoming(it) }
          "message_deleted" -> root.optString("message_id").takeIf { it.isNotBlank() }?.let { onDeleted?.invoke(it) }
          "group_deleted" -> onError?.invoke("हा ग्रुप बंद करण्यात आला आहे.")
          "error" -> onError?.invoke(root.optString("error", "Message error"))
        }
      } catch (_: Exception) { onError?.invoke("Invalid realtime message received.") }
    }
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { socket = null; if (intentionalClose) _state.value = ChatConnectionState.Disconnected else scheduleReconnect() }
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) { socket = null; if (intentionalClose) _state.value = ChatConnectionState.Disconnected else { onError?.invoke("Realtime connection lost."); scheduleReconnect() } }
  }

  private fun scheduleReconnect() {
    if (intentionalClose || groupId == null || token == null) return
    if (reconnectThread?.isAlive == true) return
    _state.value = ChatConnectionState.Reconnecting
    val delayMs = (2000L * (1L shl reconnectAttempt.coerceAtMost(3))).coerceAtMost(15000L)
    reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(4)
    reconnectThread = Thread { try { Thread.sleep(delayMs) } catch (_: InterruptedException) { return@Thread }; if (!intentionalClose) connectNow() }.also { it.start() }
  }

  fun sendText(text: String): String? {
    val value = text.trim(); if (value.isEmpty()) return null
    val clientId = "client-${System.currentTimeMillis()}-${UUID.randomUUID()}"
    return if (sendPayload(JSONObject().apply { put("type", "message"); put("message_type", "text"); put("content", value); put("client_message_id", clientId) })) clientId else null
  }

  fun sendAttachment(messageType: String, attachmentKey: String, fileName: String, mimeType: String, fileSize: Long?): String? {
    val clientId = "client-${System.currentTimeMillis()}-${UUID.randomUUID()}"
    return if (sendPayload(JSONObject().apply { put("type", "message"); put("message_type", messageType); put("attachment_key", attachmentKey); put("file_name", fileName); put("mime_type", mimeType); if (fileSize != null) put("file_size", fileSize); put("client_message_id", clientId) })) clientId else null
  }

  fun sendLink(url: String, content: String? = null): String? {
    val value = url.trim(); if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(value)) return null
    val clientId = "client-${System.currentTimeMillis()}-${UUID.randomUUID()}"
    return if (sendPayload(JSONObject().apply { put("type", "message"); put("message_type", "link"); put("link_url", value); if (!content.isNullOrBlank()) put("content", content.trim()); put("client_message_id", clientId) })) clientId else null
  }

  fun deleteMessage(messageId: String): Boolean = sendPayload(JSONObject().apply { put("type", "delete_message"); put("message_id", messageId) })
  private fun sendPayload(payload: JSONObject): Boolean = socket?.send(payload.toString()) == true

  private fun parseIncoming(item: JSONObject) {
    val id = item.optString("id"); val clientId = item.optString("client_message_id").ifBlank { null }; if (id.isBlank()) return
    if (!knownIds.add(id)) return
    if (clientId != null && !knownClientIds.add(clientId)) { knownIds.remove(id); return }
    if (knownIds.size > 1000) knownIds.iterator().let { it.next(); it.remove() }
    if (knownClientIds.size > 1000) knownClientIds.iterator().let { it.next(); it.remove() }
    onMessage?.invoke(GroupMessage(
      id = id, groupId = item.optString("group_id"), groupName = item.optString("group_name").ifBlank { null }, senderId = item.optString("sender_id").ifBlank { null }, senderName = item.optString("sender_name", "Unknown"), text = item.optString("content").ifBlank { null }, timestamp = item.optString("created_at"), messageType = item.optString("message_type", "text"), attachmentKey = item.optString("attachment_key").ifBlank { null }, attachmentName = item.optString("file_name").ifBlank { null }, mimeType = item.optString("mime_type").ifBlank { null }, fileSize = if (item.has("file_size") && !item.isNull("file_size")) item.optLong("file_size") else null, linkUrl = item.optString("link_url").ifBlank { null }, isDeleted = item.optInt("is_deleted", 0) == 1, isRead = item.optInt("is_read", 0) == 1, updatedAt = item.optString("updated_at").ifBlank { null }, mediaUrl = item.optString("media_url").ifBlank { null }, clientMessageId = clientId, excelStatus = item.optString("excel_status", "editable"), excelVersion = item.optInt("excel_version", 1), excelPublishedAt = item.optString("excel_published_at").ifBlank { null }, excelPublishedBy = item.optString("excel_published_by").ifBlank { null }
    ))
  }

  fun disconnect() { intentionalClose = true; reconnectThread?.interrupt(); reconnectThread = null; socket?.close(1000, "Leaving chat"); socket = null; _state.value = ChatConnectionState.Disconnected; knownIds.clear(); knownClientIds.clear() }
}
