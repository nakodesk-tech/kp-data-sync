package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.NotificationApi
import com.example.model.UserSession
import com.example.ui.theme.HighDensityBackground
import com.example.ui.theme.HighDensityOnBackground
import com.example.ui.theme.HighDensityPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCreateScreen(session: UserSession, onBack: () -> Unit, onPublished: () -> Unit) {
  var title by remember { mutableStateOf("") }
  var content by remember { mutableStateOf("") }
  var scopeType by remember { mutableStateOf("system") }
  var scopeId by remember { mutableStateOf("") }
  var saving by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var showSuccess by remember { mutableStateOf(false) }
  var savedAsDraft by remember { mutableStateOf(false) }

  BackHandler(enabled = !saving) { onBack() }

  fun createNotification(publishNow: Boolean) {
    saving = true
    error = null
    NotificationApi.createDraft(
      session, title, content, scopeType, scopeId.ifBlank { null },
      onSuccess = { id ->
        if (publishNow) {
          NotificationApi.publish(
            session, id,
            onSuccess = { saving = false; savedAsDraft = false; showSuccess = true },
            onError = { saving = false; error = it }
          )
        } else {
          saving = false
          savedAsDraft = true
          showSuccess = true
        }
      },
      onError = { saving = false; error = it }
    )
  }

  Scaffold(
    containerColor = HighDensityBackground,
    topBar = {
      TopAppBar(
        title = { Text("नवीन सूचना", fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(enabled = !saving, onClick = onBack) { Icon(Icons.Default.ArrowBack, "मागे") } }
      )
    }
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Campaign, null, tint = HighDensityPrimary)
        Column {
          Text("सूचना तयार करा", fontSize = MaterialTheme.typography.titleLarge.fontSize, fontWeight = FontWeight.Black, color = HighDensityOnBackground)
          Text("फक्त App Admin आणि Cluster Head साठी", style = MaterialTheme.typography.bodySmall)
        }
      }
      OutlinedTextField(title, { title = it; error = null }, Modifier.fillMaxWidth(), label = { Text("शीर्षक") }, singleLine = true, enabled = !saving)
      OutlinedTextField(content, { content = it; error = null }, Modifier.fillMaxWidth().heightIn(min = 150.dp), label = { Text("सूचना मजकूर") }, minLines = 5, enabled = !saving)
      Text("कोणासाठी?", fontWeight = FontWeight.Bold)
      ScopeOption("संपूर्ण प्रणाली", "system", scopeType, !saving) { scopeType = "system"; scopeId = "" }
      ScopeOption("केंद्र / Cluster", "cluster", scopeType, !saving) { scopeType = "cluster"; scopeId = "" }
      ScopeOption("शाळा", "school", scopeType, !saving) { scopeType = "school"; scopeId = "" }
      if (scopeType != "system") {
        OutlinedTextField(scopeId, { scopeId = it; error = null }, Modifier.fillMaxWidth(), label = { Text(if (scopeType == "cluster") "Cluster Code" else "School Code") }, singleLine = true, enabled = !saving)
      }
      error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          enabled = !saving && title.isNotBlank() && content.isNotBlank() && (scopeType == "system" || scopeId.isNotBlank()),
          modifier = Modifier.weight(1f).height(50.dp),
          onClick = { createNotification(false) }
        ) { Text(if (saving) "जतन करत आहे…" else "Draft जतन करा", fontWeight = FontWeight.Bold) }
        Button(
          enabled = !saving && title.isNotBlank() && content.isNotBlank() && (scopeType == "system" || scopeId.isNotBlank()),
          modifier = Modifier.weight(1f).height(50.dp),
          onClick = { createNotification(true) }
        ) { Text(if (saving) "प्रक्रिया…" else "सूचना प्रकाशित करा", fontWeight = FontWeight.Bold) }
      }
    }
  }

  if (showSuccess) {
    AlertDialog(
      onDismissRequest = { showSuccess = false; onPublished() },
      title = { Text(if (savedAsDraft) "Draft जतन झाला" else "सूचना प्रकाशित झाली") },
      text = { Text(if (savedAsDraft) "सूचना Draft Notifications मध्ये जतन झाली आहे." else "सूचना यशस्वीपणे प्रकाशित झाली आहे.") },
      confirmButton = { TextButton(onClick = { showSuccess = false; onPublished() }) { Text("ठीक आहे") } }
    )
  }
}

@Composable
private fun ScopeOption(title: String, value: String, selected: String, enabled: Boolean, onClick: () -> Unit) {
  Surface(onClick = onClick, enabled = enabled, tonalElevation = if (selected == value) 2.dp else 0.dp) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      RadioButton(selected == value, onClick = onClick, enabled = enabled)
      Text(title, fontWeight = if (selected == value) FontWeight.Bold else FontWeight.Normal)
    }
  }
}
