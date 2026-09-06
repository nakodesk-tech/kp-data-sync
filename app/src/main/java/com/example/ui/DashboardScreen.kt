package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SyncRepository
import com.example.model.*
import com.example.ui.theme.*

enum class DashboardTab(val title: String) {
  Chats("Chats"),
  Schools("Schools"),
  Users("Users"),
  Profile("Profile")
}

@Composable
fun DashboardScreen(
  session: UserSession,
  onLogout: () -> Unit
) {
  var currentTab by remember { mutableStateOf(DashboardTab.Chats) }
  var groups by remember { mutableStateOf(SyncRepository.initialGroups) }
  var schools by remember { mutableStateOf(SyncRepository.initialSchools) }
  var searchQuery by remember { mutableStateOf("") }
  var activeChatGroup by remember { mutableStateOf<ChatGroup?>(null) }
  var showCreateGroupDialog by remember { mutableStateOf(false) }
  var showUploadExcelDialog by remember { mutableStateOf(false) }
  var showNotificationDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .background(HighDensityBackground),
    containerColor = HighDensityBackground,
    topBar = {
      // Header matching High Density theme & PDF Screen 2
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(HighDensityBackground)
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // User Avatar with Initials
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(HighDensityPrimaryContainer),
              contentAlignment = Alignment.Center
            ) {
              val initials = session.name
                .split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.toString() }
                .joinToString("")
                .ifEmpty { "US" }

              Text(
                text = initials,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HighDensityOnPrimaryContainer
              )
            }

            Column {
              Text(
                text = session.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = HighDensityOnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
                )
                Text(
                  text = "${session.role.displayName} • Active",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color(0xFF49454F),
                  letterSpacing = 0.3.sp
                )
              }
            }
          }

          IconButton(
            onClick = { showNotificationDialog = true },
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
          ) {
            BadgedBox(
              badge = {
                Badge(
                  containerColor = HighDensityPrimary,
                  contentColor = Color.White
                ) {
                  Text("3", fontSize = 9.sp)
                }
              }
            ) {
              Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Notifications",
                tint = Color(0xFF49454F)
              )
            }
          }
        }
      }
    },
    bottomBar = {
      // High Density Material 3 Navigation Bar
      Surface(
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCAC4D0)),
        modifier = Modifier.navigationBarsPadding()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          DashboardNavTab(
            title = "Chats",
            icon = Icons.Default.ChatBubble,
            isSelected = currentTab == DashboardTab.Chats,
            onClick = { currentTab = DashboardTab.Chats }
          )
          DashboardNavTab(
            title = "Schools",
            icon = Icons.Default.AccountBalance,
            isSelected = currentTab == DashboardTab.Schools,
            onClick = { currentTab = DashboardTab.Schools }
          )
          DashboardNavTab(
            title = "Users",
            icon = Icons.Default.Groups,
            isSelected = currentTab == DashboardTab.Users,
            onClick = { currentTab = DashboardTab.Users }
          )
          DashboardNavTab(
            title = "Profile",
            icon = Icons.Default.Person,
            isSelected = currentTab == DashboardTab.Profile,
            onClick = { currentTab = DashboardTab.Profile }
          )
        }
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      when (currentTab) {
        DashboardTab.Chats -> ChatsTabContent(
          groups = groups,
          searchQuery = searchQuery,
          onSearchChange = { searchQuery = it },
          onGroupClick = { activeChatGroup = it },
          onCreateGroupClick = { showCreateGroupDialog = true }
        )
        DashboardTab.Schools -> SchoolsTabContent(
          schools = schools,
          userRole = session.role,
          onUploadExcelClick = { showUploadExcelDialog = true }
        )
        DashboardTab.Users -> UsersTabContent(
          session = session
        )
        DashboardTab.Profile -> ProfileTabContent(
          session = session,
          onLogout = onLogout
        )
      }
    }
  }

  // Active Group Chat Dialog / Sheet
  if (activeChatGroup != null) {
    GroupChatDialog(
      group = activeChatGroup!!,
      session = session,
      onDismiss = { activeChatGroup = null }
    )
  }

  // Create Group Dialog
  if (showCreateGroupDialog) {
    CreateGroupDialog(
      userRole = session.role,
      onDismiss = { showCreateGroupDialog = false },
      onCreate = { newName, newScope ->
        val newGroup = ChatGroup(
          id = "grp-${System.currentTimeMillis()}",
          name = newName,
          lastMessage = "Group created",
          senderName = session.name,
          unreadCount = 0,
          time = "Just now",
          scope = newScope
        )
        groups = listOf(newGroup) + groups
        showCreateGroupDialog = false
      }
    )
  }

  // Upload Excel Dialog (PDF Priority Feature)
  if (showUploadExcelDialog) {
    UploadExcelDialog(
      onDismiss = { showUploadExcelDialog = false },
      onUploaded = { fileName ->
        showUploadExcelDialog = false
      }
    )
  }

  // Notification Dialog
  if (showNotificationDialog) {
    NotificationDialog(
      onDismiss = { showNotificationDialog = false }
    )
  }
}

