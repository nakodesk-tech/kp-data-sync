package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
import com.example.model.SchoolDirectorySeed
import com.example.model.SchoolRecord
import com.example.model.UserRole
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityPrimary

@Composable
fun SchoolsTabContent(
  schools: SchoolDirectorySeed,
  userRole: UserRole,
  onUploadExcelClick: () -> Unit
) {
  var records by remember { mutableStateOf<List<SchoolRecord>>(emptyList()) }
  var search by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf("all") }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var showAdd by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<SchoolRecord?>(null) }
  var deleting by remember { mutableStateOf<SchoolRecord?>(null) }
  var busyId by remember { mutableStateOf<String?>(null) }
  val canManage = userRole == UserRole.Admin

  fun reload() {
    loading = true
    error = null
    BackendApi.getSchools(
      onSuccess = { records = it; loading = false },
      onError = { error = it; loading = false }
    )
  }

  LaunchedEffect(Unit) { reload() }

  val active = records.count { it.isActive }
  val inactive = records.size - active
  val filtered = remember(records, search, filter) {
    records.filter { school ->
      val statusOk = when (filter) { "active" -> school.isActive; "inactive" -> !school.isActive; else -> true }
      val q = search.trim()
      val searchOk = q.isBlank() || listOf(
        school.schoolName, school.udiseCode, school.clusterName, school.clusterCode,
        school.taluka, school.district, school.hmName, school.hmMobile
      ).any { it.contains(q, ignoreCase = true) }
      statusOk && searchOk
    }
  }

  if (showAdd) {
    SchoolRegistrationScreen(
      session = BackendApi.currentSession(),
      onBack = { showAdd = false },
      onRegistered = { showAdd = false; reload() }
    )
    return
  }

  Column(Modifier.fillMaxSize().background(HighDensityBackground)) {
    LazyColumn(
      Modifier.fillMaxSize().padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp)
    ) {
      item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("शाळा व्यवस्थापन", fontSize = 23.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033))
            Text("नोंदणीकृत शाळांची माहिती, शोध व व्यवस्थापन", fontSize = 12.sp, color = Color(0xFF64748B))
          }
          Surface(color = Color(0xFFEDE7F6), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Text("${records.size}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = HighDensityPrimary)
              Text("एकूण शाळा", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = HighDensityPrimary)
            }
          }
        }
      }

      item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (canManage) {
            SchoolActionCard(
              Modifier.weight(1f), Icons.Default.AddBusiness, "नवीन शाळा नोंदणी",
              "UDISE, केंद्र, तालुका व मुख्याध्यापक माहिती जतन करा", Color(0xFFE8F5E9), Color(0xFF00897B)
            ) { showAdd = true }
          }
          SchoolActionCard(
            Modifier.weight(1f), Icons.Default.School, "शाळांची यादी",
            "नोंदणीकृत शाळा शोधा व त्यांचा Active / Inactive status पहा", Color(0xFFE8EAF6), Color(0xFF5E35B1)
          ) { filter = "all" }
        }
      }

      item {
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(13.dp)) {
            Text("शाळा शोधा", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
              value = search, onValueChange = { search = it }, singleLine = true,
              modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              trailingIcon = if (search.isNotEmpty()) ({ IconButton(onClick = { search = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear") } }) else null,
              placeholder = { Text("शाळेचे नाव, UDISE कोड, केंद्र शोधा…") }
            )
          }
        }
      }

      item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(filter == "all", { filter = "all" }, label = { Text("सर्व ${records.size}") })
          FilterChip(filter == "active", { filter = "active" }, label = { Text("सक्रिय $active") })
          FilterChip(filter == "inactive", { filter = "inactive" }, label = { Text("निष्क्रिय $inactive") })
        }
      }

      item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column {
            Text("नोंदणीकृत शाळा", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033))
            Text("${filtered.size} शाळा दिसत आहेत", fontSize = 11.sp, color = Color(0xFF64748B))
          }
          TextButton(onClick = { reload() }) { Text("ताजे करा") }
        }
      }

      if (loading) {
        item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
      } else if (error != null) {
        item {
          Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = Color(0xFFFFEBEE)) {
            Column(Modifier.padding(14.dp)) {
              Text("शाळांची माहिती मिळवता आली नाही", fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
              Text(error.orEmpty(), fontSize = 12.sp, color = Color(0xFF7F1D1D))
              TextButton(onClick = { reload() }) { Text("पुन्हा प्रयत्न करा") }
            }
          }
        }
      } else if (filtered.isEmpty()) {
        item {
          Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.White) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(42.dp))
              Spacer(Modifier.height(8.dp))
              Text(if (records.isEmpty()) "अजून कोणतीही शाळा नोंदणीकृत नाही" else "शोधानुसार शाळा सापडली नाही", fontWeight = FontWeight.Bold)
              Text(if (records.isEmpty()) "‘नवीन शाळा नोंदणी’ कार्डवरून पहिली शाळा जोडा." else "शोध शब्द किंवा Active / Inactive filter बदला.", fontSize = 12.sp, color = Color(0xFF64748B))
            }
          }
        }
      } else {
        items(filtered, key = { it.id }) { school ->
          SchoolManagementCard(
            school = school, busy = busyId == school.id, canManage = canManage,
            onEdit = { editing = school },
            onToggle = {
              busyId = school.id
              BackendApi.setSchoolActive(school.id, !school.isActive, onSuccess = { busyId = null; reload() }, onError = { error = it; busyId = null })
            },
            onDelete = { deleting = school }
          )
        }
      }
    }
  }

  editing?.let { school ->
    SchoolEditDialog(school, onDismiss = { editing = null }, onSaved = { editing = null; reload() })
  }

  deleting?.let { school ->
    AlertDialog(
      onDismissRequest = { deleting = null },
      title = { Text("शाळा हटवायची आहे?") },
      text = { Text("‘${school.schoolName}’ ही नोंद कायमची हटवली जाईल. संबंधित वापरकर्ते असल्यास सर्वप्रथम शाळा निष्क्रिय करा.") },
      confirmButton = {
        TextButton(onClick = {
          deleting = null; busyId = school.id
          BackendApi.deleteSchool(school.id, onSuccess = { busyId = null; reload() }, onError = { busyId = null; error = it })
        }) { Text("हटवा", color = Color(0xFFC62828), fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { deleting = null }) { Text("रद्द करा") } }
    )
  }
}

