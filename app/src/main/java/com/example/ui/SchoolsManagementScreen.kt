package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BackendApi
import com.example.model.SchoolDirectorySeed
import com.example.model.SchoolRecord
import com.example.model.UserRole
import com.example.ui.theme.HighDensityPrimary

private val SchoolScreenBackground = Color(0xFFF9F8FD)
private val SchoolText = Color(0xFF172033)
private val SchoolSecondary = Color(0xFF64748B)
private val PurpleSoft = Color(0xFFF1E9FF)
private val PurpleBorder = Color(0xFFE2D4FF)
private val Green = Color(0xFF159A62)
private val GreenSoft = Color(0xFFD9F8EC)
private val Red = Color(0xFFE52D43)
private val RedSoft = Color(0xFFFFE9ED)

@Composable
fun SchoolsTabContent(
    schools: SchoolDirectorySeed,
    userRole: UserRole,
    @Suppress("UNUSED_PARAMETER") onUploadExcelClick: () -> Unit = {}
) {
    var records by remember { mutableStateOf<List<SchoolRecord>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SchoolRecord?>(null) }
    var deleting by remember { mutableStateOf<SchoolRecord?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }

    // Keep the existing permission model: registration is available to Admin/Cluster Head;
    // destructive/status management remains App Admin only.
    val canRegister = userRole == UserRole.Admin || userRole == UserRole.Cluster_Head
    val canManage = userRole == UserRole.Admin

    fun reload() {
        loading = true
        error = null
        BackendApi.getSchools(
            { records = it; loading = false },
            { error = it; loading = false }
        )
    }

    LaunchedEffect(userRole) { reload() }

    val filtered = records.filter { school ->
        val matchesFilter = when (filter) {
            "active" -> school.isActive
            "inactive" -> !school.isActive
            else -> true
        }
        val query = search.trim()
        val matchesSearch = query.isBlank() || listOf(
            school.schoolName,
            school.udiseCode,
            school.clusterName,
            school.clusterCode,
            school.hmName
        ).any { it.contains(query, ignoreCase = true) }
        matchesFilter && matchesSearch
    }

    if (add) {
        SchoolRegistrationScreen(
            BackendApi.currentSession(),
            { add = false },
            { add = false; reload() }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SchoolScreenBackground)
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "शाळा व्यवस्थापन",
                            fontSize = 24.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.Black,
                            color = SchoolText
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = scopeText(userRole),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SchoolSecondary
                        )
                    }

                    Surface(
                        modifier = Modifier.size(width = 108.dp, height = 96.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = PurpleSoft
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                records.size.toString(),
                                fontSize = 28.sp,
                                lineHeight = 30.sp,
                                fontWeight = FontWeight.Black,
                                color = HighDensityPrimary
                            )
                            Text(
                                "एकूण शाळा",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = HighDensityPrimary
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { reload() },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = HighDensityPrimary,
                            modifier = Modifier.size(31.dp)
                        )
                    }
                }
            }

            if (canRegister) {
                item {
                    Surface(
                        onClick = { add = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFBF8FF),
                        border = BorderStroke(1.dp, PurpleBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(58.dp),
                                shape = RoundedCornerShape(15.dp),
                                color = HighDensityPrimary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AddBusiness,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(31.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "नवीन शाळा नोंदणी",
                                    fontSize = 18.sp,
                                    lineHeight = 23.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF3F177D)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    if (userRole == UserRole.Cluster_Head)
                                        "आपल्या केंद्रातील नवीन शाळा नोंदवा."
                                    else
                                        "UDISE, केंद्र व शाळेची माहिती सुरक्षितपणे जतन करा.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF6C5A85),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.ArrowForwardIos,
                                contentDescription = null,
                                tint = HighDensityPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(19.dp),
                    color = Color.White,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                        Text(
                            "शाळा शोधा",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = SchoolSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = SchoolText
                            ),
                            placeholder = {
                                Text(
                                    "शाळेचे नाव, UDISE किंवा केंद्र शोधा…",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SchoolSecondary
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = SchoolSecondary,
                                    modifier = Modifier.size(29.dp)
                                )
                            },
                            trailingIcon = {
                                if (search.isNotEmpty()) {
                                    IconButton(onClick = { search = "" }) {
                                        Icon(Icons.Default.Clear, "Clear")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    SchoolFilterChip(
                        selected = filter == "all",
                        text = "सर्व ${records.size}",
                        selectedColor = GreenSoft,
                        onClick = { filter = "all" },
                        modifier = Modifier.weight(1f)
                    )
                    SchoolFilterChip(
                        selected = filter == "active",
                        text = "सक्रिय ${records.count { it.isActive }}",
                        selectedColor = Color.White,
                        onClick = { filter = "active" },
                        modifier = Modifier.weight(1f)
                    )
                    SchoolFilterChip(
                        selected = filter == "inactive",
                        text = "निष्क्रिय ${records.count { !it.isActive }}",
                        selectedColor = Color.White,
                        onClick = { filter = "inactive" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text(
                    "${filtered.size} नोंदणीकृत शाळा",
                    modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = SchoolText
                )
            }

            if (loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = HighDensityPrimary) }
                }
            } else if (error != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White
                    ) {
                        Text(
                            error.orEmpty(),
                            modifier = Modifier.padding(18.dp),
                            color = Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White
                    ) {
                        Column(
                            modifier = Modifier.padding(30.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("शाळा सापडली नाही", fontWeight = FontWeight.Bold, color = SchoolText)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { school ->
                    SchoolCard(
                        school = school,
                        canManage = canManage,
                        busy = busy == school.id,
                        edit = { editing = school },
                        toggle = {
                            busy = school.id
                            BackendApi.setSchoolActive(
                                school.id,
                                !school.isActive,
                                { busy = null; reload() },
                                { busy = null; error = it }
                            )
                        },
                        delete = { deleting = school }
                    )
                }
            }
        }
    }

    editing?.let { school ->
        SchoolEditDialog(
            school,
            { editing = null; reload() },
            { editing = null }
        )
    }

    deleting?.let { school ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("शाळा हटवायची आहे?") },
            text = {
                Text("‘${school.schoolName}’ ही नोंद हटवली जाईल. संबंधित वापरकर्ते असल्यास delete नाकारले जाईल.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        busy = school.id
                        BackendApi.deleteSchool(
                            school.id,
                            { busy = null; reload() },
                            { busy = null; error = it }
                        )
                    }
                ) { Text("हटवा", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("रद्द करा") }
            }
        )
    }
}

@Composable
private fun SchoolFilterChip(
    selected: Boolean,
    text: String,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) selectedColor else Color(0xFF64748B)),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = selectedColor,
            labelColor = SchoolSecondary,
            selectedLabelColor = if (selectedColor == GreenSoft) Color(0xFF087A54) else SchoolSecondary
        ),
        label = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    )
}

