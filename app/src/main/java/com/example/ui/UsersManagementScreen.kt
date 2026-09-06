package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.data.UserDirectoryApi
import com.example.model.UserRecord
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.theme.*

@Composable
fun UsersTabContent(session: UserSession, onRegisterUser: () -> Unit) {
  var records by remember { mutableStateOf<List<UserRecord>>(emptyList()) }
  var search by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf("all") }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var selected by remember { mutableStateOf<UserRecord?>(null) }
  var editing by remember { mutableStateOf<UserRecord?>(null) }
  var deleting by remember { mutableStateOf<UserRecord?>(null) }
  var busy by remember { mutableStateOf<String?>(null) }

  fun reload() {
    loading = true; error = null
    UserDirectoryApi.getUsers(session.token, { records = it; loading = false }, { error = it; loading = false })
  }
  LaunchedEffect(session.id) { reload() }

  val counts = UserRole.values().associateWith { role -> records.count { it.role == role } }
  val filtered = records.filter { user ->
    val matchesFilter = filter == "all" || user.role.roleName == filter
    val q = search.trim()
    matchesFilter && (q.isBlank() || listOf(user.name, user.email, user.mobile, user.schoolName, user.clusterName, user.role.displayName).any { it.contains(q, true) })
  }
  val canRegister = session.role != UserRole.Teacher
  val canManage = session.role != UserRole.Teacher

  LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(bottom = 22.dp)) {
    item {
      Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("नोंदणीकृत वापरकर्ते", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033))
          Text(scopeText(session), fontSize = 11.sp, color = Color(0xFF64748B))
        }
        IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Refresh", tint = HighDensityPrimary) }
      }
    }

    item {
      Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
          Surface(color = Color(0xFFEDE7F6), shape = CircleShape) {
            Icon(if (session.role == UserRole.Teacher) Icons.Default.School else Icons.Default.Security, null, tint = HighDensityPrimary, modifier = Modifier.padding(11.dp).size(25.dp))
          }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(roleHeading(session.role), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033))
            Text(roleDescription(session.role, session), fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 15.sp)
          }
        }
      }
    }

    if (canRegister) {
      item {
        Surface(onClick = onRegisterUser, Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color(0xFFF5F1FF), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3D8FF))) {
          Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = HighDensityPrimary, shape = CircleShape) { Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.padding(11.dp).size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(registrationTitle(session.role), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E176E))
              Text(registrationInfo(session.role), fontSize = 11.sp, color = Color(0xFF5B4B78), lineHeight = 15.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = HighDensityPrimary)
          }
        }
      }
    } else {
      item {
        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color(0xFFF8FAFC)) {
          Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = Color(0xFF64748B), modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
            Text("आपल्या शाळेतील वापरकर्त्यांची माहिती येथे पाहता येईल. नवीन भूमिका नोंदणी करण्याचा अधिकार नाही.", fontSize = 11.sp, color = Color(0xFF475569))
          }
        }
      }
    }

    item {
      Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = Color.White) {
        Column(Modifier.padding(12.dp)) {
          Text("वापरकर्ते शोधा", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
          Spacer(Modifier.height(6.dp))
          OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), placeholder = { Text("नाव, ईमेल, शाळा किंवा केंद्र शोधा…") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, "Clear") } })
        }
      }
    }

    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        RoleFilter("सर्व", records.size, filter == "all") { filter = "all" }
        RoleFilter("अधिकारी", counts[UserRole.Cluster_Head] ?: 0, filter == UserRole.Cluster_Head.roleName) { filter = UserRole.Cluster_Head.roleName }
        RoleFilter("शाळा प्रशासक", counts[UserRole.School_HM] ?: 0, filter == UserRole.School_HM.roleName) { filter = UserRole.School_HM.roleName }
        RoleFilter("शिक्षक", counts[UserRole.Teacher] ?: 0, filter == UserRole.Teacher.roleName) { filter = UserRole.Teacher.roleName }
      }
    }

    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text("नोंदणीकृत वापरकर्ते", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033)); Text("${filtered.size} वापरकर्ते दिसत आहेत", fontSize = 10.sp, color = Color(0xFF64748B)) }
        if (session.role == UserRole.Admin) Text("सर्व स्तर", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
      }
    }

    if (loading) item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    else if (error != null) item { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = Color(0xFFFFEBEE)) { Column(Modifier.padding(14.dp)) { Text("वापरकर्त्यांची माहिती मिळवता आली नाही", fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C)); Text(error.orEmpty(), fontSize = 11.sp, color = Color(0xFF7F1D1D)); TextButton(onClick = { reload() }) { Text("पुन्हा प्रयत्न करा") } } } }
    else if (filtered.isEmpty()) item { EmptyUsersState(search.isNotBlank()) }
    else items(filtered, key = { it.id }) { user ->
      UserDirectoryCard(user, canManage, busy == user.id, onClick = { selected = user }, onEdit = { editing = user }, onToggle = { busy = user.id; UserDirectoryApi.setUserStatus(session.token, user.id, user.status != "Active", { busy = null; reload() }, { error = it; busy = null }) }, onDelete = { deleting = user })
    }
  }

  selected?.let { UserInfoDialog(it) { selected = null } }
  editing?.let { user -> UserEditDialog(user, session.token, { editing = null; reload() }, { editing = null }) }
  deleting?.let { user ->
    AlertDialog(onDismissRequest = { deleting = null }, title = { Text("वापरकर्ता हटवायचा आहे?") }, text = { Text("‘${user.name}’ खाते कायमचे हटवले जाईल. संबंधित नोंदी असल्यास system delete नाकारेल; अशावेळी खाते निष्क्रिय करा.") }, confirmButton = { TextButton(onClick = { deleting = null; busy = user.id; UserDirectoryApi.deleteUser(session.token, user.id, { busy = null; reload() }, { busy = null; error = it }) }) { Text("हटवा", color = Color(0xFFC62828), fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("रद्द करा") } })
  }
}

