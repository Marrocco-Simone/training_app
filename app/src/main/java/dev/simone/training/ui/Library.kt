package dev.simone.training.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.simone.training.data.Category
import dev.simone.training.data.Exercise
import dev.simone.training.data.Level
import dev.simone.training.data.Role
import dev.simone.training.data.SsLift
import dev.simone.training.data.TrainingData
import dev.simone.training.data.fmtNum
import dev.simone.training.data.snakeId

private enum class LevelKind(val label: String) { NONE("None"), SS("SS lift"), OG("OG value") }

private fun Level?.text(): String? = when (this) {
    null -> null
    is Level.Ss -> lift.label
    is Level.Og -> "OG ${fmtNum(value)}"
}

@Composable
fun LibraryScreen(data: TrainingData, onSave: (Exercise) -> Unit) {
    var editing by remember { mutableStateOf<Exercise?>(null) }
    val grouped = remember(data.exercises) { data.exercises.sortedBy { it.name.lowercase() }.groupBy { it.category } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            Category.entries.forEach { category ->
                val list = grouped[category].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = category.key) {
                        SectionLabel(category.label, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp), categoryColor(category).takeIf { category.isGroup } ?: Muted)
                    }
                    items(list, key = { it.id }) { exercise ->
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = { Text(listOfNotNull(exercise.role.key, exercise.level.text()).joinToString(" · ")) },
                            modifier = Modifier.clickable { editing = exercise },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = Exercise("", "", Category.OTHER, Role.MAIN, null, null, null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, "Add exercise") }
    }
    editing?.let { exercise ->
        ExerciseDialog(
            initial = exercise,
            onDismiss = { editing = null },
            onSave = { e ->
                editing = null
                onSave(if (e.id.isEmpty()) e.copy(id = snakeId(e.name, data.exerciseById.keys)) else e)
            },
        )
    }
}

@Composable
private fun ExerciseDialog(initial: Exercise, onDismiss: () -> Unit, onSave: (Exercise) -> Unit) {
    var e by remember { mutableStateOf(initial) }
    val kind = when (e.level) {
        null -> LevelKind.NONE
        is Level.Ss -> LevelKind.SS
        is Level.Og -> LevelKind.OG
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isEmpty()) "New exercise" else initial.id) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextIn("Name", e.name, { e = e.copy(name = it ?: "") })
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Choices(Category.entries, e.category, { it.label }) { e = e.copy(category = it) }
                Text("Role", style = MaterialTheme.typography.labelLarge)
                Choices(Role.entries, e.role, { it.key }) { e = e.copy(role = it) }
                Text("Level", style = MaterialTheme.typography.labelLarge)
                Choices(LevelKind.entries, kind, { it.label }) {
                    e = e.copy(
                        level = when (it) {
                            LevelKind.NONE -> null
                            LevelKind.SS -> Level.Ss(SsLift.BACK_SQUAT)
                            LevelKind.OG -> Level.Og(1.0)
                        },
                    )
                }
                when (val level = e.level) {
                    is Level.Ss -> Choices(SsLift.entries, level.lift, { it.label }) { e = e.copy(level = Level.Ss(it)) }
                    is Level.Og -> NumField("OG value", level.value, { v -> v?.let { e = e.copy(level = Level.Og(it)) } }, Modifier.fillMaxWidth())
                    null -> Unit
                }
                IntField("Top reps", e.topReps, { e = e.copy(topReps = it) }, Modifier.fillMaxWidth())
                TextIn("Notes", e.notes, { e = e.copy(notes = it) }, singleLine = false)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(e.copy(name = e.name.trim())) }, enabled = e.name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
