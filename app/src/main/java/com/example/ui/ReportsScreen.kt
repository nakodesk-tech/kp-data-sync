package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
import com.example.data.ExcelReport
import com.example.data.ReportsApi
import com.example.model.ChatGroup
import com.example.model.UserRole
import com.example.model.UserSession
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityOnBackground
import com.example.ui.theme.HighDensityPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(session: UserSession) {
  val context = LocalContext.current
  var reports by remember { mutableStateOf<List<ExcelReport>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var openingId by remember { mutableStateOf<String?>(null) }
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("सर्व") }
  var sortExpanded by remember { mutableStateOf(false) }
  var sortNewestFirst by remember { mutableStateOf(true) }
  var showUpload by remember { mutableStateOf(false) }
  var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
  var selectedFileName by remember { mutableStateOf("") }
  var groups by remember { mutableStateOf<List<ChatGroup>>(emptyList()) }
  var selectedGroupId by remember { mutableStateOf("") }
  var groupsLoading by remember { mutableStateOf(false) }
  var uploading by remember { mutableStateOf(false) }
  var uploadError by remember { mutableStateOf<String?>(null) }
  var deletingId by remember { mutableStateOf<String?>(null) }
  var deleteTarget by remember { mutableStateOf<ExcelReport?>(null) }
  var actionError by remember { mutableStateOf<String?>(null) }

  val canManage = session.role == UserRole.Admin || session.role == UserRole.Cluster_Head
  val canUpload = canManage

  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      selectedFileUri = uri
      selectedFileName = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
      }.getOrDefault("").ifBlank { uri.lastPathSegment.orEmpty() }
    }
  }

  fun loadReports() {
    loading = true
    error = null
    ReportsApi.getPublishedReports(
      token = session.token,
      onSuccess = { reports = it; loading = false },
      onError = { error = it; loading = false }
    )
  }

  fun openUpload() {
    showUpload = true
    uploadError = null
    if (groups.isEmpty()) {
      groupsLoading = true
      BackendApi.getGroups(
        token = session.token,
        onSuccess = { list ->
          groups = list
          groupsLoading = false
          if (selectedGroupId.isBlank()) selectedGroupId = list.firstOrNull()?.id.orEmpty()
        },
        onError = { message -> groupsLoading = false; uploadError = message }
      )
    } else if (selectedGroupId.isBlank()) selectedGroupId = groups.firstOrNull()?.id.orEmpty()
  }

  LaunchedEffect(session.token) { loadReports() }

  val visibleReports = remember(reports, searchQuery, selectedFilter, sortNewestFirst) {
    val filtered = reports.filter { report ->
      val matchesSearch = searchQuery.isBlank() || report.fileName.contains(searchQuery, true) || report.groupName.contains(searchQuery, true) || report.senderName.contains(searchQuery, true)
      val matchesType = when (selectedFilter) {
        "Excel" -> report.mimeType.contains("excel", true) || report.fileName.endsWith(".xlsx", true) || report.fileName.endsWith(".xls", true) || report.fileName.endsWith(".csv", true)
        "PDF" -> report.mimeType == "application/pdf" || report.fileName.endsWith(".pdf", true)
        "Other" -> !report.mimeType.contains("excel", true) && report.mimeType != "application/pdf" && !report.fileName.endsWith(".xlsx", true) && !report.fileName.endsWith(".xls", true) && !report.fileName.endsWith(".csv", true) && !report.fileName.endsWith(".pdf", true)
        else -> true
      }
      matchesSearch && matchesType
    }
    if (sortNewestFirst) filtered.sortedByDescending { it.publishedAt ?: "" } else filtered.sortedBy { it.publishedAt ?: "" }
  }

  val excelCount = reports.count { it.mimeType.contains("excel", true) || it.fileName.endsWith(".xlsx", true) || it.fileName.endsWith(".xls", true) || it.fileName.endsWith(".csv", true) }
  val pdfCount = reports.count { it.mimeType == "application/pdf" || it.fileName.endsWith(".pdf", true) }
  val otherCount = reports.size - excelCount - pdfCount

  Column(Modifier.fillMaxSize().background(HighDensityBackground)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Column(Modifier.weight(1f)) {
        Text("Reports", fontSize = 24.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
        Text("सर्व शाळा / गट अहवाल एका ठिकाणी", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
      }
      if (canUpload) {
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text("फक्त App Admin आणि Cluster Head करिता.") } }, state = rememberTooltipState()) {
          Button(onClick = { openUpload() }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp), colors = ButtonDefaults.buttonColors(containerColor = HighDensityPrimary)) {
            Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Report Upload करा", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
      Spacer(Modifier.width(8.dp))
      Icon(Icons.Default.Info, contentDescription = "Upload permission", tint = HighDensityPrimary, modifier = Modifier.size(26.dp))
    }

    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), singleLine = true, placeholder = { Text("Reports शोधा...", color = Color(0xFF94A3B8)) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF64748B)) }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFB8C7D9), unfocusedBorderColor = Color(0xFFB8C7D9), focusedTextColor = HighDensityOnBackground, unfocusedTextColor = HighDensityOnBackground))

    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      ReportFilterChip("सर्व (${reports.size})", selectedFilter == "सर्व") { selectedFilter = "सर्व" }
      ReportFilterChip("Excel ($excelCount)", selectedFilter == "Excel") { selectedFilter = "Excel" }
      ReportFilterChip("PDF ($pdfCount)", selectedFilter == "PDF") { selectedFilter = "PDF" }
      ReportFilterChip("Other ($otherCount)", selectedFilter == "Other") { selectedFilter = "Other" }
      Box {
        OutlinedButton(onClick = { sortExpanded = true }, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
          Text(if (sortNewestFirst) "नवीन प्रथम" else "जुने प्रथम", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
          DropdownMenuItem(text = { Text("नवीन प्रथम") }, onClick = { sortNewestFirst = true; sortExpanded = false })
          DropdownMenuItem(text = { Text("जुने प्रथम") }, onClick = { sortNewestFirst = false; sortExpanded = false })
        }
      }
    }

    actionError?.let { message ->
      Text(message, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp), fontSize = 11.sp, color = Color(0xFF9A3412))
    }

    when {
      loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HighDensityPrimary) }
      error != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(error.orEmpty(), color = Color(0xFF9A3412), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { loadReports() }) { Text("पुन्हा प्रयत्न करा") }
      }
      visibleReports.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(if (reports.isEmpty()) "सध्या कोणतेही Published Reports उपलब्ध नाहीत." else "दिलेल्या शोधासाठी Report सापडला नाही.", color = Color(0xFF64748B)) }
      else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(visibleReports, key = { it.id }) { report ->
          ReportCard(
            report = report,
            opening = openingId == report.id,
            canManage = canManage,
            onOpen = {
              openingId = report.id
              ReportsApi.downloadAndOpen(context, session.token, report) { openingId = null }
            },
            onEdit = if (canManage && !isPdfReport(report)) {
              {
                openingId = report.id
                ReportsApi.downloadAndOpen(context, session.token, report) { openingId = null }
              }
            } else null,
            onDelete = if (canManage) { { deleteTarget = report } } else null
          )
        }
        item {
          Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color(0xFFEAF2FF)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
              Icon(Icons.Default.Info, null, tint = Color(0xFF2563EB), modifier = Modifier.size(22.dp))
              Spacer(Modifier.width(10.dp))
              Column {
                Text("टीप:", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                Text("Reports मध्ये पाठवलेल्या फाइल्स सर्व नोंदणीकृत वापरकर्त्यांना पाहण्यासाठी उपलब्ध असतात. फक्त App Admin आणि Cluster Head यांना त्या फाइलमध्ये बदल करण्याची परवानगी आहे.", fontSize = 11.sp, color = Color(0xFF526784), lineHeight = 16.sp)
              }
            }
          }
        }
      }
    }
  }

  deleteTarget?.let { report ->
    AlertDialog(
      onDismissRequest = { if (deletingId == null) deleteTarget = null },
      title = { Text("Report delete करायचा?") },
      text = { Text("${report.fileName}\nही Published Report कायमची काढली जाईल.", fontSize = 13.sp) },
      confirmButton = {
        Button(enabled = deletingId == null, onClick = {
          deletingId = report.id
          actionError = null
          ReportsApi.deleteReport(session.token, report.id,
            onSuccess = {
              deletingId = null
              deleteTarget = null
              reports = reports.filterNot { it.id == report.id }
            },
            onError = {
              deletingId = null
              actionError = it
            }
          )
        }) { Text(if (deletingId == report.id) "Deleting..." else "Delete") }
      },
      dismissButton = { TextButton(enabled = deletingId == null, onClick = { deleteTarget = null }) { Text("रद्द करा") } }
    )
  }

  if (showUpload) {
    var groupMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
      onDismissRequest = { if (!uploading) { showUpload = false; selectedFileUri = null; selectedFileName = ""; uploadError = null } },
      title = { Text("Report Upload करा", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Excel किंवा PDF Report निवडा.", color = Color(0xFF64748B), fontSize = 13.sp)
          Box {
            OutlinedButton(onClick = { groupMenuExpanded = true }, enabled = !groupsLoading && !uploading && groups.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)) {
              Icon(Icons.Default.Groups, null, tint = HighDensityPrimary); Spacer(Modifier.width(8.dp))
              Text(groups.firstOrNull { it.id == selectedGroupId }?.name ?: if (groupsLoading) "Group माहिती घेत आहे..." else "Report साठी Group निवडा", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
              Icon(Icons.Default.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded = groupMenuExpanded, onDismissRequest = { groupMenuExpanded = false }) {
              groups.forEach { group -> DropdownMenuItem(text = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { selectedGroupId = group.id; groupMenuExpanded = false }) }
            }
          }
          Surface(Modifier.fillMaxWidth().clickable(enabled = !uploading) { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/csv", "application/pdf")) }, RoundedCornerShape(14.dp), color = Color(0xFFF5F7FB)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(if (selectedFileUri == null) Icons.Default.AttachFile else Icons.Default.Description, null, tint = HighDensityPrimary); Spacer(Modifier.width(10.dp))
              Text(if (selectedFileName.isBlank()) "Report file निवडा" else selectedFileName, color = HighDensityOnBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
          }
          Text("फक्त App Admin आणि Cluster Head करिता.", fontSize = 11.sp, color = HighDensityPrimary, fontWeight = FontWeight.SemiBold)
          uploadError?.let { Text(it, fontSize = 12.sp, color = Color(0xFF9A3412)) }
          if (uploading) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = HighDensityPrimary); Text("Report upload होत आहे...", fontSize = 11.sp, color = Color(0xFF64748B)) }
        }
      },
      confirmButton = {
        Button(onClick = {
          val uri = selectedFileUri ?: return@Button
          if (selectedGroupId.isBlank()) { uploadError = "Report साठी Group निवडा."; return@Button }
          uploading = true; uploadError = null
          ReportsApi.uploadReport(context, session.token, selectedGroupId, uri,
            onSuccess = { uploading = false; showUpload = false; selectedFileUri = null; selectedFileName = ""; uploadError = null; loadReports() },
            onError = { uploading = false; uploadError = it }
          )
        }, enabled = selectedFileUri != null && selectedGroupId.isNotBlank() && !uploading && !groupsLoading) { Text(if (uploading) "Uploading..." else "Upload") }
      },
      dismissButton = { TextButton(onClick = { if (!uploading) { showUpload = false; selectedFileUri = null; selectedFileName = ""; uploadError = null } }) { Text("रद्द करा") } }
    )
  }
}