@Composable private fun RoleFilter(label: String, count: Int, selected: Boolean, onClick: () -> Unit) { FilterChip(selected = selected, onClick = onClick, label = { Text("$label $count", fontSize = 9.sp) }) }

@Composable private fun UserDirectoryCard(user: UserRecord, canManage: Boolean, busy: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
  Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
    Column(Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = roleBg(user.role), shape = CircleShape) { Icon(roleIcon(user.role), null, tint = roleTint(user.role), modifier = Modifier.padding(10.dp).size(23.dp)) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
          Text(user.name, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033), maxLines = 1, overflow = TextOverflow.Ellipsis)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(user.role.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = roleTint(user.role))
            Surface(color = if (user.status == "Active") Color(0xFFE8F5E9) else Color(0xFFFFEBEE), shape = RoundedCornerShape(7.dp)) { Text(if (user.status == "Active") "● सक्रिय" else "● निष्क्रिय", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (user.status == "Active") Color(0xFF137333) else Color(0xFFC62828), modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) }
          }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF94A3B8))
      }
      Spacer(Modifier.height(9.dp))
      Text("✉  ${user.email}", fontSize = 10.sp, color = Color(0xFF475569), maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (user.mobile.isNotBlank()) Text("☎  ${user.mobile}", fontSize = 10.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 4.dp))
      if (user.schoolName.isNotBlank()) Text("शाळा: ${user.schoolName} • ${user.schoolCode}", fontSize = 10.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 4.dp))
      if (user.clusterName.isNotBlank()) Text("केंद्र: ${user.clusterName} • ${user.clusterCode}", fontSize = 10.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 4.dp))
      if (canManage) {
        Spacer(Modifier.height(7.dp)); HorizontalDivider(color = Color(0xFFE2E8F0))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF5E35B1)) }
          IconButton(onClick = onToggle, enabled = !busy) { Icon(if (user.status == "Active") Icons.Default.ToggleOn else Icons.Default.ToggleOff, "Status", tint = if (user.status == "Active") Color(0xFFC62828) else Color(0xFF2E7D32)) }
          IconButton(onClick = onDelete, enabled = !busy) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFC62828)) }
        }
      }
    }
  }
}

@Composable private fun UserInfoDialog(user: UserRecord, onClose: () -> Unit) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff)) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), color = Color.White) {
      Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = roleBg(user.role), shape = CircleShape) { Icon(roleIcon(user.role), null, tint = roleTint(user.role), modifier = Modifier.padding(15.dp).size(34.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Surface(color = Color(0xFFF5F1FF), shape = RoundedCornerShape(10.dp)) { Text("${user.role.displayName} (${roleMarathi(user.role)})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = roleTint(user.role), modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFE2E8F0))
        InfoLine("ईमेल (Email)", user.email); InfoLine("मोबाईल (Mobile)", user.mobile.ifBlank { "—" }); InfoLine("शाळा (School)", user.schoolName.ifBlank { "—" }); InfoLine("केंद्र (Cluster)", user.clusterName.ifBlank { "—" }); InfoLine("खाते स्थिती (Status)", if (user.status == "Active") "सक्रिय (Active)" else "निष्क्रिय (Inactive)"); InfoLine("नोंदणी तारीख (Created)", user.createdAt.take(10).ifBlank { "—" })
        Spacer(Modifier.height(15.dp))
        Button(onClick = onClose, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = HighDensityPrimary)) { Text("बंद करा (Close)", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
      }
    }
  }
}

@Composable private fun InfoLine(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B)); Spacer(Modifier.width(12.dp)); Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172033), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End) } }

