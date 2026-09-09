package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.model.UserSession
import com.example.ui.theme.HighDensityBackground

/**
 * Dedicated notification screen.
 * Dashboard should only provide the entry point (bell + unread badge).
 * Notification-specific UI remains isolated from the Dashboard layout.
 */
@Composable
fun NotificationScreen(
  session: UserSession,
  onBack: () -> Unit,
  onCreate: () -> Unit
) {
  BackHandler(onBack = onBack)

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = HighDensityBackground,
    topBar = {
      TopAppBar(
        title = { Text("सूचना", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "मागे")
          }
        }
      )
    }
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      NotificationPublishedPanel(
        session = session,
        onCreate = onCreate
      )
    }
  }
}
