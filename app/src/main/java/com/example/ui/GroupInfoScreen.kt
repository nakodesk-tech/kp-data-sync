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
import com.example.data.BackendApi
import com.example.data.GroupMemberManagementApi
import com.example.data.ManagedGroupMember
import com.example.model.*
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityOnBackground
import com.example.ui.theme.HighDensityPrimary
import com.example.ui.theme.HighDensityPrimaryContainer

@Composable
fun GroupInfoScreen(group: ChatGroup, session: UserSession, onBack: () -> Unit) {
  var detail by remember(group.id) { mutableStateOf<GroupInfo?>(null) }
  var managedMembers by remember(group.id) { mutableStateOf<List<ManagedGroupMember>>(emptyList()) }
  var error by remember(group.id) { mutableStateOf<String?>(null) }
  var loading by remember(group.id) { mutableStateOf(true) }
  var groupIsActive by remember(group.id) { mutableStateOf(group.isActive) }
  var manage by remember { mutableStateOf(false) }
  var manageLoading by remember { mutableStateOf(false) }
  var lifecycleLoading by remember { mutableStateOf(false) }
  var showCloseWarning by remember { mutableStateOf(false) }
  var editName by remember { mutableStateOf("") }
  var editDescription by remember { mutableStateOf("") }
  var showEdit by remember { mutableStateOf(false) }
  var showAdd by remember { mutableStateOf(false) }
  var actionError by remember { mutableStateOf<String?>(null) }

  fun reload() {
    loading = true
    BackendApi.getGroupInfo(session.token, group.id,
      onSuccess = { info -> detail = info.copy(isActive = groupIsActive); editName = info.name; editDescription = info.description.orEmpty(); loading = false; error = null },
      onError = { error = it; loading = false }
    )
  }

  fun reloadManagedMembers() {
    manageLoading = true
    GroupMemberManagementApi.getMembers(session.token, group.id,
      onSuccess = { managedMembers = it; manageLoading = false },
      onError = { actionError = it; manageLoading = false }
    )
  }

  fun changeGroupActiveState(active: Boolean) {
    if (lifecycleLoading) return
    lifecycleLoading = true
    actionError = null
    if (active) {
      GroupMemberManagementApi.reactivateGroup(session.token, group.id,
        onSuccess = { groupIsActive = true; lifecycleLoading = false; reload(); reloadManagedMembers() },
        onError = { lifecycleLoading = false; actionError = it }
      )
    } else {
      BackendApi.deactivateGroup(session.token, group.id,
        onSuccess = { groupIsActive = false; lifecycleLoading = false; reload() },
        onError = { lifecycleLoading = false; actionError = it }
      )
    }
  }

  LaunchedEffect(group.id, session.token) { reload() }

  val canManage = detail?.let { session.role == UserRole.Admin || it.createdBy == session.id } == true
  val isOwner = detail?.createdBy == session.id

  LaunchedEffect(manage, canManage, detail?.isActive) {
    if (manage && canManage && detail?.isActive == true) reloadManagedMembers()
  }

  Column(Modifier.fillMaxSize().background(HighDensityBackground)) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
      Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = HighDensityOnBackground) }
        Text("ग्रुप माहिती", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
        if (canManage) IconButton(onClick = { manage = !manage }) { Icon(if (manage) Icons.Default.Close else Icons.Default.Settings, "Manage group", tint = HighDensityPrimary) }
      }
    }

    if (loading) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HighDensityPrimary) }
    } else if (detail == null) {
      Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(error ?: "ग्रुप माहिती उपलब्ध नाही.", color = Color(0xFF64748B)) }
    } else {
      LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
          Surface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), color = Color.White) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Box(Modifier.size(76.dp).clip(CircleShape).background(HighDensityPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, tint = HighDensityPrimary, modifier = Modifier.size(38.dp)) }
              Spacer(Modifier.height(10.dp))
              Text(detail!!.name, fontSize = 21.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
              Text("${scopeLabel(detail!!.scopeType)} • ${detail!!.memberCount} सदस्य", fontSize = 11.sp, color = HighDensityPrimary, fontWeight = FontWeight.SemiBold)
              Surface(shape = RoundedCornerShape(8.dp), color = if (detail!!.isActive) Color(0xFFE8F7EE) else Color(0xFFFFEDEF)) {
                Text(if (detail!!.isActive) "सक्रिय" else "निष्क्रिय", modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (detail!!.isActive) Color(0xFF15803D) else Color(0xFFB91C1C))
              }
              if (!detail!!.description.isNullOrBlank()) Text(detail!!.description.orEmpty(), Modifier.padding(top = 8.dp), color = Color(0xFF64748B), fontSize = 12.sp)
            }
          }
        }
        item {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("सदस्य", fontSize = 18.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
            if (canManage) {
              Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F5F9)) {
                Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                  TextButton(enabled = !lifecycleLoading && !detail!!.isActive, onClick = { changeGroupActiveState(true) }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)) { Text("सक्रिय", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (!detail!!.isActive) Color(0xFF16A34A) else Color(0xFF64748B)) }
                  TextButton(enabled = !lifecycleLoading && detail!!.isActive, onClick = { showCloseWarning = true }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)) { Text("निष्क्रिय", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (detail!!.isActive) Color(0xFFB91C1C) else Color(0xFF64748B)) }
                }
              }
            }
          }
        }

        if (manage && canManage) {
          if (manageLoading) {
            item { Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = HighDensityPrimary) } }
          }
          items(managedMembers, key = { it.id }) { member ->
            ManagedMemberRow(member, member.id == session.id,
              onSetActive = { active ->
                GroupMemberManagementApi.setActive(session.token, group.id, member.id, active,
                  onSuccess = { reloadManagedMembers(); reload() }, onError = { actionError = it })
              },
              onRemove = {
                GroupMemberManagementApi.removeMember(session.token, group.id, member.id,
                  onSuccess = { reloadManagedMembers(); reload() }, onError = { actionError = it })
              }
            )
          }
        } else {
          items(detail!!.members, key = { it.id }) { member ->
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = Color.White) {
              Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(HighDensityPrimaryContainer), contentAlignment = Alignment.Center) { Text(member.name.take(1).uppercase(), color = HighDensityPrimary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                  Text(member.name, fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
                  Text("${member.role.displayName} • ${member.roleInGroup}", fontSize = 10.sp, color = Color(0xFF64748B))
                }
              }
            }
          }
        }

        if (manage && canManage) {
          item {
            Surface(Modifier.fillMaxWidth().clickable(enabled = detail!!.isActive) { showAdd = true }, RoundedCornerShape(16.dp), color = Color.White) {
              Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PersonAdd, null, tint = if (detail!!.isActive) HighDensityPrimary else Color(0xFF94A3B8)); Spacer(Modifier.width(12.dp)); Text("नोंदणीकृत सदस्य जोडा", fontWeight = FontWeight.Bold, color = if (detail!!.isActive) HighDensityOnBackground else Color(0xFF94A3B8)) }
            }
          }
          if (isOwner) {
            item {
              Surface(Modifier.fillMaxWidth().clickable { showEdit = true }, RoundedCornerShape(16.dp), color = Color.White) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Edit, null, tint = HighDensityPrimary); Spacer(Modifier.width(12.dp)); Text("ग्रुप माहिती संपादित करा", fontWeight = FontWeight.Bold) }
              }
            }
          }
        }
        if (actionError != null) item { Text(actionError.orEmpty(), color = Color(0xFFB91C1C), fontSize = 11.sp) }
      }
    }
  }

  if (showEdit && detail != null) AlertDialog(
    onDismissRequest = { showEdit = false },
    title = { Text("ग्रुप माहिती") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(editName, { editName = it }, label = { Text("ग्रुप नाव") }, singleLine = true)
        OutlinedTextField(editDescription, { editDescription = it }, label = { Text("वर्णन") }, minLines = 3, maxLines = 4)
      }
    },
    confirmButton = { TextButton(onClick = {
      BackendApi.updateGroup(session.token, group.id, editName, editDescription, onSuccess = { showEdit = false; reload() }, onError = { actionError = it })
    }) { Text("जतन करा") } },
    dismissButton = { TextButton(onClick = { showEdit = false }) { Text("रद्द") } }
  )

  if (showAdd && detail != null && detail!!.isActive) AddMemberDialog(group, session, detail!!, onDismiss = { showAdd = false }, onAdded = { showAdd = false; reloadManagedMembers(); reload() }, onError = { actionError = it })

  if (showCloseWarning) AlertDialog(
    onDismissRequest = { if (!lifecycleLoading) showCloseWarning = false },
    title = { Text("ग्रुप निष्क्रिय करायचा?") },
    text = { Text("ग्रुप बंद केल्यावर तो सामान्य Chats यादीत दिसणार नाही आणि सदस्यांचे active access थांबेल. नंतर हा ग्रुप पुन्हा सक्रिय करता येईल.", fontSize = 13.sp, color = Color(0xFF475569)) },
    confirmButton = {
      Button(enabled = !lifecycleLoading, onClick = { showCloseWarning = false; changeGroupActiveState(false) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))) {
        Text(if (lifecycleLoading) "थांबा..." else "निष्क्रिय करा")
      }
    },
    dismissButton = { TextButton(enabled = !lifecycleLoading, onClick = { showCloseWarning = false }) { Text("रद्द करा") } }
  )
}