@Composable
private fun DashboardNavTab(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .clickable { onClick() }
      .padding(vertical = 4.dp, horizontal = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(if (isSelected) HighDensityPrimaryContainer else Color.Transparent)
        .padding(horizontal = 16.dp, vertical = 4.dp),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = if (isSelected) HighDensityPrimary else Color(0xFF49454F),
        modifier = Modifier.size(22.dp)
      )
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = title,
      fontSize = 11.sp,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) HighDensityPrimary else Color(0xFF49454F)
    )
  }
}

@Composable
private fun ChatsTabContent(
  groups: List<ChatGroup>,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  onGroupClick: (ChatGroup) -> Unit,
  onCreateGroupClick: () -> Unit
) {
  val filteredGroups = remember(groups, searchQuery) {
    if (searchQuery.isBlank()) groups
    else groups.filter {
      it.name.contains(searchQuery, ignoreCase = true) ||
      it.lastMessage.contains(searchQuery, ignoreCase = true) ||
      it.senderName.contains(searchQuery, ignoreCase = true)
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. High-Density Dual Metric Cards (from Design HTML & PDF)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Card 1: D1 Sync
        Surface(
          modifier = Modifier
            .weight(1f)
            .height(96.dp),
          shape = RoundedCornerShape(24.dp),
          color = StatBlueBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = StatBlueText,
                modifier = Modifier.size(20.dp)
              )
              Surface(
                color = Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "D1 SYNC",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Black,
                  color = StatBlueText,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            Column {
              Text(
                text = "48",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = StatBlueText,
                lineHeight = 26.sp
              )
              Text(
                text = "MANAGED SCHOOLS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF004A77),
                letterSpacing = (-0.2).sp
              )
            }
          }
        }

        // Card 2: R2 Storage
        Surface(
          modifier = Modifier
            .weight(1f)
            .height(96.dp),
          shape = RoundedCornerShape(24.dp),
          color = StatGreenBg,
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = StatGreenText,
                modifier = Modifier.size(20.dp)
              )
              Surface(
                color = Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "R2 STORAGE",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Black,
                  color = StatGreenText,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            Column {
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = "1.2",
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Black,
                  color = StatGreenText,
                  lineHeight = 26.sp
                )
                Text(
                  text = "GB",
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = StatGreenText,
                  modifier = Modifier.padding(bottom = 2.dp)
                )
              }
              Text(
                text = "TOTAL ASSETS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF005220),
                letterSpacing = (-0.2).sp
              )
            }
          }
        }
      }
    }

    // 2. Infrastructure Health Card
    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "INFRASTRUCTURE HEALTH",
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = Color(0xFF94A3B8),
              letterSpacing = 1.sp
            )
            Text(
              text = "HONO v3.12.0",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF2563EB)
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFFF8FAFC))
              .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "D1 SQLite (kp-prod-db)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
              )
            }
            Surface(
              color = Color(0xFFDCFCE7),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = "9ms",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF15803D),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFFF8FAFC))
              .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "R2 Bucket (report-assets)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
              )
            }
            Surface(
              color = Color(0xFFDBEAFE),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = "Online",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D4ED8),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
              )
            }
          }
        }
      }
    }

    // 3. Search Groups & Chats (matches PDF Screen 2)
    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Search Groups and Chats", fontSize = 13.sp) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = Color(0xFF64748B)
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchChange("") }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF64748B))
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = Color.White,
          unfocusedContainerColor = Color.White,
          focusedBorderColor = HighDensityPrimary,
          unfocusedBorderColor = Color(0xFFCBD5E1)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("search_groups_input")
      )
    }

    // 4. Section Header: Chats & "Create New Group" button (matches PDF Screen 2)
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Chats",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = HighDensityOnBackground
        )

        Button(
          onClick = onCreateGroupClick,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF00897B),
            contentColor = Color.White
          ),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          modifier = Modifier.testTag("create_new_group_button")
        ) {
          Text(
            text = "Create New Group",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // 5. Group Items (matching Group 1, Group 2, Group 3, Group 4 in PDF Screen 2)
    items(filteredGroups) { group ->
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onGroupClick(group) },
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shadowElevation = 0.5.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = group.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HighDensityOnBackground
              )
              Surface(
                color = HighDensityPrimaryContainer,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = group.scope.uppercase(),
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = HighDensityOnPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = "${group.senderName}: ${group.lastMessage}",
              fontSize = 12.sp,
              color = Color(0xFF64748B),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "Options",
              tint = Color(0xFF64748B),
              modifier = Modifier.size(18.dp)
            )

            if (group.unreadCount > 0) {
              Surface(
                color = Color(0xFF00897B),
                shape = CircleShape
              ) {
                Text(
                  text = "${group.unreadCount}",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            } else {
              Text(
                text = group.time,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }
        }
      }
    }

    // 6. Recent Sync Logs section (from High Density layout)
    item {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "RECENT SYNC LOGS",
          fontSize = 12.sp,
          fontWeight = FontWeight.Black,
          color = Color(0xFF1D1B20),
          letterSpacing = 0.5.sp
        )
        TextButton(onClick = { /* Filter */ }) {
          Text(
            text = "FILTER",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = HighDensityPrimary
          )
        }
      }
    }

    items(SyncRepository.initialSyncLogs) { log ->
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shadowElevation = 0.5.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(
                when (log.iconType) {
                  "report" -> Color(0xFFEADDFF)
                  "group" -> Color(0xFFFDE2E1)
                  else -> Color(0xFFE8DEF8)
                }
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = when (log.iconType) {
                "report" -> Icons.Default.Description
                "group" -> Icons.Default.GroupAdd
                else -> Icons.Default.Sync
              },
              contentDescription = null,
              tint = when (log.iconType) {
                "report" -> Color(0xFF21005D)
                "group" -> Color(0xFF410002)
                else -> Color(0xFF1D192B)
              },
              modifier = Modifier.size(20.dp)
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = log.title,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF1E293B)
            )
            Text(
              text = log.subtitle,
              fontSize = 10.sp,
              color = Color(0xFF64748B)
            )
          }

          Text(
            text = log.time,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF94A3B8)
          )
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

