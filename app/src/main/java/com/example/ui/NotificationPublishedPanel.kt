package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NotificationApi
import com.example.model.UserRole
import com.example.model.UserSession

@Composable
fun NotificationPublishedPanel(session: UserSession, onCreate: () -> Unit) {
  val canManage = session.role == UserRole.Admin || session.role == UserRole.Cluster_Head
  var published by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
  var drafts by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var selectedTab by remember { mutableStateOf(0) }
  var opened by remember { mutableStateOf<NotificationItem?>(null) }

  fun reload() {
    loading = true
    error = null
    NotificationApi.list(
      session,
      onSuccess = { published = it; loading = false },
      onError = { error = it; loading = false }
    )
    if (canManage) {
      NotificationApi.listDrafts(
        session,
        onSuccess = { drafts = it },
        onError = { }
      )
    }
  }

  LaunchedEffect(session.token) { reload() }

  if (opened != null) {
    NotificationDetail(
      session = session,
      item = opened!!,
      onBack = {
        opened = null
        reload()
      }
    )
    return
  }

  val visibleItems = if (canManage) {
    if (selectedTab == 0) published else drafts
  } else {
    if (selectedTab == 0) published.filter { !it.isRead } else published.filter { it.isRead }
  }

  Column(
    Modifier.fillMaxSize().padding(horizontal = 16.dp)
  ) {
    Row(
      Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(36.dp),
        color = Color(0xFFDDF7EC)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(Icons.Default.Notifications, null, tint = Color(0xFF008C68), modifier = Modifier.size(38.dp))
        }
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text("Published Notifications", fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("प्रकाशित सूचना", fontWeight = FontWeight.Bold)
      }
    }

    if (canManage) {
      Button(
        onClick = onCreate,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(vertical = 13.dp, horizontal = 18.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
      ) {
        Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(7.dp))
        Text("नवीन सूचना तयार करा", fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
      }
    }

    Row(
      Modifier.fillMaxWidth().padding(bottom = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (canManage) {
        NotificationTab(Modifier.weight(1f), selectedTab == 0, Icons.Default.Notifications, "Published Notifications", published.size) { selectedTab = 0 }
        NotificationTab(Modifier.weight(1f), selectedTab == 1, Icons.Default.Description, "Draft Notifications", drafts.size) { selectedTab = 1 }
      } else {
        NotificationTab(Modifier.weight(1f), selectedTab == 0, Icons.Default.Notifications, "Published Notifications", published.count { !it.isRead }) { selectedTab = 0 }
        NotificationTab(Modifier.weight(1f), selectedTab == 1, Icons.Default.Description, "वाचलेल्या Notifications", published.count { it.isRead }) { selectedTab = 1 }
      }
    }

    when {
      loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
      visibleItems.isEmpty() -> EmptyNotificationState(
        if (canManage && selectedTab == 1) "सध्या कोणत्याही Draft सूचना उपलब्ध नाहीत."
        else if (!canManage && selectedTab == 1) "अद्याप कोणतीही सूचना वाचलेली नाही."
        else "सध्या कोणतीही नवीन प्रकाशित सूचना नाही."
      )
      else -> LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
      ) {
        items(visibleItems, key = { it.id }) { item ->
          PublishedNotificationRow(
            session = session,
            item = item,
            isDraft = canManage && selectedTab == 1,
            onOpen = { opened = item },
            onChanged = { reload() }
          )
        }
      }
    }
  }
}