@Composable
private fun SchoolCard(
    school: SchoolRecord,
    canManage: Boolean,
    busy: Boolean,
    edit: () -> Unit,
    toggle: () -> Unit,
    delete: () -> Unit
) {
    var menuOpen by remember(school.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = Color.White,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (school.isActive) Color(0xFFF2E8FF) else Color(0xFFEFF2FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = if (school.isActive) Color(0xFF5B18C9) else Color(0xFF1D4ED8),
                        modifier = Modifier.size(35.dp)
                    )
                }

                Spacer(Modifier.width(13.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        school.schoolName,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = SchoolText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (school.isActive) GreenSoft else Color(0xFFE9EBEF)
                    ) {
                        Text(
                            if (school.isActive) "सक्रिय" else "निष्क्रिय",
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp),
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (school.isActive) Color(0xFF138A5B) else Color(0xFF56616F)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More actions",
                            tint = SchoolSecondary,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        if (canManage) {
                            DropdownMenuItem(
                                text = { Text("संपादित करा") },
                                onClick = { menuOpen = false; edit() },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = HighDensityPrimary) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (school.isActive) "निष्क्रिय करा" else "सक्रिय करा") },
                                onClick = { menuOpen = false; toggle() },
                                leadingIcon = {
                                    Icon(
                                        if (school.isActive) Icons.Default.ToggleOff else Icons.Default.ToggleOn,
                                        null,
                                        tint = if (school.isActive) Red else Green
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("हटवा", color = Red) },
                                onClick = { menuOpen = false; delete() },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = Red) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            SchoolInfoLine(Icons.Default.Description, "UDISE:", school.udiseCode)
            Spacer(Modifier.height(7.dp))
            SchoolInfoLine(Icons.Default.LocationOn, "केंद्र:", school.clusterName)
            Spacer(Modifier.height(7.dp))
            if (school.hmName.isNotBlank()) {
                SchoolInfoLine(Icons.Default.Person, "मुख्याध्यापक:", "${school.hmName} ${school.hmMobile}")
            }

            if (canManage) {
                Spacer(Modifier.height(11.dp))
                HorizontalDivider(color = Color(0xFFE8E9ED))
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SchoolActionButton(
                        text = "संपादित",
                        icon = Icons.Default.Edit,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        container = PurpleSoft,
                        content = HighDensityPrimary,
                        onClick = edit
                    )

                    Row(
                        modifier = Modifier.weight(1.15f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = school.isActive,
                            onCheckedChange = { if (!busy) toggle() },
                            enabled = !busy,
                            modifier = Modifier.size(width = 54.dp, height = 34.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (school.isActive) "सक्रिय" else "निष्क्रिय",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = SchoolText
                        )
                    }

                    SchoolActionButton(
                        text = "हटवा",
                        icon = Icons.Default.DeleteOutline,
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        container = RedSoft,
                        content = Red,
                        onClick = delete
                    )
                }
            }
        }
    }
}

