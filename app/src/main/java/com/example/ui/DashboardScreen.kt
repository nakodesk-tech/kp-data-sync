package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
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
        DashboardTab.Chats -> ChatsContent(session = session, onOpenSchools = { currentTab = DashboardTab.Schools })
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
private fun ChatsContent(session: UserSession, onOpenSchools: () -> Unit) {
  var groups by remember { mutableStateOf(SyncRepository.initialGroups) }
  var searchQuery by remember { mutableStateOf("") }
  var showCreateGroup by remember { mutableStateOf(false) }
  var newGroupName by remember { mutableStateOf("") }
  var newGroupScope by remember { mutableStateOf(defaultScope(session.role)) }

  val visibleGroups = remember(groups, searchQuery, session.role) {
    val scoped = groups.filter { isGroupVisible(it, session) }
    if (searchQuery.isBlank()) scoped else scoped.filter {
      it.name.contains(searchQuery, true) || it.lastMessage.contains(searchQuery, true) || it.senderName.contains(searchQuery, true)
    }
  }

  LazyColumn(
    Modifier.fillMaxSize().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)
  ) {
    if (session.role == UserRole.Admin) {
      item { AdminDataSection(onOpenSchools = onOpenSchools) }
    }

    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Chats", fontSize = 22.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
        if (session.role != UserRole.Teacher) {
          TextButton(onClick = { newGroupName = ""; newGroupScope = defaultScope(session.role); showCreateGroup = true }) {
            Icon(Icons.Default.GroupAdd, null, tint = HighDensityPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("नवीन ग्रुप तयार करा", color = HighDensityPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      }
    }

    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Chats शोधा…") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search)
      )
    }

    if (visibleGroups.isEmpty()) {
      item { EmptyCard(if (searchQuery.isBlank()) "आपल्या भूमिकेसाठी सध्या कोणतेही Chats उपलब्ध नाहीत." else "दिलेल्या शोधासाठी Chat सापडला नाही.") }
    } else {
      items(visibleGroups, key = { it.id }) { group ->
        ChatRow(group)
      }
    }
  }

  if (showCreateGroup) {
    AlertDialog(
      onDismissRequest = { showCreateGroup = false },
      title = { Text("नवीन ग्रुप तयार करा") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(newGroupName, { newGroupName = it }, label = { Text("ग्रुपचे नाव") }, singleLine = true, modifier = Modifier.fillMaxWidth())
          Text("ग्रुप scope", fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            allowedScopes(session.role).forEach { scope ->
              FilterChip(selected = newGroupScope == scope, onClick = { newGroupScope = scope }, label = { Text(scopeLabel(scope), fontSize = 11.sp) })
            }
          }
        }
      },
      confirmButton = {
        TextButton(enabled = newGroupName.isNotBlank(), onClick = {
          groups = listOf(ChatGroup("grp-${System.currentTimeMillis()}", newGroupName.trim(), "नवीन ग्रुप तयार झाला", session.name, time = "आत्ताच", scope = newGroupScope)) + groups
          showCreateGroup = false
        }) { Text("तयार करा") }
      },
      dismissButton = { TextButton(onClick = { showCreateGroup = false }) { Text("रद्द करा") } }
    )
  }
}

@Composable
private fun AdminDataSection(onOpenSchools: () -> Unit) {
  var schoolCount by remember { mutableStateOf<Int?>(null) }
  var loading by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    BackendApi.getSchools(
      onSuccess = { schoolCount = it.count { school -> school.isActive }; loading = false },
      onError = { loading = false }
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("System Overview", fontSize = 20.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
    Text("App Admin साठी उपलब्ध system data", fontSize = 11.sp, color = Color(0xFF64748B))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      AdminMetricCard(
        modifier = Modifier.weight(1f),
        icon = Icons.Default.School,
        tag = "D1 SYNC",
        value = if (loading) "…" else (schoolCount?.toString() ?: "—"),
        label = "ACTIVE SCHOOLS",
        onClick = onOpenSchools,
        action = "शाळा डेटा पहा"
      )
      AdminMetricCard(
        modifier = Modifier.weight(1f),
        icon = Icons.Default.CloudUpload,
        tag = "R2 STORAGE",
        value = "R2",
        label = "FILE STORAGE",
        onClick = { },
        action = "Attachments / files"
      )
    }
  }
}

@Composable
private fun AdminMetricCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, value: String, label: String, onClick: () -> Unit, action: String) {
  Surface(
    modifier = modifier.clickable(onClick = onClick),
    shape = RoundedCornerShape(22.dp),
    color = if (tag == "D1 SYNC") Color(0xFFE7F0FB) else Color(0xFFE6F7EC),
    border = androidx.compose.foundation.BorderStroke(1.dp, if (tag == "D1 SYNC") Color(0xFFB7D3F2) else Color(0xFFB9E9C9))
  ) {
    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = HighDensityPrimary, modifier = Modifier.size(22.dp))
        Surface(color = Color.White.copy(alpha = 0.65f), shape = RoundedCornerShape(7.dp)) { Text(tag, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) }
      }
      Text(value, fontSize = 25.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
      Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF475569))
      Text(action, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
    }
  }
}

@Composable
private fun ChatRow(group: ChatGroup) {
  Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(46.dp).clip(CircleShape).background(HighDensityPrimaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Groups, null, tint = HighDensityPrimary)
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(group.name, fontWeight = FontWeight.Bold, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(group.time, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
        Text(group.lastMessage, fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${group.senderName} • ${scopeLabel(group.scope)}", fontSize = 9.sp, color = HighDensityPrimary, fontWeight = FontWeight.SemiBold)
      }
      if (group.unreadCount > 0) {
        Spacer(Modifier.width(6.dp))
        Badge(containerColor = HighDensityPrimary) { Text(group.unreadCount.toString()) }
      }
    }
  }
}

private fun isGroupVisible(group: ChatGroup, session: UserSession): Boolean = when (session.role) {
  UserRole.Admin -> true
  UserRole.Cluster_Head -> group.scope in setOf("cluster", "administrative", "general")
  UserRole.School_HM -> group.scope in setOf("school", "general")
  UserRole.Teacher -> group.scope in setOf("school", "general")
}

private fun allowedScopes(role: UserRole): List<String> = when (role) {
  UserRole.Admin -> listOf("administrative", "cluster", "school", "general")
  UserRole.Cluster_Head -> listOf("cluster", "general")
  UserRole.School_HM -> listOf("school", "general")
  UserRole.Teacher -> emptyList()
}

private fun defaultScope(role: UserRole): String = when (role) {
  UserRole.Admin -> "administrative"
  UserRole.Cluster_Head -> "cluster"
  UserRole.School_HM, UserRole.Teacher -> "school"
}

private fun scopeLabel(scope: String): String = when (scope) {
  "administrative" -> "Admin"
  "cluster" -> "Cluster"
  "school" -> "School"
  else -> "General"
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
