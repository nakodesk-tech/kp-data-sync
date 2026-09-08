package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RealtimeMessageApi
import com.example.ui.theme.HighDensityOnBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object MediaAutoDownloadSettings {
  private const val PREFS = "media_auto_download"
  private const val MOBILE = "mobile"
  private const val WIFI = "wifi"
  private const val ROAMING = "roaming"

  const val NONE = 0
  const val IMAGES = 1
  const val IMAGES_AND_FILES = 2

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun get(context: Context, network: String): Int = prefs(context).getInt(network, NONE)
  fun set(context: Context, network: String, value: Int) { prefs(context).edit().putInt(network, value).apply() }

  fun currentNetwork(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return MOBILE
    val caps = cm.getNetworkCapabilities(network) ?: return MOBILE
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return WIFI
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
      if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) return ROAMING
    }
    return MOBILE
  }

  fun shouldAutoDownload(context: Context, messageType: String): Boolean {
    val mode = get(context, currentNetwork())
    return when {
      mode == IMAGES_AND_FILES -> true
      mode == IMAGES -> messageType == "image"
      else -> false
    }
  }

  fun label(value: Int): String = when (value) {
    IMAGES -> "Images"
    IMAGES_AND_FILES -> "Images + Files"
    else -> "No media"
  }
}

@Composable
fun MediaAutoDownloadDialog(onDismiss: () -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var mobile by remember { mutableStateOf(MediaAutoDownloadSettings.get(context, "mobile")) }
  var wifi by remember { mutableStateOf(MediaAutoDownloadSettings.get(context, "wifi")) }
  var roaming by remember { mutableStateOf(MediaAutoDownloadSettings.get(context, "roaming")) }

  fun next(value: Int) = (value + 1) % 3
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Media auto-download") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Images आणि files आपोआप डाउनलोड करण्याची निवड करा.", fontSize = 12.sp, color = Color(0xFF64748B))
        Spacer(Modifier.height(10.dp))
        MediaDownloadRow("When using mobile data", mobile) { mobile = next(mobile) }
        MediaDownloadRow("When connected on Wi-Fi", wifi) { wifi = next(wifi) }
        MediaDownloadRow("When roaming", roaming) { roaming = next(roaming) }
        Spacer(Modifier.height(4.dp))
        Text("No media → Images → Images + Files", fontSize = 10.sp, color = Color(0xFF94A3B8))
      }
    },
    confirmButton = {
      TextButton(onClick = {
        MediaAutoDownloadSettings.set(context, "mobile", mobile)
        MediaAutoDownloadSettings.set(context, "wifi", wifi)
        MediaAutoDownloadSettings.set(context, "roaming", roaming)
        onDismiss()
      }) { Text("सेव्ह") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("रद्द") } }
  )
}

@Composable
private fun MediaDownloadRow(title: String, value: Int, onClick: () -> Unit) {
  Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
    Text(title, fontSize = 15.sp, color = HighDensityOnBackground, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Text(MediaAutoDownloadSettings.label(value), fontSize = 14.sp, color = Color(0xFF94A3B8))
  }
}

private val previewClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

suspend fun downloadAttachmentPreview(context: Context, token: String, messageId: String, groupId: String, fileName: String): File? = withContext(Dispatchers.IO) {
  try {
    val extension = fileName.substringAfterLast('.', "bin")
    val file = File(context.cacheDir, "preview-$messageId.$extension")
    if (!file.exists() || file.length() == 0L) {
      val request = Request.Builder().url(RealtimeMessageApi.attachmentUrl(groupId, messageId)).header("Authorization", "Bearer $token").get().build()
      previewClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@withContext null
        val body = response.body ?: return@withContext null
        FileOutputStream(file).use { output -> body.byteStream().use { input -> input.copyTo(output) } }
      }
    }
    file
  } catch (_: Exception) { null }
}
