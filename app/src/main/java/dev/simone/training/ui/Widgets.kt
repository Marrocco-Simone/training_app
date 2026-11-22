package dev.simone.training.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.simone.training.data.Exercise
import dev.simone.training.data.TrainingData
import dev.simone.training.data.fmtNum
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

fun fmtDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun parseDuration(text: String): Int? {
    val parts = text.trim().split(":").map { it.toIntOrNull() ?: return null }
    if (parts.isEmpty() || parts.size > 3 || parts.any { it < 0 }) return null
    return parts.fold(0) { acc, p -> acc * 60 + p }
}

private data class Edit(val text: String, val basis: String, val valid: Boolean)

private class Synced(val text: String, val error: Boolean, val onChange: (String) -> Unit)

/**
 * [accept] applies the typed text and returns its canonical form, or null when the text is not valid.
 * The field keeps the typed text while the value matches it, and shows [formatted] after an outside change.
 */
@Composable
private fun rememberSynced(formatted: String, accept: (String) -> String?): Synced {
    var edit by remember { mutableStateOf(Edit(formatted, formatted, true)) }
    val current = edit.basis == formatted
    return Synced(
        text = if (current) edit.text else formatted,
        error = current && !edit.valid,
        onChange = { t -> accept(t).let { edit = Edit(t, it ?: formatted, it != null) } },
    )
}

@Composable
fun SyncedField(
    label: String,
    formatted: String,
    accept: (String) -> String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val synced = rememberSynced(formatted, accept)
    OutlinedTextField(
        value = synced.text,
        onValueChange = synced.onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        isError = synced.error,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

private fun acceptNumber(integer: Boolean, onChange: (Double?) -> Unit): (String) -> String? = { t ->
    if (t.isBlank()) {
        onChange(null)
        ""
    } else {
        (if (integer) t.trim().toIntOrNull()?.toDouble() else t.trim().replace(',', '.').toDoubleOrNull())?.let {
            onChange(it)
            fmtNum(it)
        }
    }
}

@Composable
fun BareNumber(value: Double?, onChange: (Double?) -> Unit, integer: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    val formatted = value?.let(::fmtNum) ?: ""
    val centered = style.copy(textAlign = TextAlign.Center)
    if (!editing) {
        Text(formatted.ifEmpty { "–" }, style = centered, color = if (formatted.isEmpty()) Faint else Chalk, modifier = modifier.clickable { editing = true })
        return
    }
    val accept = acceptNumber(integer, onChange)
    var field by remember { mutableStateOf(TextFieldValue(formatted, TextRange(0, formatted.length))) }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            error = accept(it.text) == null
        },
        singleLine = true,
        textStyle = centered.copy(color = if (error) Down else Chalk),
        cursorBrush = SolidColor(Chalk),
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { editing = false }),
        modifier = modifier.focusRequester(focus).onFocusChanged {
            if (focused && !it.isFocused) editing = false
            focused = it.isFocused
        },
    )
}

@Composable
fun NumField(
    label: String,
    value: Double?,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    step: Double? = null,
    integer: Boolean = false,
    min: Double? = null,
    start: Double? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (step != null) {
            IconButton(onClick = { onChange(((value ?: start ?: 0.0) - step).let { v -> min?.let { v.coerceAtLeast(it) } ?: v }) }, Modifier.size(40.dp)) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
        }
        SyncedField(
            label = label,
            formatted = value?.let(::fmtNum) ?: "",
            accept = acceptNumber(integer, onChange),
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        if (step != null) {
            IconButton(onClick = { onChange((value ?: start ?: 0.0) + step) }, Modifier.size(40.dp)) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun IntField(label: String, value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier, step: Boolean = false, start: Int? = null) {
    NumField(label, value?.toDouble(), { onChange(it?.toInt()) }, modifier, step = if (step) 1.0 else null, integer = true, min = 0.0, start = start?.toDouble())
}

@Composable
fun TextIn(label: String, value: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value ?: "",
        onValueChange = { onChange(it.ifEmpty { null }) },
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun DateField(label: String, value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    SyncedField(
        label = label,
        formatted = value.toString(),
        accept = { t -> runCatching { LocalDate.parse(t.trim()) }.getOrNull()?.also(onChange)?.toString() },
        modifier = modifier,
    )
}

@Composable
fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) = Segments(options, selected, label, onSelect)

@Composable
fun ExercisePicker(data: TrainingData, onPick: (String) -> Unit, onCreate: ((String) -> Unit)?, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val lastUsed = remember(data) {
        buildMap<String, LocalDate> {
            data.sessions.forEach { s ->
                s.blocks.forEach { b -> b.items.forEach { i -> if ((get(i.exerciseId) ?: LocalDate.MIN) < s.date) put(i.exerciseId, s.date) } }
            }
        }
    }
    val sorted = remember(data) {
        data.exercises.sortedWith(compareByDescending<Exercise> { lastUsed[it.id] ?: LocalDate.MIN }.thenBy { it.name })
    }
    val q = query.trim()
    val shown = if (q.isEmpty()) sorted else sorted.filter { it.name.contains(q, ignoreCase = true) || it.id.contains(q, ignoreCase = true) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (onCreate != null && q.isNotEmpty() && shown.none { it.name.equals(q, ignoreCase = true) }) {
                        item {
                            ListItem(
                                headlineContent = { Text("Create \"$q\"") },
                                modifier = Modifier.clickable { onCreate(q) },
                            )
                        }
                    }
                    items(shown, key = { it.id }) { exercise ->
                        ListItem(
                            headlineContent = { Text(exercise.name) },
                            supportingContent = { Text(exercise.category.label) },
                            modifier = Modifier.clickable { onPick(exercise.id) },
                        )
                    }
                }
            }
        }
    }
}
