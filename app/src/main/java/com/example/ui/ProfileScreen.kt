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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityOnBackground
import com.example.ui.theme.HighDensityOnPrimaryContainer
import com.example.ui.theme.HighDensityPrimary
import com.example.ui.theme.HighDensityPrimaryContainer

private data class ProfileAction(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val tint: Color,
  val onClick: () -> Unit
)

@Composable
fun ProfileScreen(
  session: UserSession,
  onLogout: () -> Unit,
  onOpenSchools: () -> Unit,
  onOpenUsers: () -> Unit,
  onOpenChats: () -> Unit
) {
  var showSettings by remember { mutableStateOf(false) }
  var showPersonalInfo by remember { mutableStateOf(false) }
  var showPasswordInfo by remember { mutableStateOf(false) }
  var showNotificationInfo by remember { mutableStateOf(false) }
  var showLanguageInfo by remember { mutableStateOf(false) }
  var showWorkInfo by remember { mutableStateOf(false) }

  val initials = session.name.trim().split(" ").filter { it.isNotBlank() }.take(2)
    .mapNotNull { it.firstOrNull()?.toString() }.joinToString("").ifEmpty { "US" }

  val quote = when (session.role) {
    UserRole.Admin -> "तंत्रज्ञानाच्या मदतीने उत्तम शिक्षण व्यवस्था साकारूया"
    UserRole.Cluster_Head -> "प्रत्येक शाळा सक्षम, प्रत्येक विद्यार्थी प्रगत"
    UserRole.School_HM -> "सक्षम शाळा, उज्ज्वल विद्यार्थ्यांचे भविष्य"
    UserRole.Teacher -> "चांगला शिक्षक उत्तम समाज घडवतो"
  }

  val managementActions = when (session.role) {
    UserRole.Admin -> listOf(
      ProfileAction("शाळा व्यवस्थापन", "शाळांची माहिती", Icons.Default.AccountBalance, Color(0xFF3B82F6), onOpenSchools),
      ProfileAction("वापरकर्ता व्यवस्थापन", "सर्व वापरकर्ते", Icons.Default.Person, HighDensityPrimary, onOpenUsers),
      ProfileAction("ग्रुप व्यवस्थापन", "चॅट ग्रुप व सदस्य", Icons.Default.Groups, Color(0xFF16A34A), onOpenChats),
      ProfileAction("System Settings", "अॅप सेटिंग्ज", Icons.Default.Settings, Color(0xFF2563EB)) { showSettings = true }
    )
    UserRole.Cluster_Head -> listOf(
      ProfileAction("केंद्रातील शाळा", "शाळांची माहिती", Icons.Default.AccountBalance, Color(0xFF3B82F6), onOpenSchools),
      ProfileAction("केंद्रातील वापरकर्ते", "शिक्षक व प्रशासक", Icons.Default.Person, HighDensityPrimary, onOpenUsers),
      ProfileAction("केंद्र ग्रुप", "चॅट ग्रुप व सदस्य", Icons.Default.Groups, Color(0xFF16A34A), onOpenChats),
      ProfileAction("केंद्र अहवाल", "प्रगती व आकडेवारी", Icons.Default.BarChart, Color(0xFF2563EB)) { showWorkInfo = true }
    )
    UserRole.School_HM -> listOf(
      ProfileAction("शाळा माहिती", "माझ्या शाळेची माहिती", Icons.Default.AccountBalance, Color(0xFF3B82F6), onOpenSchools),
      ProfileAction("शिक्षक व्यवस्थापन", "शिक्षक व कर्मचारी", Icons.Default.Person, HighDensityPrimary, onOpenUsers),
      ProfileAction("शाळा ग्रुप", "चॅट ग्रुप व सदस्य", Icons.Default.Groups, Color(0xFF16A34A), onOpenChats),
      ProfileAction("शाळा अहवाल", "दैनंदिन व प्रगती अहवाल", Icons.Default.BarChart, Color(0xFF2563EB)) { showWorkInfo = true }
    )
    UserRole.Teacher -> listOf(
      ProfileAction("माझी शाळा", "शाळेची माहिती", Icons.Default.AccountBalance, Color(0xFF3B82F6), onOpenSchools),
      ProfileAction("शाळा सदस्य", "शिक्षक व सहकारी", Icons.Default.Person, HighDensityPrimary, onOpenUsers),
      ProfileAction("शाळा ग्रुप", "चॅट व संवाद", Icons.Default.Groups, Color(0xFF16A34A), onOpenChats),
      ProfileAction("दैनिक काम / अहवाल", "उपक्रम व नोंदी", Icons.Default.Description, Color(0xFF2563EB)) { showWorkInfo = true }
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(HighDensityBackground),
    contentPadding = PaddingValues(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Box(Modifier.fillMaxWidth()) {
        Column(
          Modifier.fillMaxWidth().background(HighDensityPrimaryContainer).padding(horizontal = 20.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
              Text("प्रोफाइल", fontSize = 26.sp, fontWeight = FontWeight.Black, color = HighDensityOnPrimaryContainer)
              Text("तुमचे खाते व अॅप सेटिंग्ज", fontSize = 12.sp, color = Color(0xFF49454F))
            }
            IconButton(onClick = { showSettings = true }) {
              Icon(Icons.Default.Settings, "Settings", tint = HighDensityOnPrimaryContainer)
            }
          }
          Spacer(Modifier.height(8.dp))
          Box(
            Modifier.size(82.dp).align(Alignment.CenterHorizontally).clip(CircleShape)
              .background(Color.White),
            contentAlignment = Alignment.Center
          ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFF1E7FF)), contentAlignment = Alignment.Center) {
              Text(initials, fontSize = 28.sp, fontWeight = FontWeight.Black, color = HighDensityPrimary)
            }
          }
        }
      }
    }

    item {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(session.name, fontSize = 20.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
          Text("Active", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF49454F))
          Surface(color = HighDensityPrimaryContainer, shape = RoundedCornerShape(14.dp)) {
            Text(session.role.displayName, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
          }
        }
      }
    }

    item {
      Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(14.dp), color = Color(0xFFF5EEFF)) {
        Text("“ $quote ”", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HighDensityPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
      }
    }

    item {
      Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp), color = Color.White, tonalElevation = 1.dp) {
        Column {
          ProfileInfoRow(Icons.Default.Email, "ईमेल", session.email)
          ProfileInfoRow(Icons.Default.Phone, "मोबाईल", "नोंदणीकृत मोबाइल")
          ProfileInfoRow(Icons.Default.Shield, "भूमिका", session.role.displayName)
          if (!session.clusterName.isNullOrBlank()) ProfileInfoRow(Icons.Default.AccountBalance, "केंद्र", session.clusterName!!)
          if (!session.schoolName.isNullOrBlank()) ProfileInfoRow(Icons.Default.School, "शाळा", session.schoolName!!)
        }
      }
    }

    item {
      Text("व्यवस्थापन / कामे", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
    }

    item {
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ManagementCard(managementActions[0], Modifier.weight(1f))
        ManagementCard(managementActions[1], Modifier.weight(1f))
      }
    }
    item {
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ManagementCard(managementActions[2], Modifier.weight(1f))
        ManagementCard(managementActions[3], Modifier.weight(1f))
      }
    }

    item {
      Text("खाते व्यवस्थापन", modifier = Modifier.padding(horizontal = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
    }

    item {
      Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp), color = Color.White) {
        Column {
          ProfileSettingRow(Icons.Default.Person, "वैयक्तिक माहिती", "प्रोफाइल तपशील पहा आणि अपडेट करा") { showPersonalInfo = true }
          ProfileSettingRow(Icons.Default.Lock, "पासवर्ड बदला", "तुमचा पासवर्ड सुरक्षित ठेवा") { showPasswordInfo = true }
          ProfileSettingRow(Icons.Default.Notifications, "सूचना सेटिंग्ज", "अॅप सूचना व्यवस्थापित करा") { showNotificationInfo = true }
          ProfileSettingRow(Icons.Default.Language, "अॅप भाषा", "मराठी (डिफॉल्ट)") { showLanguageInfo = true }
        }
      }
    }

    item {
      Button(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFDE7E7), contentColor = Color(0xFFD92D20)),
        shape = RoundedCornerShape(12.dp)
      ) {
        Icon(Icons.Default.Logout, null)
        Spacer(Modifier.width(8.dp))
        Text("लॉगआउट", fontWeight = FontWeight.Bold)
      }
    }
  }

  if (showSettings) InfoDialog("अॅप सेटिंग्ज", "या विभागात अॅपच्या सामान्य सेटिंग्ज व्यवस्थापित करता येतील.") { showSettings = false }
  if (showPersonalInfo) InfoDialog("वैयक्तिक माहिती", "नाव: ${session.name}\nईमेल: ${session.email}\nभूमिका: ${session.role.displayName}") { showPersonalInfo = false }
  if (showPasswordInfo) InfoDialog("पासवर्ड बदला", "पासवर्ड बदलण्यासाठी सुरक्षित खाते प्रक्रिया वापरली जाईल.") { showPasswordInfo = false }
  if (showNotificationInfo) InfoDialog("सूचना सेटिंग्ज", "सध्या सूचना सेटिंग्जची माहिती येथे उपलब्ध आहे.") { showNotificationInfo = false }
  if (showLanguageInfo) InfoDialog("अॅप भाषा", "सध्या मराठी (डिफॉल्ट) भाषा सक्रिय आहे.") { showLanguageInfo = false }
  if (showWorkInfo) InfoDialog("लवकरच उपलब्ध", "या role-specific कामासाठी स्वतंत्र कार्यप्रवाह पुढील टप्प्यात जोडता येईल. सध्याच्या उपलब्ध management screens मधून काम सुरू ठेवू शकता.") { showWorkInfo = false }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = Color(0xFF4B5563), modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
    Text(label, modifier = Modifier.width(78.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
    Text(value, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun ManagementCard(action: ProfileAction, modifier: Modifier) {
  Surface(
    modifier = modifier.clickable(onClick = action.onClick),
    shape = RoundedCornerShape(14.dp),
    color = Color.White,
    tonalElevation = 1.dp
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
      Box(Modifier.size(38.dp).clip(CircleShape).background(action.tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        Icon(action.icon, null, tint = action.tint, modifier = Modifier.size(21.dp))
      }
      Text(action.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(action.subtitle, fontSize = 9.sp, color = Color(0xFF64748B), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun ProfileSettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF1E7FF)), contentAlignment = Alignment.Center) {
      Icon(icon, null, tint = HighDensityPrimary, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
      Text(subtitle, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748B))
  }
}

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("ठीक आहे") } }
  )
}
