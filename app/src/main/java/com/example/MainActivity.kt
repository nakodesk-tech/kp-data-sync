package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.AdminRegistrationScreen
import com.example.ui.DashboardScreen
import com.example.ui.RemoteLoginScreen
import com.example.ui.SchoolRegistrationScreen
import com.example.ui.UserRegistrationScreen
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private fun removeSecureFlag() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    val lp = window.attributes
    lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
    window.attributes = lp
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      setRecentsScreenshotEnabled(true)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    removeSecureFlag()
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = HighDensityBackground) { MainApp() }
      }
    }
    window.decorView.post {
      removeSecureFlag()
    }
  }

  override fun onStart() {
    super.onStart()
    removeSecureFlag()
  }

  override fun onResume() {
    super.onResume()
    removeSecureFlag()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    removeSecureFlag()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    removeSecureFlag()
  }
}

@Composable
fun MainApp() {
  var activeSession by remember { mutableStateOf<UserSession?>(null) }
  var showRegistration by remember { mutableStateOf(false) }
  var showSchoolRegistration by remember { mutableStateOf(false) }
  var showAdminRegistration by remember { mutableStateOf(false) }
  var registrationMessage by remember { mutableStateOf<String?>(null) }

  activeSession?.let { session ->
    when {
      showRegistration -> {
        UserRegistrationScreen(
          session = session,
          onBack = { showRegistration = false },
          onRegistered = { name ->
            registrationMessage = "$name registered successfully"
            showRegistration = false
          }
        )
      }
      showSchoolRegistration -> {
        SchoolRegistrationScreen(
          session = session,
          onBack = { showSchoolRegistration = false },
          onRegistered = { name ->
            registrationMessage = "$name registered successfully"
            showSchoolRegistration = false
          }
        )
      }
      else -> {
        Box(modifier = Modifier.fillMaxSize()) {
          DashboardScreen(
            session = session,
            onLogout = {
              showRegistration = false
              showSchoolRegistration = false
              activeSession = null
            }
          )

          if (session.role == UserRole.Admin) {
            FloatingActionButton(
              onClick = {
                registrationMessage = null
                showSchoolRegistration = true
              },
              containerColor = Color(0xFF1565C0),
              contentColor = Color.White,
              shape = CircleShape,
              modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 146.dp)
            ) {
              Icon(Icons.Default.AddBusiness, contentDescription = "Register school")
            }
          }

          if (session.role != UserRole.Teacher) {
            FloatingActionButton(
              onClick = {
                registrationMessage = null
                showRegistration = true
              },
              containerColor = Color(0xFF00897B),
              contentColor = Color.White,
              shape = CircleShape,
              modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 82.dp)
            ) {
              Icon(Icons.Default.PersonAdd, contentDescription = "Register user")
            }
          }

          registrationMessage?.let { message ->
            Snackbar(
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
            ) { Text(message) }
          }
        }
      }
    }
  } ?: if (showAdminRegistration) {
    AdminRegistrationScreen(
      onBack = { showAdminRegistration = false },
      onRegistered = { showAdminRegistration = false }
    )
  } else {
    RemoteLoginScreen(
      onLoginSuccess = { activeSession = it },
      onAdminRegistration = { showAdminRegistration = true }
    )
  }
}
