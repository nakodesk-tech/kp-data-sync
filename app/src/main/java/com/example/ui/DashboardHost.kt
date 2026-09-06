package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.UserSession
import com.example.ui.theme.HighDensityBackground

@Composable
fun DashboardHost(
  session: UserSession,
  onLogout: () -> Unit,
  onRegisterUser: () -> Unit,
  onExitApp: () -> Unit
) {
  var showUsers by remember { mutableStateOf(false) }
  var selectedTab by remember { mutableStateOf(DashboardTab.Chats) }
  var showExitConfirmation by remember { mutableStateOf(false) }

  BackHandler {
    if (showUsers) {
      // Back from the redesigned Users directory returns to the dashboard instead of exiting.
      showUsers = false
      selectedTab = DashboardTab.Chats
    } else {
      showExitConfirmation = true
    }
  }

  Box(Modifier.fillMaxSize()) {
    DashboardScreen(
      session = session,
      onLogout = onLogout,
      initialTab = selectedTab,
      onTabChanged = { tab ->
        selectedTab = tab
        showUsers = tab == DashboardTab.Users
      }
    )

    if (showUsers) {
      // Opaque, inset-aware Users directory. The real Dashboard navigation bar remains
      // visible and receives all non-Users tab taps through DashboardScreen's callback.
      Box(
        Modifier
          .fillMaxSize()
          .background(HighDensityBackground)
          .systemBarsPadding()
          .padding(bottom = 64.dp)
      ) {
        UsersTabContent(session = session, onRegisterUser = onRegisterUser)
      }
    }
  }

  if (showExitConfirmation) {
    AlertDialog(
      onDismissRequest = { showExitConfirmation = false },
      title = { Text("अॅप बंद करायचे आहे का?") },
      text = { Text("अॅप बंद केल्यावर तुमचे लॉगिन सत्र सुरक्षित राहील. पुढील वेळी अॅप उघडल्यावर पुन्हा लॉगिन करण्याची आवश्यकता नाही.") },
      confirmButton = {
        TextButton(
          onClick = {
            showExitConfirmation = false
            onExitApp()
          }
        ) { Text("बंद करा") }
      },
      dismissButton = {
        TextButton(onClick = { showExitConfirmation = false }) { Text("रद्द करा") }
      }
    )
  }
}