@Composable
private fun SchoolActionCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, container: Color, tint: Color, onClick: () -> Unit) {
  Surface(modifier.clickable(onClick = onClick), color = Color.White, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
    Column(Modifier.padding(14.dp)) {
      Surface(color = container, shape = RoundedCornerShape(12.dp)) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(10.dp).size(23.dp)) }
      Spacer(Modifier.height(9.dp))
      Text(title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033))
      Text(subtitle, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun SchoolManagementCard(school: SchoolRecord, busy: Boolean, canManage: Boolean, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
  Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
    Column(Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(school.schoolName, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF172033), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Surface(color = if (school.isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp)) {
              Text(if (school.isActive) "सक्रिय" else "निष्क्रिय", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (school.isActive) Color(0xFF137333) else Color(0xFFC62828), modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
            }
          }
          Spacer(Modifier.height(5.dp))
          Text("UDISE कोड: ${school.udiseCode}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
        }
      }
      Spacer(Modifier.height(9.dp))
      Text("केंद्र: ${school.clusterName.ifBlank { "माहिती उपलब्ध नाही" }}  •  ${school.clusterCode}", fontSize = 11.sp, color = Color(0xFF475569))
      if (school.hmName.isNotBlank() || school.hmMobile.isNotBlank()) Text("मुख्याध्यापक: ${listOf(school.hmName, school.hmMobile).filter { it.isNotBlank() }.joinToString(" • ")}", fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 5.dp))
      if (school.taluka.isNotBlank() || school.district.isNotBlank()) Text("ठिकाण: ${listOf(school.taluka, school.district).filter { it.isNotBlank() }.joinToString(" • ")}", fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 5.dp))
      if (canManage) {
        Spacer(Modifier.height(8.dp)); HorizontalDivider(color = Color(0xFFE2E8F0))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF5E35B1)) }
          IconButton(onClick = onToggle, enabled = !busy) { Icon(if (school.isActive) Icons.Default.ToggleOn else Icons.Default.ToggleOff, "Active status", tint = if (school.isActive) Color(0xFFC62828) else Color(0xFF2E7D32)) }
          IconButton(onClick = onDelete, enabled = !busy) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Color(0xFFC62828)) }
        }
      }
    }
  }
}

