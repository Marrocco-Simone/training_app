package dev.simone.training

import android.app.Application
import android.content.res.AssetManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.simone.training.data.Exercise
import dev.simone.training.data.Session
import dev.simone.training.data.Standards
import dev.simone.training.data.TrainingData
import dev.simone.training.data.newSession
import dev.simone.training.data.newSessionId
import dev.simone.training.data.parseData
import dev.simone.training.data.parseStandards
import dev.simone.training.data.readDataFile
import dev.simone.training.data.serializeData
import dev.simone.training.data.withCatalog
import dev.simone.training.data.writeDataFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate

private const val CATALOG_FILE = "exercises.json"
private const val STANDARDS_FILE = "levels.json"

private fun readCatalog(assets: AssetManager): List<Exercise> =
    if (CATALOG_FILE in assets.list("").orEmpty()) assets.open(CATALOG_FILE).bufferedReader().use { parseData(it.readText()).exercises }
    else emptyList()

private fun readStandards(assets: AssetManager): Standards? =
    if (STANDARDS_FILE in assets.list("").orEmpty()) assets.open(STANDARDS_FILE).bufferedReader().use { parseStandards(it.readText()) }
    else null

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: TrainingData) : LoadState
    data class Failed(val message: String) : LoadState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val file = File(app.filesDir, "data.json")
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
        }
    }

    fun update(transform: (TrainingData) -> TrainingData) {
        val current = data ?: return
        state = LoadState.Ready(transform(current))
        viewModelScope.launch(Dispatchers.IO) {
            writeLock.withLock {
                val latest = data ?: return@withLock
                runCatching { writeDataFile(file, latest) }.onFailure { message = "Save failed: ${it.message}" }
            }
        }
    }

    fun openNewSession() {
        val current = data ?: return
        openDraft(newSession(current, LocalDate.now()))
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
