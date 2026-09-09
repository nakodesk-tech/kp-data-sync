package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
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

  fun reportDownloadUrl(reportId: String): String = "$BASE_URL/api/excel/reports/$reportId/download"

  fun downloadAndOpen(context: Context, token: String, report: ExcelReport, onComplete: (String?) -> Unit) {
    Thread {
      try {
        val request = Request.Builder().url(reportDownloadUrl(report.id)).header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrElse { JSONObject() }
            onComplete(json.optString("error").ifBlank { "Report उघडता आली नाही (HTTP ${response.code})" }); return@use
          }
          val safeName = report.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "report.xlsx" }
          val target = File(context.cacheDir, "report_${report.id}_$safeName")
          response.body?.byteStream()?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
          val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
          val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, report.mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK) }
          try { context.startActivity(intent); onComplete(null) } catch (_: Exception) { onComplete("ही Report उघडण्यासाठी योग्य app उपलब्ध नाही.") }
        }
      } catch (e: Exception) {
        onComplete(e.message?.trim().takeUnless { it.isNullOrBlank() }?.let { "Network error: $it" } ?: "Report download failed.")
      }
    }.start()
  }
}