@Composable
private fun SchoolEditDialog(school: SchoolRecord, onDismiss: () -> Unit, onSaved: () -> Unit) {
  var name by remember(school.id) { mutableStateOf(school.schoolName) }
  var clusterName by remember(school.id) { mutableStateOf(school.clusterName) }
  var clusterCode by remember(school.id) { mutableStateOf(school.clusterCode) }
  var taluka by remember(school.id) { mutableStateOf(school.taluka) }
  var district by remember(school.id) { mutableStateOf(school.district) }
  var hmName by remember(school.id) { mutableStateOf(school.hmName) }
  var hmMobile by remember(school.id) { mutableStateOf(school.hmMobile) }
  var schoolType by remember(school.id) { mutableStateOf(school.schoolType) }
  var active by remember(school.id) { mutableStateOf(school.isActive) }
  var error by remember { mutableStateOf<String?>(null) }
  var saving by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("शाळा माहिती संपादित करा", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(name, { name = it }, label = { Text("शाळेचे नाव") }, singleLine = true)
        OutlinedTextField(school.udiseCode, {}, label = { Text("UDISE कोड") }, enabled = false, singleLine = true)
        OutlinedTextField(clusterName, { clusterName = it }, label = { Text("केंद्राचे नाव") }, singleLine = true)
        OutlinedTextField(clusterCode, { clusterCode = it }, label = { Text("केंद्र कोड") }, singleLine = true)
        OutlinedTextField(taluka, { taluka = it }, label = { Text("तालुका") }, singleLine = true)
        OutlinedTextField(district, { district = it }, label = { Text("जिल्हा") }, singleLine = true)
        OutlinedTextField(hmName, { hmName = it }, label = { Text("मुख्याध्यापक नाव") }, singleLine = true)
        OutlinedTextField(hmMobile, { hmMobile = it }, label = { Text("मुख्याध्यापक मोबाईल") }, singleLine = true)
        OutlinedTextField(schoolType, { schoolType = it }, label = { Text("शाळेचा प्रकार") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(if (active) "शाळा सक्रिय" else "शाळा निष्क्रिय", fontWeight = FontWeight.Bold); Switch(active, { active = it }) }
        error?.let { Text(it, color = Color(0xFFC62828), fontSize = 12.sp) }
      }
    },
    confirmButton = {
      TextButton(enabled = !saving, onClick = {
        if (name.isBlank() || clusterName.isBlank() || clusterCode.isBlank()) { error = "शाळेचे नाव, केंद्राचे नाव आणि केंद्र कोड आवश्यक आहेत"; return@TextButton }
        saving = true
        BackendApi.updateSchool(school.id, name.trim(), clusterName.trim(), clusterCode.trim(), taluka.trim(), district.trim(), hmName.trim(), hmMobile.trim(), schoolType.trim(), active, onSuccess = { saving = false; onSaved() }, onError = { saving = false; error = it })
      }) { Text(if (saving) "जतन होत आहे…" else "जतन करा", fontWeight = FontWeight.Bold) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("रद्द करा") } }
  )
}