@Composable private fun UserEditDialog(user: UserRecord, token: String, onSaved: () -> Unit, onClose: () -> Unit) {
  var name by remember(user.id) { mutableStateOf(user.name) }; var mobile by remember(user.id) { mutableStateOf(user.mobile) }; var address by remember(user.id) { mutableStateOf(user.address) }; var error by remember { mutableStateOf<String?>(null) }; var saving by remember { mutableStateOf(false) }
  AlertDialog(onDismissRequest = onClose, title = { Text("वापरकर्ता माहिती संपादित करा", fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("नाव") }, singleLine = true); OutlinedTextField(mobile, { mobile = it }, label = { Text("मोबाईल") }, singleLine = true); OutlinedTextField(address, { address = it }, label = { Text("पत्ता") }, singleLine = false); error?.let { Text(it, color = Color(0xFFC62828), fontSize = 11.sp) } } }, confirmButton = { TextButton(enabled = !saving, onClick = { if (name.trim().length < 2) { error = "कृपया योग्य नाव द्या"; return@TextButton }; saving = true; UserDirectoryApi.updateUser(token, user.id, name.trim(), mobile.trim(), address.trim(), { saving = false; onSaved() }, { saving = false; error = it }) }) { Text(if (saving) "जतन…" else "जतन करा", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onClose) { Text("रद्द करा") } })
}

@Composable private fun EmptyUsersState(hasSearch: Boolean) { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.White) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Groups, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(42.dp)); Spacer(Modifier.height(8.dp)); Text(if (hasSearch) "शोधानुसार वापरकर्ता सापडला नाही" else "अजून वापरकर्ते नोंदणीकृत नाहीत", fontWeight = FontWeight.Bold); Text(if (hasSearch) "शोध शब्द बदलून पुन्हा प्रयत्न करा." else "आपल्या अधिकारानुसार नवीन वापरकर्ता नोंदणी करता येईल.", fontSize = 11.sp, color = Color(0xFF64748B)) } } }

private fun scopeText(s: UserSession) = when (s.role) { UserRole.Admin -> "सर्व केंद्रे, शाळा व वापरकर्त्यांची माहिती"; UserRole.Cluster_Head -> "आपले केंद्र • संबंधित शाळा, मुख्याध्यापक व शिक्षक"; UserRole.School_HM -> "आपली शाळा • मुख्याध्यापक व शिक्षक"; UserRole.Teacher -> "आपली शाळा • उपलब्ध वापरकर्ता माहिती" }
private fun roleHeading(r: UserRole) = when (r) { UserRole.Admin -> "App Admin • संपूर्ण व्यवस्थापन"; UserRole.Cluster_Head -> "Cluster Head • केंद्र व्यवस्थापन"; UserRole.School_HM -> "School HM • शाळा व्यवस्थापन"; UserRole.Teacher -> "Teacher • शाळा माहिती" }
private fun roleDescription(r: UserRole, s: UserSession) = when (r) { UserRole.Admin -> "सर्व वापरकर्ते, भूमिका, स्थिती आणि संबंधित माहिती एका ठिकाणी."; UserRole.Cluster_Head -> "${s.clusterName ?: "आपल्या केंद्राची"} हद्दीतील वापरकर्ते येथे दिसतील."; UserRole.School_HM -> "${s.schoolName ?: "आपल्या शाळेतील"} वापरकर्त्यांची माहिती येथे दिसेल."; UserRole.Teacher -> "आपल्या शाळेशी संबंधित माहितीच येथे दाखवली जाईल." }
private fun registrationTitle(r: UserRole) = when (r) { UserRole.Admin -> "नवीन वापरकर्ता नोंदणी"; UserRole.Cluster_Head -> "केंद्रातील वापरकर्ता नोंदणी"; UserRole.School_HM -> "शाळेतील शिक्षक नोंदणी"; UserRole.Teacher -> "" }
private fun registrationInfo(r: UserRole) = when (r) { UserRole.Admin -> "Cluster Head, School HM किंवा Teacher खाते तयार करा. प्रत्येक खाते योग्य केंद्र/शाळेशी जोडले जाईल."; UserRole.Cluster_Head -> "आपल्या केंद्रातील School HM किंवा Teacher नोंदवा. इतर केंद्रातील खाते तयार करता येणार नाही."; UserRole.School_HM -> "आपल्या शाळेसाठी Teacher खाते तयार करा. दुसऱ्या शाळेतील खाते तयार करता येणार नाही."; UserRole.Teacher -> "" }
private fun roleMarathi(r: UserRole) = when (r) { UserRole.Admin -> "अॅप प्रशासक"; UserRole.Cluster_Head -> "केंद्रप्रमुख"; UserRole.School_HM -> "शाळा प्रशासक"; UserRole.Teacher -> "शिक्षक" }
private fun roleIcon(r: UserRole) = when (r) { UserRole.Admin -> Icons.Default.AdminPanelSettings; UserRole.Cluster_Head -> Icons.Default.SupervisorAccount; UserRole.School_HM -> Icons.Default.AccountBalance; UserRole.Teacher -> Icons.Default.School }
private fun roleTint(r: UserRole) = when (r) { UserRole.Admin -> Color(0xFF5E35B1); UserRole.Cluster_Head -> Color(0xFF00897B); UserRole.School_HM -> Color(0xFFF57C00); UserRole.Teacher -> Color(0xFF1565C0) }
private fun roleBg(r: UserRole) = when (r) { UserRole.Admin -> Color(0xFFEDE7F6); UserRole.Cluster_Head -> Color(0xFFE0F2F1); UserRole.School_HM -> Color(0xFFFFF3E0); UserRole.Teacher -> Color(0xFFE3F2FD) }