@Composable
private fun SchoolsTabContent(
  schools: List<SchoolItem>,
  userRole: UserRole,
  onUploadExcelClick: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    item {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "School Directory & Excel Sync",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = HighDensityOnBackground
          )
          Text(
            text = "Direct sync with Cloudflare D1 & R2",
            fontSize = 11.sp,
            color = Color(0xFF64748B)
          )
        }

        Button(
          onClick = onUploadExcelClick,
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF00897B),
            contentColor = Color.White
          ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Excel Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    items(schools) { school ->
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = school.name,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF0F172A),
              modifier = Modifier.weight(1f)
            )
            Surface(
              color = Color(0xFFE0F2FE),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = "UDISE: ${school.udiseCode}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0369A1),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Hub, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(text = school.clusterName, fontSize = 11.sp, color = Color(0xFF475569))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(text = "${school.teacherCount} Teachers", fontSize = 11.sp, color = Color(0xFF475569))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(text = "${school.studentCount} Students", fontSize = 11.sp, color = Color(0xFF475569))
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Last sync: ${school.lastSyncTime}",
              fontSize = 10.sp,
              color = Color(0xFF94A3B8)
            )
            TextButton(
              onClick = onUploadExcelClick,
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text("Upload Sheet", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun UsersTabContent(
  session: UserSession
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Role-Based Access Control (RBAC)",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = HighDensityOnBackground
      )
      Text(
        text = "Roles and authorization hierarchy configured in Cloudflare D1",
        fontSize = 11.sp,
        color = Color(0xFF64748B)
      )
    }

    // Role Hierarchy Cards
    items(UserRole.values()) { role ->
      val isMyRole = session.role == role
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isMyRole) Color(0xFFF3E8FF) else Color.White,
        border = androidx.compose.foundation.BorderStroke(
          width = if (isMyRole) 1.5.dp else 1.dp,
          color = if (isMyRole) HighDensityPrimary else Color(0xFFE2E8F0)
        )
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(
                    when (role) {
                      UserRole.Admin -> Color(0xFFEF5350)
                      UserRole.Cluster_Head -> Color(0xFF26A69A)
                      UserRole.School_HM -> Color(0xFFFFA726)
                      UserRole.Teacher -> Color(0xFF8D6E63)
                    }
                  )
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = role.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
              )
            }

            if (isMyRole) {
              Surface(
                color = HighDensityPrimary,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "CURRENT SESSION",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Black,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = role.description,
            fontSize = 11.sp,
            color = Color(0xFF475569),
            lineHeight = 15.sp
          )
        }
      }
    }
  }
}