@Composable
private fun NotificationTab(
  modifier: Modifier,
  selected: Boolean,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  count: Int,
  onClick: () -> Unit
) {
  Surface(
    modifier = modifier,
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = if (selected) Color(0xFFE5F9F0) else Color(0xFFF0F3F9),
    border = if (selected) BorderStroke(1.dp, Color(0xFFBFECDD)) else null
  ) {
    Row(
      Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 8.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(icon, null, tint = if (selected) Color(0xFF008C68) else Color(0xFF5E6875), modifier = Modifier.size(23.dp))
      Spacer(Modifier.width(5.dp))
      Text(
        title,
        color = if (selected) Color(0xFF008C68) else Color(0xFF5E6875),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      if (count > 0) {
        Spacer(Modifier.width(5.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = if (selected) Color(0xFF008C68) else Color(0xFFD5DCE4)) {
          Text(
            count.toString(),
            color = if (selected) Color.White else Color(0xFF4F5965),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun PublishedNotificationRow(
  session: UserSession,
  item: NotificationItem,
  isDraft: Boolean,
  onOpen: () -> Unit,
  onChanged: () -> Unit
) {
  var busy by remember { mutableStateOf(false) }
  var audienceType by remember { mutableStateOf<String?>(null) }
  val canViewAudience = session.role == UserRole.Admin ||
    (session.role == UserRole.Cluster_Head && (item.scopeType == "cluster" || item.scopeType == "school"))

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F1F7))
  ) {
    Column(Modifier.padding(14.dp)) {
      Surface(onClick = { if (!isDraft) onOpen() }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
          Surface(Modifier.size(58.dp), RoundedCornerShape(29.dp), color = Color(0xFFE8DFFF)) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.Default.Campaign, null, tint = Color(0xFF5A35B5), modifier = Modifier.size(30.dp))
            }
          }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(item.content, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(
              "${item.publisherName} • ${if (isDraft) item.createdAt ?: "" else item.publishedAt ?: item.createdAt ?: ""}",
              color = Color(0xFF687887), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
          }
          if (!isDraft && !item.isRead) {
            Spacer(Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE11D2E)) {
              Text("NEW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
          }
        }
      }

      if (isDraft) {
        Spacer(Modifier.height(12.dp))
        Button(
          enabled = !busy,
          onClick = {
            busy = true
            NotificationApi.publish(session, item.id, onSuccess = { busy = false; onChanged() }, onError = { busy = false })
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
          shape = RoundedCornerShape(15.dp)
        ) { Text(if (busy) "प्रकाशित करत आहे…" else "सूचना प्रकाशित करा", fontWeight = FontWeight.Bold) }
      } else if (canViewAudience) {
        Spacer(Modifier.height(10.dp))
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          AudienceButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.People,
            title = "सूचना वाचलेले सदस्य",
            containerColor = Color(0xFFE4F9EC), contentColor = Color(0xFF07865D),
            onClick = { audienceType = "read" }
          )
          AudienceButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PersonOff,
            title = "सूचना न वाचलेले सदस्य",
            containerColor = Color(0xFFFFE9EF), contentColor = Color(0xFFD72661),
            onClick = { audienceType = "unread" }
          )
        }
      }
    }
  }

  audienceType?.let { status ->
    NotificationAudienceDialog(session, item, status) { audienceType = null }
  }
}

@Composable
private fun AudienceButton(
  modifier: Modifier,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit
) {
  Surface(
    modifier = modifier.heightIn(min = 52.dp),
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = containerColor
  ) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, null, tint = contentColor, modifier = Modifier.size(23.dp))
      Spacer(Modifier.width(5.dp))
      Text(title, color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp, modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun NotificationAudienceDialog(session: UserSession, item: NotificationItem, status: String, onClose: () -> Unit) {
  var members by remember { mutableStateOf<List<NotificationAudienceMember>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(item.id, status) {
    NotificationApi.audience(session, item.id, status,
      onSuccess = { members = it; loading = false },
      onError = { error = it; loading = false }
    )
  }

  AlertDialog(
    onDismissRequest = onClose,
    title = { Text(if (status == "read") "सूचना वाचलेले सदस्य" else "सूचना न वाचलेले सदस्य", fontWeight = FontWeight.Bold) },
    text = {
      when {
        loading -> Box(Modifier.fillMaxWidth().heightIn(min = 100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
        members.isEmpty() -> Text(if (status == "read") "अद्याप कोणत्याही सदस्याने सूचना वाचलेली नाही." else "सर्व संबंधित सदस्यांनी सूचना वाचली आहे.")
        else -> LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(members, key = { it.id }) { member ->
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
              Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text(member.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("भूमिका: ${roleLabel(member.role)}", fontSize = 13.sp)
                Text("शाळा: ${member.schoolName.ifBlank { "—" }}", fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (status == "read" && !member.readAt.isNullOrBlank()) {
                  Text("वाचले: ${member.readAt}", fontSize = 12.sp, color = Color(0xFF687887))
                }
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onClose) { Text("बंद करा") } }
  )
}

@Composable
private fun NotificationDetail(session: UserSession, item: NotificationItem, onBack: () -> Unit) {
  var marking by remember { mutableStateOf(false) }
  LaunchedEffect(item.id) {
    if (!item.isRead && (session.role != UserRole.Admin && session.role != UserRole.Cluster_Head)) {
      marking = true
      NotificationApi.markRead(session, item.id, onSuccess = { marking = false }, onError = { marking = false })
    }
  }
  BackHandler { onBack() }
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "मागे") }
        Text("सूचना", fontSize = 20.sp, fontWeight = FontWeight.Bold)
      }
      LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
          Text(item.title, fontSize = 24.sp, fontWeight = FontWeight.Black)
          Text("${item.publisherName} • ${item.publishedAt ?: item.createdAt ?: ""}", color = Color(0xFF687887), fontSize = 13.sp)
          Spacer(Modifier.height(8.dp))
          Text(item.content, fontSize = 17.sp, lineHeight = 25.sp)
          if (marking) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
          }
        }
      }
    }
  }
}

private fun roleLabel(role: String): String = when (role) {
  "Admin" -> "App Admin"
  "Cluster_Head" -> "Cluster Head"
  "School_HM" -> "School HM"
  "Teacher" -> "Teacher"
  else -> role
}

@Composable
private fun EmptyNotificationState(message: String) {
  Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
    Text(message, color = Color(0xFF687887), fontWeight = FontWeight.Medium)
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

data class NotificationAudienceMember(
  val id: String,
  val name: String,
  val role: String,
  val schoolName: String,
  val readAt: String?
)
