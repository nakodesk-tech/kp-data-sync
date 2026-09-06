package com.example.data

import com.example.model.UserRole
import com.example.model.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object BackendApi {
  private const val BASE_URL = "https://kp-data-sync-backend.workers.dev"
  private val client = OkHttpClient()
  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun login(email: String, password: String, role: String, onSuccess: (UserSession) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val body = JSONObject().apply {
          put("email", email)
          put("password", password)
          put("role", role)
        }.toString().toRequestBody(jsonType)
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/auth/login").post(body).build()).execute()
        val raw = response.body?.string().orEmpty()
        val obj = JSONObject(raw)
        if (!response.isSuccessful || !obj.optBoolean("success", false)) {
          withContext(Dispatchers.Main) { onError(obj.optString("error", "Login failed")) }
          return@launch
        }
        val user = obj.getJSONObject("user")
        val session = UserSession(
          id = user.getString("id"), name = user.getString("name"), email = user.getString("email"),
          role = UserRole.values().first { it.roleName == user.getString("role") },
          clusterName = user.optString("cluster_name").ifBlank { null },
          schoolName = user.optString("school_name").ifBlank { null },
          token = obj.getString("token"), status = user.optString("status", "active")
        )
        withContext(Dispatchers.Main) { onSuccess(session) }
      } catch (_: Exception) {
        withContext(Dispatchers.Main) { onError("Unable to reach the server. Please check your internet connection.") }
      }
    }
  }

  fun registerUser(token: String, name: String, email: String, mobile: String?, role: String, clusterName: String?, clusterCode: String?, schoolName: String?, schoolCode: String?, address: String?, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val json = JSONObject().apply {
          put("name", name); put("email", email); put("role", role); put("password", password)
          if (mobile != null) put("mobile_number", mobile)
          if (clusterName != null) put("cluster_name", clusterName)
          if (clusterCode != null) put("cluster_code", clusterCode)
          if (schoolName != null) put("school_name", schoolName)
          if (schoolCode != null) put("school_code", schoolCode)
          if (address != null) put("address", address)
        }
        val response = client.newCall(
          Request.Builder().url("$BASE_URL/api/user/register").addHeader("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody(jsonType)).build()
        ).execute()
        val raw = response.body?.string().orEmpty()
        val obj = JSONObject(raw)
        if (!response.isSuccessful || !obj.optBoolean("success", false)) {
          withContext(Dispatchers.Main) { onError(obj.optString("error", "Registration failed")) }
          return@launch
        }
        val created = obj.optJSONObject("data")?.optString("name").orEmpty().ifBlank { name }
        withContext(Dispatchers.Main) { onSuccess(created) }
      } catch (_: Exception) {
        withContext(Dispatchers.Main) { onError("Unable to reach the server. Please try again.") }
      }
    }
  }
}
