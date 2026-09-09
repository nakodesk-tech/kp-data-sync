package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.NotificationApi
import com.example.model.UserRole
import com.example.model.UserSession

@Composable
fun NotificationPublishedPanel(session: UserSession, onCreate: () -> Unit) {
  var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  fun reload() {
    loading = true
    NotificationApi.list(session, onSuccess = { items = it; loading = false; error = null }, onError = { error = it; loading = false })
  }

  LaunchedEffect(session.token) { reload() }

  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Default.Public, null)
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text("Published Notifications", fontWeight = FontWeight.Black)
        Text("प्रकाशित सूचना", style = MaterialTheme.typography.bodySmall)
      }
      if (session.role == UserRole.Admin || session.role == UserRole.Cluster_Head) {
        Button(onClick = onCreate) { Text("नवीन सूचना") }
      }
    }

    when {
      loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
      items.isEmpty() -> Text("सध्या कोणतीही प्रकाशित सूचना नाही.", modifier = Modifier.padding(18.dp))
      else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        items(items, key = { it.id }) { item ->
          PublishedNotificationRow(session, item) { reload() }
        }
      }
    }
  }
}

@Composable
private fun PublishedNotificationRow(session: UserSession, item: NotificationItem, onChanged: () -> Unit) {
  var busy by remember { mutableStateOf(false) }
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Campaign, null)
        Spacer(Modifier.width(8.dp))
        Text(item.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!item.isRead) Badge { Text("NEW") }
      }
      Text(item.content, style = MaterialTheme.typography.bodyMedium)
      Text("${item.publisherName} • ${item.publishedAt ?: item.createdAt}", style = MaterialTheme.typography.labelSmall)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(enabled = !busy, onClick = {
          busy = true
          NotificationApi.markRead(session, item.id, onSuccess = { busy = false; onChanged() }, onError = { busy = false })
        }) { Text("वाचले") }
        TextButton(enabled = !busy, onClick = {
          busy = true
          NotificationApi.dismiss(session, item.id, onSuccess = { busy = false; onChanged() }, onError = { busy = false })
        }) {
          Icon(Icons.Default.Close, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(3.dp)); Text("Dismiss")
        }
      }
    }
  }
}

data class NotificationItem(
  val id: String,
  val title: String,
  val content: String,
  val publisherName: String,
  val publisherRole: String,
  val createdAt: String?,
  val publishedAt: String?,
  val scopeType: String,
  val scopeId: String?,
  val isRead: Boolean
)
