package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.data.SessionStore
import com.example.model.UserSession
import com.example.ui.AdminRegistrationScreen
import com.example.ui.DashboardHost
import com.example.ui.RemoteLoginScreen
import com.example.ui.SchoolRegistrationScreen
import com.example.ui.UserRegistrationScreen
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  private fun removeSecureFlag() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    val lp = window.attributes
    lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
    window.attributes = lp
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(true)
  }

  private fun keepSystemBarsVisible() {
    WindowCompat.getInsetsController(window, window.decorView)
      .show(WindowInsetsCompat.Type.systemBars())
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    removeSecureFlag()
    enableEdgeToEdge()
    keepSystemBarsVisible()
    val restoredSession = SessionStore.load(this)
    setContent {
      MyApplicationTheme {
        Surface(Modifier.fillMaxSize(), color = HighDensityBackground) {
          MainApp(
            initialSession = restoredSession,
            onExitApp = { finishAndRemoveTask() }
          )
        }
      }
    }
    window.decorView.post {
      removeSecureFlag()
      keepSystemBarsVisible()
    }
  }

  override fun onStart() {
    super.onStart()
    removeSecureFlag()
    keepSystemBarsVisible()
  }

  override fun onResume() {
    super.onResume()
    removeSecureFlag()
    keepSystemBarsVisible()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) keepSystemBarsVisible()
    removeSecureFlag()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    removeSecureFlag()
    keepSystemBarsVisible()
  }
}

@Composable
fun MainApp(
  initialSession: UserSession? = null,
  onExitApp: () -> Unit = {}
) {
  var activeSession by remember(initialSession) { mutableStateOf(initialSession) }
  var showRegistration by remember { mutableStateOf(false) }
  var showSchoolRegistration by remember { mutableStateOf(false) }
  var showAdminRegistration by remember { mutableStateOf(false) }
  var showLoginExitConfirmation by remember { mutableStateOf(false) }
  var registrationMessage by remember { mutableStateOf<String?>(null) }

  val context = androidx.compose.ui.platform.LocalContext.current

  LaunchedEffect(registrationMessage) {
    if (registrationMessage != null) {
      delay(3500)
      registrationMessage = null
    }
  }

  BackHandler(enabled = activeSession == null) {
    if (showAdminRegistration) showAdminRegistration = false
    else showLoginExitConfirmation = true
  }

  BackHandler(enabled = activeSession != null && (showRegistration || showSchoolRegistration)) {
    showRegistration = false
    showSchoolRegistration = false
  }

  activeSession?.let { session ->
    when {
      showRegistration -> UserRegistrationScreen(
        session,
        { showRegistration = false },
        { name ->
          registrationMessage = "$name registered successfully"
          showRegistration = false
        }
      )
      showSchoolRegistration -> SchoolRegistrationScreen(
        session,
        { showSchoolRegistration = false },
        { name ->
          registrationMessage = "$name registered successfully"
          showSchoolRegistration = false
        }
      )
      else -> Box(Modifier.fillMaxSize()) {
        DashboardHost(
          session = session,
          onLogout = {
            SessionStore.clear(context)
            showRegistration = false
            showSchoolRegistration = false
            activeSession = null
            registrationMessage = null
          },
          onRegisterUser = {
            registrationMessage = null
            showRegistration = true
          },
          onExitApp = onExitApp
        )
        registrationMessage?.let { message ->
          Snackbar(
            Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
          ) { Text(message) }
        }
      }
    }
  } ?: if (showAdminRegistration) {
    AdminRegistrationScreen(
      { showAdminRegistration = false },
      { showAdminRegistration = false }
    )
  } else {
    RemoteLoginScreen(
      onLoginSuccess = {
        SessionStore.save(context, it)
        activeSession = it
      },
      onAdminRegistration = { showAdminRegistration = true }
    )
  }

  if (showLoginExitConfirmation) {
    AlertDialog(
      onDismissRequest = { showLoginExitConfirmation = false },
      title = { Text("अॅप बंद करायचे आहे का?") },
      text = { Text("लॉगिन न करता अॅप बंद केले जाईल.") },
      confirmButton = {
        TextButton(onClick = { showLoginExitConfirmation = false; onExitApp() }) { Text("बंद करा") }
      },
      dismissButton = {
        TextButton(onClick = { showLoginExitConfirmation = false }) { Text("रद्द करा") }
      }
    )
  }
}
