package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
import com.example.ui.theme.*

@Composable
fun AdminRegistrationScreen(onBack: () -> Unit, onRegistered: () -> Unit) {
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var mobile by remember { mutableStateOf("") }
  var clusterName by remember { mutableStateOf("") }
  var clusterCode by remember { mutableStateOf("") }
  var schoolName by remember { mutableStateOf("") }
  var schoolCode by remember { mutableStateOf("") }
  var address by remember { mutableStateOf("") }
  val configuredSetupSecret = remember {
    try { com.example.BuildConfig.SETUP_SECRET.takeIf { it.isNotBlank() && it != "YOUR_SETUP_SECRET" } ?: "" }
    catch (_: Exception) { "" }
  }
  var setupSecret by remember { mutableStateOf(configuredSetupSecret) }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }

  Box(Modifier.fillMaxSize().background(HighDensityBackground).statusBarsPadding().navigationBarsPadding()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        Column(Modifier.weight(1f)) {
          Text("App Admin Registration", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = HighDensityOnBackground)
          Text("Create the first system administrator", fontSize = 12.sp, color = Color(0xFF64748B))
        }
      }
      Spacer(Modifier.height(12.dp))
      Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color(0xFFF3E8FF), border = androidx.compose.foundation.BorderStroke(1.dp, HighDensityPrimary)) {
        Column(Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("SYSTEM APP ADMIN", fontSize = 13.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
          }
          Spacer(Modifier.height(6.dp))
          Text("Full administrative authority across clusters, schools, users, and groups.", fontSize = 11.sp, color = Color(0xFF475569), lineHeight = 16.sp)
        }
      }
      Spacer(Modifier.height(18.dp))
      AdminField("Full Name", name, { name = it; error = null }, Icons.Default.Person)
      AdminField("E-Mail Address", email, { email = it; error = null }, Icons.Default.Email, KeyboardType.Email)
      AdminField("Mobile Number", mobile, { mobile = it; error = null }, Icons.Default.Phone, KeyboardType.Phone)
      AdminField("Cluster Name", clusterName, { clusterName = it; error = null }, Icons.Default.Hub)
      AdminField("Cluster Code", clusterCode, { clusterCode = it; error = null }, Icons.Default.Tag)
      AdminField("School Name", schoolName, { schoolName = it; error = null }, Icons.Default.AccountBalance)
      AdminField("School Code", schoolCode, { schoolCode = it; error = null }, Icons.Default.Badge)
      AdminField("Address", address, { address = it; error = null }, Icons.Default.LocationOn)
      AdminField("Admin Setup Key", setupSecret, { setupSecret = it; error = null }, Icons.Default.Key, KeyboardType.Password, true, '*', true, false)
      AdminField("Password", password, { password = it; error = null }, Icons.Default.Lock, KeyboardType.Password, true, '*')
      AdminField("Confirm Password", confirmPassword, { confirmPassword = it; error = null }, Icons.Default.Lock, KeyboardType.Password, true, '*')
      Spacer(Modifier.height(4.dp))
      Text("All profile fields are required because they map directly to the users table. The Admin Setup Key is encrypted (* format) and protected from copying.", fontSize = 10.sp, color = Color(0xFF64748B), lineHeight = 14.sp)
      error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
      Spacer(Modifier.height(18.dp))
      Button(enabled = !loading, onClick = {
        val resolvedSecret = setupSecret.trim().ifBlank { configuredSetupSecret }
        error = when {
          name.trim().length < 2 -> "Please enter the full name."
          !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
          mobile.trim().isBlank() -> "Mobile Number is required."
          clusterName.trim().isBlank() -> "Cluster Name is required."
          clusterCode.trim().isBlank() -> "Cluster Code is required."
          schoolName.trim().isBlank() -> "School Name is required."
          schoolCode.trim().isBlank() -> "School Code is required."
          address.trim().isBlank() -> "Address is required."
          resolvedSecret.isBlank() -> "Admin Setup Key is required."
          password.length < 8 -> "Password must be at least 8 characters."
          password != confirmPassword -> "Passwords do not match."
          else -> null
        }
        if (error == null) {
          loading = true
          BackendApi.setupInitialAdmin(setupSecret = resolvedSecret, name = name.trim(), email = email.trim(), mobile = mobile.trim(), clusterName = clusterName.trim(), clusterCode = clusterCode.trim(), schoolName = schoolName.trim(), schoolCode = schoolCode.trim(), address = address.trim(), password = password, onSuccess = { loading = false; onRegistered() }, onError = { message -> loading = false; error = message })
        }
      }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) {
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp)) else { Icon(Icons.Default.PersonAdd, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("CREATE APP ADMIN", fontWeight = FontWeight.Bold) }
      }
      Spacer(Modifier.height(12.dp))
      OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("BACK TO LOGIN", fontWeight = FontWeight.Bold) }
      Spacer(Modifier.height(20.dp))
    }
  }
}

@Composable
private fun AdminField(label: String, value: String, onValueChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false, maskChar: Char = '*', preventCopy: Boolean = false, allowToggleVisibility: Boolean = true) {
  val currentToolbar = LocalTextToolbar.current
  val currentClipboard = LocalClipboardManager.current
  val secureToolbar = remember(currentToolbar, preventCopy) { if (preventCopy) object : TextToolbar {
    override val status: TextToolbarStatus get() = currentToolbar.status
    override fun hide() = currentToolbar.hide()
    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) { currentToolbar.showMenu(rect, null, onPasteRequested, null, onSelectAllRequested) }
  } else currentToolbar }
  val secureClipboard = remember(currentClipboard, preventCopy) { if (preventCopy) object : ClipboardManager {
    override fun getText(): AnnotatedString? = currentClipboard.getText()
    override fun setText(annotatedString: AnnotatedString) {}
    override fun hasText(): Boolean = currentClipboard.hasText()
  } else currentClipboard }
  var isVisible by remember { mutableStateOf(false) }
  CompositionLocalProvider(LocalTextToolbar provides secureToolbar, LocalClipboardManager provides secureClipboard) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label, fontSize = 12.sp) }, leadingIcon = { Icon(icon, contentDescription = null, tint = Color(0xFF64748B)) }, trailingIcon = if (password && allowToggleVisibility) { { IconButton(onClick = { isVisible = !isVisible }) { Icon(if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (isVisible) "Hide password" else "Show password", tint = Color(0xFF64748B)) } } } else null, singleLine = true, visualTransformation = if (password && (!isVisible || !allowToggleVisibility)) PasswordVisualTransformation(maskChar) else androidx.compose.ui.text.input.VisualTransformation.None, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = HighDensityOnBackground, unfocusedTextColor = HighDensityOnBackground, disabledTextColor = Color(0xFF94A3B8), focusedLabelColor = HighDensityPrimary, unfocusedLabelColor = Color(0xFF64748B), cursorColor = HighDensityPrimary, focusedLeadingIconColor = HighDensityPrimary, unfocusedLeadingIconColor = Color(0xFF64748B), focusedBorderColor = HighDensityPrimary, unfocusedBorderColor = Color(0xFFCBD5E1), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).then(if (preventCopy) Modifier.onKeyEvent { keyEvent -> if (keyEvent.isCtrlPressed && (keyEvent.key == Key.C || keyEvent.key == Key.X)) true else false } else Modifier))
  }
}
