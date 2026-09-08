package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
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

private enum class ProfileDialog { Settings, Personal, Password, Notifications, Language, MediaAutoDownload, Report, Work }
private enum class ProfileActionType { Schools, Users, Chats, Dialog }

private data class ProfileAction(val title: String, val subtitle: String, val icon: ImageVector, val tint: Color, val type: ProfileActionType, val dialog: ProfileDialog? = null)

@Composable
fun ProfileScreen(session: UserSession, onLogout: () -> Unit, onOpenSchools: () -> Unit, onOpenUsers: () -> Unit, onOpenChats: () -> Unit) {
  var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
  val initials = session.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "US" }
  LazyColumn(modifier = Modifier.fillMaxSize().background(HighDensityBackground), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ProfileHero(initials) { dialog = ProfileDialog.Settings } }
    item { Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(session.name, fontSize = 20.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(5.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF16C172))); Text("Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563))
        Surface(color = HighDensityPrimaryContainer, shape = RoundedCornerShape(14.dp)) { Text(session.role.displayName, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary) }
      }
    } }
    item { Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(14.dp), color = Color(0xFFF5EEFF)) { Text("“ ${roleQuote(session.role)} ”", Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HighDensityPrimary) } }
    item { ProfileDetails(session) }
    item { Text("व्यवस्थापन / कामे", Modifier.padding(horizontal = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground) }
    item { val actions = profileActions(session.role); Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      actions.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { action -> ManagementCard(action, Modifier.weight(1f)) { when (action.type) { ProfileActionType.Schools -> onOpenSchools(); ProfileActionType.Users -> onOpenUsers(); ProfileActionType.Chats -> onOpenChats(); ProfileActionType.Dialog -> dialog = action.dialog } } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
    } }
    item { Text("खाते व्यवस्थापन", Modifier.padding(horizontal = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground) }
    item { Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp), color = Color.White) { Column {
      ProfileSettingRow(Icons.Default.Person, "वैयक्तिक माहिती", "प्रोफाइल तपशील पहा आणि अपडेट करा") { dialog = ProfileDialog.Personal }
      ProfileSettingRow(Icons.Default.Lock, "पासवर्ड बदला", "तुमचा पासवर्ड सुरक्षित ठेवा") { dialog = ProfileDialog.Password }
      ProfileSettingRow(Icons.Default.Notifications, "सूचना सेटिंग्ज", "अॅप सूचना व्यवस्थापित करा") { dialog = ProfileDialog.Notifications }
      ProfileSettingRow(Icons.Default.CloudDownload, "Media auto-download", "मोबाईल डेटा, Wi-Fi आणि roaming") { dialog = ProfileDialog.MediaAutoDownload }
      ProfileSettingRow(Icons.Default.Language, "अॅप भाषा", "मराठी (डिफॉल्ट)", true) { dialog = ProfileDialog.Language }
    } } }
    item { Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFDE7E7), contentColor = Color(0xFFD92D20)), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Logout, null, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(8.dp)); Text("लॉगआउट", fontSize = 14.sp, fontWeight = FontWeight.Bold) } }
  }
  dialog?.let { ProfileDialogHost(it, session) { dialog = null } }
}

@Composable private fun ProfileHero(initials: String, onSettings: () -> Unit) { Column(Modifier.fillMaxWidth().background(HighDensityPrimaryContainer)) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { Text("प्रोफाइल", fontSize = 26.sp, fontWeight = FontWeight.Black, color = HighDensityOnPrimaryContainer); Text("तुमचे खाते व अॅप सेटिंग्ज", fontSize = 12.sp, color = Color(0xFF49454F)) }; IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "सेटिंग्ज", tint = HighDensityOnPrimaryContainer, modifier = Modifier.size(27.dp)) } }
  Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Box(Modifier.size(84.dp).clip(CircleShape).background(Color(0xFFF1E7FF)), contentAlignment = Alignment.Center) { Text(initials, fontSize = 29.sp, fontWeight = FontWeight.Black, color = Color(0xFF65479B)) } } }
} }

