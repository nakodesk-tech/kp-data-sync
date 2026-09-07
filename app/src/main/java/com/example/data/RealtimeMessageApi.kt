package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.AttachmentUploadResult
import com.example.model.GroupMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RealtimeMessageApi {
  const val BASE_URL = "https://kp-data-sync-api.nakodesk.workers.dev"
  const val MAX_ATTACHMENT_BYTES = 50L * 1024L * 1024L

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

  fun getMessageHistory(
    groupId: String,
    token: String,
    limit: Int = 50,
    before: String? = null,
    onSuccess: (List<GroupMessage>) -> Unit,
    onError: (String) -> Unit
  ) {
    Thread {
      try {
        val query = buildString {
          append("?limit=")
          append(limit.coerceIn(1, 100))
          if (!before.isNullOrBlank()) {
            append("&before=")
            append(java.net.URLEncoder.encode(before, "UTF-8"))
          }
        }
        val request = Request.Builder()
          .url("$BASE_URL/api/messages/$groupId$query")
          .header("Authorization", "Bearer $token")
          .get()
          .build()
        client.newCall(request).execute().use { response ->
          val body = response.body?.string().orEmpty()
          val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
          if (!response.isSuccessful || !json.optBoolean("success", false)) {
            onError(json.optString("error").ifBlank { "Unable to load messages (HTTP ${response.code})" })
            return@use
          }
          val data = json.optJSONArray("data")
          val result = buildList {
            if (data != null) for (i in 0 until data.length()) {
              data.optJSONObject(i)?.let { add(parseMessage(it, token)) }
            }
          }
          onSuccess(result)
        }
      } catch (e: Exception) {
        onError(networkError(e))
      }
    }.start()
  }

  fun uploadAttachment(
    context: Context,
    token: String,
    groupId: String,
    uri: Uri,
    messageType: String,
    fileName: String,
    onSuccess: (AttachmentUploadResult) -> Unit,
    onError: (String) -> Unit
  ) {
    Thread {
      try {
        val mime = context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase()
          ?: "application/octet-stream"
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
          ?: -1L
        if (size > MAX_ATTACHMENT_BYTES) {
          onError("फाइल 50 MB पेक्षा कमी असावी.")
          return@Thread
        }
        val body = object : RequestBody() {
          override fun contentType() = mime.toMediaType()
          override fun contentLength() = size
          override fun writeTo(sink: BufferedSink) {
            context.contentResolver.openInputStream(uri)?.use { input ->
              sink.writeAll(input.source())
            } ?: throw IllegalStateException("फाइल वाचता आली नाही.")
          }
        }
        val request = Request.Builder()
          .url("$BASE_URL/api/messages/$groupId/attachment")
          .header("Authorization", "Bearer $token")
          .header("X-Message-Type", messageType)
          .header("X-File-Name", fileName.take(240))
          .post(body)
          .build()
        client.newCall(request).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
          if (!response.isSuccessful || !json.optBoolean("success", false)) {
            onError(json.optString("error").ifBlank { "Attachment upload failed (HTTP ${response.code})" })
            return@use
          }
          val data = json.optJSONObject("data") ?: JSONObject()
          onSuccess(
            AttachmentUploadResult(
              attachmentKey = data.optString("attachment_key"),
              fileName = data.optString("file_name", fileName),
              mimeType = data.optString("mime_type", mime),
              fileSize = if (data.has("file_size") && !data.isNull("file_size")) data.optLong("file_size") else null,
              messageType = data.optString("message_type", messageType)
            )
          )
        }
      } catch (e: Exception) {
        onError(networkError(e))
      }
    }.start()
  }

  fun attachmentUrl(groupId: String, messageId: String): String =
    "$BASE_URL/api/messages/$groupId/attachment/$messageId"

  private fun parseMessage(item: JSONObject, token: String): GroupMessage {
    val senderId = item.optString("sender_id").ifBlank { null }
    return GroupMessage(
      id = item.optString("id"),
      groupId = item.optString("group_id"),
      groupName = item.optString("group_name").ifBlank { null },
      senderId = senderId,
      senderName = item.optString("sender_name", "Unknown"),
      text = item.optString("content").ifBlank { null },
      timestamp = item.optString("created_at"),
      messageType = item.optString("message_type", "text"),
      attachmentKey = item.optString("attachment_key").ifBlank { null },
      attachmentName = item.optString("file_name").ifBlank { null },
      mimeType = item.optString("mime_type").ifBlank { null },
      fileSize = if (item.has("file_size") && !item.isNull("file_size")) item.optLong("file_size") else null,
      linkUrl = item.optString("link_url").ifBlank { null },
      isDeleted = item.optInt("is_deleted", 0) == 1,
      isRead = item.optInt("is_read", 0) == 1,
      updatedAt = item.optString("updated_at").ifBlank { null },
      mediaUrl = item.optString("media_url").ifBlank { null },
      clientMessageId = item.optString("client_message_id").ifBlank { null },
      isMe = false
    )
  }

  private fun networkError(e: Exception): String {
    val detail = e.message?.trim().orEmpty()
    return if (detail.isBlank()) "Network error: unable to reach the KP Data Sync server." else "Network error: $detail"
  }
}
