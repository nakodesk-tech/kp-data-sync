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
import java.util.concurrent.TimeUnit

object BackendApi {
  private const val BASE_URL = "https://kp-data-sync-backend.workers.dev"
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .build()
  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun login(email: String, password: String, role: String, onSuccess: (UserSession) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val body = JSONObject().apply { put("email", email); put("password", password); put("role", role) }.toString().toRequestBody(jsonType)
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/auth/login").post(body).build()).execute()
        val obj = JSONObject(response.body?.string().orEmpty())
        if (!response.isSuccessful || !obj.optBoolean("success", false)) {
          withContext(Dispatchers.Main) { onError(obj.optString("error", "Login failed (HTTP ${response.code})")) }
          return@launch
        }
        val user = obj.getJSONObject("user")
        val session = UserSession(
          id = user.getString("id"), name = user.getString("name"), email = user.getString("email"),
          role = UserRole.values().first { it.roleName == user.getString("role") },
          clusterName = user.optString("cluster_name").ifBlank { null },
          clusterCode = user.optString("cluster_code").ifBlank { null },
          schoolName = user.optString("school_name").ifBlank { null },
          schoolCode = user.optString("school_code").ifBlank { null },
          token = obj.getString("token"), status = user.optString("status", "active")
        )
        withContext(Dispatchers.Main) { onSuccess(session) }
      } catch (error: Exception) {
        withContext(Dispatchers.Main) { onError(networkError(error)) }
      }
    }
  }

  fun setupInitialAdmin(setupSecret: String, name: String, email: String, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val json = JSONObject().apply { put("name", name); put("email", email); put("password", password) }
        val response = client.newCall(
          Request.Builder()
            .url("$BASE_URL/api/auth/setup-admin")
            .addHeader("X-Setup-Secret", setupSecret.trim())
            .post(json.toString().toRequestBody(jsonType))
            .build()
        ).execute()
        val raw = response.body?.string().orEmpty()
        val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) {
          val serverMessage = obj.optString("error").ifBlank { "Admin registration failed (HTTP ${response.code})" }
          withContext(Dispatchers.Main) { onError(serverMessage) }
          return@launch
        }
        val created = obj.optJSONObject("user")?.optString("name").orEmpty().ifBlank { name }
        withContext(Dispatchers.Main) { onSuccess(created) }
      } catch (error: Exception) {
        withContext(Dispatchers.Main) { onError(networkError(error)) }
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
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/user/register").addHeader("Authorization", "Bearer $token").post(json.toString().toRequestBody(jsonType)).build()).execute()
        val obj = JSONObject(response.body?.string().orEmpty())
        if (!response.isSuccessful || !obj.optBoolean("success", false)) {
          withContext(Dispatchers.Main) { onError(obj.optString("error", "Registration failed (HTTP ${response.code})")) }
          return@launch
        }
        val created = obj.optJSONObject("data")?.optString("name").orEmpty().ifBlank { name }
        withContext(Dispatchers.Main) { onSuccess(created) }
      } catch (error: Exception) {
        withContext(Dispatchers.Main) { onError(networkError(error)) }
      }
    }
  }

  private fun networkError(error: Exception): String {
    val detail = error.message?.trim().orEmpty()
    return if (detail.isBlank()) {
      "Network error: unable to reach the KP Data Sync server. Please check your internet connection."
    } else {
      "Network error: $detail"
    }
  }
}
