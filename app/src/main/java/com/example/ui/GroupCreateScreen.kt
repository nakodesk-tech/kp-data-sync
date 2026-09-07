package com.example.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
import com.example.model.ChatGroup
import com.example.model.GroupCreateResult
import com.example.model.GroupMemberCandidate
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityOnBackground
import com.example.ui.theme.HighDensityPrimary
import com.example.ui.theme.HighDensityPrimaryContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ScopeOption(val code: String, val label: String, val subtitle: String)
private data class SchoolOption(val code: String, val name: String, val clusterCode: String)

@Composable
fun GroupCreateScreen(
  session: UserSession,
  onBack: () -> Unit,
  onCreated: (ChatGroup) -> Unit
) {
  var groupName by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var groupType by remember { mutableStateOf(if (session.role == UserRole.Cluster_Head) "cluster" else "general") }
  var scopeType by remember { mutableStateOf(if (session.role == UserRole.Cluster_Head) "cluster" else "system") }
  var selectedClusterCode by remember { mutableStateOf(session.clusterCode.orEmpty()) }
  var selectedSchoolCode by remember { mutableStateOf("") }
  var memberSearch by remember { mutableStateOf("") }
  var selectedMemberIds by remember { mutableStateOf(setOf<String>()) }
  var candidates by remember { mutableStateOf<List<GroupMemberCandidate>>(emptyList()) }
  var loadingMembers by remember { mutableStateOf(true) }
  var saving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
  var photoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

  val context = androidx.compose.ui.platform.LocalContext.current

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    photoUri = uri
    errorMessage = null
  }

  LaunchedEffect(photoUri) {
    photoBitmap = photoUri?.let { uri ->
      withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) }.getOrNull()
      }
    }
  }

  LaunchedEffect(Unit) {
    BackendApi.getUserDirectory(session.token,
      onSuccess = { candidates = it.filter { user -> user.id != session.id && user.status == "Active" }; loadingMembers = false },
      onError = { errorMessage = it; loadingMembers = false }
    )
  }

  BackHandler(enabled = !saving) { onBack() }

  val clusterOptions = remember(candidates) {
    candidates.filter { it.clusterCode.isNotBlank() }.groupBy { it.clusterCode }
      .map { (code, users) -> ScopeOption(code, users.firstOrNull()?.clusterName?.ifBlank { code } ?: code, "केंद्रातील ${users.size} सक्रिय वापरकर्ते") }
      .sortedBy { it.label.lowercase() }
  }
  val schoolOptions = remember(candidates) {
    candidates.filter { it.schoolCode.isNotBlank() }.groupBy { it.schoolCode }
      .map { (code, users) -> SchoolOption(code, users.firstOrNull()?.schoolName?.ifBlank { code } ?: code, users.firstOrNull()?.clusterCode.orEmpty()) }
      .sortedBy { it.name.lowercase() }
  }

  LaunchedEffect(scopeType) {
    if (scopeType == "system") { selectedClusterCode = ""; selectedSchoolCode = "" }
    if (scopeType == "cluster") { selectedSchoolCode = ""; if (session.role == UserRole.Cluster_Head) selectedClusterCode = session.clusterCode.orEmpty() }
    if (scopeType == "school") { selectedClusterCode = ""; selectedSchoolCode = "" }
    selectedMemberIds = emptySet()
  }

  val scopedCandidates = remember(candidates, scopeType, selectedClusterCode, selectedSchoolCode, memberSearch) {
    candidates.filter { user ->
      val scopeMatch = when (scopeType) {
        "system" -> true
        "cluster" -> selectedClusterCode.isNotBlank() && user.clusterCode == selectedClusterCode
        "school" -> selectedSchoolCode.isNotBlank() && user.schoolCode == selectedSchoolCode
        else -> false
      }
      val searchMatch = memberSearch.isBlank() || user.name.contains(memberSearch, true) || user.email.contains(memberSearch, true) || user.schoolName.contains(memberSearch, true)
      scopeMatch && searchMatch
    }
  }

  fun validate(): String? {
    if (groupName.trim().length < 2) return "ग्रुपचे नाव किमान 2 अक्षरांचे असावे."
    if (groupName.trim().length > 80) return "ग्रुपचे नाव 80 अक्षरांपेक्षा मोठे असू नये."
    if (description.trim().length > 500) return "ग्रुपचे वर्णन 500 अक्षरांपेक्षा मोठे असू नये."
    if (session.role == UserRole.Cluster_Head && selectedClusterCode != session.clusterCode) return "Cluster Head साठी फक्त नियुक्त केंद्राचा scope वापरता येतो."
    if (scopeType == "cluster" && selectedClusterCode.isBlank()) return "केंद्र निवडा."
    if (scopeType == "school" && selectedSchoolCode.isBlank()) return "शाळा निवडा."
    if (selectedMemberIds.isEmpty()) return "किमान 1 सदस्य निवडा."
    return null
  }

  fun submit() {
    val validation = validate()
    if (validation != null) { errorMessage = validation; return }
    saving = true
    errorMessage = null
    BackendApi.createGroup(
      context = context,
      session = session,
      groupName = groupName,
      description = description,
      groupType = groupType,
      scopeType = scopeType,
      clusterCode = selectedClusterCode.takeIf { scopeType == "cluster" },
      schoolCode = selectedSchoolCode.takeIf { scopeType == "school" },
      memberIds = selectedMemberIds.toList(),
      photoUri = photoUri,
      onSuccess = { result ->
        saving = false
        onCreated(
          ChatGroup(
            id = result.id,
            name = result.name,
            lastMessage = "ग्रुप तयार झाला",
            senderName = session.name,
            time = "आत्ताच",
            scope = result.scopeType,
            groupType = result.groupType,
            memberCount = result.memberCount,
            photoKey = result.photoKey
          )
        )
      },
      onError = { message -> saving = false; errorMessage = message }
    )
  }

  Box(Modifier.fillMaxSize().background(HighDensityBackground).statusBarsPadding().navigationBarsPadding()) {
    Column(Modifier.fillMaxSize()) {
      Surface(color = HighDensityBackground, shadowElevation = 1.dp) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(enabled = !saving, onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = HighDensityOnBackground) }
          Column(Modifier.weight(1f)) {
            Text("नवीन ग्रुप तयार करा", fontSize = 21.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
            Text(if (session.role == UserRole.Cluster_Head) "फक्त ${session.clusterName ?: "आपल्या केंद्रासाठी"}" else "निवडलेल्या scope नुसार सदस्य जोडले जातील", fontSize = 11.sp, color = Color(0xFF64748B))
          }
        }
      }

      LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item {
          SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Box(
                Modifier.size(82.dp).clip(CircleShape).background(HighDensityPrimaryContainer).clickable(enabled = !saving) {
                  photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentAlignment = Alignment.Center
              ) {
                if (photoBitmap != null) {
                  Image(photoBitmap!!.asImageBitmap(), "Group photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                  Icon(Icons.Default.Groups, null, tint = HighDensityPrimary, modifier = Modifier.size(38.dp))
                  Box(Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(HighDensityPrimary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                  }
                }
              }
              Spacer(Modifier.width(14.dp))
              Column(Modifier.weight(1f)) {
                Text("ग्रुप फोटो", fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
                Text("ऐच्छिक • JPG / PNG / WebP • कमाल 5 MB", fontSize = 11.sp, color = Color(0xFF64748B))
                Spacer(Modifier.height(6.dp))
                TextButton(enabled = !saving, onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                  Text(if (photoBitmap == null) "फोटो निवडा" else "फोटो बदला", color = HighDensityPrimary, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }

        item {
          SectionCard {
            FieldLabel("ग्रुपचे नाव *")
            OutlinedTextField(
              value = groupName,
              onValueChange = { if (it.length <= 80) { groupName = it; errorMessage = null } },
              modifier = Modifier.fillMaxWidth(), singleLine = true,
              placeholder = { Text("उदा. चिंचघर केंद्र शिक्षक गट") },
              shape = RoundedCornerShape(14.dp),
              leadingIcon = { Icon(Icons.Default.Group, null) },
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(10.dp))
            FieldLabel("ग्रुपचे वर्णन")
            OutlinedTextField(
              value = description,
              onValueChange = { if (it.length <= 500) description = it },
              modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
              placeholder = { Text("ग्रुपचा उद्देश थोडक्यात लिहा…") },
              shape = RoundedCornerShape(14.dp),
              minLines = 3
            )
          }
        }

        item {
          SectionCard {
            FieldLabel("ग्रुपचा प्रकार *")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              GroupTypeChip("administrative", "प्रशासकीय", Icons.Default.AdminPanelSettings, groupType) { groupType = it }
              GroupTypeChip("cluster", "केंद्र समन्वय", Icons.Default.Hub, groupType) { groupType = it }
              GroupTypeChip("school", "शाळा समन्वय", Icons.Default.School, groupType) { groupType = it }
              GroupTypeChip("general", "सामान्य संवाद", Icons.Default.Chat, groupType) { groupType = it }
            }
          }
        }

        item {
          SectionCard {
            FieldLabel("ग्रुप scope *")
            if (session.role == UserRole.Cluster_Head) {
              Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = Color(0xFFF1F5F9), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Lock, null, tint = HighDensityPrimary)
                  Spacer(Modifier.width(10.dp))
                  Column {
                    Text("आपले नियुक्त केंद्र", fontWeight = FontWeight.Bold, color = HighDensityOnBackground)
                    Text(session.clusterName ?: session.clusterCode.orEmpty(), fontSize = 12.sp, color = Color(0xFF64748B))
                  }
                }
              }
            } else {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopeChip("system", "संपूर्ण प्रणाली", "सर्व", scopeType) { scopeType = it }
                ScopeChip("cluster", "केंद्र", "Cluster", scopeType) { scopeType = it }
                ScopeChip("school", "शाळा", "School", scopeType) { scopeType = it }
              }
            }

            if (session.role == UserRole.Admin && scopeType == "cluster") {
              Spacer(Modifier.height(10.dp))
              FieldLabel("केंद्र निवडा *")
              OptionRow(
                options = clusterOptions.map { it.code to it.label },
                selected = selectedClusterCode,
                placeholder = "केंद्र निवडा",
                onSelect = { selectedClusterCode = it; selectedMemberIds = emptySet() }
              )
            }
            if (session.role == UserRole.Admin && scopeType == "school") {
              Spacer(Modifier.height(10.dp))
              FieldLabel("शाळा निवडा *")
              OptionRow(
                options = schoolOptions.map { it.code to it.name },
                selected = selectedSchoolCode,
                placeholder = "शाळा निवडा",
                onSelect = { selectedSchoolCode = it; selectedMemberIds = emptySet() }
              )
            }
          }
        }

        item {
          SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
              Column(Modifier.weight(1f)) {
                FieldLabel("सदस्य निवडा *")
                Text("तुम्हाला जोडायचे सक्रिय वापरकर्ते निवडा", fontSize = 11.sp, color = Color(0xFF64748B))
              }
              Surface(color = HighDensityPrimaryContainer, shape = RoundedCornerShape(10.dp)) {
                Text("${selectedMemberIds.size} निवडले", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
              }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
              value = memberSearch,
              onValueChange = { memberSearch = it },
              modifier = Modifier.fillMaxWidth(), singleLine = true,
              placeholder = { Text("नाव, इमेल किंवा शाळा शोधा…") },
              leadingIcon = { Icon(Icons.Default.Search, null) },
              shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (loadingMembers) {
              Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(modifier = Modifier.size(26.dp), color = HighDensityPrimary) }
            } else if (scopedCandidates.isEmpty()) {
              Text("या scope मध्ये निवडण्यासाठी सक्रिय वापरकर्ता सापडला नाही.", fontSize = 12.sp, color = Color(0xFF64748B), modifier = Modifier.padding(vertical = 14.dp))
            } else {
              scopedCandidates.take(100).forEach { user ->
                MemberRow(user, selectedMemberIds.contains(user.id)) {
                  selectedMemberIds = if (selectedMemberIds.contains(user.id)) selectedMemberIds - user.id else selectedMemberIds + user.id
                  errorMessage = null
                }
              }
              if (scopedCandidates.size > 100) Text("पहिले 100 परिणाम दाखवले आहेत. Search वापरून सदस्य शोधा.", fontSize = 10.sp, color = Color(0xFF64748B))
            }
          }
        }

        if (errorMessage != null) {
          item {
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), color = Color(0xFFFFF1F2), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))) {
              Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFDC2626))
                Spacer(Modifier.width(8.dp))
                Text(errorMessage!!, fontSize = 12.sp, color = Color(0xFF991B1B))
              }
            }
          }
        }
      }

      Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
          Button(
            enabled = !saving,
            onClick = ::submit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HighDensityPrimary)
          ) {
            if (saving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            else {
              Icon(Icons.Default.CheckCircle, null)
              Spacer(Modifier.width(8.dp))
              Text("ग्रुप तयार करा", fontWeight = FontWeight.Black)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
  Surface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), color = Color.White, tonalElevation = 1.dp) {
    Column(Modifier.padding(16.dp), content = content)
  }
}

