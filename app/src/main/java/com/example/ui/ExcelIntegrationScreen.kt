package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
import com.example.model.GroupMemberCandidate
import com.example.model.SchoolRecord
import com.example.model.UserRole
import com.example.model.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ExcelImportMode { Schools, Users }
private data class ImportRow(val rowNumber: Int, val values: Map<String, String>)
private data class ImportIssue(val row: Int, val message: String)
private data class ImportPreview(val rows: List<ImportRow>, val issues: List<ImportIssue>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelIntegrationScreen(session: UserSession, onBack: () -> Unit) {
  val context = LocalContext.current
  var mode by remember { mutableStateOf(ExcelImportMode.Schools) }
  var preview by remember { mutableStateOf<ImportPreview?>(null) }
  var selectedFileName by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  var importing by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf(0) }
  var imported by remember { mutableStateOf(0) }
  var failed by remember { mutableStateOf(0) }
  var resultMessage by remember { mutableStateOf<String?>(null) }
  var schools by remember { mutableStateOf<List<SchoolRecord>>(emptyList()) }
  var users by remember { mutableStateOf<List<GroupMemberCandidate>>(emptyList()) }

  fun loadExisting(onDone: () -> Unit) {
    loading = true
    if (mode == ExcelImportMode.Schools) {
      BackendApi.getSchools({ schools = it; loading = false; onDone() }, { loading = false; resultMessage = it; onDone() })
    } else {
      BackendApi.getUserDirectory(session.token, { users = it; loading = false; onDone() }, { loading = false; resultMessage = it; onDone() })
    }
  }

  fun validate(rows: List<ImportRow>): ImportPreview {
    val issues = mutableListOf<ImportIssue>()
    val seenKeys = mutableSetOf<String>()
    rows.forEach { row ->
      val v = row.values
      if (mode == ExcelImportMode.Schools) {
        val name = v["school_name"].orEmpty().trim()
        val udise = v["udise_code"].orEmpty().trim().ifBlank { v["school_code"].orEmpty().trim() }
        val cluster = v["cluster_name"].orEmpty().trim()
        val clusterCode = v["cluster_code"].orEmpty().trim()
        if (name.isBlank()) issues += ImportIssue(row.rowNumber, "School Name आवश्यक आहे")
        if (udise.isBlank()) issues += ImportIssue(row.rowNumber, "UDISE / School Code आवश्यक आहे")
        if (cluster.isBlank()) issues += ImportIssue(row.rowNumber, "Cluster Name आवश्यक आहे")
        if (clusterCode.isBlank()) issues += ImportIssue(row.rowNumber, "Cluster Code आवश्यक आहे")
        if (udise.isNotBlank() && !seenKeys.add(udise.lowercase())) issues += ImportIssue(row.rowNumber, "या Excel मध्ये UDISE duplicate आहे")
        if (udise.isNotBlank() && schools.any { it.udiseCode.equals(udise, true) }) issues += ImportIssue(row.rowNumber, "हा UDISE आधीच system मध्ये आहे; existing record overwrite केला जाणार नाही")
      } else {
        val name = v["name"].orEmpty().trim()
        val email = v["email"].orEmpty().trim().lowercase()
        val role = v["role"].orEmpty().trim()
        val password = v["password"].orEmpty()
        if (name.isBlank()) issues += ImportIssue(row.rowNumber, "Name आवश्यक आहे")
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) issues += ImportIssue(row.rowNumber, "Valid Email आवश्यक आहे")
        if (password.length < 6) issues += ImportIssue(row.rowNumber, "Password किमान 6 characters असावा")
        if (parseRole(role) == null) issues += ImportIssue(row.rowNumber, "Role Admin / Cluster_Head / School_HM / Teacher पैकी एक असावा")
        if (email.isNotBlank() && !seenKeys.add(email)) issues += ImportIssue(row.rowNumber, "या Excel मध्ये Email duplicate आहे")
        if (email.isNotBlank() && users.any { it.email.equals(email, true) }) issues += ImportIssue(row.rowNumber, "हा Email आधीच system मध्ये आहे; existing user overwrite केला जाणार नाही")
      }
    }
    return ImportPreview(rows, issues)
  }

  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Excel file"
    loading = true; resultMessage = null; preview = null
    CoroutineScope(Dispatchers.Main).launch {
      val result = withContext(Dispatchers.IO) { ExcelFileParser.parse(context.contentResolver, uri) }
      loading = false
      result.onSuccess { parsed ->
        if (parsed.isEmpty()) resultMessage = "Excel मध्ये कोणतीही data row सापडली नाही."
        else loadExisting { preview = validate(parsed) }
      }.onFailure { resultMessage = "Excel वाचता आले नाही: ${it.message ?: "Invalid .xlsx file"}" }
    }
  }

  fun importNext(index: Int) {
    val rows = preview?.rows.orEmpty()
    val invalidRows = preview?.issues.orEmpty().map { it.row }.toSet()
    if (index >= rows.size) {
      importing = false
      resultMessage = "Import पूर्ण: $imported यशस्वी, $failed अयशस्वी. Existing records overwrite केले नाहीत."
      return
    }
    progress = index + 1
    val row = rows[index]
    if (row.rowNumber in invalidRows) { importNext(index + 1); return }
    if (mode == ExcelImportMode.Schools) {
      val v = row.values
      BackendApi.registerSchool(session.token, v["school_name"].orEmpty().trim(), v["udise_code"].orEmpty().trim().ifBlank { v["school_code"].orEmpty().trim() }, v["cluster_name"].orEmpty().trim(), v["cluster_code"].orEmpty().trim(), v["taluka"].orEmpty().trim(), v["district"].orEmpty().trim(), v["hm_name"].orEmpty().trim(), v["hm_mobile"].orEmpty().trim(), v["school_type"].orEmpty().trim(), parseBoolean(v["is_active"], true), { imported++; importNext(index + 1) }, { failed++; importNext(index + 1) })
    } else {
      val v = row.values
      BackendApi.registerUser(session.token, v["name"].orEmpty().trim(), v["email"].orEmpty().trim(), v["mobile"].orEmpty().trim().ifBlank { null }, parseRole(v["role"].orEmpty())!!.roleName, v["cluster_name"].orEmpty().trim().ifBlank { null }, v["cluster_code"].orEmpty().trim().ifBlank { null }, v["school_name"].orEmpty().trim().ifBlank { null }, v["school_code"].orEmpty().trim().ifBlank { null }, v["address"].orEmpty().trim().ifBlank { null }, v["password"].orEmpty(), { imported++; importNext(index + 1) }, { failed++; importNext(index + 1) })
    }
  }

  Scaffold(
    containerColor = com.example.ui.theme.HighDensityBackground,
    topBar = { TopAppBar(title = { Text("Excel Integration", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack, enabled = !importing) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }
  ) { padding ->
    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
      item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Safe Excel Import", fontWeight = FontWeight.Black, fontSize = 17.sp) }; Text("Preview → Validate → Commit. Existing records कधीही overwrite होणार नाहीत.", fontSize = 12.sp) } } }
      item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(mode == ExcelImportMode.Schools, { mode = ExcelImportMode.Schools; preview = null; resultMessage = null }, label = { Text("Schools") }, leadingIcon = { Icon(Icons.Default.School, null) }); FilterChip(mode == ExcelImportMode.Users, { mode = ExcelImportMode.Users; preview = null; resultMessage = null }, label = { Text("Users") }, leadingIcon = { Icon(Icons.Default.People, null) }) } }
      item { OutlinedButton(onClick = { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip")) }, enabled = !loading && !importing, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Description, null); Spacer(Modifier.width(8.dp)); Text("Excel (.xlsx) निवडा", fontWeight = FontWeight.Bold) } }
      selectedFileName?.let { name -> item { Text("निवडलेली file: $name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
      if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
      preview?.let { p ->
        item { SummaryCard(p) }
        if (p.issues.isNotEmpty()) item { IssueCard(p.issues) }
        item { Button(onClick = { imported = 0; failed = 0; progress = 0; importing = true; resultMessage = null; importNext(0) }, enabled = !importing && p.rows.isNotEmpty() && p.issues.isEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (importing) "Importing $progress/${p.rows.size}…" else "COMMIT IMPORT", fontWeight = FontWeight.Black) } }
      }
      resultMessage?.let { msg -> item { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = if (msg.startsWith("Import पूर्ण")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (msg.startsWith("Import पूर्ण")) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null); Spacer(Modifier.width(8.dp)); Text(msg, fontSize = 12.sp) } } } }
      item { TemplateCard(mode) }
    }
  }
}

@Composable private fun SummaryCard(p: ImportPreview) { Card { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) { Summary("Rows", p.rows.size); Summary("Errors", p.issues.size) } } }
@Composable private fun Summary(label: String, value: Int) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.Black); Text(label, fontSize = 10.sp) } }
@Composable private fun IssueCard(issues: List<ImportIssue>) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("Import blocked — errors दुरुस्त करा", fontWeight = FontWeight.Black); issues.take(20).forEach { Text("Row ${it.row}: ${it.message}", fontSize = 11.sp) }; if (issues.size > 20) Text("+ ${issues.size - 20} more errors", fontSize = 10.sp) } } }
@Composable private fun TemplateCard(mode: ExcelImportMode) { val fields = if (mode == ExcelImportMode.Schools) "school_name, udise_code, cluster_name, cluster_code, taluka, district, hm_name, hm_mobile, school_type, is_active" else "name, email, mobile, role, cluster_name, cluster_code, school_name, school_code, address, password"; Card { Column(Modifier.padding(14.dp)) { Text("Canonical columns", fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(fields, fontSize = 10.sp); Spacer(Modifier.height(5.dp)); Text("Headers case/space/underscore differences सहन केले जातील.", fontSize = 10.sp) } } }

private fun parseRole(value: String): UserRole? = when (value.trim().lowercase().replace(" ", "_").replace("-", "_")) { "admin" -> UserRole.Admin; "cluster_head", "clusterhead" -> UserRole.Cluster_Head; "school_hm", "schoolhm", "hm" -> UserRole.School_HM; "teacher" -> UserRole.Teacher; else -> null }
private fun parseBoolean(value: String?, default: Boolean): Boolean = when (value?.trim()?.lowercase()) { "false", "0", "no", "inactive", "नाही", "निष्क्रिय" -> false; "true", "1", "yes", "active", "हो", "सक्रिय" -> true; else -> default }
