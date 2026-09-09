package com.example.data

import com.example.model.UserSession
import com.example.ui.NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NotificationApi {
  private const val BASE_URL = "https://kp-data-sync-api.nakodesk.workers.dev"
  private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun createDraft(session: UserSession, title: String, content: String, scopeType: String, scopeId: String?, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    request(session.token, "POST", "/api/notifications", JSONObject().apply {
      put("title", title.trim()); put("content", content.trim()); put("scope_type", scopeType)
      if (!scopeId.isNullOrBlank()) put("scope_id", scopeId.trim())
    }, onSuccess = { onSuccess(it.optJSONObject("data")?.optString("id").orEmpty()) }, onError)
  }

  fun publish(session: UserSession, notificationId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request(session.token, "POST", "/api/notifications/$notificationId/publish", null, onSuccess = { onSuccess() }, onError)
  }

  fun list(session: UserSession, onSuccess: (List<NotificationItem>) -> Unit, onError: (String) -> Unit) {
    request(session.token, "GET", "/api/notifications", null, onSuccess = { obj ->
      val array = obj.optJSONArray("data")
      val result = buildList {
        if (array != null) for (i in 0 until array.length()) {
          val n = array.optJSONObject(i) ?: continue
          add(NotificationItem(n.optString("id"), n.optString("title"), n.optString("content"), n.optString("publisher_name"), n.optString("publisher_role"), n.optString("created_at").ifBlank { null }, n.optString("published_at").ifBlank { null }, n.optString("scope_type"), n.optString("scope_id").ifBlank { null }, n.optInt("is_read", 0) == 1))
        }
      }
      onSuccess(result)
    }, onError)
  }

  fun markRead(session: UserSession, notificationId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request(session.token, "POST", "/api/notifications/$notificationId/read", null, onSuccess = { onSuccess() }, onError)
  }

  fun dismiss(session: UserSession, notificationId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request(session.token, "POST", "/api/notifications/$notificationId/dismiss", null, onSuccess = { onSuccess() }, onError)
  }

  private fun request(token: String, method: String, path: String, body: JSONObject?, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val builder = Request.Builder().url("$BASE_URL$path").addHeader("Authorization", "Bearer $token")
        val request = when (method) {
          "POST" -> builder.post((body?.toString().orEmpty()).toRequestBody(jsonType)).build()
          else -> builder.get().build()
        }
        client.newCall(request).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
          if (!response.isSuccessful || !obj.optBoolean("success", false)) {
            withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Notification action failed (HTTP ${response.code})" }) }
            return@use
          }
          withContext(Dispatchers.Main) { onSuccess(obj) }
        }
      } catch (e: Exception) { withContext(Dispatchers.Main) { onError(e.message ?: "Network error") } }
    }
  }
}
