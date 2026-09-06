package com.example.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
  var showExitConfirmation by remember { mutableStateOf(false) }
  val hostView = LocalView.current
  val density = LocalDensity.current
  val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  fun activateDashboardTab(index: Int) {
    showUsers = false
    hostView.post {
      val navBottomPx = ViewCompat.getRootWindowInsets(hostView)
        ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
      val x = hostView.width * ((index + 0.5f) / 4f)
      val y = hostView.height - navBottomPx - with(density) { 32.dp.toPx() }
      val downTime = SystemClock.uptimeMillis()
      val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
      val up = MotionEvent.obtain(downTime, downTime + 40L, MotionEvent.ACTION_UP, x, y, 0)
      hostView.dispatchTouchEvent(down)
      hostView.dispatchTouchEvent(up)
      down.recycle()
      up.recycle()
    }
  }

  BackHandler {
    if (showUsers) activateDashboardTab(0) else showExitConfirmation = true
  }

  Box(Modifier.fillMaxSize()) {
    DashboardScreen(session = session, onLogout = onLogout)

    // Transparent hit-area over the real Users navigation item. The existing
    // Dashboard navigation remains visually unchanged.
    Column(
      Modifier
        .fillMaxWidth()
        .height(64.dp + navigationBarPadding)
        .align(Alignment.BottomCenter)
    ) {
      Spacer(Modifier.height(navigationBarPadding))
      Row(Modifier.fillMaxWidth().height(64.dp)) {
        if (!showUsers) {
          Spacer(Modifier.weight(1f))
          Spacer(Modifier.weight(1f))
          Box(
            Modifier
              .weight(1f)
              .fillMaxHeight()
              .clickable { showUsers = true }
          )
          Spacer(Modifier.weight(1f))
        } else {
          repeat(4) { index ->
            Box(
              Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable {
                  if (index == 2) return@clickable
                  activateDashboardTab(index)
                }
            )
          }
        }
      }
    }

    if (showUsers) {
      // Opaque, inset-aware Users directory. The real Dashboard navigation bar stays
      // visible underneath and is controlled by the transparent hit areas above.
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
        TextButton(onClick = { showExitConfirmation = false; onExitApp() }) { Text("बंद करा") }
      },
      dismissButton = {
        TextButton(onClick = { showExitConfirmation = false }) { Text("रद्द करा") }
      }
    )
  }
}