@Composable private fun ProfileDetails(session: UserSession) { Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(16.dp), color = Color.White, tonalElevation = 1.dp) { Column { ProfileInfoRow(Icons.Default.Email, "ईमेल", session.email); ProfileInfoRow(Icons.Default.Phone, "मोबाईल", session.mobile ?: "नोंद उपलब्ध नाही"); ProfileInfoRow(Icons.Default.Shield, "भूमिका", session.role.displayName); ProfileInfoRow(Icons.Default.AccountBalance, "केंद्र", session.clusterName ?: "नोंद उपलब्ध नाही"); ProfileInfoRow(Icons.Default.School, "शाळा", session.schoolName ?: "नोंद उपलब्ध नाही", true) } } }

@Composable private fun ProfileInfoRow(icon: ImageVector, label: String, value: String, last: Boolean = false) { Column { Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color(0xFF566173), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(label, Modifier.width(78.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF566173)); Text(value, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis) }; if (!last) HorizontalDivider(color = Color(0xFFF0F1F4)) } }

@Composable private fun ManagementCard(action: ProfileAction, modifier: Modifier, onClick: () -> Unit) { Surface(modifier = modifier.height(128.dp).clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFF0F0F5))) { Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) { Box(Modifier.size(44.dp).clip(CircleShape).background(action.tint.copy(alpha = 0.11f)), contentAlignment = Alignment.Center) { Icon(action.icon, null, tint = action.tint, modifier = Modifier.size(23.dp)) }; Text(action.title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C2F36), maxLines = 2, overflow = TextOverflow.Ellipsis); Text(action.subtitle, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 9.sp, color = Color(0xFF718096), maxLines = 2, overflow = TextOverflow.Ellipsis) } } }

@Composable private fun ProfileSettingRow(icon: ImageVector, title: String, subtitle: String, last: Boolean = false, onClick: () -> Unit) { Column { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFF1E7FF)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = HighDensityPrimary, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground); Text(subtitle, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748B)) }; if (!last) HorizontalDivider(Modifier.padding(start = 62.dp), color = Color(0xFFF0F1F4)) } }

private fun profileActions(role: UserRole): List<ProfileAction> = when (role) {
  UserRole.Admin -> listOf(ProfileAction("शाळा व्यवस्थापन", "शाळांची माहिती", Icons.Default.AccountBalance, Color(0xFF3B82F6), ProfileActionType.Schools), ProfileAction("वापरकर्ता व्यवस्थापन", "सर्व वापरकर्ते", Icons.Default.Person, HighDensityPrimary, ProfileActionType.Users), ProfileAction("ग्रुप व्यवस्थापन", "चॅट ग्रुप व सदस्य", Icons.Default.Groups, Color(0xFF16A34A), ProfileActionType.Chats), ProfileAction("System Settings", "अॅप सेटिंग्ज", Icons.Default.Settings, Color(0xFF2563EB), ProfileActionType.Dialog, ProfileDialog.Settings))
  UserRole.Cluster_Head -> listOf(ProfileAction("केंद्रीय शाळा", "शाळांची माहिती", Icons.Default.AccountBalance, Color(0xFF3B82F6), ProfileActionType.Schools), ProfileAction("केंद्रीय वापरकर्ते", "शिक्षक व प्रशासक", Icons.Default.Person, HighDensityPrimary, ProfileActionType.Users), ProfileAction("केंद्र ग्रुप", "चॅट ग्रुप व सदस्य", Icons.Default.Groups, Color(0xFF16A34A), ProfileActionType.Chats), ProfileAction("केंद्र अहवाल", "प्रगती व आकडेवारी", Icons.Default.BarChart, Color(0xFF2563EB), ProfileActionType.Dialog, ProfileDialog.Report))
  UserRole.School_HM -> listOf(ProfileAction("शाळा माहिती", "माझ्या शाळेची माहिती", Icons.Default.AccountBalance, Color(0xFF3B82F6), ProfileActionType.Schools), ProfileAction("शिक्षक व्यवस्थापन", "शिक्षक व कर्मचारी", Icons.Default.Person, HighDensityPrimary, ProfileActionType.Users), ProfileAction("शाळा ग्रुप", "चॅट ग्रुप व सदस्य", Icons.Default.Groups, Color(0xFF16A34A), ProfileActionType.Chats), ProfileAction("शाळा अहवाल", "दैनंदिन व प्रगती अहवाल", Icons.Default.BarChart, Color(0xFF2563EB), ProfileActionType.Dialog, ProfileDialog.Report))
  UserRole.Teacher -> listOf(ProfileAction("माझी शाळा", "शाळेची माहिती", Icons.Default.AccountBalance, Color(0xFF3B82F6), ProfileActionType.Schools), ProfileAction("शाळा सदस्य", "शिक्षक व सहकारी", Icons.Default.Person, HighDensityPrimary, ProfileActionType.Users), ProfileAction("शाळा ग्रुप", "चॅट व संवाद", Icons.Default.Groups, Color(0xFF16A34A), ProfileActionType.Chats), ProfileAction("दैनिक काम / अहवाल", "उपक्रम व नोंदी", Icons.Default.Description, Color(0xFF2563EB), ProfileActionType.Dialog, ProfileDialog.Work))
}

