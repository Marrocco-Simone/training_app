package dev.simone.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import dev.simone.training.data.daysBetween
import java.time.DayOfWeek
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.simone.training.data.Km
import dev.simone.training.data.KmKind
import dev.simone.training.data.Run
import dev.simone.training.data.RunType
import dev.simone.training.data.fmtNum
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private val KM_COLORS: Map<KmKind, Color> = mapOf(
    KmKind.NORMAL to Color(0xFF6B717A),
    KmKind.FAST to Down,
    KmKind.RECOVERY to Up,
    KmKind.UP to Warn,
    KmKind.DOWN to Color(0xFF4A8DF8),
)

fun Run.km(): String = distanceM?.let { "${fmtNum((it / 10).roundToInt() / 100.0)} km" } ?: "– km"

private fun Run.paceSec(): Double? {
    val d = distanceM ?: return null
    val t = durationSec ?: return null
    return if (d <= 0) null else t / (d / 1000)
}

fun Run.pace(): String? = paceSec()?.let { "${fmtDuration(it.roundToInt())}/km" }

private val WEEK_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

@Composable
private fun KmBars(runs: List<Run>, from: LocalDate, today: LocalDate) {
    val weekly = daysBetween(from, today) <= 200
    val buckets = remember(runs, from, today, weekly) {
        val starts = generateSequence(if (weekly) from.with(DayOfWeek.MONDAY) else from.withDayOfMonth(1)) { if (weekly) it.plusWeeks(1) else it.plusMonths(1) }
            .takeWhile { it <= today }.toList()
        val km = runs.groupBy { r ->
            val d = r.startedAt.toLocalDate()
            if (weekly) d.with(DayOfWeek.MONDAY) else d.withDayOfMonth(1)
        }.mapValues { (_, list) -> list.sumOf { it.distanceM ?: 0.0 } / 1000 }
        starts.map { it to (km[it] ?: 0.0) }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Faint)
    val max = buckets.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val bottom = size.height - 16.dp.toPx()
        val slot = size.width / buckets.size.coerceAtLeast(1)
        drawLine(Seam, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        buckets.forEachIndexed { i, (_, km) ->
            val h = (km / max * (bottom - 14.dp.toPx())).toFloat()
            if (h > 0) drawRoundRect(RunTeal, Offset(i * slot + slot * 0.18f, bottom - h), Size(slot * 0.64f, h), CornerRadius(2.dp.toPx()))
        }
        drawText(measurer, "${max.roundToInt()} km", Offset.Zero, labelStyle)
        listOf(0, buckets.size - 1).distinct().forEach { i ->
            val text = buckets[i].first.format(if (weekly) WEEK_LABEL else MONTH_LABEL)
            val w = measurer.measure(text, labelStyle).size.width
            drawText(measurer, text, Offset((i * slot).coerceAtMost(size.width - w), bottom + 2.dp.toPx()), labelStyle)
        }
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineLarge)
        SectionLabel(label)
    }
}

@Composable
fun RunsScreen(runs: List<Run>, onOpen: (Run) -> Unit, onAdd: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(Range.Y1) }
    val today = LocalDate.now()
    val from = range.from(today, runs.minOfOrNull { it.startedAt.toLocalDate() })
    val shown = remember(runs, from) { runs.filter { it.startedAt.toLocalDate() >= from }.sortedByDescending { it.startedAt } }
    val totalKm = shown.sumOf { it.distanceM ?: 0.0 } / 1000
    val timed = shown.filter { it.paceSec() != null }
    val avgPace = timed.sumOf { it.durationSec ?: 0 }.takeIf { it > 0 }?.let { it / (timed.sumOf { r -> r.distanceM ?: 0.0 } / 1000) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "range") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Segments(Range.entries, range, { it.label }, { range = it }, Modifier.weight(1f))
                ActionButton("+ Run", onAdd)
            }
        }
        item(key = "summary") {
            Panel(accent = RunTeal) {
                Row {
                    Figure("${shown.size}", "runs", Modifier.weight(1f))
                    Figure(fmtNum(totalKm.roundToInt().toDouble()), "km", Modifier.weight(1f))
                    Figure(avgPace?.let { fmtDuration(it.roundToInt()) } ?: "–", "avg /km", Modifier.weight(1f))
                }
                if (shown.isNotEmpty()) KmBars(shown, from, today)
            }
        }
        if (timed.size >= 2) {
            item(key = "pace") {
                Panel {
                    SectionLabel("Pace")
                    LineChart(
                        listOf(Series(RunTeal, timed.reversed().map { it.startedAt.toLocalDate().toEpochDay() to it.paceSec() }, dots = true, width = 0.dp)),
                        from.toEpochDay(),
                        today.toEpochDay(),
                        height = 150.dp,
                        invert = true,
                        label = { fmtDuration(it.roundToInt()) },
                    )
                }
            }
        }
        items(shown, key = { it.id }) { run ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(run) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(run.startedAt.toLocalDate().format(DAY_FORMAT), style = MaterialTheme.typography.titleSmall)
                    Text(run.title ?: run.type.key, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(run.km(), style = MaterialTheme.typography.titleLarge)
                    run.pace()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Seam))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunDetail(run: Run) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(run.type.key, RunTeal)
            run.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
        }
        Text(run.km(), style = MaterialTheme.typography.displayLarge)
        RouteMap(run, { if (it == KmKind.NORMAL) RunTeal else KM_COLORS.getValue(it) })
        Row {
            Figure(run.durationSec?.let(::fmtDuration) ?: "–", "time", Modifier.weight(1f))
            Figure(run.paceSec()?.let { fmtDuration(it.roundToInt()) } ?: "–", "pace /km", Modifier.weight(1f))
            Figure(run.startedAt.format(TIME_FORMAT), "start", Modifier.weight(1f))
        }
        Row {
            Figure(run.avgHr?.toString() ?: "–", "avg hr", Modifier.weight(1f))
            Figure(run.maxHr?.toString() ?: "–", "max hr", Modifier.weight(1f))
            Figure(run.elevationGainM?.let { "${it.roundToInt()} m" } ?: "–", "climb", Modifier.weight(1f))
        }
        if (run.kms.isNotEmpty()) {
            Panel {
                SectionLabel("Splits")
                val fastest = run.kms.mapNotNull { it.sec }.minOrNull()
                val slowest = run.kms.mapNotNull { it.sec }.maxOrNull()
                Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    run.kms.forEach { km ->
                        val t = if (km.sec == null || fastest == null || slowest == null || fastest == slowest) 0.7f
                        else 0.35f + 0.65f * (slowest - km.sec).toFloat() / (slowest - fastest)
                        Box(Modifier.weight(1f).fillMaxHeight(t).clip(MaterialTheme.shapes.extraSmall).background(KM_COLORS.getValue(km.kind)))
                    }
                }
                run.kms.forEachIndexed { i, km ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = Faint, modifier = Modifier.width(28.dp))
                        Dot(KM_COLORS.getValue(km.kind))
                        Text(" " + km.kind.key, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.weight(1f))
                        Text(km.sec?.let(::fmtDuration) ?: "–", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        run.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
        Text(run.source, style = MaterialTheme.typography.labelSmall, color = Faint)
        Spacer(Modifier.height(8.dp))
    }
}

