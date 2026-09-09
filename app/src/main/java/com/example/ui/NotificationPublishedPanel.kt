package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
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
  var items by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var selectedTab by remember { mutableStateOf(0) }

  fun reload() {
    loading = true
    NotificationApi.list(
      session,
      onSuccess = { items = it; loading = false; error = null },
      onError = { error = it; loading = false }
    )
  }

  LaunchedEffect(session.token) { reload() }

  val canCreate = session.role == UserRole.Admin || session.role == UserRole.Cluster_Head

  Column(
    Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(top = 14.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(36.dp),
        color = Color(0xFFDDF7EC)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            tint = Color(0xFF008C68),
            modifier = Modifier.size(38.dp)
          )
        }
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          "Published Notifications",
          fontSize = 24.sp,
          fontWeight = FontWeight.Black
        )
        Text("प्रकाशित सूचना", fontWeight = FontWeight.Bold)
      }
      if (canCreate) {
        Button(
          onClick = onCreate,
          shape = RoundedCornerShape(28.dp),
          contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) {
          Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp))
          Spacer(Modifier.width(6.dp))
          Text("नवीन सूचना तयार करा", fontWeight = FontWeight.Bold)
        }
      }
    }

    Row(
      Modifier
        .fillMaxWidth()
        .padding(bottom = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      NotificationTab(
        modifier = Modifier.weight(1f),
        selected = selectedTab == 0,
        icon = Icons.Default.Notifications,
        title = "Published Notifications",
        onClick = { selectedTab = 0 }
      )
      NotificationTab(
        modifier = Modifier.weight(1f),
        selected = selectedTab == 1,
        icon = Icons.Default.Description,
        title = "Draft Notifications",
        onClick = { selectedTab = 1 }
      )
    }

    if (selectedTab == 1) {
      EmptyNotificationState("सध्या कोणत्याही Draft सूचना उपलब्ध नाहीत.")
      return@Column
    }

    when {
      loading -> Box(
        Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
      ) { CircularProgressIndicator() }

      error != null -> Text(
        error!!,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(12.dp)
      )

      items.isEmpty() -> EmptyNotificationState("सध्या कोणतीही प्रकाशित सूचना नाही.")

      else -> LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
      ) {
        items(items, key = { it.id }) { item ->
          PublishedNotificationRow(session, item) { reload() }
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
  onClick: () -> Unit
) {
  Surface(
    modifier = modifier,
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = if (selected) Color(0xFFE5F9F0) else Color(0xFFF0F3F9),
    border = if (selected) BorderStroke(1.dp, Color(0xFFBFECDD)) else null
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(vertical = 13.dp, horizontal = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        icon,
        null,
        tint = if (selected) Color(0xFF008C68) else Color(0xFF5E6875),
        modifier = Modifier.size(25.dp)
      )
      Spacer(Modifier.height(4.dp))
      Text(
        title,
        color = if (selected) Color(0xFF008C68) else Color(0xFF5E6875),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun PublishedNotificationRow(
  session: UserSession,
  item: NotificationItem,
  onChanged: () -> Unit
) {
  var busy by remember { mutableStateOf(false) }
  val canViewAudience = when (session.role) {
    UserRole.Admin -> true
    UserRole.Cluster_Head -> item.scopeType == "cluster" || item.scopeType == "school"
    else -> false
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F1F7))
  ) {
    Column(Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Surface(
          modifier = Modifier.size(58.dp),
          shape = RoundedCornerShape(29.dp),
          color = Color(0xFFE8DFFF)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Default.Campaign,
              null,
              tint = Color(0xFF5A35B5),
              modifier = Modifier.size(30.dp)
            )
          }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(
            item.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(Modifier.height(4.dp))
          Text(item.content, fontSize = 16.sp, lineHeight = 22.sp)
          Spacer(Modifier.height(8.dp))
          Text(
            "${item.publisherName} • ${item.publishedAt ?: item.createdAt}",
            color = Color(0xFF687887),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
          )
        }
        if (!item.isRead) {
          Spacer(Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFE11D2E)
          ) {
            Text(
              "NEW",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Black,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))

      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (canViewAudience) {
          AudienceButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.People,
            title = "सूचना वाचलेले सदस्य",
            containerColor = Color(0xFFE4F9EC),
            contentColor = Color(0xFF07865D)
          )
          AudienceButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PersonOff,
            title = "सूचना न वाचलेले सदस्य",
            containerColor = Color(0xFFFFE9EF),
            contentColor = Color(0xFFD72661)
          )
        }

        TextButton(
          enabled = !busy,
          onClick = {
            busy = true
            NotificationApi.dismiss(
              session,
              item.id,
              onSuccess = { busy = false; onChanged() },
              onError = { busy = false }
            )
          }
        ) {
          Icon(Icons.Default.Close, null, modifier = Modifier.size(19.dp))
          Spacer(Modifier.width(4.dp))
          Text("Dismiss", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun AudienceButton(
  modifier: Modifier,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  containerColor: Color,
  contentColor: Color
) {
  Surface(
    modifier = modifier.heightIn(min = 64.dp),
    shape = RoundedCornerShape(18.dp),
    color = containerColor
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(icon, null, tint = contentColor, modifier = Modifier.size(28.dp))
      Spacer(Modifier.width(7.dp))
      Text(
        title,
        color = contentColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 17.sp,
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun EmptyNotificationState(message: String) {
  Box(
    Modifier.fillMaxWidth().padding(28.dp),
    contentAlignment = Alignment.Center
  ) {
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