private fun roleQuote(role: UserRole): String = when (role) { UserRole.Admin -> "तंत्रज्ञानाच्या मदतीने उत्तम शिक्षण व्यवस्था साकारूया"; UserRole.Cluster_Head -> "प्रत्येक शाळा सक्षम, प्रत्येक विद्यार्थी प्रगत"; UserRole.School_HM -> "सक्षम शाळा, उज्ज्वल विद्यार्थ्यांचे भविष्य"; UserRole.Teacher -> "चांगला शिक्षक उत्तम समाज घडवतो" }

@Composable private fun ProfileDialogHost(dialog: ProfileDialog, session: UserSession, onDismiss: () -> Unit) { when (dialog) {
  ProfileDialog.Settings -> InfoDialog("System Settings", "अॅप सेटिंग्ज खाते व्यवस्थापनातून नियंत्रित करता येतील.", onDismiss)
  ProfileDialog.Personal -> InfoDialog("वैयक्तिक माहिती", "नाव: ${session.name}\nईमेल: ${session.email}\nभूमिका: ${session.role.displayName}\nमोबाईल: ${session.mobile ?: "नोंद उपलब्ध नाही"}\nकेंद्र: ${session.clusterName ?: "नोंद उपलब्ध नाही"}\nशाळा: ${session.schoolName ?: "नोंद उपलब्ध नाही"}", onDismiss)
  ProfileDialog.Password -> InfoDialog("पासवर्ड बदला", "सुरक्षित खाते प्रक्रिया वापरून पासवर्ड बदलता येईल. सध्या backend password-change endpoint उपलब्ध नसल्याने येथे कोणताही खोटा बदल केला जात नाही.", onDismiss)
  ProfileDialog.Notifications -> NotificationSettingsDialog(onDismiss)
  ProfileDialog.Language -> LanguageDialog(onDismiss)
  ProfileDialog.MediaAutoDownload -> MediaAutoDownloadDialog(onDismiss)
  ProfileDialog.Report -> InfoDialog("अहवाल", "तुमच्या भूमिकेनुसार अहवाल विभाग उपलब्ध आहे. Live report workflow जोडला गेल्यावर येथून थेट उघडता येईल.", onDismiss)
  ProfileDialog.Work -> InfoDialog("दैनिक काम / अहवाल", "दैनिक उपक्रम आणि नोंदींसाठी कार्यक्षेत्र येथे जोडता येईल. सध्या उपलब्ध Chats, Schools आणि Users मधून संबंधित कामे करता येतात.", onDismiss)
} }

@Composable private fun NotificationSettingsDialog(onDismiss: () -> Unit) { var enabled by remember { mutableStateOf(true) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("सूचना सेटिंग्ज") }, text = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("अॅप सूचना", fontWeight = FontWeight.Bold); Text("नवीन संदेश व प्रणाली सूचना", fontSize = 12.sp, color = Color(0xFF64748B)) }; Switch(checked = enabled, onCheckedChange = { enabled = it }) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("सेव्ह") } }) }

@Composable private fun LanguageDialog(onDismiss: () -> Unit) { var selected by remember { mutableStateOf("मराठी") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("अॅप भाषा") }, text = { Column { listOf("मराठी", "English").forEach { language -> Row(Modifier.fillMaxWidth().clickable { selected = language }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected == language, onClick = { selected = language }); Text(language) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("सेव्ह") } }) }

@Composable private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text("ठीक आहे") } }) }
