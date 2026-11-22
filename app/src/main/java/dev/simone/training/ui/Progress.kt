package dev.simone.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.simone.training.data.Category
import dev.simone.training.data.GROUPS
import dev.simone.training.data.TrainingData
import dev.simone.training.data.exerciseName
import dev.simone.training.data.fmtNum
import dev.simone.training.data.groupLevels
import dev.simone.training.data.Item
import dev.simone.training.data.Standards
import dev.simone.training.data.itemLevel
import dev.simone.training.data.itemText
import dev.simone.training.data.itemValue
import dev.simone.training.data.measureFor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class Series(val color: Color, val points: List<Pair<Long, Double?>>, val dots: Boolean = false, val width: Dp = 2.dp)

private val YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yy", Locale.getDefault())

enum class Range(val label: String, val days: Long?) {
    M3("3M", 91), M6("6M", 182), Y1("1Y", 365), Y3("3Y", 1095), ALL("All", null);

    fun from(today: LocalDate, earliest: LocalDate?): LocalDate = days?.let { today.minusDays(it) } ?: earliest ?: today
}

@Composable
fun LineChart(series: List<Series>, xStart: Long, xEnd: Long, modifier: Modifier = Modifier, height: Dp = 200.dp, invert: Boolean = false, label: (Double) -> String = { if (kotlin.math.abs(it) >= 20) "${it.roundToInt()}" else fmtNum((it * 10).roundToInt() / 10.0) }) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Faint)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val values = series.flatMap { s -> s.points.mapNotNull { it.second } }
        if (values.isEmpty()) return@Canvas
        var lo = values.min()
        var hi = values.max()
        if (hi - lo < 1) {
            lo -= 0.5
            hi += 0.5
        }
        val left = 34.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val width = size.width - left
        fun x(day: Long) = left + if (xEnd == xStart) width / 2 else (day - xStart).toFloat() / (xEnd - xStart) * width
        fun y(v: Double): Float {
            val t = ((v - lo) / (hi - lo)).toFloat()
            return if (invert) top + t * (bottom - top) else bottom - t * (bottom - top)
        }
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            drawLine(Seam, Offset(left, y(v)), Offset(size.width, y(v)), 1.dp.toPx())
            drawText(measurer, label(v), Offset(0f, (y(v) - 8.dp.toPx()).coerceIn(0f, bottom - 14.dp.toPx())), labelStyle)
        }
        val span = xEnd - xStart
        val step = if (span > 730) 12L else if (span > 200) 3L else 1L
        var tick = LocalDate.ofEpochDay(xStart).withDayOfMonth(1).plusMonths(1)
        if (step == 12L) tick = tick.withDayOfYear(1).plusYears(if (tick.monthValue == 1) 0 else 1)
        while (tick.toEpochDay() <= xEnd) {
            val tx = x(tick.toEpochDay())
            drawLine(Seam, Offset(tx, bottom), Offset(tx, bottom + 4.dp.toPx()))
            drawText(measurer, tick.format(if (step == 12L) YEAR_FORMAT else MONTH_FORMAT), Offset(tx + 2.dp.toPx(), bottom + 2.dp.toPx()), labelStyle)
            tick = tick.plusMonths(step)
        }
        series.forEach { s ->
            val path = Path()
            var drawing = false
            s.points.forEach { (day, v) ->
                if (v == null) {
                    drawing = false
                } else {
                    if (drawing) path.lineTo(x(day), y(v)) else path.moveTo(x(day), y(v))
                    drawing = true
                    if (s.dots) drawCircle(s.color, 3.dp.toPx(), Offset(x(day), y(v)))
                }
            }
            if (s.width > 0.dp) drawPath(path, s.color, style = Stroke(s.width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun Sparkline(values: List<Double?>, color: Color, lo: Double, hi: Double) {
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val n = values.size
        if (n < 2) return@Canvas
        fun x(i: Int) = i.toFloat() / (n - 1) * size.width
        fun y(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
        val path = Path()
        var drawing = false
        values.forEachIndexed { i, v ->
            if (v == null) {
                drawing = false
            } else {
                if (drawing) path.lineTo(x(i), y(v)) else path.moveTo(x(i), y(v))
                drawing = true
            }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ProgressScreen(data: TrainingData, standards: Standards?) {
    var range by rememberSaveable { mutableStateOf(Range.Y1) }
    var group by rememberSaveable { mutableStateOf<Category?>(null) }
    var exerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val from = range.from(today, data.sessions.minOfOrNull { it.date })
    val levels = remember(data, standards, from, today) { groupLevels(data, standards, from, today) }

    val selected = exerciseId
    val exercise = selected?.let { data.exerciseById[it] }
    val rows = remember(data, selected, from) {
        if (selected == null) {
            emptyList()
        } else {
            data.sessions.filter { it.date >= from }.sortedBy { it.date }.mapNotNull { s ->
                s.blocks.flatMap { it.items }.filter { it.exerciseId == selected }.takeIf { it.isNotEmpty() }?.let { s to it }
            }
        }
    }
    fun levelOf(item: Item, date: LocalDate): Double? = data.exerciseById[item.exerciseId]?.let { itemLevel(item, it, date, standards, data.settings) }
    val groupExercises = remember(data, group, from) {
        group?.let { g ->
            data.sessions.filter { it.date >= from }.sortedByDescending { it.date }
                .flatMap { s -> s.blocks.flatMap { it.items }.map { s.date to it } }
                .filter { (_, item) -> data.exerciseById[item.exerciseId]?.category == g }
                .groupBy { it.second.exerciseId }
                .map { (_, uses) -> Triple(uses.first().first, uses.first().second, uses.mapNotNull { (date, item) -> levelOf(item, date) }.maxOrNull()) }
                .sortedWith(compareByDescending<Triple<LocalDate, Item, Double?>> { it.third ?: -1.0 }.thenByDescending { it.first })
        }.orEmpty()
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "range") { Segments(Range.entries, range, { it.label }, { range = it }) }
        GROUPS.chunked(2).forEach { pair ->
            item(key = pair.first().key) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { g ->
                        val series = levels.getValue(g)
                        val color = categoryColor(g)
                        val on = g == group
                        Column(
                            Modifier.weight(1f).clip(MaterialTheme.shapes.medium).background(Plate)
                                .border(1.dp, if (on) color else Seam, MaterialTheme.shapes.medium)
                                .clickable { group = if (on) null else g }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Dot(color, 7)
                                Text(" " + g.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val current = series.lastOrNull()
                            Text(
                                current?.let { "%.1f".format(it) } ?: series.lastOrNull { it != null }?.let { "%.1f".format(it) } ?: "–",
                                style = MaterialTheme.typography.displaySmall,
                                color = if (current != null) Chalk else Faint,
                            )
                            val known = series.filterNotNull()
                            val mid = ((known.minOrNull() ?: 0.0) + (known.maxOrNull() ?: 0.0)) / 2
                            val half = maxOf(0.75, ((known.maxOrNull() ?: 0.0) - (known.minOrNull() ?: 0.0)) / 2 + 0.2)
                            Sparkline(series, color, mid - half, mid + half)
                        }
                    }
                }
            }
        }
        if (group != null) {
            item(key = "group-label") { SectionLabel(group!!.label, Modifier.padding(top = 8.dp), categoryColor(group)) }
            items(groupExercises, key = { "g-" + it.second.exerciseId }) { (date, item, level) ->
                val on = item.exerciseId == selected
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(if (on) PlateHigh else Iron)
                        .clickable {
                            exerciseId = item.exerciseId
                            group = null
                        }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(exerciseName(data, item.exerciseId), style = MaterialTheme.typography.titleSmall)
                        Text(itemText(item, data.exerciseById[item.exerciseId], withSets = true), style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(level?.let { "%.1f".format(it) } ?: "–", style = MaterialTheme.typography.titleLarge, color = if (level != null) categoryColor(group) else Faint)
                        Text(date.format(SHORT_DATE), style = MaterialTheme.typography.bodySmall, color = Faint)
                    }
                }
            }
        }
        item(key = "picker") {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.medium).border(1.dp, Seam, MaterialTheme.shapes.medium)
                    .clickable { picking = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercise?.let { Dot(categoryColor(it.category)) }
                Text(
                    (selected?.let { exerciseName(data, it) } ?: "Choose exercise"),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected != null) Chalk else Muted,
                    modifier = Modifier.weight(1f).padding(start = if (exercise != null) 10.dp else 0.dp),
                )
                Text("▾", color = Muted)
            }
        }
        if (selected != null) {
            val measure = measureFor(exercise, rows.flatMap { it.second })
            val points = rows.mapNotNull { (s, items) ->
                items.mapNotNull { itemValue(it, exercise, measure, data.settings.bodyweightKg) }.maxOrNull()?.let { s.date.toEpochDay() to it }
            }
            var best = Double.NEGATIVE_INFINITY
            val bestLine = points.map { (d, v) ->
                best = maxOf(best, v)
                d to best
            } + listOfNotNull(points.lastOrNull()?.let { today.toEpochDay() to best })
            val color = categoryColor(exercise?.category).takeIf { exercise?.category in GROUPS } ?: Chalk
            item(key = "chart") {
                Panel {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(points.maxOfOrNull { it.second }?.let(::fmtNum) ?: "–", style = MaterialTheme.typography.displaySmall)
                        Text(" " + measure.unit, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
                        Spacer(Modifier.weight(1f))
                        Text("${points.size} sessions", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (points.isEmpty()) {
                        Text("No sessions in this range", style = MaterialTheme.typography.bodySmall, color = Faint)
                    } else {
                        LineChart(
                            listOf(
                                Series(color.copy(alpha = 0.5f), bestLine, width = 2.dp),
                                Series(color, points, dots = true, width = 0.dp),
                            ),
                            from.toEpochDay(),
                            today.toEpochDay(),
                        )
                    }
                }
            }
            items(rows.reversed(), key = { "r-" + it.first.id }) { (session, items) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(session.date.format(SHORT_DATE), style = MaterialTheme.typography.bodySmall, color = Faint, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
                    Column(Modifier.weight(1f)) {
                        items.forEach { Text(itemText(it, exercise, withSets = true), style = MaterialTheme.typography.bodyMedium) }
                    }
                    items.mapNotNull { levelOf(it, session.date) }.maxOrNull()?.let {
                        Text("%.1f".format(it), style = MaterialTheme.typography.titleMedium, color = color)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Seam))
            }
        }
    }
    if (picking) {
        ExercisePicker(
            data = data,
            onPick = {
                exerciseId = it
                picking = false
            },
            onCreate = null,
            onDismiss = { picking = false },
        )
    }
}
