package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.model.UserSession
import com.example.ui.DashboardScreen
import com.example.ui.LoginScreen
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = HighDensityBackground
        ) {
          MainApp()
        }
      }
    }
  }
}

@Composable
fun MainApp() {
  var activeSession by remember { mutableStateOf<UserSession?>(null) }

  Crossfade(targetState = activeSession, label = "ScreenTransition") { session ->
    if (session == null) {
      LoginScreen(
        onLoginSuccess = { loggedInUser ->
          activeSession = loggedInUser
        }
      )
    } else {
      DashboardScreen(
        session = session,
        onLogout = {
          activeSession = null
        }
      )
    }
  }
}