@Composable
private fun ProfileTabContent(
  session: UserSession,
  onLogout: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "User Profile & Cloudflare Session",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = HighDensityOnBackground
      )
    }

    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          ProfileRow("Name", session.name)
          Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 8.dp))
          ProfileRow("Email", session.email)
          Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 8.dp))
          ProfileRow("Role", session.role.displayName)
          Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 8.dp))
          ProfileRow("Cluster", session.clusterName ?: "System Wide")
          Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 8.dp))
          ProfileRow("School", session.schoolName ?: "All Schools")
        }
      }
    }

    // Signed JWT claims card
    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "SIGNED JWT TOKEN",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF38BDF8)
            )
            Text(
              text = "HS256 (Web Crypto)",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = Color(0xFF94A3B8)
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = session.token,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFCBD5E1),
            lineHeight = 14.sp
          )
        }
      }
    }

    item {
      Button(
        onClick = onLogout,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFFEF4444),
          contentColor = Color.White
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
      ) {
        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("LOGOUT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      }
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun ProfileRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(text = label, fontSize = 12.sp, color = Color(0xFF64748B))
    Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
  }
}

@Composable
private fun GroupChatDialog(
  group: ChatGroup,
  session: UserSession,
  onDismiss: () -> Unit
) {
  var messages by remember { mutableStateOf(SyncRepository.getMessagesForGroup(group.id)) }
  var messageInput by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .height(550.dp),
      shape = RoundedCornerShape(24.dp),
      color = Color.White
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Chat Header
        Surface(
          color = HighDensityPrimaryContainer,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HighDensityOnPrimaryContainer)
              }
              Column {
                Text(
                  text = group.name,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = HighDensityOnPrimaryContainer
                )
                Text(
                  text = "Scope: ${group.scope} • D1 Synced",
                  fontSize = 10.sp,
                  color = Color(0xFF49454F)
                )
              }
            }
          }
        }

        // Messages List
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(messages) { msg ->
            val isMine = msg.isMe || msg.senderRole == session.role
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
            ) {
              Text(
                text = "${msg.senderName} (${msg.senderRole.displayName})",
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
              )
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isMine) HighDensityPrimaryContainer else Color(0xFFF1F5F9)
              ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                  Text(
                    text = msg.text,
                    fontSize = 12.sp,
                    color = if (isMine) HighDensityOnPrimaryContainer else Color(0xFF0F172A)
                  )
                  if (msg.attachmentName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                      color = Color.White,
                      shape = RoundedCornerShape(8.dp),
                      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF00897B), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = msg.attachmentName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                      }
                    }
                  }
                  Text(
                    text = msg.timestamp,
                    fontSize = 9.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                  )
                }
              }
            }
          }
        }

        // Message Input
        Surface(
          color = Color(0xFFF8FAFC),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = messageInput,
              onValueChange = { messageInput = it },
              placeholder = { Text("Type message...", fontSize = 12.sp) },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(20.dp),
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
              )
            )

            IconButton(
              onClick = {
                if (messageInput.isNotBlank()) {
                  val newMsg = GroupMessage(
                    id = "msg-${System.currentTimeMillis()}",
                    senderName = session.name,
                    senderRole = session.role,
                    text = messageInput.trim(),
                    timestamp = "Now",
                    isMe = true
                  )
                  messages = messages + newMsg
                  messageInput = ""
                }
              },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(HighDensityPrimary)
            ) {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CreateGroupDialog(
  userRole: UserRole,
  onDismiss: () -> Unit,
  onCreate: (name: String, scope: String) -> Unit
) {
  var groupName by remember { mutableStateOf("") }
  var scope by remember { mutableStateOf("cluster") }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = Color.White
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        Text("Create New Group", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
          value = groupName,
          onValueChange = { groupName = it },
          label = { Text("Group Name") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Scope", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("cluster", "school", "administrative").forEach { s ->
            FilterChip(
              selected = scope == s,
              onClick = { scope = s },
              label = { Text(s.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancel")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              if (groupName.isNotBlank()) onCreate(groupName.trim(), scope)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
          ) {
            Text("Create")
          }
        }
      }
    }
  }
}

@Composable
private fun UploadExcelDialog(
  onDismiss: () -> Unit,
  onUploaded: (String) -> Unit
) {
  var isUploading by remember { mutableStateOf(false) }
  var isCompleted by remember { mutableStateOf(false) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = Color.White
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.TableChart,
          contentDescription = null,
          tint = Color(0xFF00897B),
          modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "Cloudflare R2 Excel Sync",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF0F172A)
        )
        Text(
          text = "Upload attendance or student data spreadsheet (.xlsx)",
          fontSize = 11.sp,
          color = Color(0xFF64748B),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isUploading) {
          CircularProgressIndicator(color = Color(0xFF00897B), modifier = Modifier.size(32.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("Uploading to Cloudflare R2...", fontSize = 12.sp, color = Color(0xFF475569))
        } else if (isCompleted) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(32.dp))
          Spacer(modifier = Modifier.height(6.dp))
          Text("Spreadsheet synced with D1 tables successfully!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
          Spacer(modifier = Modifier.height(12.dp))
          Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))) {
            Text("Done")
          }
        } else {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFF1F5F9))
              .padding(12.dp)
          ) {
            Column {
              Text("Target: kp-data-sync-reports bucket", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              Text("Key: reports/2026/09/school_data_batch_01.xlsx", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                isUploading = true
                isUploading = false
                isCompleted = true
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
              Text("Start Upload")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NotificationDialog(onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = Color.White
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("System Notifications", fontSize = 15.sp, fontWeight = FontWeight.Bold)
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("• D1 database schema updated with RBAC roles", fontSize = 11.sp, color = Color(0xFF475569))
        Spacer(modifier = Modifier.height(4.dp))
        Text("• R2 storage bucket connected for Excel sheets", fontSize = 11.sp, color = Color(0xFF475569))
        Spacer(modifier = Modifier.height(4.dp))
        Text("• Academic reporting cycle 2026-27 is open", fontSize = 11.sp, color = Color(0xFF475569))
      }
    }
  }
}
