package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.ChatGroup
import com.example.model.GroupCreateResult
import com.example.model.GroupMemberCandidate
import com.example.model.SchoolRecord
import com.example.model.UserRecord
import com.example.model.UserRole
import com.example.model.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BackendApi {
  private const val BASE_URL = "https://kp-data-sync-api.nakodesk.workers.dev"
  private const val MAX_GROUP_PHOTO_BYTES = 5 * 1024 * 1024
  private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
  private val jsonType = "application/json; charset=utf-8".toMediaType()
  @Volatile private var activeSession: UserSession? = null

  fun currentSession(): UserSession = activeSession ?: UserSession("", "", "", UserRole.Admin, token = "")

  fun login(email: String, password: String, role: String, onSuccess: (UserSession) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val body = JSONObject().apply { put("email", email); put("password", password); put("role", role) }.toString().toRequestBody(jsonType)
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/auth/login").post(body).build()).execute()
        val obj = JSONObject(response.body?.string().orEmpty())
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error", "Login failed (HTTP ${response.code})")) }; return@launch }
        val user = obj.getJSONObject("user")
        val session = UserSession(id = user.getString("id"), name = user.getString("name"), email = user.getString("email"), role = UserRole.values().first { it.roleName == user.getString("role") }, clusterName = user.optString("cluster_name").ifBlank { null }, clusterCode = user.optString("cluster_code").ifBlank { null }, schoolName = user.optString("school_name").ifBlank { null }, schoolCode = user.optString("school_code").ifBlank { null }, token = obj.getString("token"), status = user.optString("status", "active"))
        activeSession = session
        withContext(Dispatchers.Main) { onSuccess(session) }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun setupInitialAdmin(setupSecret: String, name: String, email: String, mobile: String, clusterName: String, clusterCode: String, schoolName: String, schoolCode: String, address: String, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val effectiveSecret = setupSecret.trim().ifBlank { try { com.example.BuildConfig.SETUP_SECRET.takeIf { it.isNotBlank() && it != "YOUR_SETUP_SECRET" } ?: "" } catch (_: Exception) { "" } }
        val json = JSONObject().apply { put("name", name); put("email", email); put("password", password); put("mobile_number", mobile); put("cluster_name", clusterName); put("cluster_code", clusterCode); put("school_name", schoolName); put("school_code", schoolCode); put("address", address) }
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/auth/setup-admin").addHeader("X-Setup-Secret", effectiveSecret).post(json.toString().toRequestBody(jsonType)).build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Admin registration failed (HTTP ${response.code})" }) }; return@launch }
        val created = obj.optJSONObject("user")?.optString("name").orEmpty().ifBlank { name }
        withContext(Dispatchers.Main) { onSuccess(created) }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun registerUser(token: String, name: String, email: String, mobile: String?, role: String, clusterName: String?, clusterCode: String?, schoolName: String?, schoolCode: String?, address: String?, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val json = JSONObject().apply { put("name", name); put("email", email); put("role", role); put("password", password); if (mobile != null) put("mobile_number", mobile); if (clusterName != null) put("cluster_name", clusterName); if (clusterCode != null) put("cluster_code", clusterCode); if (schoolName != null) put("school_name", schoolName); if (schoolCode != null) put("school_code", schoolCode); if (address != null) put("address", address) }
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/user/register").addHeader("Authorization", "Bearer $token").post(json.toString().toRequestBody(jsonType)).build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Registration failed (HTTP ${response.code})" }) }; return@launch }
        val created = obj.optJSONObject("data")?.optString("name").orEmpty().ifBlank { name }
        withContext(Dispatchers.Main) { onSuccess(created) }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun getUserDirectory(token: String, onSuccess: (List<GroupMemberCandidate>) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/user/directory").addHeader("Authorization", "Bearer $token").get().build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Unable to load users (HTTP ${response.code})" }) }; return@launch }
        val array = obj.optJSONArray("data") ?: JSONArray()
        val list = buildList {
          for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val role = UserRole.values().firstOrNull { it.roleName == item.optString("role") } ?: continue
            add(GroupMemberCandidate(item.optString("id"), item.optString("name"), item.optString("email"), role, item.optString("cluster_name"), item.optString("cluster_code"), item.optString("school_name"), item.optString("school_code"), item.optString("status")))
          }
        }
        withContext(Dispatchers.Main) { onSuccess(list) }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun getGroups(token: String, onSuccess: (List<ChatGroup>) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/groups").addHeader("Authorization", "Bearer $token").get().build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Unable to load groups (HTTP ${response.code})" }) }; return@launch }
        val array = obj.optJSONArray("data") ?: JSONArray()
        val list = buildList {
          for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(ChatGroup(item.optString("id"), item.optString("group_name"), "", "", time = item.optString("created_at").take(16).replace('T', ' '), scope = item.optString("scope_type", "cluster"), groupType = item.optString("group_type", "general"), memberCount = item.optInt("member_count", 0), photoKey = item.optString("photo_key").ifBlank { null }))
          }
        }
        withContext(Dispatchers.Main) { onSuccess(list) }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun createGroup(context: Context, session: UserSession, groupName: String, description: String, groupType: String, scopeType: String, clusterCode: String?, schoolCode: String?, memberIds: List<String>, photoUri: Uri?, onSuccess: (GroupCreateResult) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        var photoPart: MultipartBody.Part? = null
        if (photoUri != null) {
          val mime = context.contentResolver.getType(photoUri).orEmpty().lowercase()
          if (mime !in setOf("image/jpeg", "image/png", "image/webp")) { withContext(Dispatchers.Main) { onError("ग्रुप फोटो JPG, PNG किंवा WebP असावा.") }; return@launch }
          val bytes = context.contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
            ?: run { withContext(Dispatchers.Main) { onError("ग्रुप फोटो वाचता आला नाही.") }; return@launch }
          if (bytes.size > MAX_GROUP_PHOTO_BYTES) { withContext(Dispatchers.Main) { onError("ग्रुप फोटो 5 MB पेक्षा कमी असावा.") }; return@launch }
          photoPart = MultipartBody.Part.createFormData("photo", "group-avatar", bytes.toRequestBody(mime.toMediaType()))
        }

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
          .addFormDataPart("group_name", groupName.trim())
          .addFormDataPart("description", description.trim())
          .addFormDataPart("group_type", groupType)
          .addFormDataPart("scope_type", scopeType)
          .addFormDataPart("member_ids", JSONArray(memberIds.distinct()).toString())
        if (!clusterCode.isNullOrBlank()) builder.addFormDataPart("cluster_code", clusterCode)
        if (!schoolCode.isNullOrBlank()) builder.addFormDataPart("school_code", schoolCode)
        if (photoPart != null) builder.addPart(photoPart)

        val response = client.newCall(Request.Builder().url("$BASE_URL/api/groups").addHeader("Authorization", "Bearer ${session.token}").post(builder.build()).build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "ग्रुप तयार करता आला नाही (HTTP ${response.code})" }) }; return@launch }
        val data = obj.optJSONObject("data") ?: JSONObject()
        val result = GroupCreateResult(data.optString("id"), data.optString("group_name"), data.optString("group_type"), data.optString("scope_type"), data.optString("cluster_code").ifBlank { null }, data.optString("school_code").ifBlank { null }, data.optString("description").ifBlank { null }, data.optInt("member_count", 0), data.optString("photo_key").ifBlank { null })
        withContext(Dispatchers.Main) { onSuccess(result) }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun registerSchool(token: String, schoolName: String, udiseCode: String, clusterName: String, clusterCode: String, taluka: String, district: String, hmName: String, hmMobile: String, schoolType: String, isActive: Boolean, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val json = JSONObject().apply { put("school_name", schoolName); put("udise_code", udiseCode); put("cluster_name", clusterName); put("cluster_code", clusterCode); put("taluka", taluka); put("district", district); put("hm_name", hmName); put("hm_mobile", hmMobile); put("school_type", schoolType); put("is_active", isActive) }
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/schools/register").addHeader("Authorization", "Bearer $token").post(json.toString().toRequestBody(jsonType)).build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "School registration failed (HTTP ${response.code})" }) }; return@launch }
        val created = obj.optJSONObject("data")?.optString("school_name").orEmpty().ifBlank { schoolName }
        withContext(Dispatchers.Main) { onSuccess(created) }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun getSchools(token: String, onSuccess: (List<SchoolRecord>) -> Unit, onError: (String) -> Unit) {
    if (token.isBlank()) { onError("Session expired. Please login again."); return }
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/schools").addHeader("Authorization", "Bearer $token").get().build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Unable to load schools (HTTP ${response.code})" }) }; return@launch }
        val array = obj.optJSONArray("data") ?: JSONArray()
        val list = buildList { for (i in 0 until array.length()) { val item = array.optJSONObject(i) ?: continue; add(SchoolRecord(item.optString("id"), item.optString("school_name"), item.optString("udise_code"), item.optString("cluster_name"), item.optString("cluster_code"), item.optString("taluka"), item.optString("district"), item.optString("hm_name"), item.optString("hm_mobile"), item.optString("school_type"), item.optInt("is_active", 1) == 1)) } }
        withContext(Dispatchers.Main) { onSuccess(list) }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun updateSchool(id: String, schoolName: String, clusterName: String, clusterCode: String, taluka: String, district: String, hmName: String, hmMobile: String, schoolType: String, isActive: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val token = activeSession?.token.orEmpty()
    if (token.isBlank()) { onError("Session expired. Please login again."); return }
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val json = JSONObject().apply { put("school_name", schoolName); put("cluster_name", clusterName); put("cluster_code", clusterCode); put("taluka", taluka); put("district", district); put("hm_name", hmName); put("hm_mobile", hmMobile); put("school_type", schoolType); put("is_active", isActive) }
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/schools/$id").addHeader("Authorization", "Bearer $token").patch(json.toString().toRequestBody(jsonType)).build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Unable to update school (HTTP ${response.code})" }) }; return@launch }
        withContext(Dispatchers.Main) { onSuccess() }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun setSchoolActive(id: String, isActive: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val token = activeSession?.token.orEmpty()
    if (token.isBlank()) { onError("Session expired. Please login again."); return }
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val json = JSONObject().apply { put("is_active", isActive) }
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/schools/$id").addHeader("Authorization", "Bearer $token").patch(json.toString().toRequestBody(jsonType)).build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Unable to change school status (HTTP ${response.code})" }) }; return@launch }
        withContext(Dispatchers.Main) { onSuccess() }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  fun deleteSchool(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val token = activeSession?.token.orEmpty()
    if (token.isBlank()) { onError("Session expired. Please login again."); return }
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val response = client.newCall(Request.Builder().url("$BASE_URL/api/schools/$id").addHeader("Authorization", "Bearer $token").delete().build()).execute()
        val obj = try { JSONObject(response.body?.string().orEmpty()) } catch (_: Exception) { JSONObject() }
        if (!response.isSuccessful || !obj.optBoolean("success", false)) { withContext(Dispatchers.Main) { onError(obj.optString("error").ifBlank { "Unable to delete school (HTTP ${response.code})" }) }; return@launch }
        withContext(Dispatchers.Main) { onSuccess() }
      } catch (error: Exception) { withContext(Dispatchers.Main) { onError(networkError(error)) } }
    }
  }

  private fun networkError(error: Exception): String {
    val detail = error.message?.trim().orEmpty()
    return if (detail.isBlank()) "Network error: unable to reach the KP Data Sync server. Please check your internet connection." else "Network error: $detail"
  }
}
