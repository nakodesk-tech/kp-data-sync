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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.theme.*

@Composable
fun UserRegistrationScreen(session: UserSession, onBack: () -> Unit, onRegistered: (String) -> Unit) {
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var mobile by remember { mutableStateOf("") }
  var role by remember { mutableStateOf(if (session.role == UserRole.Admin) UserRole.Cluster_Head else if (session.role == UserRole.Cluster_Head) UserRole.School_HM else UserRole.Teacher) }
  var clusterName by remember { mutableStateOf(session.clusterName?.takeUnless { it == "All Clusters" } ?: "") }
  var clusterCode by remember { mutableStateOf(session.clusterCode ?: "") }
  var schoolName by remember { mutableStateOf(session.schoolName?.takeUnless { it == "All Schools" } ?: "") }
  var schoolCode by remember { mutableStateOf(session.schoolCode ?: "") }
  var address by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }

  val allowedRoles = when (session.role) {
    UserRole.Admin -> listOf(UserRole.Cluster_Head, UserRole.School_HM, UserRole.Teacher)
    UserRole.Cluster_Head -> listOf(UserRole.School_HM, UserRole.Teacher)
    UserRole.School_HM -> listOf(UserRole.Teacher)
    UserRole.Teacher -> emptyList()
  }

  Box(Modifier.fillMaxSize().background(HighDensityBackground)) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        Column(Modifier.weight(1f)) {
          Text("Register New User", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
          Text("Create an account within your authorized scope", fontSize = 11.sp, color = Color(0xFF64748B))
        }
      }
      Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = HighDensityPrimaryContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Security, contentDescription = null, tint = HighDensityPrimary)
          Spacer(Modifier.width(8.dp))
          Text("Signed in as ${session.role.displayName}. Server-side RBAC verifies this registration.", fontSize = 11.sp, color = HighDensityOnPrimaryContainer)
        }
      }
      Text("SELECT ROLE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        allowedRoles.forEach { item ->
          FilterChip(selected = role == item, onClick = { role = item; error = null }, label = { Text(item.displayName, fontSize = 10.sp) })
        }
      }
      RegistrationField("Full Name", name, { name = it }, Icons.Default.Person)
      RegistrationField("E-Mail Address", email, { email = it }, Icons.Default.Email, KeyboardType.Email)
      RegistrationField("Mobile Number", mobile, { mobile = it }, Icons.Default.Phone, KeyboardType.Phone)
      RegistrationField("Cluster Name", clusterName, { clusterName = it }, Icons.Default.Hub)
      RegistrationField("Cluster Code", clusterCode, { clusterCode = it }, Icons.Default.Tag)
      if (role == UserRole.School_HM || role == UserRole.Teacher) {
        RegistrationField("School Name", schoolName, { schoolName = it }, Icons.Default.AccountBalance)
        RegistrationField("School / UDISE Code", schoolCode, { schoolCode = it }, Icons.Default.Badge, KeyboardType.Number)
      }
      RegistrationField("Address", address, { address = it }, Icons.Default.LocationOn)
      RegistrationField("Password", password, { password = it }, Icons.Default.Lock, KeyboardType.Password, true)
      RegistrationField("Confirm Password", confirmPassword, { confirmPassword = it }, Icons.Default.Lock, KeyboardType.Password, true)
      error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
      Button(
        onClick = {
          error = when {
            allowedRoles.isEmpty() -> "Your role cannot create users."
            name.trim().length < 2 -> "Please enter the full name."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
            clusterCode.isBlank() -> "Cluster Code is required."
            (role == UserRole.School_HM || role == UserRole.Teacher) && schoolCode.isBlank() -> "School / UDISE Code is required."
            password.length < 8 -> "Password must be at least 8 characters."
            password != confirmPassword -> "Passwords do not match."
            else -> null
          }
          if (error == null) {
            loading = true
            BackendApi.registerUser(
              token = session.token, name = name.trim(), email = email.trim(), mobile = mobile.trim().ifBlank { null },
              role = role.roleName, clusterName = clusterName.trim().ifBlank { null }, clusterCode = clusterCode.trim(),
              schoolName = schoolName.trim().ifBlank { null }, schoolCode = schoolCode.trim().ifBlank { null },
              address = address.trim().ifBlank { null }, password = password,
              onSuccess = { createdName -> loading = false; onRegistered(createdName) },
              onError = { message -> loading = false; error = message }
            )
          }
        },
        enabled = !loading && allowedRoles.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
      ) {
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
        else { Icon(Icons.Default.PersonAdd, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("CREATE ${role.displayName.uppercase()}", fontWeight = FontWeight.Bold) }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun RegistrationField(label: String, value: String, onValueChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false) {
  var isVisible by remember { mutableStateOf(false) }
  OutlinedTextField(
    value = value, onValueChange = onValueChange, label = { Text(label, fontSize = 12.sp) },
    leadingIcon = { Icon(icon, contentDescription = null, tint = Color(0xFF64748B)) },
    trailingIcon = if (password) {
      {
        IconButton(onClick = { isVisible = !isVisible }) {
          Icon(
            imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (isVisible) "Hide password" else "Show password",
            tint = Color(0xFF64748B)
          )
        }
      }
    } else null,
    singleLine = true,
    visualTransformation = if (password && !isVisible) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(14.dp),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = HighDensityOnBackground,
      unfocusedTextColor = HighDensityOnBackground,
      disabledTextColor = Color(0xFF94A3B8),
      focusedLabelColor = HighDensityPrimary,
      unfocusedLabelColor = Color(0xFF64748B),
      cursorColor = HighDensityPrimary,
      focusedLeadingIconColor = HighDensityPrimary,
      unfocusedLeadingIconColor = Color(0xFF64748B),
      focusedBorderColor = HighDensityPrimary,
      unfocusedBorderColor = Color(0xFFCBD5E1),
      focusedContainerColor = Color.White,
      unfocusedContainerColor = Color.White
    ),
    modifier = Modifier.fillMaxWidth()
  )
}
