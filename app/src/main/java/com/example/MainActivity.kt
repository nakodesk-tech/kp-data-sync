package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.DashboardScreen
import com.example.ui.LoginScreen
import com.example.ui.UserRegistrationScreen
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = HighDensityBackground) {
          MainApp()
        }
      }
    }
  }
}

@Composable
fun MainApp() {
  var activeSession by remember { mutableStateOf<UserSession?>(null) }
  var showRegistration by remember { mutableStateOf(false) }
  var registrationMessage by remember { mutableStateOf<String?>(null) }

  activeSession?.let { session ->
    if (showRegistration) {
      UserRegistrationScreen(
        session = session,
        onBack = { showRegistration = false },
        onRegistered = { name ->
          registrationMessage = "$name registered successfully"
          showRegistration = false
        }
      )
    } else {
      Box(modifier = Modifier.fillMaxSize()) {
        DashboardScreen(
          session = session,
          onLogout = {
            showRegistration = false
            activeSession = null
          }
        )

        if (session.role != UserRole.Teacher) {
          FloatingActionButton(
            onClick = {
              registrationMessage = null
              showRegistration = true
            },
            containerColor = Color(0xFF00897B),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(end = 18.dp, bottom = 82.dp)
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = "Register user")
          }
        }

        registrationMessage?.let { message ->
          Snackbar(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
          ) { Text(message) }
        }
      }
    }
  } ?: LoginScreen(
    onLoginSuccess = { loggedInUser -> activeSession = loggedInUser }
  )
}
