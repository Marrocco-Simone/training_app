package dev.simone.training.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.simone.training.MainActivity
import dev.simone.training.data.DATA_FILE
import dev.simone.training.data.GROUPS
import dev.simone.training.data.HEAT_WEEKS
import dev.simone.training.data.Standards
import dev.simone.training.data.TrainingData
import dev.simone.training.data.currentLevels
import dev.simone.training.data.daysBetween
import dev.simone.training.data.exerciseName
import dev.simone.training.data.heatmapStart
import dev.simone.training.data.latestRun
import dev.simone.training.data.latestSession
import dev.simone.training.data.levelFraction
import dev.simone.training.data.nextSessionRows
import dev.simone.training.data.nextWarmup
import dev.simone.training.data.readCatalog
import dev.simone.training.data.readDataFile
import dev.simone.training.data.readStandards
import dev.simone.training.data.runDays
import dev.simone.training.data.strengthDays
import dev.simone.training.data.withCatalog
import dev.simone.training.ui.Chalk
import dev.simone.training.ui.Faint
import dev.simone.training.ui.Iron
import dev.simone.training.ui.Muted
import dev.simone.training.ui.Plate
import dev.simone.training.ui.PlateHigh
import dev.simone.training.ui.RunTeal
import dev.simone.training.ui.categoryColor
import dev.simone.training.ui.runDaysColor
import dev.simone.training.ui.strengthDaysColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private val Background = Plate.copy(alpha = 0.94f)
private val Condensed = FontFamily("sans-serif-condensed")
private val STATUS_SMALL = DpSize(110.dp, 40.dp)
private val STATUS_LARGE = DpSize(250.dp, 140.dp)

private val saves = MutableStateFlow(0)

suspend fun updateWidgets(context: Context) {
    saves.update { it + 1 }
    StatusWidget().updateAll(context)
    NextSessionWidget().updateAll(context)
    LevelsWidget().updateAll(context)
}

private suspend fun readData(context: Context): TrainingData = withContext(Dispatchers.IO) {
    readDataFile(File(context.filesDir, DATA_FILE)).withCatalog(readCatalog(context.assets))
}

/** A Glance session stays open for about 45 s after provideGlance, and an update in that time only recomposes. The counter must be read before the file. */
private suspend fun GlanceAppWidget.provideData(context: Context, content: @Composable (TrainingData) -> Unit): Nothing {
    val loadedAtSave = saves.value
    val loaded = readData(context)
    provideContent {
        val save by saves.collectAsState()
        val data by produceState(loaded, save) { if (save != loadedAtSave) value = readData(context) }
        content(data)
    }
}

class StatusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(STATUS_SMALL, STATUS_LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideData(context) { StatusContent(it) }
}

class NextSessionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideData(context) { NextSessionContent(it) }
}

class LevelsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val standards = withContext(Dispatchers.IO) { readStandards(context.assets) }
        provideData(context) { LevelsContent(it, standards) }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}

class NextSessionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextSessionWidget()
}

class LevelsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LevelsWidget()
}

