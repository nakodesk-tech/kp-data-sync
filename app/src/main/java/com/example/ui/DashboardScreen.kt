package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SyncRepository
import com.example.model.*
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityOnBackground
import com.example.ui.theme.HighDensityOnPrimaryContainer
import com.example.ui.theme.HighDensityPrimary
import com.example.ui.theme.HighDensityPrimaryContainer

private enum class DashboardTab { Chats, Schools, Users, Profile }

@Composable
fun DashboardScreen(
  session: UserSession,
  onLogout: () -> Unit,
  onRegisterUser: () -> Unit,
  onExitApp: () -> Unit
) {
  var currentTab by remember { mutableStateOf(DashboardTab.Chats) }
  var showExitConfirmation by remember { mutableStateOf(false) }
  var showNotificationInfo by remember { mutableStateOf(false) }

  BackHandler {
    if (currentTab != DashboardTab.Chats) currentTab = DashboardTab.Chats
    else showExitConfirmation = true
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = HighDensityBackground,
    topBar = {
      Column(Modifier.fillMaxWidth().background(HighDensityBackground).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val initials = session.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").ifEmpty { "US" }
            Box(Modifier.size(48.dp).clip(CircleShape).background(HighDensityPrimaryContainer), contentAlignment = Alignment.Center) {
              Text(initials, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HighDensityOnPrimaryContainer)
            }
            Column {
              Text(session.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                Text("${session.role.displayName} • Active", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF49454F))
              }
            }
          }
          IconButton(onClick = { showNotificationInfo = true }) {
            BadgedBox(badge = { Badge(containerColor = HighDensityPrimary) { Text("3", fontSize = 9.sp) } }) {
              Icon(Icons.Default.Notifications, "Notifications", tint = Color(0xFF49454F))
            }
          }
        }
      }
    },
    bottomBar = {
      Surface(color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCAC4D0)), modifier = Modifier.navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
          NavItem("Chats", Icons.Default.ChatBubble, currentTab == DashboardTab.Chats) { currentTab = DashboardTab.Chats }
          NavItem("Schools", Icons.Default.AccountBalance, currentTab == DashboardTab.Schools) { currentTab = DashboardTab.Schools }
          NavItem("Users", Icons.Default.Groups, currentTab == DashboardTab.Users) { currentTab = DashboardTab.Users }
          NavItem("Profile", Icons.Default.Person, currentTab == DashboardTab.Profile) { currentTab = DashboardTab.Profile }
        }
      }
    }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      when (currentTab) {
        DashboardTab.Chats -> ChatsContent()
        DashboardTab.Schools -> SchoolsTabContent(SchoolDirectorySeed(), session.role) { }
        DashboardTab.Users -> UsersTabContent(session = session, onRegisterUser = onRegisterUser)
        DashboardTab.Profile -> ProfileContent(session, onLogout)
      }
    }
  }

  if (showNotificationInfo) AlertDialog(
    onDismissRequest = { showNotificationInfo = false },
    title = { Text("सूचना") },
    text = { Text("सध्या कोणतीही नवीन सूचना नाही.") },
    confirmButton = { TextButton(onClick = { showNotificationInfo = false }) { Text("ठीक आहे") } }
  )

  if (showExitConfirmation) AlertDialog(
    onDismissRequest = { showExitConfirmation = false },
    title = { Text("अॅप बंद करायचे आहे का?") },
    text = { Text("लॉगिन सत्र सुरक्षित राहील. पुन्हा अॅप उघडल्यावर लॉगिन करण्याची आवश्यकता नाही.") },
    confirmButton = { TextButton(onClick = { showExitConfirmation = false; onExitApp() }) { Text("बंद करा") } },
    dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("रद्द करा") } }
  )
}

@Composable
private fun NavItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
  Column(Modifier.clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) HighDensityPrimaryContainer else Color.Transparent).padding(horizontal = 16.dp, vertical = 4.dp)) {
      Icon(icon, title, tint = if (selected) HighDensityPrimary else Color(0xFF49454F), modifier = Modifier.size(22.dp))
    }
    Text(title, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) HighDensityPrimary else Color(0xFF49454F))
  }
}

@Composable
private fun ChatsContent() {
  val groups = remember { SyncRepository.initialGroups }
  LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)) {
    item {
      Text("संवाद", fontSize = 22.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
      Text("आपल्या गटांमधील संदेश", fontSize = 11.sp, color = Color(0xFF64748B))
    }
    if (groups.isEmpty()) item { EmptyCard("सध्या कोणतेही गट उपलब्ध नाहीत.") }
    else items(groups, key = { it.id }) { group ->
      Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(42.dp).clip(CircleShape).background(HighDensityPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, tint = HighDensityPrimary) }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(group.name, fontWeight = FontWeight.Bold, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(group.lastMessage, fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Text(group.time, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
      }
    }
  }
}

@Composable
private fun ProfileContent(session: UserSession, onLogout: () -> Unit) {
  Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("प्रोफाइल", fontSize = 22.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), color = Color.White) {
      Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(session.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(session.email, color = Color(0xFF475569))
        Text("भूमिका: ${session.role.displayName}", color = Color(0xFF475569))
      }
    }
    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
  }
}

@Composable
private fun EmptyCard(message: String) {
  Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.White) { Box(Modifier.padding(28.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(message, color = Color(0xFF64748B)) } }
}
