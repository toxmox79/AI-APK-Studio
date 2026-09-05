@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.aiapk.studio.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.aiapk.studio.MainViewModel
import de.aiapk.studio.data.BuildEntity
import de.aiapk.studio.data.ChatMessageEntity
import de.aiapk.studio.data.ProjectEntity
import de.aiapk.studio.data.ProviderEntity

@Composable
fun HomeScreen(vm: MainViewModel, onNew: () -> Unit, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val projects by vm.projects.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI APK", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("STUDIO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Einstellungen") }
        }
        Spacer(Modifier.height(12.dp))
        NeoActionButton("Neue App erstellen", Modifier.fillMaxWidth(), icon = { Icon(Icons.Default.Add, null) }, onClick = onNew)
        Spacer(Modifier.height(18.dp))
        Text("Meine Projekte", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (projects.isEmpty()) {
            NeoCard(Modifier.fillMaxWidth()) { Text("Noch keine Projekte. Erstelle deine erste App per KI.") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(projects, key = { it.id }) { p -> ProjectCard(p) { onOpen(p.id) } }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, onClick: () -> Unit) {
    NeoCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .14f), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.Bold)
                Text("${project.type} · ${project.versionName}", style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(project.lastBuildStatus, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }
}

@Composable
fun NewProjectScreen(vm: MainViewModel, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Automatisch") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        TopBar("Neue App", onBack)
        Spacer(Modifier.height(12.dp))
        Text("Was möchtest du erstellen?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Beschreibe Funktion und Aussehen. Der Agent legt anschließend das Projekt an.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        NeoCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                placeholder = { Text("z. B. WiFi Popup – eine App, die Captive-Portals erkennt …") },
                shape = RoundedCornerShape(18.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        NeoCard(Modifier.fillMaxWidth()) {
            Column {
                Text("App-Typ", fontWeight = FontWeight.Bold)
                listOf("Automatisch", "Quick App", "Native Android").forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { type = option }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == option, onClick = { type = option })
                        Text(option)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        NeoActionButton(
            "App erstellen",
            Modifier.fillMaxWidth(),
            enabled = description.isNotBlank(),
            icon = { Icon(Icons.Default.AutoAwesome, null) },
            onClick = { vm.createProject(description, type, onCreated) }
        )
    }
}

@Composable
fun ProjectScreen(vm: MainViewModel, projectId: Long, onBack: () -> Unit) {
    val project by vm.project(projectId).collectAsState(initial = null)
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project?.name ?: "Projekt")
                        Text(project?.lastBuildStatus ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Icons.Default.Chat to "Chat", Icons.Default.Folder to "Dateien", Icons.Default.Build to "Build").forEachIndexed { i, item ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.first, null) }, label = { Text(item.second) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> ChatScreen(vm, projectId)
                1 -> FilesScreen(vm, projectId, project)
                else -> BuildScreen(vm, projectId, project)
            }
        }
    }
}

@Composable
private fun ChatScreen(vm: MainViewModel, projectId: Long) {
    val messages by vm.messages(projectId).collectAsState(initial = emptyList())
    val busy by vm.busyProjects.collectAsState()
    val isBusy = projectId in busy
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(messages, key = { it.id }) { MessageBubble(it) }
            if (isBusy) item { AgentActivityBubble() }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isBusy) "KI arbeitet …" else "Änderung beschreiben …") },
                shape = RoundedCornerShape(20.dp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { vm.sendChat(projectId, text); text = "" } })
            )
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick = { if (text.isNotBlank()) { vm.sendChat(projectId, text); text = "" } },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(if (isBusy) Icons.Default.HourglassTop else Icons.Default.Send, null) }
        }
    }
}

