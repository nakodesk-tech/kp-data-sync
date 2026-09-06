package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SyncRepository
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.theme.*

@Composable
fun LoginScreen(
  onLoginSuccess: (UserSession) -> Unit
) {
  var selectedRole by remember { mutableStateOf(UserRole.Admin) }
  var email by remember { mutableStateOf("admin@kpdatasync.com") }
  var password by remember { mutableStateOf("Password@123") }
  var passwordVisible by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isLoading by remember { mutableStateOf(false) }

  // Update prefilled email when role changes
  LaunchedEffect(selectedRole) {
    val matched = SyncRepository.demoAccounts.find { it.role == selectedRole }
    if (matched != null) {
      email = matched.email
      password = "Password@123"
      errorMessage = null
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(HighDensityBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      // App Logo & Title (matching PDF Screen 1)
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(RoundedCornerShape(20.dp))
          .background(Color(0xFF00897B)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.School,
          contentDescription = "App Logo",
          tint = Color.White,
          modifier = Modifier.size(44.dp)
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "KP Data Sync",
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        color = HighDensityOnBackground,
        letterSpacing = (-0.5).sp
      )

      Text(
        text = "Data Collection And Management",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = HighDensitySecondary,
        modifier = Modifier.padding(top = 2.dp)
      )

      Spacer(modifier = Modifier.height(32.dp))

      // Section Title: Select Role To Login
      Text(
        text = "Select Role To Login",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = HighDensityPrimary,
        modifier = Modifier.padding(bottom = 12.dp)
      )

      // Role Selection Grid (4 Roles matching PDF)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        RoleCard(
          modifier = Modifier.weight(1f),
          role = UserRole.Admin,
          isSelected = selectedRole == UserRole.Admin,
          icon = Icons.Default.Shield,
          badgeColor = Color(0xFFEF5350),
          onClick = { selectedRole = UserRole.Admin }
        )
        RoleCard(
          modifier = Modifier.weight(1f),
          role = UserRole.Cluster_Head,
          isSelected = selectedRole == UserRole.Cluster_Head,
          icon = Icons.Default.Hub,
          badgeColor = Color(0xFF26A69A),
          onClick = { selectedRole = UserRole.Cluster_Head }
        )
        RoleCard(
          modifier = Modifier.weight(1f),
          role = UserRole.School_HM,
          isSelected = selectedRole == UserRole.School_HM,
          icon = Icons.Default.AccountBalance,
          badgeColor = Color(0xFFFFA726),
          onClick = { selectedRole = UserRole.School_HM }
        )
        RoleCard(
          modifier = Modifier.weight(1f),
          role = UserRole.Teacher,
          isSelected = selectedRole == UserRole.Teacher,
          icon = Icons.Default.Person,
          badgeColor = Color(0xFF8D6E63),
          onClick = { selectedRole = UserRole.Teacher }
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Role description card
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = HighDensityPrimary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = selectedRole.description,
            fontSize = 11.sp,
            color = Color(0xFF475569),
            lineHeight = 15.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Input Form (matching PDF fields)
      OutlinedTextField(
        value = email,
        onValueChange = {
          email = it
          errorMessage = null
        },
        label = { Text("E-Mail Address", fontSize = 13.sp) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Email,
            contentDescription = "Email Icon",
            tint = HighDensitySecondary
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Email,
          imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = HighDensityPrimary,
          unfocusedBorderColor = Color(0xFFCBD5E1),
          focusedContainerColor = Color.White,
          unfocusedContainerColor = Color.White
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("email_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = password,
        onValueChange = {
          password = it
          errorMessage = null
        },
        label = { Text("Password", fontSize = 13.sp) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Password Icon",
            tint = HighDensitySecondary
          )
        },
        trailingIcon = {
          IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
              imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = "Toggle password visibility",
              tint = Color(0xFF64748B)
            )
          }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Password,
          imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = {
          performLogin(selectedRole, email, password, { isLoading = it }, { errorMessage = it }, onLoginSuccess)
        }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = HighDensityPrimary,
          unfocusedBorderColor = Color(0xFFCBD5E1),
          focusedContainerColor = Color.White,
          unfocusedContainerColor = Color.White
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("password_input")
      )

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = errorMessage ?: "",
          color = MaterialTheme.colorScheme.error,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // "LOGIN AS ${Selected Role}" button (matching PDF Screen 1)
      Button(
        onClick = {
          performLogin(selectedRole, email, password, { isLoading = it }, { errorMessage = it }, onLoginSuccess)
        },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF00897B),
          contentColor = Color.White
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("login_button")
      ) {
        if (isLoading) {
          CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
        } else {
          Text(
            text = "LOGIN AS ${selectedRole.displayName.uppercase()}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Cloudflare Backend badge
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEADDFF),
        modifier = Modifier.padding(bottom = 12.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(Color(0xFF00C853))
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Cloudflare D1 (SQLite) • R2 Storage • Hono.js",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF21005D)
          )
        }
      }
    }
  }
}

@Composable
private fun RoleCard(
  modifier: Modifier = Modifier,
  role: UserRole,
  isSelected: Boolean,
  icon: ImageVector,
  badgeColor: Color,
  onClick: () -> Unit
) {
  val borderColor = if (isSelected) HighDensityPrimary else Color(0xFFE2E8F0)
  val backgroundColor = if (isSelected) Color(0xFFF3E8FF) else Color.White

  Surface(
    modifier = modifier
      .clickable { onClick() }
      .clip(RoundedCornerShape(16.dp))
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(16.dp)
      ),
    color = backgroundColor,
    shadowElevation = if (isSelected) 2.dp else 0.dp
  ) {
    Column(
      modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(badgeColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = role.displayName,
          tint = badgeColor,
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = role.displayName,
        fontSize = 11.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = if (isSelected) HighDensityPrimary else HighDensityOnBackground,
        textAlign = TextAlign.Center,
        maxLines = 2,
        lineHeight = 13.sp
      )
    }
  }
}

private fun performLogin(
  role: UserRole,
  email: String,
  pass: String,
  setLoading: (Boolean) -> Unit,
  setError: (String?) -> Unit,
  onSuccess: (UserSession) -> Unit
) {
  if (email.isBlank() || pass.isBlank()) {
    setError("Please enter both email and password")
    return
  }

  // Find user matching role or email
  val user = SyncRepository.demoAccounts.find {
    it.email.equals(email.trim(), ignoreCase = true)
  } ?: SyncRepository.demoAccounts.find { it.role == role }

  if (user != null) {
    onSuccess(user.copy(role = role))
  } else {
    setError("Invalid credentials for $role")
  }
}