@Composable
private fun WidgetPanel(verticalAlignment: Alignment.Vertical = Alignment.Top, content: @Composable ColumnScope.() -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(Background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

@Composable
private fun Label(text: String, modifier: GlanceModifier = GlanceModifier) {
    Text(text.uppercase(), modifier, TextStyle(ColorProvider(Muted), 11.sp, FontWeight.Medium), maxLines = 1)
}

@Composable
private fun Stat(days: Long?, label: String, color: Color, size: TextUnit, modifier: GlanceModifier) {
    Column(modifier) {
        Text(days?.toString() ?: "–", style = TextStyle(ColorProvider(color), size, FontWeight.Bold, fontFamily = Condensed), maxLines = 1)
        Label(label)
    }
}

@Composable
private fun StatusContent(data: TrainingData) {
    val today = LocalDate.now()
    val sinceStrength = latestSession(data)?.let { daysBetween(it.date, today) }
    val sinceRun = latestRun(data)?.let { daysBetween(it.startedAt.toLocalDate(), today) }
    val large = LocalSize.current == STATUS_LARGE
    val numberSize = if (large) 44.sp else 32.sp
    WidgetPanel(if (large) Alignment.Top else Alignment.CenterVertically) {
        Row(GlanceModifier.fillMaxWidth()) {
            Stat(sinceStrength, if (large) "Days since strength" else "Strength", strengthDaysColor(sinceStrength), numberSize, GlanceModifier.defaultWeight())
            Stat(sinceRun, if (large) "Days since run" else "Run", runDaysColor(sinceRun), numberSize, GlanceModifier.defaultWeight())
        }
        if (large) {
            val density = LocalContext.current.resources.displayMetrics.density
            val heatmap = remember(data, today) { heatmapBitmap(data, today, density) }
            Spacer(GlanceModifier.height(10.dp))
            Image(ImageProvider(heatmap), "Training days, last $HEAT_WEEKS weeks", GlanceModifier.fillMaxWidth().defaultWeight(), ContentScale.Fit)
        }
    }
}

private fun heatmapBitmap(data: TrainingData, today: LocalDate, density: Float): Bitmap {
    val start = heatmapStart(today)
    val strength = strengthDays(data.sessions, start)
    val runs = runDays(data.runs, start)
    val cell = 12 * density
    val gap = 3 * density
    val radius = 2 * density
    val bitmap = Bitmap.createBitmap(
        (HEAT_WEEKS * cell + (HEAT_WEEKS - 1) * gap).toInt(),
        (7 * cell + 6 * gap).toInt(),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Muted.toArgb()
    }
    for (w in 0 until HEAT_WEEKS) {
        for (d in 0 until 7) {
            val day = start.plusDays(w * 7L + d)
            if (day > today) continue
            val x = w * (cell + gap)
            val y = d * (cell + gap)
            val s = day in strength
            val r = day in runs
            fill.color = (if (s) Chalk else if (r) RunTeal else PlateHigh).toArgb()
            canvas.drawRoundRect(x, y, x + cell, y + cell, radius, radius, fill)
            if (s && r) {
                fill.color = RunTeal.toArgb()
                canvas.drawRoundRect(x + cell / 2, y, x + cell, y + cell, radius, radius, fill)
            }
            if (day == today) canvas.drawRoundRect(x, y, x + cell, y + cell, radius, radius, outline)
        }
    }
    return bitmap
}

@Composable
private fun NextSessionContent(data: TrainingData) {
    val last = latestSession(data)
    WidgetPanel(if (last == null) Alignment.CenterVertically else Alignment.Top) {
        if (last != null) {
            val rows = nextSessionRows(data, last, LocalDate.now())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Next session", GlanceModifier.defaultWeight())
                nextWarmup(data, last)?.let { Tag(it.name) }
            }
            Spacer(GlanceModifier.height(8.dp))
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(rows) { (id, text) -> ExerciseRow(data, id, text) }
            }
            Spacer(GlanceModifier.height(8.dp))
        }
        StartButton()
    }
}

@Composable
private fun Tag(text: String) {
    Box(GlanceModifier.cornerRadius(4.dp).background(PlateHigh).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text, style = TextStyle(ColorProvider(Muted), 11.sp, FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun ExerciseRow(data: TrainingData, id: String, text: String?) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp).clickable(actionStartActivity<MainActivity>())) {
        Box(GlanceModifier.padding(top = 6.dp)) {
            Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(categoryColor(data.exerciseById[id]?.category))) {}
        }
        Column(GlanceModifier.padding(start = 10.dp)) {
            Text(exerciseName(data, id), style = TextStyle(ColorProvider(Chalk), 14.sp, FontWeight.Medium), maxLines = 1)
            text?.let { Text(it, style = TextStyle(ColorProvider(Muted), 12.sp), maxLines = 2) }
        }
    }
}

@Composable
private fun StartButton() {
    val intent = Intent(LocalContext.current, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_SESSION)
    Box(
        GlanceModifier.fillMaxWidth().height(44.dp).cornerRadius(10.dp).background(Chalk).clickable(actionStartActivity(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Text("START SESSION", style = TextStyle(ColorProvider(Iron), 14.sp, FontWeight.Bold, fontFamily = Condensed), maxLines = 1)
    }
}

@Composable
private fun LevelsContent(data: TrainingData, standards: Standards?) {
    val levels = currentLevels(data, standards, LocalDate.now())
    WidgetPanel {
        Label("Level")
        GROUPS.forEach { group ->
            val (current, known) = levels.getValue(group)
            val color = categoryColor(group)
            val textColor = ColorProvider(if (current != null) Chalk else Faint)
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(group.label, GlanceModifier.width(110.dp), TextStyle(textColor, 13.sp), maxLines = 1)
                LinearProgressIndicator(
                    progress = known?.let(::levelFraction) ?: 0f,
                    modifier = GlanceModifier.defaultWeight().height(6.dp),
                    color = ColorProvider(if (current != null) color else color.copy(alpha = 0.3f)),
                    backgroundColor = ColorProvider(PlateHigh),
                )
                Text(
                    known?.let { "%.1f".format(it) } ?: "–",
                    GlanceModifier.width(44.dp),
                    TextStyle(textColor, 16.sp, FontWeight.Bold, textAlign = TextAlign.End, fontFamily = Condensed),
                    maxLines = 1,
                )
            }
        }
    }
}