@Composable
private fun FieldLabel(text: String) {
  Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HighDensityOnBackground, modifier = Modifier.padding(bottom = 7.dp))
}

@Composable
private fun GroupTypeChip(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: String, onSelect: (String) -> Unit) {
  FilterChip(selected = selected == value, onClick = { onSelect(value) }, leadingIcon = { Icon(icon, null, modifier = Modifier.size(17.dp)) }, label = { Text(label, fontSize = 11.sp) })
}

@Composable
private fun ScopeChip(value: String, label: String, subtitle: String, selected: String, onSelect: (String) -> Unit) {
  FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text("$label • $subtitle", fontSize = 11.sp) })
}

@Composable
private fun OptionRow(options: List<Pair<String, String>>, selected: String, placeholder: String, onSelect: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(options.firstOrNull { it.first == selected }?.second ?: placeholder, color = if (selected.isBlank()) Color(0xFF94A3B8) else HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ExpandMore, null)
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { (code, label) ->
        DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(code); expanded = false })
      }
    }
  }
}

@Composable
private fun MemberRow(user: GroupMemberCandidate, selected: Boolean, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(HighDensityPrimaryContainer), contentAlignment = Alignment.Center) {
      Text(user.name.take(2).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(user.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = HighDensityOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("${user.role.displayName} • ${user.schoolName.ifBlank { user.clusterName }}", fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Checkbox(checked = selected, onCheckedChange = { onClick() }, colors = CheckboxDefaults.colors(checkedColor = HighDensityPrimary))
  }
}
