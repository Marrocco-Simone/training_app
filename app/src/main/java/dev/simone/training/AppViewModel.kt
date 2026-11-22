package dev.simone.training

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.simone.training.data.DATA_FILE
import dev.simone.training.data.Exercise
import dev.simone.training.data.Session
import dev.simone.training.data.Standards
import dev.simone.training.data.TrainingData
import dev.simone.training.data.newSession
import dev.simone.training.data.newSessionId
import dev.simone.training.data.parseData
import dev.simone.training.data.readCatalog
import dev.simone.training.data.readDataFile
import dev.simone.training.data.readStandards
import dev.simone.training.data.serializeData
import dev.simone.training.data.withCatalog
import dev.simone.training.data.writeDataFile
import dev.simone.training.widget.updateWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: TrainingData) : LoadState
    data class Failed(val message: String) : LoadState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, DATA_FILE)
    private val writeLock = Mutex()
    private var catalog: List<Exercise> = emptyList()
    var standards: Standards? = null
        private set

    var state by mutableStateOf<LoadState>(LoadState.Loading)
        private set
    var message by mutableStateOf<String?>(null)
    var pendingImport by mutableStateOf<TrainingData?>(null)
        private set

    var draft by mutableStateOf<Session?>(null)
        private set
    private var draftOrigin: Session? = null
    private var newSessionPending = false
    val draftChanged: Boolean get() = draft != draftOrigin

    val data: TrainingData? get() = (state as? LoadState.Ready)?.data

    init {
        viewModelScope.launch {
            state = withContext(Dispatchers.IO) {
                runCatching {
                    catalog = readCatalog(app.assets)
                    standards = readStandards(app.assets)
                    readDataFile(file).withCatalog(catalog)
                }.fold(
                    onSuccess = { LoadState.Ready(it) },
                    onFailure = { LoadState.Failed("Cannot load data: ${it.message}") },
                )
            }
            if (newSessionPending) {
                newSessionPending = false
                openNewSession()
            }
        }
    }

    fun update(transform: (TrainingData) -> TrainingData) {
        val current = data ?: return
        state = LoadState.Ready(transform(current))
        viewModelScope.launch(Dispatchers.IO) {
            val saved = writeLock.withLock {
                val latest = data ?: return@withLock false
                runCatching { writeDataFile(file, latest) }.onFailure { message = "Save failed: ${it.message}" }.isSuccess
            }
            if (saved) updateWidgets(getApplication())
        }
    }

    fun openNewSession() {
        if (draft != null) return
        val current = data
        if (current == null) newSessionPending = true else openDraft(newSession(current, LocalDate.now()))
    }

    fun openDraft(session: Session) {
        draft = session
        draftOrigin = session
    }

    fun editDraft(session: Session) {
        draft = session
    }

    fun discardDraft() {
        draft = null
        draftOrigin = null
    }

    fun saveDraft() {
        val session = draft ?: return
        update { data ->
            val saved = if (session.id.isEmpty()) session.copy(id = newSessionId(data, session.date)) else session
            data.copy(sessions = data.sessions.filter { it.id != saved.id } + saved)
        }
        discardDraft()
    }

    fun deleteDraft() {
        val id = draft?.id ?: return
        update { data -> data.copy(sessions = data.sessions.filter { it.id != id }) }
        discardDraft()
    }

    fun export(uri: Uri) {
        val current = data ?: return
        viewModelScope.launch(Dispatchers.IO) {
            message = runCatching {
                val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("Cannot open the file")
                stream.bufferedWriter().use { it.write(serializeData(current, Instant.now())) }
            }.fold(
                onSuccess = { "Exported ${current.sessions.size} sessions and ${current.runs.size} runs" },
                onFailure = { "Export failed: ${it.message}" },
            )
        }
    }

    fun readImport(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open the file")
                parseData(stream.bufferedReader().use { it.readText() })
            }.fold(
                onSuccess = { pendingImport = it },
                onFailure = { message = "Import failed: ${it.message}" },
            )
        }
    }

    fun confirmImport() {
        val imported = pendingImport ?: return
        pendingImport = null
        discardDraft()
        state = LoadState.Ready(imported.withCatalog(catalog))
        update { it }
    }

    fun cancelImport() {
        pendingImport = null
    }
}
