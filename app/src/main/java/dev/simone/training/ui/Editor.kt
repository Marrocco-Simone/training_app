package dev.simone.training.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.simone.training.data.Block
import dev.simone.training.data.Category
import dev.simone.training.data.Exercise
import dev.simone.training.data.Item
import dev.simone.training.data.Role
import dev.simone.training.data.Session
import dev.simone.training.data.SetValues
import dev.simone.training.data.Suggestion
import dev.simone.training.data.TrainingData
import dev.simone.training.data.exerciseName
import dev.simone.training.data.occurrences
import dev.simone.training.data.previousOccurrence
import dev.simone.training.data.snakeId
import dev.simone.training.data.suggestion
import dev.simone.training.data.withValuesOf

typealias PickExercise = ((String) -> Unit) -> Unit

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
private fun <T> List<T>.removed(index: Int): List<T> = toMutableList().also { it.removeAt(index) }
private fun <T> List<T>.swapped(a: Int, b: Int): List<T> = toMutableList().also { it[a] = this[b]; it[b] = this[a] }

/** Holds the picker dialog for the editors; [content] receives the function that opens it. */
@Composable
fun WithExercisePicker(data: TrainingData, onCreateExercise: (Exercise) -> Unit, content: @Composable (PickExercise) -> Unit) {
    var onPicked by remember { mutableStateOf<((String) -> Unit)?>(null) }
    content { callback -> onPicked = callback }
    onPicked?.let { callback ->
        ExercisePicker(
            data = data,
            onPick = { id ->
                onPicked = null
                callback(id)
            },
            onCreate = { name ->
                val exercise = Exercise(snakeId(name, data.exerciseById.keys), name, Category.OTHER, Role.MAIN, null, null, null)
                onCreateExercise(exercise)
                onPicked = null
                callback(exercise.id)
            },
            onDismiss = { onPicked = null },
        )
    }
}

@Composable
fun SessionEditor(
    data: TrainingData,
    draft: Session,
    onChange: (Session) -> Unit,
    onCreateExercise: (Exercise) -> Unit,
    onDelete: () -> Unit,
) {
    val index = remember(data.sessions) { occurrences(data.sessions) }
    val exerciseIds = draft.blocks.flatMap { b -> b.items.map { it.exerciseId } }.toSet()
    val suggestions = remember(index, draft.date, draft.id, exerciseIds) {
        exerciseIds.associateWith { id ->
            suggestion(previousOccurrence(index, id, draft.date, draft.id), data.exerciseById[id], draft.date)
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    WithExercisePicker(data, onCreateExercise) { pick ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField("Date", draft.date, { onChange(draft.copy(date = it)) }, Modifier.fillMaxWidth())
                    if (data.settings.warmups.isNotEmpty()) {
                        SectionLabel("Warmup")
                        Segments(
                            listOf<String?>(null) + data.settings.warmups.map { it.id },
                            draft.warmup,
                            { id -> data.settings.warmups.firstOrNull { it.id == id }?.name ?: "None" },
                            { onChange(draft.copy(warmup = it)) },
                        )
                    }
                }
            }
            blockItems("s", data, draft.blocks, { onChange(draft.copy(blocks = it)) }, pick) { suggestions[it.exerciseId] }
            item(key = "notes") {
                TextIn("Notes", draft.notes, { onChange(draft.copy(notes = it)) }, singleLine = false)
            }
            if (draft.id.isNotEmpty()) {
                item(key = "delete") {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete session", color = Down) }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

fun LazyListScope.blockItems(
    keyPrefix: String,
    data: TrainingData,
    blocks: List<Block>,
    onBlocks: (List<Block>) -> Unit,
    pick: PickExercise,
    suggestionFor: ((Item) -> Suggestion?)? = null,
) {
    itemsIndexed(blocks, key = { bi, _ -> "$keyPrefix-$bi" }, contentType = { _, _ -> "block" }) { bi, block ->
        val setBlock: (Block) -> Unit = { onBlocks(blocks.replaced(bi, it)) }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(('A' + bi % 26).toString(), style = MaterialTheme.typography.headlineMedium, color = Faint)
                Spacer(Modifier.width(14.dp))
                SectionLabel("Rest")
                Spacer(Modifier.width(6.dp))
                Box(Modifier.width(52.dp).clip(MaterialTheme.shapes.small).background(PlateHigh).padding(vertical = 6.dp)) {
                    BareNumber(block.restSec?.toDouble(), { setBlock(block.copy(restSec = it?.toInt())) }, true, MaterialTheme.typography.titleSmall)
                }
                Text(" s", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.weight(1f))
                SmallIcon(Icons.Default.KeyboardArrowUp, "Move up", bi > 0) { onBlocks(blocks.swapped(bi, bi - 1)) }
                SmallIcon(Icons.Default.KeyboardArrowDown, "Move down", bi < blocks.size - 1) { onBlocks(blocks.swapped(bi, bi + 1)) }
                SmallIcon(Icons.Default.Close, "Remove block") { onBlocks(blocks.removed(bi)) }
            }
            block.items.forEachIndexed { ii, item ->
                if (ii > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Seam))
                ItemEditor(
                    data = data,
                    item = item,
                    suggestion = suggestionFor?.invoke(item),
                    onChange = { setBlock(block.copy(items = block.items.replaced(ii, it))) },
                    onRemove = {
                        val items = block.items.removed(ii)
                        onBlocks(if (items.isEmpty()) blocks.removed(bi) else blocks.replaced(bi, block.copy(items = items)))
                    },
                    onChangeExercise = { pick { id -> setBlock(block.copy(items = block.items.replaced(ii, item.copy(exerciseId = id)))) } },
                )
            }
            Text(
                "+ Superset exercise",
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
                modifier = Modifier.clickable { pick { id -> setBlock(block.copy(items = block.items + Item(id))) } }.padding(vertical = 6.dp),
            )
        }
    }
    item(key = "$keyPrefix-add") {
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(MaterialTheme.shapes.medium)
                .border(1.dp, Seam, MaterialTheme.shapes.medium)
                .clickable { pick { id -> onBlocks(blocks + Block(listOf(Item(id)))) } },
            contentAlignment = Alignment.Center,
        ) { Text("+ BLOCK", style = MaterialTheme.typography.labelMedium, color = Chalk) }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, label, tint = if (enabled) Muted else Seam, modifier = Modifier.size(20.dp))
    }
}