private fun isPdfReport(report: ExcelReport): Boolean = report.mimeType == "application/pdf" || report.fileName.endsWith(".pdf", true)

private fun formatPublishedAt(value: String?): String {
  if (value.isNullOrBlank()) return "—"
  val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(value) ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(value) }.getOrNull() ?: return value
  return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(parsed.time))
}

private fun roleLabel(role: String): String = when (role) {
  "Admin" -> "App Admin"
  "Cluster_Head" -> "Cluster Head"
  else -> role.ifBlank { "—" }
}

@Composable
private fun ReportFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
  Surface(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = if (selected) HighDensityPrimary else Color(0xFFEFF3F9)) {
    Text(label, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = if (selected) Color.White else Color(0xFF526784), fontSize = 11.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun ReportCard(report: ExcelReport, opening: Boolean, canManage: Boolean, onOpen: () -> Unit, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
  val isPdf = isPdfReport(report)
  val iconColor = if (isPdf) Color(0xFFE53935) else Color(0xFF16A34A)
  val iconBackground = if (isPdf) Color(0xFFFFEDEF) else Color(0xFFE8F7EE)

  Surface(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), color = Color.White, tonalElevation = 1.dp) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Surface(Modifier.size(42.dp), RoundedCornerShape(12.dp), color = iconBackground) {
          Box(contentAlignment = Alignment.Center) { Icon(if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description, null, tint = iconColor, modifier = Modifier.size(23.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(report.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = HighDensityOnBackground)
          Spacer(Modifier.height(2.dp))
          Text(report.groupName, fontSize = 10.sp, color = Color(0xFF526784), maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(5.dp))
          Surface(shape = RoundedCornerShape(8.dp), color = if (isPdf) Color(0xFFEFF3F9) else Color(0xFFE8F7EE)) {
            Text("Version ${report.version}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 8.sp, color = if (isPdf) Color(0xFF526784) else Color(0xFF15803D), fontWeight = FontWeight.Bold)
          }
        }
      }

      Spacer(Modifier.height(7.dp))
      if (opening) {
        Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = HighDensityPrimary); Spacer(Modifier.width(8.dp)); Text("Report उघडत आहे...", fontSize = 10.sp, color = Color(0xFF64748B)) }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
          ReportAction(Icons.Default.Visibility, "View", Color(0xFF10B981), onOpen)
          ReportAction(Icons.Default.Download, "Download", HighDensityPrimary, onOpen)
          if (onEdit != null) ReportAction(Icons.Default.Edit, "Edit", Color(0xFF2563EB), onEdit)
          if (onDelete != null) ReportAction(Icons.Default.Delete, "Delete", Color(0xFFE53935), onDelete)
        }
      }

      Spacer(Modifier.height(7.dp))
      HorizontalDivider(color = Color(0xFFE8EDF4), thickness = 1.dp)
      Spacer(Modifier.height(6.dp))
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("पाठविले: ${formatPublishedAt(report.publishedAt)}", fontSize = 9.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("नाव: ${report.senderName.ifBlank { "—" }}   •   भूमिका: ${roleLabel(report.publisherRole)}", fontSize = 9.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun ReportAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
  Row(Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(4.dp))
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tint)
  }
}