@Composable
private fun ManagedMemberRow(member: ManagedGroupMember, isSelf: Boolean, onSetActive: (Boolean) -> Unit, onRemove: () -> Unit) {
  Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = Color.White) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(42.dp).clip(CircleShape).background(HighDensityPrimaryContainer), contentAlignment = Alignment.Center) { Text(member.name.take(1).uppercase(), color = HighDensityPrimary, fontWeight = FontWeight.Bold) }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(member.name, fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
        Text("${member.role.displayName} • ${if (member.isActive) "सक्रिय" else "निष्क्रिय"}", fontSize = 10.sp, color = if (member.isActive) Color(0xFF16A34A) else Color(0xFF94A3B8))
      }
      if (!isSelf) {
        IconButton(onClick = { onSetActive(!member.isActive) }) { Icon(if (member.isActive) Icons.Default.PersonOff else Icons.Default.PersonAdd, if (member.isActive) "Inactive" else "Active", tint = if (member.isActive) Color(0xFFD97706) else Color(0xFF16A34A)) }
        IconButton(onClick = onRemove) { Icon(Icons.Default.PersonRemove, "Remove", tint = Color(0xFFB91C1C)) }
      }
    }
  }
}

@Composable
private fun AddMemberDialog(group: ChatGroup, session: UserSession, detail: GroupInfo, onDismiss: () -> Unit, onAdded: () -> Unit, onError: (String) -> Unit) {
  var candidates by remember { mutableStateOf<List<GroupMemberCandidate>>(emptyList()) }
  var selected by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(true) }
  val existingIds = remember(detail.members) { detail.members.map { it.id }.toSet() }

  LaunchedEffect(Unit) {
    BackendApi.getUserDirectory(session.token, { all ->
      candidates = all.filter { candidate ->
        candidate.status.equals("Active", ignoreCase = true) &&
          candidate.id !in existingIds &&
          when (detail.scopeType) {
            "system" -> true
            "cluster" -> candidate.clusterCode == detail.clusterCode
            "school" -> candidate.schoolCode == detail.schoolCode
            else -> false
          }
      }
      loading = false
    }, { onError(it); loading = false })
  }

  AlertDialog(onDismissRequest = onDismiss, title = { Text("नोंदणीकृत सदस्य जोडा") }, text = {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("फक्त या ग्रुपच्या scope मधील सक्रिय नोंदणीकृत सदस्य दिसतील.", fontSize = 11.sp, color = Color(0xFF64748B))
      if (loading) CircularProgressIndicator(color = HighDensityPrimary)
      else LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(candidates, key = { it.id }) { candidate ->
          Row(Modifier.fillMaxWidth().clickable { selected = candidate.id }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected == candidate.id, { selected = candidate.id })
            Column { Text(candidate.name, fontWeight = FontWeight.SemiBold); Text("${candidate.role.displayName} • ${candidate.email}", fontSize = 10.sp, color = Color(0xFF64748B)) }
          }
        }
        if (candidates.isEmpty()) item { Text("या scope मध्ये जोडण्यासाठी नवीन सक्रिय नोंदणीकृत सदस्य उपलब्ध नाही.", color = Color(0xFF64748B), fontSize = 12.sp) }
      }
    }
  }, confirmButton = { TextButton(enabled = selected != null, onClick = { GroupMemberManagementApi.addMember(session.token, group.id, selected!!, onSuccess = onAdded, onError = onError) }) { Text("जोडा") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("रद्द") } })
}

private fun scopeLabel(scope: String): String = when (scope) { "system" -> "संपूर्ण प्रणाली"; "cluster" -> "केंद्र"; "school" -> "शाळा"; else -> scope }
