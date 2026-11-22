package dev.simone.training.ui

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.simone.training.data.GROUPS
import dev.simone.training.data.Role
import dev.simone.training.data.Run
import dev.simone.training.data.STALE_DAYS
import dev.simone.training.data.Session
import dev.simone.training.data.Settings
import dev.simone.training.data.Standards
import dev.simone.training.data.TrainingData
import dev.simone.training.data.blockText
import dev.simone.training.data.daysBetween
import dev.simone.training.data.exerciseName
import dev.simone.training.data.groupLevels
import dev.simone.training.data.latestSession
import dev.simone.training.data.occurrences
import dev.simone.training.data.previousOccurrence
import dev.simone.training.data.suggestion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val HEAT_WEEKS = 16
private const val LEVEL_SCALE = 12.0
private val SHORT_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

@Composable
private fun BigStat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.displayLarge, color = color)
        SectionLabel(label)
    }
}

@Composable
fun TodayScreen(
    data: TrainingData,
    standards: Standards?,
    onStart: () -> Unit,
    onOpenSession: (Session) -> Unit,
    onOpenRun: (Run) -> Unit,
    onSaveSettings: (Settings) -> Unit,
) {
    val today = LocalDate.now()
    val last = latestSession(data)
    val lastRun = data.runs.maxByOrNull { it.startedAt }
    val sinceSession = last?.let { daysBetween(it.date, today) }
    val sinceRun = lastRun?.let { daysBetween(it.startedAt.toLocalDate(), today) }
    val stale = sinceSession != null && sinceSession >= STALE_DAYS
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(horizontal = 4.dp)) {
            BigStat(sinceSession?.toString() ?: "–", "days since strength", if (stale) Warn else Chalk, Modifier.weight(1f))
            BigStat(sinceRun?.toString() ?: "–", "days since run", if (sinceRun != null && sinceRun >= STALE_DAYS) Faint else RunTeal, Modifier.weight(1f))
        }
        if (stale && last != null) {
            Panel(accent = Warn, onClick = { onOpenSession(last) }) {
                Text("$sinceSession days off. Last time:", style = MaterialTheme.typography.titleMedium, color = Warn)
                last.blocks.forEach { Text(blockText(data, it, withSets = true), style = MaterialTheme.typography.bodyMedium, color = Muted) }
            }
        }
        if (last != null) NextSession(data, last, today, onStart) else ActionRow("Start session", onStart)
        if (data.sessions.isEmpty() && data.runs.isEmpty()) Profile(data.settings, onSaveSettings)
        Heatmap(data, today)
        Levels(data, standards, today)
        lastRun?.let { run -> LastRun(run, onOpenRun) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) = Slab(text, onClick, Modifier.fillMaxWidth(), filled = true)

@Composable
private fun NextSession(data: TrainingData, last: Session, today: LocalDate, onStart: () -> Unit) {
    val index = remember(data.sessions) { occurrences(data.sessions) }
    val rows = remember(data, today) {
        last.blocks.flatMap { it.items }
            .filter { data.exerciseById[it.exerciseId]?.role != Role.WARMUP }
            .map { it.exerciseId }.distinct()
            .map { id -> id to suggestion(previousOccurrence(index, id, today, ""), data.exerciseById[id], today) }
    }
    val warmup = data.settings.warmups.firstOrNull { it.id == last.warmup } ?: data.settings.warmups.firstOrNull()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Next session", Modifier.weight(1f))
            warmup?.let { Tag(it.name) }
        }
        rows.forEach { (id, s) ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp)) { Dot(categoryColor(data.exerciseById[id]?.category)) }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(exerciseName(data, id), style = MaterialTheme.typography.titleSmall)
                    s?.text?.takeUnless { it.endsWith(": ") }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        ActionRow("Start session", onStart)
    }
}

@Composable
private fun Heatmap(data: TrainingData, today: LocalDate) {
    val start = today.with(DayOfWeek.MONDAY).minusWeeks(HEAT_WEEKS - 1L)
    val strength = remember(data.sessions, start) { data.sessions.map { it.date }.filter { it >= start }.toSet() }
    val runs = remember(data.runs, start) { data.runs.map { it.startedAt.toLocalDate() }.filter { it >= start }.toSet() }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Faint)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("$HEAT_WEEKS weeks", Modifier.weight(1f))
            Dot(Chalk, 7)
            Text(" ${strength.size}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            Dot(RunTeal, 7)
            Text(" ${runs.size}", style = MaterialTheme.typography.labelLarge)
        }
        Canvas(Modifier.fillMaxWidth().aspectRatio(HEAT_WEEKS / 7.9f)) {
            val top = 14.dp.toPx()
            val gap = 3.dp.toPx()
            val cell = minOf((size.width - gap * (HEAT_WEEKS - 1)) / HEAT_WEEKS, (size.height - top - gap * 6) / 7)
            val radius = CornerRadius(2.dp.toPx())
            for (w in 0 until HEAT_WEEKS) {
                val x = w * (cell + gap)
                val monday = start.plusWeeks(w.toLong())
                if (monday.plusDays(6).dayOfMonth <= 7) {
                    drawText(measurer, monday.plusDays(6).format(SHORT_MONTH), Offset(x, 0f), labelStyle)
                }
                for (d in 0 until 7) {
                    val day = monday.plusDays(d.toLong())
                    if (day > today) continue
                    val origin = Offset(x, top + d * (cell + gap))
                    val s = day in strength
                    val r = day in runs
                    drawRoundRect(if (s) Chalk else if (r) RunTeal else PlateHigh, origin, Size(cell, cell), radius)
                    if (s && r) drawRoundRect(RunTeal, Offset(origin.x + cell / 2, origin.y), Size(cell / 2, cell), radius)
                    if (day == today) drawRoundRect(Muted, origin, Size(cell, cell), radius, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun Profile(settings: Settings, onSave: (Settings) -> Unit) {
    var draft by remember(settings) { mutableStateOf(settings) }
    Panel {
        ProfileFields(draft) { draft = it }
        if (draft != settings) ActionRow("Save") { onSave(draft) }
    }
}

@Composable
private fun Levels(data: TrainingData, standards: Standards?, today: LocalDate) {
    val levels = remember(data, standards) { groupLevels(data, standards, today.minusDays(364), today) }
    Panel {
        SectionLabel("Level")
        GROUPS.forEach { group ->
            val series = levels.getValue(group)
            val current = series.last()
            val known = current ?: series.lastOrNull { it != null }
            val color = categoryColor(group)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.label, style = MaterialTheme.typography.bodyMedium, color = if (current != null) Chalk else Faint, modifier = Modifier.width(118.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(MaterialTheme.shapes.extraSmall).background(PlateHigh)) {
                    known?.let {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth((it / LEVEL_SCALE).toFloat().coerceIn(0.02f, 1f))
                                .background(if (current != null) color else color.copy(alpha = 0.3f)),
                        )
                    }
                }
                Text(
                    known?.let { "%.1f".format(it) } ?: "–",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (current != null) Chalk else Faint,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun LastRun(run: Run, onOpen: (Run) -> Unit) {
    Panel(accent = RunTeal, onClick = { onOpen(run) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Last run", Modifier.weight(1f))
            Text(run.startedAt.toLocalDate().format(DAY_FORMAT), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            run.distanceM?.let { Text("${(it / 10).roundToInt() / 100.0} km", style = MaterialTheme.typography.headlineLarge) }
            run.durationSec?.let { Text(fmtDuration(it), style = MaterialTheme.typography.headlineSmall, color = Muted) }
            run.pace()?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = Muted) }
        }
        Text(run.title ?: run.type.key, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
