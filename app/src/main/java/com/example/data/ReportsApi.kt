package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class ExcelReport(
  val id: String,
  val groupId: String,
  val groupName: String,
  val senderName: String,
  val fileName: String,
  val mimeType: String,
  val fileSize: Long?,
  val version: Int,
  val publishedAt: String?,
  val publishedBy: String?,
  val publisherRole: String = ""
)

object ReportsApi {
  private const val BASE_URL = RealtimeMessageApi.BASE_URL
  private const val MAX_UPLOAD_BYTES = 50L * 1024L * 1024L
  private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

  fun getPublishedReports(token: String, onSuccess: (List<ExcelReport>) -> Unit, onError: (String) -> Unit) {
    Thread {
      try {
        val request = Request.Builder().url("$BASE_URL/api/excel/reports").header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
          val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrElse { JSONObject() }
          if (!response.isSuccessful || !json.optBoolean("success", false)) {
            onError(json.optString("error").ifBlank { "Reports load करता आले नाहीत (HTTP ${response.code})" })
            return@use
          }
          val data = json.optJSONArray("data")
          val reports = buildList {
            if (data != null) for (i in 0 until data.length()) {
              data.optJSONObject(i)?.let { item ->
                add(ExcelReport(
                  id = item.optString("id"), groupId = item.optString("group_id"), groupName = item.optString("group_name", "Report"),
                  senderName = item.optString("sender_name", ""), fileName = item.optString("file_name", "report.xlsx"),
                  mimeType = item.optString("mime_type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                  fileSize = if (item.has("file_size") && !item.isNull("file_size")) item.optLong("file_size") else null,
                  version = item.optInt("excel_version", 1), publishedAt = item.optString("excel_published_at").ifBlank { null },
                  publishedBy = item.optString("excel_published_by").ifBlank { null }, publisherRole = item.optString("publisher_role", "")
                ))
              }
            }
          }
          onSuccess(reports)
        }
      } catch (e: Exception) {
        onError(e.message?.trim().takeUnless { it.isNullOrBlank() }?.let { "Network error: $it" } ?: "Network error: Reports serverशी जोडता आले नाही.")
      }
    }.start()
  }

  fun uploadReport(context: Context, token: String, groupId: String, uri: Uri, onSuccess: (ExcelReport) -> Unit, onError: (String) -> Unit) {
    Thread {
      try {
        val resolver = context.contentResolver
        val mimeType = (resolver.getType(uri) ?: "").lowercase().split(';')[0].trim()
        val supported = setOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv", "application/pdf")
        if (mimeType !in supported) { onError("फक्त Excel, CSV किंवा PDF Report upload करता येईल."); return@Thread }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) { onError("Report file वाचता आला नाही."); return@Thread }
        if (bytes.size.toLong() > MAX_UPLOAD_BYTES) { onError("Report file 50 MB पेक्षा मोठी असू शकत नाही."); return@Thread }
        val name = queryDisplayName(resolver, uri).ifBlank { if (mimeType == "application/pdf") "report.pdf" else "report.xlsx" }
        val body = bytes.toRequestBody(mimeType.toMediaType())
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("group_id", groupId).addFormDataPart("file", name, body).build()
        val request = Request.Builder().url("$BASE_URL/api/excel/reports/upload").header("Authorization", "Bearer $token").post(multipart).build()
        client.newCall(request).execute().use { response ->
          val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrElse { JSONObject() }
          if (!response.isSuccessful || !json.optBoolean("success", false)) { onError(json.optString("error").ifBlank { "Report upload अयशस्वी (HTTP ${response.code})" }); return@use }
          val item = json.optJSONObject("data") ?: JSONObject()
          onSuccess(ExcelReport(
            id = item.optString("id"), groupId = item.optString("group_id", groupId), groupName = item.optString("group_name", "Report"),
            senderName = item.optString("sender_name", ""), fileName = item.optString("file_name", name), mimeType = item.optString("mime_type", mimeType),
            fileSize = if (item.has("file_size") && !item.isNull("file_size")) item.optLong("file_size") else bytes.size.toLong(),
            version = item.optInt("excel_version", 1), publishedAt = item.optString("excel_published_at").ifBlank { null },
            publishedBy = item.optString("excel_published_by").ifBlank { null }, publisherRole = item.optString("publisher_role", "")
          ))
        }
      } catch (e: Exception) {
        onError(e.message?.trim().takeUnless { it.isNullOrBlank() }?.let { "Network error: $it" } ?: "Report upload failed.")
      }
    }.start()
  }

  fun deleteReport(token: String, reportId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    Thread {
      try {
        val request = Request.Builder().url("$BASE_URL/api/excel/reports/$reportId").header("Authorization", "Bearer $token").delete().build()
        client.newCall(request).execute().use { response ->
          val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrElse { JSONObject() }
          if (!response.isSuccessful || !json.optBoolean("success", false)) {
            onError(json.optString("error").ifBlank { "Report delete अयशस्वी (HTTP ${response.code})" })
            return@use
          }
          onSuccess()
        }
      } catch (e: Exception) {
        onError(e.message?.trim().takeUnless { it.isNullOrBlank() }?.let { "Network error: $it" } ?: "Report delete failed.")
      }
    }.start()
  }

  private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String = runCatching {
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }.orEmpty()
  }.getOrDefault("")

  fun downloadAndOpen(context: Context, token: String, report: ExcelReport, onError: (String) -> Unit) {
    Thread {
      try {
        val request = Request.Builder().url("$BASE_URL/api/excel/reports/${report.id}/download").header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrElse { JSONObject() }
            onError(json.optString("error").ifBlank { "Report उघडता आली नाही (HTTP ${response.code})" }); return@use
          }
          val safeName = report.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "report.xlsx" }
          val target = File(context.cacheDir, "report_${report.id}_$safeName")
          response.body?.byteStream()?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
          val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
          val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, report.mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }
          try { context.startActivity(intent) } catch (_: Exception) { onError("ही Report उघडण्यासाठी योग्य app उपलब्ध नाही.") }
        }
      } catch (e: Exception) {
        onError(e.message?.trim().takeUnless { it.isNullOrBlank() }?.let { "Network error: $it" } ?: "Report download failed.")
      }
    }.start()
  }
}
