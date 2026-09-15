package com.example.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import com.example.data.RealtimeMessageApi
import com.example.model.GroupMessage
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

private val newExcelEditorClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
private val newExcelEditorMain = Handler(Looper.getMainLooper())

private fun downloadWorkbookForNewEditor(context: Context, token: String, message: GroupMessage, onSuccess: (File) -> Unit, onError: (String) -> Unit) {
    Thread {
        try {
            val request = Request.Builder().url(RealtimeMessageApi.attachmentUrl(message.groupId, message.id)).header("Authorization", "Bearer $token").get().build()
            newExcelEditorClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) { newExcelEditorMain.post { onError("Excel download अयशस्वी (HTTP ${response.code})") }; return@use }
                val body = response.body ?: run { newExcelEditorMain.post { onError("Excel file रिकामी आहे.") }; return@use }
                val file = File.createTempFile("kp_excel_editor_", ".xlsx", context.cacheDir)
                body.byteStream().use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
                newExcelEditorMain.post { onSuccess(file) }
            }
        } catch (e: Exception) { newExcelEditorMain.post { onError(e.message?.trim().takeUnless { it.isNullOrBlank() } ?: "Excel उघडता आली नाही.") } }
    }.start()
}

@Composable
fun InAppExcelEditorScreen(message: GroupMessage, token: String, canPublish: Boolean, onBack: () -> Unit, onPublished: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var workbook by remember(message.id, message.excelVersion) { mutableStateOf<InAppXlsxWorkbook?>(null) }
    var loading by remember(message.id, message.excelVersion) { mutableStateOf(true) }
    var error by remember(message.id, message.excelVersion) { mutableStateOf<String?>(null) }

    LaunchedEffect(message.id, message.excelVersion) {
        loading = true; error = null
        downloadWorkbookForNewEditor(context, token, message,
            onSuccess = { file ->
                InAppXlsxWorkbook.load(file).onSuccess { workbook = it; loading = false }.onFailure { loading = false; error = "Excel वाचता आली नाही: ${it.message}" }
                file.delete()
            },
            onError = { loading = false; error = it }
        )
    }

    when {
        loading -> Column(Modifier.fillMaxSize().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Excel उघडत आहे…") }
        error != null -> Column(Modifier.fillMaxSize().background(Color.White), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(error.orEmpty()) }
        workbook != null -> InAppExcelEditorV2Screen(workbook = workbook!!, fileName = message.attachmentName ?: "Schools.xlsx", onBack = onBack) { editedFile ->
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", editedFile)
            RealtimeMessageApi.saveExcel(context, token, message.groupId, message.id, uri, message.excelVersion,
                onSuccess = { _, _ -> editedFile.delete(); onBack() },
                onError = { editedFile.delete() }
            )
        }
    }
}
