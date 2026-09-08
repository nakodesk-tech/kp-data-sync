package com.example.data

import com.example.model.UserRole
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

data class ManagedGroupMember(
  val id: String,
  val name: String,
  val email: String,
  val role: UserRole,
  val roleInGroup: String,
  val isActive: Boolean
)

object GroupMemberManagementApi {
  private const val BASE_URL = "https://kp-data-sync-api.nakodesk.workers.dev"
  private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun getMembers(token: String, groupId: String, onSuccess: (List<ManagedGroupMember>) -> Unit, onError: (String) -> Unit) {
    request(token, "GET", "/api/groups/$groupId/management/members", null, { obj ->
      val array = obj.optJSONArray("data") ?: org.json.JSONArray()
      buildList {
        for (i in 0 until array.length()) {
          val item = array.optJSONObject(i) ?: continue
          val role = UserRole.values().firstOrNull { it.roleName == item.optString("role") } ?: UserRole.Teacher
          add(ManagedGroupMember(item.optString("id"), item.optString("name"), item.optString("email"), role, item.optString("role_in_group", "member"), item.optInt("is_active", 1) == 1))
        }
      }
    }, onSuccess, onError)
  }

  fun addMember(token: String, groupId: String, userId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request(token, "POST", "/api/groups/$groupId/management/members", JSONObject().apply { put("user_id", userId) }, { }, { onSuccess() }, onError)
  }

  fun setActive(token: String, groupId: String, userId: String, active: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request(token, "PATCH", "/api/groups/$groupId/management/members/$userId", JSONObject().apply { put("is_active", active) }, { }, { onSuccess() }, onError)
  }

  fun removeMember(token: String, groupId: String, userId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request(token, "DELETE", "/api/groups/$groupId/management/members/$userId", null, { }, { onSuccess() }, onError)
  }

  fun reactivateGroup(token: String, groupId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request(token, "POST", "/api/groups/$groupId/reactivate", null, { }, { onSuccess() }, onError)
  }

  private fun <T> request(token: String, method: String, path: String, body: JSONObject?, parse: (JSONObject) -> T, onSuccess: (T) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val builder = Request.Builder().url("$BASE_URL$path").addHeader("Authorization", "Bearer $token")
        val request = when (method) {
          "POST" -> builder.post((body?.toString().orEmpty()).toRequestBody(jsonType)).build()
          "PATCH" -> builder.patch((body?.toString().orEmpty()).toRequestBody(jsonType)).build()
          "DELETE" -> builder.delete().build()
          else -> builder.get().build()
        }
        client.newCall(request).execute().use { response ->
          val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
          if (!response.isSuccessful || !obj.optBoolean("success", false)) {
            withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Group member action failed (HTTP ${response.code})" }) }
            return@use
          }
          val value = parse(obj)
          withContext(Dispatchers.Main) { onSuccess(value) }
        }
      } catch (error: Exception) {
        withContext(Dispatchers.Main) { onError(error.message?.ifBlank { "Network error" } ?: "Network error") }
      }
    }
  }
}