@Composable
private fun SchoolInfoLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = SchoolSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = SchoolSecondary
        )
        Spacer(Modifier.width(5.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = SchoolSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SchoolActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = container
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Black, color = content, maxLines = 1)
        }
    }
}

@Composable
private fun SchoolEditDialog(
    s: SchoolRecord,
    onSaved: () -> Unit,
    onClose: () -> Unit
) {
    var name by remember(s.id) { mutableStateOf(s.schoolName) }
    var cn by remember(s.id) { mutableStateOf(s.clusterName) }
    var cc by remember(s.id) { mutableStateOf(s.clusterCode) }
    var taluka by remember(s.id) { mutableStateOf(s.taluka) }
    var district by remember(s.id) { mutableStateOf(s.district) }
    var hm by remember(s.id) { mutableStateOf(s.hmName) }
    var mobile by remember(s.id) { mutableStateOf(s.hmMobile) }
    var type by remember(s.id) { mutableStateOf(s.schoolType) }
    var active by remember(s.id) { mutableStateOf(s.isActive) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("शाळा माहिती संपादित करा") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("शाळेचे नाव") }, singleLine = true)
                OutlinedTextField(s.udiseCode, {}, label = { Text("UDISE कोड") }, enabled = false, singleLine = true)
                OutlinedTextField(cn, { cn = it }, label = { Text("केंद्र") }, singleLine = true)
                OutlinedTextField(cc, { cc = it }, label = { Text("केंद्र कोड") }, singleLine = true)
                OutlinedTextField(taluka, { taluka = it }, label = { Text("तालुका") }, singleLine = true)
                OutlinedTextField(district, { district = it }, label = { Text("जिल्हा") }, singleLine = true)
                OutlinedTextField(hm, { hm = it }, label = { Text("मुख्याध्यापक") }, singleLine = true)
                OutlinedTextField(mobile, { mobile = it }, label = { Text("मोबाईल") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("शाळेचा प्रकार") }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (active) "सक्रिय" else "निष्क्रिय")
                    Switch(active, { active = it })
                }
                error?.let { Text(it, color = Red, fontSize = 10.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    BackendApi.updateSchool(
                        s.id,
                        name.trim(),
                        cn.trim(),
                        cc.trim(),
                        taluka.trim(),
                        district.trim(),
                        hm.trim(),
                        mobile.trim(),
                        type.trim(),
                        active,
                        { saving = false; onSaved() },
                        { saving = false; error = it }
                    )
                }
            ) { Text(if (saving) "जतन…" else "जतन करा") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("रद्द करा") } }
    )
}

private fun scopeText(role: UserRole): String = when (role) {
    UserRole.Admin -> "सर्व केंद्रे व शाळांची माहिती"
    UserRole.Cluster_Head -> "आपले केंद्र • संबंधित शाळा"
    UserRole.School_HM -> "आपली शाळा • संबंधित माहिती"
    UserRole.Teacher -> "आपली शाळा • उपलब्ध माहिती"
}
