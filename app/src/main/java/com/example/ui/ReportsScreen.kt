package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import com.example.data.ExcelReport
import com.example.data.ReportsApi
import com.example.model.UserSession
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityOnBackground
import com.example.ui.theme.HighDensityPrimary

@Composable
fun ReportsScreen(session: UserSession) {
  val context = LocalContext.current
  var reports by remember { mutableStateOf<List<ExcelReport>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var openingId by remember { mutableStateOf<String?>(null) }

  fun loadReports() {
    loading = true
    error = null
    ReportsApi.getPublishedReports(
      token = session.token,
      onSuccess = { reports = it; loading = false },
      onError = { error = it; loading = false }
    )
  }

  LaunchedEffect(session.token) { loadReports() }

  Column(Modifier.fillMaxSize().background(HighDensityBackground)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(Modifier.weight(1f)) {
        Text("Reports", fontSize = 24.sp, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
        Text("Published Excel reports", fontSize = 12.sp, color = Color(0xFF64748B))
      }
      IconButton(onClick = { loadReports() }, enabled = !loading) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = HighDensityPrimary)
      }
    }

    when {
      loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = HighDensityPrimary)
      }
      error != null -> Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text(error.orEmpty(), color = Color(0xFF9A3412), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { loadReports() }) { Text("पुन्हा प्रयत्न करा") }
      }
      reports.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("सध्या कोणतेही Published Reports उपलब्ध नाहीत.", color = Color(0xFF64748B))
      }
      else -> LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
      ) {
        items(reports, key = { it.id }) { report ->
          ReportCard(
            report = report,
            opening = openingId == report.id,
            onOpen = {
              openingId = report.id
              ReportsApi.downloadAndOpen(
                context = context,
                token = session.token,
                report = report,
                onError = { openingId = null }
              )
            }
          )
        }
      }
    }
  }
}

@Composable
private fun ReportCard(report: ExcelReport, opening: Boolean, onOpen: () -> Unit) {
  Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      Surface(Modifier.size(46.dp), RoundedCornerShape(14.dp), color = Color(0xFFE8F5E9)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, null, tint = Color(0xFF2E7D32)) }
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(report.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = HighDensityOnBackground)
        Text(report.groupName, fontSize = 11.sp, color = Color(0xFF475569), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("v${report.version}${report.senderName.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}", fontSize = 10.sp, color = Color(0xFF64748B))
      }
      if (opening) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HighDensityPrimary)
      else IconButton(onClick = onOpen) { Icon(Icons.Default.Download, "Open report", tint = HighDensityPrimary) }
    }
  }
}