@Composable
private fun AgentActivityBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        NeoCard(Modifier.widthIn(max = 330.dp), PaddingValues(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Projekt analysieren · Dateien ändern · Build prüfen", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessageEntity) {
    val user = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        if (user) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.widthIn(max = 330.dp)
            ) { Text(m.text, Modifier.padding(14.dp)) }
        } else {
            NeoCard(Modifier.widthIn(max = 330.dp), PaddingValues(14.dp)) { Text(m.text) }
        }
    }
}

@Composable
private fun FilesScreen(vm: MainViewModel, projectId: Long, project: ProjectEntity?) {
    val files by vm.files(projectId).collectAsState(initial = emptyList())
    val history by vm.history(projectId).collectAsState(initial = emptyList())
    val preview by vm.filePreview.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    LaunchedEffect(projectId) { vm.refreshFiles(projectId) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        NeoCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Projektdateien", fontWeight = FontWeight.Bold)
                    Text(project?.path ?: "–", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, "Änderungsverlauf") }
                IconButton(onClick = { vm.refreshFiles(projectId) }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
            }
        }
        if (!vm.termuxInstalled()) {
            NeoCard(Modifier.fillMaxWidth()) { Text("Termux ist nicht installiert. Projektdateien können erst nach dem Setup gelesen werden.") }
        } else if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(files) { path ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openFile(projectId, path) }.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(fileIcon(path), null, tint = MaterialTheme.colorScheme.primary.copy(alpha = .82f))
                        Spacer(Modifier.width(10.dp))
                        Text(path, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Icon(Icons.Default.ChevronRight, null)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
                }
            }
        }
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Schließen") } },
            dismissButton = {
                TextButton(
                    enabled = history.size > 1,
                    onClick = { vm.undoLastChange(projectId); showHistory = false }
                ) { Text("Letzte Änderung rückgängig") }
            },
            title = { Text("Lokale Snapshots") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (history.isEmpty()) Text("Noch keine Git-Snapshots vorhanden.")
                    history.forEach { entry ->
                        Text(entry.substringAfter('|', entry), fontWeight = FontWeight.Medium)
                        Text(entry.substringBefore('|', ""), style = MaterialTheme.typography.labelSmall)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        )
    }

    preview?.takeIf { it.projectId == projectId }?.let { p ->
        AlertDialog(
            onDismissRequest = vm::closeFilePreview,
            confirmButton = { TextButton(onClick = vm::closeFilePreview) { Text("Schließen") } },
            title = { Text(p.path, style = MaterialTheme.typography.titleSmall) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    Text(p.content, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }
}

private fun fileIcon(path: String) = when {
    path.endsWith(".kt") || path.endsWith(".java") -> Icons.Default.Code
    path.endsWith(".xml") -> Icons.Default.DataObject
    path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js") -> Icons.Default.Language
    path.contains("gradle") -> Icons.Default.Build
    else -> Icons.Default.Description
}

@Composable
private fun BuildScreen(vm: MainViewModel, projectId: Long, project: ProjectEntity?) {
    val builds by vm.builds(projectId).collectAsState(initial = emptyList())
    val busy by vm.busyProjects.collectAsState()
    val notice by vm.notice.collectAsState()
    val latest = builds.firstOrNull()
    val installed = remember { vm.termuxInstalled() }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            NeoCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Build Engine", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    StatusRow("Termux", installed)
                    StatusRow("RUN_COMMAND", installed, if (installed) "Berechtigung erforderlich" else "")
                    Text("JDK, SDK, aapt2 und Gradle lassen sich über Systemdiagnose prüfen.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            NeoCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Build", fontWeight = FontWeight.Bold)
                    Text(project?.lastBuildStatus ?: "Bereit", color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(12.dp))
                    NeoActionButton(
                        if (projectId in busy) "Build läuft …" else "Build starten",
                        Modifier.fillMaxWidth(),
                        enabled = installed && projectId !in busy,
                        icon = { Icon(Icons.Default.Build, null) },
                        onClick = { vm.buildProject(projectId) }
                    )
                    if (latest?.success == true && !latest.apkPath.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        NeoActionButton(
                            "APK installieren",
                            Modifier.fillMaxWidth(),
                            primary = false,
                            icon = { Icon(Icons.Default.InstallMobile, null) },
                            onClick = { vm.installLatest(projectId) }
                        )
                    }
                }
            }
        }
        latest?.let { build ->
            item { BuildResultCard(build) }
        }
        notice?.let { msg -> item { NeoCard(Modifier.fillMaxWidth()) { Text(msg, style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable
private fun BuildResultCard(build: BuildEntity) {
    var expanded by remember(build.id) { mutableStateOf(false) }
    NeoCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (build.success) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (build.success) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(if (build.success) "BUILD SUCCESSFUL" else "BUILD FAILED", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger" else "Log") }
            }
            build.apkPath?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    Text(build.output.takeLast(20_000), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, note: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f))
        if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val providers by vm.providers.collectAsState()
    val notice by vm.notice.collectAsState()
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        TopBar("Einstellungen", onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 2.dp)) {
            item {
                NeoCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("Darstellung & Agent", fontWeight = FontWeight.Bold)
                        SettingSwitch("Dark Mode", settings.darkMode) { vm.setDarkMode(it) }
                        Spacer(Modifier.height(4.dp))
                        Text("Automatische Reparaturdurchgänge: ${settings.repairPasses}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = settings.repairPasses.toFloat(),
                            onValueChange = { vm.setRepairPasses(it.toInt()) },
                            valueRange = 1f..8f,
                            steps = 6
                        )
                    }
                }
            }
            item { ProviderEditor(vm, providers.firstOrNull()) }
            item {
                NeoCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("Systemdiagnose", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        StatusRow("Termux installiert", vm.termuxInstalled())
                        Text("RUN_COMMAND benötigt in Termux allow-external-apps=true sowie die Android-Berechtigung unter Zusätzliche Berechtigungen.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        NeoActionButton(
                            "App-Berechtigungen öffnen",
                            Modifier.fillMaxWidth(),
                            primary = false,
                            icon = { Icon(Icons.Default.AdminPanelSettings, null) }
                        ) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                        Spacer(Modifier.height(8.dp))
                        NeoActionButton("Build Engine installieren", Modifier.fillMaxWidth(), enabled = vm.termuxInstalled(), icon = { Icon(Icons.Default.Download, null) }) { vm.installBuildEngine() }
                        Spacer(Modifier.height(8.dp))
                        NeoActionButton("Diagnose starten", Modifier.fillMaxWidth(), primary = false, enabled = vm.termuxInstalled(), icon = { Icon(Icons.Default.HealthAndSafety, null) }) { vm.runDoctor() }
                    }
                }
            }
            notice?.let { msg -> item { NeoCard(Modifier.fillMaxWidth()) { Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } } }
        }
    }
}

@Composable
private fun ProviderEditor(vm: MainViewModel, existing: ProviderEntity?) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "NVIDIA NIM") }
    var base by remember(existing?.id) { mutableStateOf(existing?.baseUrl ?: "https://integrate.api.nvidia.com/v1") }
    var model by remember(existing?.id) { mutableStateOf(existing?.model ?: "poolside/laguna-xs-2.1") }
    var key by remember(existing?.id) { mutableStateOf("") }
    NeoCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("KI Provider", fontWeight = FontWeight.Bold)
            }
            Text("NVIDIA NIM oder jeder OpenAI-kompatible Endpoint", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
            OutlinedTextField(base, { base = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text("Modell") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
            OutlinedTextField(
                key, { key = it },
                label = { Text(if (existing == null) "API-Key" else "API-Key (leer = beibehalten)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.testProvider(name, "openai", base, model, key) }, enabled = key.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Testen") }
                Button(onClick = { vm.saveProvider(existing?.id, name, "openai", base, model, key) }, enabled = key.isNotBlank() || existing != null, modifier = Modifier.weight(1f)) { Text("Speichern") }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
