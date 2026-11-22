package dev.simone.training.ui

import dev.simone.training.data.Period
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.simone.training.data.Delta
import dev.simone.training.data.Session
import dev.simone.training.data.TrainingData
import dev.simone.training.data.exerciseName
import dev.simone.training.data.fmtNum
import dev.simone.training.data.itemDeltas
import dev.simone.training.data.itemText
import dev.simone.training.data.occurrences
import dev.simone.training.data.planPeriods
import dev.simone.training.data.previousOccurrence
import java.time.YearMonth
import kotlin.math.abs
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val MONTH_SHORT_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val SHORT_DATE_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private enum class HistoryView(val label: String) { SESSIONS("Sessions"), PERIODS("Periods") }

@Composable
fun HistoryScreen(data: TrainingData, onOpen: (Session) -> Unit) {
    var view by rememberSaveable { mutableStateOf(HistoryView.SESSIONS) }
    val sessions = remember(data.sessions) { data.sessions.sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.id }) }
    val periods = remember(data) { planPeriods(data) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item(key = "view") { Segments(HistoryView.entries, view, { it.label }, { view = it }, Modifier.padding(bottom = 8.dp)) }
        if (view == HistoryView.SESSIONS) {
            itemsIndexed(sessions, key = { _, s -> s.id }) { i, session ->
                val month = YearMonth.from(session.date)
                if (i == 0 || YearMonth.from(sessions[i - 1].date) != month) {
                    SectionLabel(month.format(MONTH_YEAR), Modifier.padding(top = 18.dp, bottom = 6.dp))
                }
                SessionRow(data, session) { onOpen(session) }
            }
        } else {
            items(periods, key = { "${it.start}-${it.end}-${it.exerciseIds.joinToString()}" }) { period ->
                Box(Modifier.padding(vertical = 6.dp)) {
                    PeriodCard(data, period)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(data: TrainingData, session: Session, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${session.date.dayOfMonth}", style = MaterialTheme.typography.headlineLarge)
            Text(session.date.format(WEEKDAY).uppercase(), style = MaterialTheme.typography.labelSmall, color = Faint)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            session.blocks.forEach { block ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    block.items.forEachIndexed { i, item ->
                        if (i > 0) Text(" / ", color = Faint, style = MaterialTheme.typography.bodySmall)
                        Dot(categoryColor(data.exerciseById[item.exerciseId]?.category), 6)
                        Text(
                            " " + exerciseName(data, item.exerciseId) + " " + itemText(item, data.exerciseById[item.exerciseId], withSets = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
            session.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Warn) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Seam))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodCard(data: TrainingData, period: Period) {
    Panel {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${period.start.format(MONTH_SHORT_YEAR)} – ${period.end.format(MONTH_SHORT_YEAR)}".uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Text("${period.sessions}", style = MaterialTheme.typography.headlineSmall)
            Text(" sessions", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            period.exerciseIds.forEach { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(categoryColor(data.exerciseById[id]?.category), 6)
                    Text(" " + exerciseName(data, id), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

private fun Delta.text(): String {
    val sign = if (diff > 0) "+" else "−"
    val n = fmtNum(abs(diff))
    return when (unit) {
        "kg" -> "$sign$n kg"
        "sets" -> "$sign$n set" + if (abs(diff) == 1.0) "" else "s"
        "reps" -> "$sign$n rep" + if (abs(diff) == 1.0) "" else "s"
        "last" -> "$sign$n last set"
        else -> "$sign$n s"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionDetail(data: TrainingData, session: Session) {
    val index = remember(data.sessions) { occurrences(data.sessions) }
    val warmup = data.settings.warmups.firstOrNull { it.id == session.warmup }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (warmup != null || session.notes != null) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    warmup?.let { Tag("Warmup · ${it.name}") }
                    session.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Warn) }
                }
            }
        }
        itemsIndexed(session.blocks, key = { i, _ -> i }) { bi, block ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(('A' + bi % 26).toString(), style = MaterialTheme.typography.titleLarge, color = Faint, modifier = Modifier.weight(1f))
                    block.restSec?.let { Text("rest ${it}s", style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
                block.items.forEach { item ->
                    val exercise = data.exerciseById[item.exerciseId]
                    val previous = previousOccurrence(index, item.exerciseId, session.date, session.id)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Dot(categoryColor(exercise?.category))
                            Text(exerciseName(data, item.exerciseId), style = MaterialTheme.typography.titleSmall, color = Muted, modifier = Modifier.padding(start = 8.dp))
                        }
                        Text(itemText(item, exercise, withSets = true), style = MaterialTheme.typography.headlineSmall)
                        if (previous == null) {
                            Tag("First time")
                        } else {
                            val deltas = itemDeltas(item, previous.item)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (deltas.isEmpty()) Tag("Same as last time")
                                deltas.forEach { Tag(it.text(), if (it.diff > 0) Up else Down) }
                            }
                            Text(
                                "${previous.date.format(if (previous.date.year == session.date.year) DAY_MONTH else SHORT_DATE_YEAR)}: ${itemText(previous.item, exercise, withSets = true)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Faint,
                            )
                        }
                        item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                    }
                }
            }
        }
    }
}
