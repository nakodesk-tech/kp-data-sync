package com.example.data

import com.example.model.UserRecord
import com.example.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UserDirectoryApi {
  private const val BASE_URL = "https://kp-data-sync-api.nakodesk.workers.dev"
  private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()
  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun getUsers(token: String, onSuccess: (List<UserRecord>) -> Unit, onError: (String) -> Unit) {
    request(token, "GET", "/api/user/directory", null, onSuccess = { obj ->
      val array = obj.optJSONArray("data") ?: JSONArray()
      val list = buildList {
        for (i in 0 until array.length()) {
          val item = array.optJSONObject(i) ?: continue
          val role = UserRole.values().firstOrNull { it.roleName == item.optString("role") } ?: continue
          add(UserRecord(item.optString("id"), item.optString("name"), item.optString("email"), item.optString("mobile_number"), role, item.optString("cluster_name"), item.optString("cluster_code"), item.optString("school_name"), item.optString("school_code"), item.optString("address"), item.optString("status"), item.optString("created_at")))
        }
      }
      onSuccess(list)
    }, onError = onError)
  }

  fun updateUser(token: String, id: String, name: String, mobile: String, address: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val body = JSONObject().apply { put("name", name); put("mobile_number", mobile); put("address", address) }.toString()
    request(token, "PATCH", "/api/user/$id", body, onSuccess = { onSuccess() }, onError = onError)
  }

  fun setUserStatus(token: String, id: String, active: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val body = JSONObject().apply { put("status", if (active) "Active" else "Inactive") }.toString()
    request(token, "PATCH", "/api/user/$id", body, onSuccess = { onSuccess() }, onError = onError)
  }

  fun deleteUser(token: String, id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    request(token, "DELETE", "/api/user/$id", null, onSuccess = { onSuccess() }, onError = onError)
  }

  private fun request(token: String, method: String, path: String, body: String?, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val builder = Request.Builder().url(BASE_URL + path).addHeader("Authorization", "Bearer $token")
        when (method) {
          "GET" -> builder.get()
          "POST" -> builder.post((body ?: "{}").toRequestBody(jsonType))
          "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonType))
          "DELETE" -> builder.delete()
        }
        val response = client.newCall(builder.build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) {
          withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "User management request failed (HTTP ${response.code})" }) }
          return@launch
        }
        withContext(Dispatchers.Main) { onSuccess(obj) }
      } catch (error: Exception) {
        withContext(Dispatchers.Main) { onError("Network error: ${error.message ?: "unable to reach server"}") }
      }
    }
  }
}
