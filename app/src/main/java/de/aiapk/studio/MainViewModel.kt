package de.aiapk.studio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.aiapk.studio.ai.AgentPlan
import de.aiapk.studio.ai.CodingAgentPrompt
import de.aiapk.studio.ai.OpenAiCompatibleClient
import de.aiapk.studio.build.*
import de.aiapk.studio.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as AIAPKApplication
    private val repo = container.repository
    private val projectStore = TermuxProjectStore(app)
    private val buildEngine: BuildEngine = TermuxBuildEngine(app)

    val projects = repo.dao.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val providers = repo.dao.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = repo.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioSettings())

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _files = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    private val _filePreview = MutableStateFlow<FilePreview?>(null)
    val filePreview: StateFlow<FilePreview?> = _filePreview.asStateFlow()
    private val _history = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    private val _busyProjects = MutableStateFlow<Set<Long>>(emptySet())
    val busyProjects: StateFlow<Set<Long>> = _busyProjects.asStateFlow()

    data class FilePreview(val projectId: Long, val path: String, val content: String)

    fun clearNotice() { _notice.value = null }
    fun project(id: Long) = repo.dao.observeProject(id)
    fun messages(id: Long) = repo.dao.observeMessages(id)
    fun builds(id: Long) = repo.dao.observeBuilds(id)
    fun files(id: Long): Flow<List<String>> = _files.map { it[id].orEmpty() }.distinctUntilChanged()
    fun history(id: Long): Flow<List<String>> = _history.map { it[id].orEmpty() }.distinctUntilChanged()

    fun createProject(description: String, requestedType: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val cleanDescription = description.trim().ifBlank { "Neue Android App" }
        val name = deriveProjectName(cleanDescription)
        val type = ProjectTemplates.chooseType(requestedType, cleanDescription)
        val packageStem = slug(name).ifBlank { "project" }.take(28)
        val packageName = "app.aiapk.${packageStem}${System.currentTimeMillis().toString().takeLast(4)}"
        val project = ProjectEntity(
            name = name,
            packageName = packageName,
            type = type,
            path = "~/aiapk/projects/$packageName",
            lastBuildStatus = "Projekt wird vorbereitet"
        )
        val id = repo.dao.insertProject(project)
        repo.dao.insertMessage(ChatMessageEntity(projectId = id, role = "user", text = cleanDescription))
        onCreated(id)

        if (!termuxInstalled()) {
            repo.dao.updateProjectStatus(id, "Termux fehlt")
            repo.dao.insertMessage(ChatMessageEntity(id = 0, projectId = id, role = "assistant", text = "Projekt gespeichert. Für das Erstellen der Projektdateien muss zuerst Termux samt RUN_COMMAND eingerichtet werden. Öffne Einstellungen → Systemdiagnose."))
            return@launch
        }

        setBusy(id, true)
        try {
            val fresh = repo.dao.getProject(id) ?: return@launch
            val dir = projectStore.ensureDirectory(fresh.path)
            if (!dir.success) {
                repo.dao.updateProjectStatus(id, "Setup fehlgeschlagen")
                repo.dao.insertMessage(ChatMessageEntity(projectId = id, role = "assistant", text = "Projektordner konnte nicht angelegt werden: ${dir.errorMessage.ifBlank { dir.stderr }}"))
                return@launch
            }
            val templateFiles = ProjectTemplates.files(type, packageName, name)
            val templateError = applyFiles(fresh, templateFiles, emptyList())
            if (templateError != null) {
                repo.dao.updateProjectStatus(id, "Template fehlgeschlagen")
                repo.dao.insertMessage(ChatMessageEntity(projectId = id, role = "assistant", text = "Template konnte nicht vollständig geschrieben werden: $templateError"))
                return@launch
            }
            projectStore.gitInit(fresh.path)
            projectStore.gitCommit(fresh.path, "Initiales Projekt-Template")
            repo.dao.updateProjectStatus(id, "Template erstellt")
            refreshFilesInternal(fresh)
            refreshHistoryInternal(fresh)
            repo.dao.insertMessage(ChatMessageEntity(projectId = id, role = "assistant", text = "✓ $type-Template erstellt. Ich setze jetzt deine Beschreibung im Projekt um."))

            val provider = repo.dao.activeProvider()
            if (provider == null) {
                repo.dao.insertMessage(ChatMessageEntity(projectId = id, role = "assistant", text = "Noch kein KI-Provider eingerichtet. Das Grundprojekt ist erstellt; richte unter Einstellungen z. B. NVIDIA NIM ein und sende danach deine Änderungswünsche im Chat."))
            } else {
                runAgent(fresh, cleanDescription, provider, insertUserMessage = false)
            }
        } finally {
            setBusy(id, false)
        }
    }

    fun sendChat(projectId: Long, text: String) = viewModelScope.launch {
        val task = text.trim()
        if (task.isBlank() || _busyProjects.value.contains(projectId)) return@launch
        repo.dao.insertMessage(ChatMessageEntity(projectId = projectId, role = "user", text = task))
        val project = repo.dao.getProject(projectId) ?: return@launch
        val provider = repo.dao.activeProvider()
        if (provider == null) {
            repo.dao.insertMessage(ChatMessageEntity(projectId = projectId, role = "assistant", text = "Noch kein KI-Provider eingerichtet. Öffne Einstellungen → KI Provider."))
            return@launch
        }
        if (!termuxInstalled()) {
            repo.dao.insertMessage(ChatMessageEntity(projectId = projectId, role = "assistant", text = "Termux ist nicht verfügbar. Ohne RUN_COMMAND kann ich die Projektdateien auf dem Gerät nicht sicher bearbeiten."))
            return@launch
        }
        setBusy(projectId, true)
        try { runAgent(project, task, provider, insertUserMessage = false) }
        finally { setBusy(projectId, false) }
    }

    private suspend fun runAgent(project: ProjectEntity, task: String, provider: ProviderEntity, insertUserMessage: Boolean) {
        if (insertUserMessage) repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "user", text = task))
        repo.dao.updateProjectStatus(project.id, "KI arbeitet")
        val fileList = projectStore.listFiles(project.path)
        val contextFiles = selectContextFiles(project, fileList, task, maxFiles = 6)
        val sourceContext = contextFiles.joinToString("\n\n") { (path, content) -> "--- $path ---\n${content.take(18_000)}" }.take(55_000)
        val recent = repo.dao.observeMessages(project.id).first().takeLast(8)
            .joinToString("\n") { "${it.role}: ${it.text.take(2500)}" }
        val userPrompt = buildString {
            append("AUFGABE:\n").append(task).append("\n\n")
            append("LETZTER CHATKONTEXT:\n").append(recent).append("\n\n")
            if (sourceContext.isNotBlank()) append("RELEVANTE DATEIEN:\n").append(sourceContext)
        }
        val result = withContext(Dispatchers.IO) {
            OpenAiCompatibleClient.chat(
                provider,
                repo.apiKey(provider),
                listOf("system" to CodingAgentPrompt.system(project.type, project.packageName, fileList), "user" to userPrompt)
            )
        }
        if (!result.ok) {
            repo.dao.updateProjectStatus(project.id, "API-Fehler")
            repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "API-Fehler: ${result.text}"))
            return
        }
        val plan = AgentPlan.parse(result.text)
        if (plan == null) {
            repo.dao.updateProjectStatus(project.id, "Antwort unbrauchbar")
            repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "Das Modell hat keine gültige Dateiaktion geliefert. Rohantwort:\n${result.text.take(5000)}"))
            return
        }
        val applyError = applyFiles(project, plan.files, plan.delete)
        if (applyError != null) {
            repo.dao.updateProjectStatus(project.id, "Dateifehler")
            repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "Änderungen konnten nicht vollständig angewendet werden: $applyError"))
            return
        }
        projectStore.gitCommit(project.path, plan.summary.ifBlank { "KI-Änderung" })
        repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "${plan.summary}\n\n✓ ${plan.files.size} Datei(en) geschrieben${if (plan.delete.isNotEmpty()) ", ${plan.delete.size} gelöscht" else ""}."))
        refreshFilesInternal(project)
        refreshHistoryInternal(project)
        if (plan.build) performBuildAndRepair(project, provider) else repo.dao.updateProjectStatus(project.id, "Änderungen gespeichert")
    }

    private suspend fun applyFiles(project: ProjectEntity, files: List<ProjectFileChange>, deletes: List<String>): String? {
        for (path in deletes) {
            val r = runCatching { projectStore.deleteFile(project.path, path) }.getOrElse { return it.message ?: "Ungültiger Löschpfad" }
            if (!r.success) return (r.errorMessage + " " + r.stderr).trim()
        }
        for (change in files) {
            val r = runCatching { projectStore.writeFile(project.path, change.path, change.content) }.getOrElse { return it.message ?: "Ungültiger Dateipfad" }
            if (!r.success) return (r.errorMessage + " " + r.stderr).trim()
        }
        return null
    }

    fun buildProject(projectId: Long) = viewModelScope.launch {
        if (_busyProjects.value.contains(projectId)) return@launch
        val project = repo.dao.getProject(projectId) ?: return@launch
        if (!termuxInstalled()) { _notice.value = "Termux ist nicht installiert"; return@launch }
        setBusy(projectId, true)
        try { performBuildAndRepair(project, repo.dao.activeProvider()) }
        finally { setBusy(projectId, false) }
    }

    private suspend fun performBuildAndRepair(project: ProjectEntity, provider: ProviderEntity?) {
        repo.dao.updateProjectStatus(project.id, "Build läuft")
        var result = buildEngine.build(project.path)
        repo.dao.insertBuild(BuildEntity(projectId = project.id, success = result.success, output = result.output, apkPath = result.apkPath))
        if (result.success) {
            repo.dao.updateProjectStatus(project.id, "APK erstellt")
            repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "✓ BUILD SUCCESSFUL${result.apkPath?.let { "\nAPK: $it" } ?: ""}"))
            return
        }

        if (provider == null) {
            repo.dao.updateProjectStatus(project.id, "Build fehlgeschlagen")
            repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "Build fehlgeschlagen. Für die automatische Reparatur ist ein KI-Provider erforderlich.\n\n${result.output.takeLast(5000)}"))
            return
        }

        val passes = repo.settings.settings.first().repairPasses.coerceIn(1, 8)
        for (index in 0 until passes) {
            val pass = index + 1
            repo.dao.updateProjectStatus(project.id, "Fehlerreparatur $pass/$passes")
            val fileList = projectStore.listFiles(project.path)
            val likely = filesMentionedInLog(fileList, result.output).take(6)
            val sourceParts = mutableListOf<String>()
  for (path in likely) {
      sourceParts += "--- $path ---\n${projectStore.readFile(project.path, path).take(20_000)}"
  }
  val source = sourceParts.joinToString("\n\n").take(60_000)
            val repairPrompt = """
                Der Android-Build ist fehlgeschlagen. Repariere ausschließlich die tatsächlichen Buildfehler und erhalte bestehende Funktionen.

                BUILDLOG:
                ${result.output.takeLast(18_000)}

                BETROFFENE QUELLDATEIEN:
                $source
            """.trimIndent()
            val ai = withContext(Dispatchers.IO) {
                OpenAiCompatibleClient.chat(
                    provider,
                    repo.apiKey(provider),
                    listOf("system" to CodingAgentPrompt.system(project.type, project.packageName, fileList), "user" to repairPrompt)
                )
            }
            if (!ai.ok) {
                repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "Automatische Reparatur $pass abgebrochen: ${ai.text}"))
                break
            }
            val plan = AgentPlan.parse(ai.text)
            if (plan == null || (plan.files.isEmpty() && plan.delete.isEmpty())) {
                repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "Automatische Reparatur $pass lieferte keine anwendbaren Dateiänderungen."))
                break
            }
            val applyError = applyFiles(project, plan.files, plan.delete)
            if (applyError != null) {
                repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "Reparatur $pass konnte nicht angewendet werden: $applyError"))
                break
            }
            projectStore.gitCommit(project.path, "Build-Reparatur $pass: ${plan.summary}")
            repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "Reparatur $pass/$passes: ${plan.summary}\n✓ ${plan.files.size} Datei(en) geändert. Neuer Build läuft …"))
            refreshFilesInternal(project)
            refreshHistoryInternal(project)
            result = buildEngine.build(project.path)
            repo.dao.insertBuild(BuildEntity(projectId = project.id, success = result.success, output = result.output, apkPath = result.apkPath))
            if (result.success) {
                repo.dao.updateProjectStatus(project.id, "APK erstellt")
                repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "✓ Fehler automatisch behoben. BUILD SUCCESSFUL${result.apkPath?.let { "\nAPK: $it" } ?: ""}"))
                return
            }
        }
        repo.dao.updateProjectStatus(project.id, "Build fehlgeschlagen")
        repo.dao.insertMessage(ChatMessageEntity(projectId = project.id, role = "assistant", text = "Build nach automatischer Reparatur weiterhin fehlgeschlagen.\n\n${result.output.takeLast(6000)}"))
    }

    fun installLatest(projectId: Long) = viewModelScope.launch {
        val latest = repo.dao.latestBuild(projectId)
        val apk = latest?.takeIf { it.success }?.apkPath
        if (apk.isNullOrBlank()) { _notice.value = "Noch keine erfolgreiche APK vorhanden"; return@launch }
        _notice.value = "APK-Installer wird geöffnet …"
        val r = buildEngine.install(apk)
        _notice.value = if (r.success) "APK an Android-Installer übergeben" else "Installation konnte nicht gestartet werden: ${r.output.take(700)}"
    }

    fun refreshFiles(projectId: Long) = viewModelScope.launch {
        repo.dao.getProject(projectId)?.let {
            refreshFilesInternal(it)
            refreshHistoryInternal(it)
        }
    }

    fun undoLastChange(projectId: Long) = viewModelScope.launch {
        if (_busyProjects.value.contains(projectId)) return@launch
        val project = repo.dao.getProject(projectId) ?: return@launch
        setBusy(projectId, true)
        try {
            val r = projectStore.undoLastCommit(project.path)
            if (r.success) {
                repo.dao.updateProjectStatus(projectId, "Letzte Änderung rückgängig")
                repo.dao.insertMessage(ChatMessageEntity(projectId = projectId, role = "assistant", text = "↶ Letzte gespeicherte KI-Änderung wurde über den lokalen Git-Snapshot zurückgesetzt."))
                refreshFilesInternal(project)
                refreshHistoryInternal(project)
            } else _notice.value = "Rückgängig nicht möglich: ${(r.stderr + " " + r.errorMessage).trim().ifBlank { r.stdout }}"
        } finally { setBusy(projectId, false) }
    }

    private suspend fun refreshFilesInternal(project: ProjectEntity) {
        val list = if (termuxInstalled()) projectStore.listFiles(project.path) else emptyList()
        _files.update { it + (project.id to list) }
    }

    private suspend fun refreshHistoryInternal(project: ProjectEntity) {
        val list = if (termuxInstalled()) projectStore.gitHistory(project.path) else emptyList()
        _history.update { it + (project.id to list) }
    }

    fun openFile(projectId: Long, path: String) = viewModelScope.launch {
        val p = repo.dao.getProject(projectId) ?: return@launch
        val content = runCatching { projectStore.readFile(p.path, path) }.getOrElse { "Fehler: ${it.message}" }
        _filePreview.value = FilePreview(projectId, path, content)
    }
    fun closeFilePreview() { _filePreview.value = null }

    fun runDoctor() = viewModelScope.launch {
        if (!termuxInstalled()) { _notice.value = "Termux ist nicht installiert"; return@launch }
        _notice.value = "Systemdiagnose läuft …"
        val bash = "/data/data/com.termux/files/usr/bin/bash"
        val script = "if command -v studio >/dev/null 2>&1; then studio doctor; elif command -v aiapk >/dev/null 2>&1; then aiapk doctor; else printf '{\"ready\":false,\"message\":\"Build Engine nicht installiert\"}'; fi"
        val r = TermuxBridge.runAwait(getApplication(), bash, arrayOf("-lc", script), "~/", timeoutMs = 120_000)
        _notice.value = if (r.success) r.stdout.take(5000) else "Diagnose fehlgeschlagen: ${(r.errorMessage + " " + r.stderr).trim()}"
    }

    fun installBuildEngine() = viewModelScope.launch {
        if (!termuxInstalled()) { _notice.value = "Installiere zuerst Termux von F-Droid oder GitHub"; return@launch }
        _notice.value = "Build Engine wird in Termux eingerichtet …"
        val bash = "/data/data/com.termux/files/usr/bin/bash"
        val script = """
            set -e
            pkg update -y
            pkg install -y git
            if [ -d "${'$'}HOME/termux-studio/.git" ]; then
              git -C "${'$'}HOME/termux-studio" pull --ff-only
            else
              rm -rf "${'$'}HOME/termux-studio"
              git clone https://github.com/poordevcode/termux-android-studio.git "${'$'}HOME/termux-studio"
            fi
            bash "${'$'}HOME/termux-studio/install.sh" -y
            . "${'$'}HOME/.bashrc" 2>/dev/null || true
            studio doctor
        """.trimIndent()
        val r = TermuxBridge.runAwait(getApplication(), bash, arrayOf("-lc", script), "~/", timeoutMs = 1_800_000)
        _notice.value = if (r.success) "✓ Build Engine eingerichtet\n${r.stdout.takeLast(3500)}" else "Build-Engine-Setup fehlgeschlagen:\n${(r.stdout + "\n" + r.stderr + "\n" + r.errorMessage).takeLast(5000)}"
    }

    fun saveProvider(id: Long?, name: String, type: String, baseUrl: String, model: String, apiKey: String) = viewModelScope.launch {
        runCatching { repo.saveProvider(id, name, type, baseUrl, model, apiKey) }
            .onSuccess { _notice.value = "Provider gespeichert" }
            .onFailure { _notice.value = "Speichern fehlgeschlagen: ${it.message}" }
    }

    fun testProvider(name: String, type: String, baseUrl: String, model: String, apiKey: String) = viewModelScope.launch {
        _notice.value = "Verbindung wird getestet …"
        val p = ProviderEntity(name = name, type = type, baseUrl = baseUrl.trimEnd('/'), model = model, keyAlias = "")
        val result = withContext(Dispatchers.IO) { OpenAiCompatibleClient.chat(p, apiKey, listOf("user" to "Antworte nur mit: OK")) }
        _notice.value = if (result.ok) "✓ Verbindung erfolgreich (${result.latencyMs} ms)" else "Verbindung fehlgeschlagen: ${result.text}"
    }

    fun setDarkMode(v: Boolean) = viewModelScope.launch { repo.settings.setDarkMode(v) }
    fun setAutoApply(v: Boolean) = viewModelScope.launch { repo.settings.setAutoApply(v) }
    fun setRepairPasses(v: Int) = viewModelScope.launch { repo.settings.setRepairPasses(v) }
    fun termuxInstalled(): Boolean = TermuxBridge.isInstalled(getApplication())

    private suspend fun selectContextFiles(project: ProjectEntity, files: List<String>, task: String, maxFiles: Int): List<Pair<String, String>> {
        val words = task.lowercase().split(Regex("[^a-z0-9äöüß]+")) .filter { it.length >= 4 }.toSet()
        val priority = files.sortedByDescending { path ->
            var score = 0
            val low = path.lowercase()
            if (low.endsWith(".kt") || low.endsWith(".java") || low.endsWith(".html") || low.endsWith(".js") || low.endsWith(".css")) score += 4
            if (low.contains("main")) score += 3
            if (low.contains("manifest") || low.contains("build.gradle")) score += 2
            score + words.count { low.contains(it) } * 3
        }.take(maxFiles)
        return priority.mapNotNull { path ->
            val content = runCatching { projectStore.readFile(project.path, path) }.getOrNull().orEmpty()
            if (content.isBlank()) null else path to content
        }
    }

    private fun filesMentionedInLog(files: List<String>, log: String): List<String> {
        val low = log.lowercase()
        val mentioned = files.filter { path -> low.contains(path.lowercase()) || low.contains(path.substringAfterLast('/').lowercase()) }
        val fallback = files.filter { it.endsWith(".kt") || it.endsWith(".java") || it.endsWith(".kts") || it.endsWith(".xml") }
        return (mentioned + fallback).distinct()
    }

    private fun setBusy(id: Long, busy: Boolean) {
        _busyProjects.update { if (busy) it + id else it - id }
    }

    private fun deriveProjectName(description: String): String {
        val first = description.lineSequence().firstOrNull().orEmpty().trim()
        val cleaned = first.replace(Regex("(?i)^(erstelle|baue|mach|entwickle)( mir)?( eine| einen)?( app)?\\s*"), "")
            .substringBefore('.').substringBefore(':').trim()
        return cleaned.ifBlank { "Neue App" }.split(Regex("\\s+")).take(5).joinToString(" ").take(38)
    }

    private fun slug(value: String): String {
        val normalized = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return normalized.replace("ß", "ss").replace(Regex("[^a-z0-9]+"), "").ifBlank { "project" }
    }
}
