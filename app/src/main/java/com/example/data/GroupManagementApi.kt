package com.example.data

import com.example.model.UserRole
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GroupInfoMember(
  val id: String,
  val name: String,
  val email: String,
  val role: UserRole?,
  val roleInGroup: String
)

data class GroupInfoData(
  val id: String,
  val name: String,
  val description: String,
  val groupType: String,
  val scopeType: String,
  val clusterCode: String?,
  val schoolCode: String?,
  val createdBy: String,
  val createdAt: String,
  val memberCount: Int,
  val members: List<GroupInfoMember>
)

object GroupManagementApi {
  private const val BASE_URL = "https://kp-data-sync-api.nakodesk.workers.dev"
  private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun getInfo(groupId: String, token: String, onSuccess: (GroupInfoData) -> Unit, onError: (String) -> Unit) {
    request("GET", "/api/groups/$groupId", token, null, onSuccess = { obj ->
      val data = obj.optJSONObject("data") ?: JSONObject()
      val members = buildList {
        val array = data.optJSONArray("members")
        if (array != null) for (i in 0 until array.length()) {
          val item = array.optJSONObject(i) ?: continue
          val role = UserRole.values().firstOrNull { it.roleName == item.optString("role") }
          add(GroupInfoMember(item.optString("id"), item.optString("name"), item.optString("email"), role, item.optString("role_in_group", "member")))
        }
      }
      onSuccess(GroupInfoData(data.optString("id"), data.optString("group_name"), data.optString("description"), data.optString("group_type"), data.optString("scope_type"), data.optString("cluster_code").ifBlank { null }, data.optString("school_code").ifBlank { null }, data.optString("created_by"), data.optString("created_at"), data.optInt("member_count", members.size), members))
    }, onError = onError)
  }

  fun update(groupId: String, token: String, name: String, description: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val body = JSONObject().apply { put("group_name", name.trim()); put("description", description.trim()) }
    request("PATCH", "/api/groups/$groupId", token, body, onSuccess = { onSuccess() }, onError = onError)
  }

  fun addMember(groupId: String, token: String, userId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val body = JSONObject().apply { put("user_id", userId) }
    request("POST", "/api/groups/$groupId/members", token, body, onSuccess = { onSuccess() }, onError = onError)
  }

  fun removeMember(groupId: String, token: String, userId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request("DELETE", "/api/groups/$groupId/members/$userId", token, null, onSuccess = { onSuccess() }, onError = onError)
  }

  fun closeGroup(groupId: String, token: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request("DELETE", "/api/groups/$groupId", token, null, onSuccess = { onSuccess() }, onError = onError)
  }

  private fun request(method: String, path: String, token: String, body: JSONObject?, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
    Thread {
      try {
        val builder = Request.Builder().url(BASE_URL + path).header("Authorization", "Bearer $token")
        if (body != null) builder.method(method, body.toString().toRequestBody(jsonType)) else builder.method(method, null)
        client.newCall(builder.build()).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
          if (!response.isSuccessful || !obj.optBoolean("success", false)) {
            onError(obj.optString("error").ifBlank { "Group action failed (HTTP ${response.code})" })
            return@use
          }
          onSuccess(obj)
        }
      } catch (e: Exception) {
        onError(e.message?.ifBlank { "Network error" } ?: "Network error")
      }
    }.start()
  }
}
