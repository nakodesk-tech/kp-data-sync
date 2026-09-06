package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.theme.*

@Composable
fun RemoteLoginScreen(onLoginSuccess: (UserSession) -> Unit, onAdminRegistration: () -> Unit) {
  var selectedRole by remember { mutableStateOf(UserRole.Admin) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var visible by remember { mutableStateOf(false) }
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HighDensityOnBackground,
    unfocusedTextColor = HighDensityOnBackground,
    focusedLabelColor = HighDensityPrimary,
    unfocusedLabelColor = Color(0xFF64748B),
    cursorColor = HighDensityPrimary,
    focusedLeadingIconColor = HighDensityPrimary,
    unfocusedLeadingIconColor = Color(0xFF64748B),
    focusedTrailingIconColor = HighDensityPrimary,
    unfocusedTrailingIconColor = Color(0xFF64748B),
    focusedBorderColor = HighDensityPrimary,
    unfocusedBorderColor = Color(0xFFCBD5E1),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White
  )

  Box(Modifier.fillMaxSize().background(HighDensityBackground).statusBarsPadding().navigationBarsPadding()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Spacer(Modifier.height(16.dp))
      Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(BrandGreen), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.School, contentDescription = "App Logo", tint = Color.White, modifier = Modifier.size(44.dp))
      }
      Spacer(Modifier.height(12.dp))
      Text("KP Data Sync", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = HighDensityOnBackground)
      Text("Data Collection And Management", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HighDensitySecondary)
      Spacer(Modifier.height(32.dp))
      Text("Select Role To Login", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
      Spacer(Modifier.height(12.dp))

      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserRole.values().forEach { role ->
          val selected = selectedRole == role
          Surface(
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = if (selected) Color(0xFFF3E8FF) else Color.White,
            border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) HighDensityPrimary else Color(0xFFE2E8F0))
          ) {
            Column(Modifier.fillMaxWidth().clickable { selectedRole = role; error = null }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                when (role) { UserRole.Admin -> Icons.Default.Shield; UserRole.Cluster_Head -> Icons.Default.Hub; UserRole.School_HM -> Icons.Default.AccountBalance; UserRole.Teacher -> Icons.Default.Person },
                contentDescription = null,
                tint = when (role) { UserRole.Admin -> Color(0xFFEF5350); UserRole.Cluster_Head -> Color(0xFF26A69A); UserRole.School_HM -> Color(0xFFFFA726); UserRole.Teacher -> Color(0xFF8D6E63) },
                modifier = Modifier.size(24.dp)
              )
              Spacer(Modifier.height(5.dp))
              Text(role.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
            }
          }
        }
      }

      Spacer(Modifier.height(20.dp))
      Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Info, contentDescription = null, tint = HighDensityPrimary, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text(selectedRole.description, fontSize = 11.sp, color = Color(0xFF475569), lineHeight = 15.sp)
        }
      }

      Spacer(Modifier.height(20.dp))
      OutlinedTextField(
        value = email,
        onValueChange = { email = it; error = null },
        label = { Text("नोंदणीकृत इमेल टाका.") },
        leadingIcon = { Icon(Icons.Default.Email, null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(Modifier.height(14.dp))
      OutlinedTextField(
        value = password,
        onValueChange = { password = it; error = null },
        label = { Text("आपला पासवर्ड टाका.") },
        leadingIcon = { Icon(Icons.Default.Lock, null) },
        trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth()
      )
      error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
      Spacer(Modifier.height(20.dp))
      Button(
        enabled = !loading,
        onClick = {
          if (email.isBlank() || password.isBlank()) { error = "नोंदणीकृत इमेल आणि पासवर्ड दोन्ही टाका."; return@Button }
          loading = true
          BackendApi.login(email.trim(), password, selectedRole.roleName,
            onSuccess = { session -> loading = false; onLoginSuccess(session) },
            onError = { message -> loading = false; error = message }
          )
        },
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
      ) {
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
        else Text("LOGIN AS ${selectedRole.displayName.uppercase()}", fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(12.dp))
      OutlinedButton(onClick = onAdminRegistration, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("FIRST-TIME APP ADMIN REGISTRATION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(18.dp))
      Text(
        "Made in ♥️ with Teacher By Sachin Nakode",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = HighDensitySecondary
      )
    }
  }
}
