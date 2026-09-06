package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.model.UserSession

@Composable
fun DashboardHost(session: UserSession, onLogout: () -> Unit, onRegisterUser: () -> Unit) {
  var showUsers by remember { mutableStateOf(false) }

  Box(Modifier.fillMaxSize()) {
    DashboardScreen(session = session, onLogout = onLogout)

    if (!showUsers) {
      // Keep the existing Dashboard navigation active; this only forwards the Users-tab tap.
      Row(Modifier.fillMaxWidth().height(76.dp).align(Alignment.BottomCenter)) {
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
        Box(Modifier.weight(1f).fillMaxHeight().clickable { showUsers = true })
        Spacer(Modifier.weight(1f))
      }
    } else {
      // Compose equivalent of fitsSystemWindows: keep the Users overlay away from
      // the visible status/navigation bars while leaving the real Dashboard nav bar
      // exposed and clickable. This prevents the old tab from showing through.
      Box(
        Modifier
          .fillMaxSize()
          .systemBarsPadding()
          .padding(bottom = 64.dp)
      ) {
        UsersTabContent(session = session, onRegisterUser = onRegisterUser)
      }
    }
  }
}
