package dev.simone.training.ui

import androidx.activity.compose.BackHandler
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.simone.training.AppViewModel
import dev.simone.training.LoadState
import dev.simone.training.data.Run
import dev.simone.training.data.Session
import dev.simone.training.data.TrainingData

private enum class Tab(val label: String) { TODAY("Today"), HISTORY("History"), PROGRESS("Progress"), RUNS("Runs") }

private sealed interface Screen {
    data object Library : Screen
    data object Settings : Screen
    data class SessionView(val id: String) : Screen
    data class RunView(val id: String) : Screen
    data class RunEdit(val run: Run?) : Screen
}

@Composable
fun App(vm: AppViewModel) {
    TrainingTheme {
        Surface(Modifier.fillMaxSize(), color = Iron) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (val state = vm.state) {
                    LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Chalk) }
                    is LoadState.Failed -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Text(state.message)
                        ImportButton(vm, Modifier.fillMaxWidth())
                    }
                    is LoadState.Ready -> Main(vm, state.data)
                }
            }
            Dialogs(vm)
        }
    }
}

@Composable
private fun Dialogs(vm: AppViewModel) {
    vm.message?.let { text ->
        AlertDialog(
            onDismissRequest = { vm.message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { vm.message = null }) { Text("OK") } },
        )
    }
    vm.pendingImport?.let { imported ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("Replace all data?") },
            text = { Text("${imported.exercises.size} exercises\n${imported.sessions.size} sessions\n${imported.runs.size} runs") },
            confirmButton = { TextButton(onClick = vm::confirmImport) { Text("Replace") } },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("Cancel") } },
        )
    }
}

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = if (onBack == null) 20.dp else 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(backIcon, "Back", tint = Chalk) }
        Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium, maxLines = 1, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(MaterialTheme.shapes.small).background(Chalk).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Iron) }
}

@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Seam))
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(28.dp).height(3.dp).background(if (on) Chalk else Iron))
                    Spacer(Modifier.weight(1f))
                    Text(t.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (on) Chalk else Faint)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Main(vm: AppViewModel, data: TrainingData) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var stack by remember { mutableStateOf(listOf<Screen>()) }
    var menu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val draft = vm.draft
    val top = stack.lastOrNull()

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    fun closeEditor() {
        if (vm.draftChanged) confirmDiscard = true else vm.discardDraft()
    }

    val openSession: (Session) -> Unit = { push(Screen.SessionView(it.id)) }
    val openRun: (Run) -> Unit = { push(Screen.RunView(it.id)) }

    BackHandler(draft != null) { closeEditor() }
    BackHandler(draft == null && top != null) { pop() }
    BackHandler(draft == null && top == null && tab != Tab.TODAY) { tab = Tab.TODAY }

    Column(Modifier.fillMaxSize().imePadding()) {
        when {
            draft != null -> {
                Header(if (draft.id.isEmpty()) "New session" else "Edit session", ::closeEditor, Icons.Default.Close) {
                    ActionButton("Save") { vm.saveDraft() }
                }
                Box(Modifier.weight(1f)) {
                    SessionEditor(
                        data = data,
                        draft = draft,
                        onChange = vm::editDraft,
                        onCreateExercise = { e -> vm.update { it.copy(exercises = it.exercises + e) } },
                        onDelete = {
                            vm.deleteDraft()
                            stack = stack.filterNot { it == Screen.SessionView(draft.id) }
                        },
                    )
                }
            }
            top != null -> {
                val title = when (top) {
                    Screen.Library -> "Library"
                    Screen.Settings -> "Settings"
                    is Screen.SessionView -> data.sessions.firstOrNull { it.id == top.id }?.date?.format(DAY_FORMAT) ?: ""
                    is Screen.RunView -> data.runs.firstOrNull { it.id == top.id }?.startedAt?.toLocalDate()?.format(DAY_FORMAT) ?: ""
                    is Screen.RunEdit -> if (top.run == null) "New run" else "Edit run"
                }
                Header(title, ::pop) {
                    when (top) {
                        is Screen.SessionView -> data.sessions.firstOrNull { it.id == top.id }?.let { s -> ActionButton("Edit") { vm.openDraft(s) } }
                        is Screen.RunView -> data.runs.firstOrNull { it.id == top.id }?.let { r -> ActionButton("Edit") { push(Screen.RunEdit(r)) } }
                        else -> Unit
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (top) {
                        Screen.Library -> LibraryScreen(data) { e ->
                            vm.update { d ->
                                val exists = d.exercises.any { it.id == e.id }
                                d.copy(exercises = if (exists) d.exercises.map { if (it.id == e.id) e else it } else d.exercises + e)
                            }
                        }
                        Screen.Settings -> SettingsScreen(vm, data)
                        is Screen.SessionView -> data.sessions.firstOrNull { it.id == top.id }?.let { SessionDetail(data, it) }
                        is Screen.RunView -> data.runs.firstOrNull { it.id == top.id }?.let { RunDetail(it) }
                        is Screen.RunEdit -> key(top) {
                            RunEditor(
                                run = top.run,
                                onSave = { run ->
                                    vm.update { d -> d.copy(runs = d.runs.filter { it.id != run.id } + run) }
                                    pop()
                                },
                                onDelete = {
                                    top.run?.let { run -> vm.update { d -> d.copy(runs = d.runs.filter { it.id != run.id }) } }
                                    stack = stack.dropLast(1).filterNot { it == Screen.RunView(top.run?.id ?: "") }
                                },
                            )
                        }
                    }
                }
            }
            else -> {
                Header(if (tab == Tab.TODAY) LocalDate.now().format(DAY_FORMAT) else tab.label) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu", tint = Muted) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Library") }, onClick = {
                                menu = false
                                push(Screen.Library)
                            })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = {
                                menu = false
                                push(Screen.Settings)
                            })
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.TODAY -> TodayScreen(
                            data = data,
                            standards = vm.standards,
                            onStart = vm::openNewSession,
                            onOpenSession = openSession,
                            onOpenRun = openRun,
                            onSaveSettings = { s -> vm.update { it.copy(settings = s) } },
                        )
                        Tab.HISTORY -> HistoryScreen(data, openSession)
                        Tab.PROGRESS -> ProgressScreen(data, vm.standards)
                        Tab.RUNS -> RunsScreen(data.runs, openRun) { push(Screen.RunEdit(null)) }
                    }
                }
                BottomTabs(tab) { tab = it }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    vm.discardDraft()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
    }
}