private fun newRun(): Run = Run(
    id = "",
    startedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES),
    durationSec = null,
    distanceM = null,
    avgHr = null,
    maxHr = null,
    type = RunType.EASY,
    title = null,
    kms = emptyList(),
    elevationGainM = null,
    notes = null,
    source = "app",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunEditor(run: Run?, onSave: (Run) -> Unit, onDelete: () -> Unit) {
    var draft by remember { mutableStateOf(run ?: newRun()) }
    var editingKm by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Date", draft.startedAt.toLocalDate(), { draft = draft.copy(startedAt = draft.startedAt.with(it)) }, Modifier.weight(1f))
                SyncedField(
                    label = "Start",
                    formatted = draft.startedAt.format(TIME_FORMAT),
                    accept = { t ->
                        runCatching { LocalTime.parse(t.trim(), TIME_FORMAT) }.getOrNull()?.let {
                            draft = draft.copy(startedAt = draft.startedAt.with(it))
                            it.format(TIME_FORMAT)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncedField(
                    label = "Duration",
                    formatted = draft.durationSec?.let(::fmtDuration) ?: "",
                    accept = { t ->
                        if (t.isBlank()) {
                            draft = draft.copy(durationSec = null)
                            ""
                        } else {
                            parseDuration(t)?.let {
                                draft = draft.copy(durationSec = it)
                                fmtDuration(it)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                NumField("Km", draft.distanceM?.let { it / 1000 }, { draft = draft.copy(distanceM = it?.let { km -> km * 1000 }) }, Modifier.weight(1f))
            }
            Choices(RunType.entries, draft.type, { it.key }) { draft = draft.copy(type = it) }
            TextIn("Title", draft.title, { draft = draft.copy(title = it) })
            NumField("Elevation gain m", draft.elevationGainM, { draft = draft.copy(elevationGainM = it) }, Modifier.fillMaxWidth())
            TextIn("Notes", draft.notes, { draft = draft.copy(notes = it) }, singleLine = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.kms.forEachIndexed { i, km ->
                    AssistChip(
                        onClick = { editingKm = i },
                        label = { Text(listOfNotNull("${i + 1}", km.sec?.let(::fmtDuration)).joinToString(" · ")) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = KM_COLORS.getValue(km.kind).copy(alpha = 0.35f)),
                    )
                }
                AssistChip(onClick = { draft = draft.copy(kms = draft.kms + Km(KmKind.NORMAL, null)) }, label = { Text("+ km") })
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (run != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
            Spacer(Modifier.weight(1f))
            Slab("Save", { onSave(if (draft.id.isEmpty()) draft.copy(id = "run-${draft.startedAt.toEpochSecond()}") else draft) }, Modifier.width(120.dp), filled = true)
        }
    }
    editingKm?.let { i ->
        val km = draft.kms[i]
        val setKm: (Km) -> Unit = { k -> draft = draft.copy(kms = draft.kms.toMutableList().also { it[i] = k }) }
        AlertDialog(
            onDismissRequest = { editingKm = null },
            title = { Text("Km ${i + 1}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choices(KmKind.entries, km.kind, { it.key }) { setKm(km.copy(kind = it)) }
                    SyncedField(
                        label = "Time",
                        formatted = km.sec?.let(::fmtDuration) ?: "",
                        accept = { t ->
                            if (t.isBlank()) {
                                setKm(km.copy(sec = null))
                                ""
                            } else {
                                parseDuration(t)?.let {
                                    setKm(km.copy(sec = it))
                                    fmtDuration(it)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { editingKm = null }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = {
                    editingKm = null
                    draft = draft.copy(kms = draft.kms.toMutableList().also { it.removeAt(i) })
                }) { Text("Delete") }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this run?") },
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