private fun Item.hasMoreFields(): Boolean =
    seconds != null || lastSeconds != null || dropReps != null || extra.isNotEmpty() || perSide || toFailure || setup != null || note != null

@Composable
private fun RowScope.StepCell(label: String, value: Double?, onChange: (Double?) -> Unit, step: Double, integer: Boolean, start: Double? = null, min: Double? = 0.0) {
    Column(
        Modifier.weight(1f).clip(MaterialTheme.shapes.small).background(PlateHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepButton("+") { onChange((value ?: start ?: 0.0) + step) }
        BareNumber(value, onChange, integer, MaterialTheme.typography.headlineSmall, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Faint)
        StepButton("−") { onChange(((value ?: start ?: 0.0) - step).let { v -> min?.let { v.coerceAtLeast(it) } ?: v }) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(34.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Muted)
    }
}

@Composable
private fun ItemEditor(
    data: TrainingData,
    item: Item,
    suggestion: Suggestion?,
    onChange: (Item) -> Unit,
    onRemove: () -> Unit,
    onChangeExercise: () -> Unit,
) {
    var expanded by remember { mutableStateOf(item.hasMoreFields()) }
    val exercise = data.exerciseById[item.exerciseId]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(categoryColor(exercise?.category))
            Text(
                exerciseName(data, item.exerciseId),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).clickable(onClick = onChangeExercise).padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
            )
            if (item.parsed) SmallIcon(Icons.Default.MoreVert, "More fields") { expanded = !expanded }
            SmallIcon(Icons.Default.Close, "Remove exercise", onClick = onRemove)
        }
        suggestion?.let { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.text, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
                s.values?.takeIf { item.parsed }?.let { values ->
                    Text(
                        "APPLY",
                        style = MaterialTheme.typography.labelMedium,
                        color = Chalk,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { onChange(item.withValuesOf(values)) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (!item.parsed) {
            Text(item.raw ?: "", fontFamily = FontFamily.Monospace, color = Muted)
            TextButton(onClick = { onChange(item.copy(parsed = true)) }) { Text("Replace with values") }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepCell("Sets", item.sets?.toDouble(), { onChange(item.copy(sets = it?.toInt())) }, 1.0, true)
            StepCell("Reps", item.reps?.toDouble(), { onChange(item.copy(reps = it?.toInt())) }, 1.0, true)
            StepCell("Last", item.lastReps?.toDouble(), { onChange(item.copy(lastReps = it?.toInt())) }, 1.0, true, start = item.reps?.toDouble())
            StepCell("Kg", item.loadKg, { onChange(item.copy(loadKg = it)) }, 2.5, false, min = null)
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Sec", item.seconds, { onChange(item.copy(seconds = it)) }, Modifier.weight(1f))
                NumField("Last sec", item.lastSeconds, { onChange(item.copy(lastSeconds = it)) }, Modifier.weight(1f))
                IntField("Drop", item.dropReps, { onChange(item.copy(dropReps = it)) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Toggle("Per side", item.perSide) { onChange(item.copy(perSide = !item.perSide)) }
                Toggle("To failure", item.toFailure) { onChange(item.copy(toFailure = !item.toFailure)) }
            }
            TextIn("Setup", item.setup, { onChange(item.copy(setup = it)) })
            TextIn("Note", item.note, { onChange(item.copy(note = it)) })
            item.extra.forEachIndexed { ei, set ->
                val setExtra: (SetValues) -> Unit = { onChange(item.copy(extra = item.extra.replaced(ei, it))) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumField("Kg", set.loadKg, { setExtra(set.copy(loadKg = it)) }, Modifier.weight(1f))
                    IntField("Reps", set.reps, { setExtra(set.copy(reps = it)) }, Modifier.weight(1f))
                    NumField("Sec", set.seconds, { setExtra(set.copy(seconds = it)) }, Modifier.weight(1f))
                    SmallIcon(Icons.Default.Close, "Remove set") { onChange(item.copy(extra = item.extra.removed(ei))) }
                }
            }
            Text(
                "+ Extra set",
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
                modifier = Modifier.clickable { onChange(item.copy(extra = item.extra + SetValues(item.reps, item.seconds, item.loadKg))) }.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(MaterialTheme.shapes.small)
            .background(if (on) Chalk else PlateHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (on) Iron else Muted) }
}
