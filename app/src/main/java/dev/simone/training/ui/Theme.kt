package dev.simone.training.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.simone.training.R
import dev.simone.training.data.Category

val Iron = Color(0xFF0E0F11)
val Plate = Color(0xFF17191C)
val PlateHigh = Color(0xFF202328)
val Seam = Color(0xFF2B2F35)
val Chalk = Color(0xFFEDEBE5)
val Muted = Color(0xFF8B9199)
val Faint = Color(0xFF555B63)
val Up = Color(0xFF4CC38A)
val Down = Color(0xFFEF5A5A)
val RunTeal = Color(0xFF4FC3D9)
val Warn = Color(0xFFF2A93B)

val GROUP_COLORS: Map<Category, Color> = mapOf(
    Category.SQUAT to Color(0xFFE5484D),
    Category.HINGE to Color(0xFF4A8DF8),
    Category.HORIZONTAL_PUSH to Color(0xFFF2C94C),
    Category.HORIZONTAL_PULL to Color(0xFF46BE7D),
    Category.VERTICAL_PUSH to Color(0xFFD9D6CE),
    Category.VERTICAL_PULL to Color(0xFFF28C38),
)

fun categoryColor(category: Category?): Color = GROUP_COLORS[category] ?: Faint

private val Display = FontFamily(
    Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlowcondensed_bold, FontWeight.Bold),
)

private val Body = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

private const val TNUM = "tnum"

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size + 6) =
    TextStyle(fontFamily = Body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, fontFeatureSettings = TNUM)

private fun display(size: Int, weight: FontWeight = FontWeight.Bold, tracking: Double = 0.0) =
    TextStyle(fontFamily = Display, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.05).sp, letterSpacing = tracking.sp, fontFeatureSettings = TNUM)

private val typography = Typography(
    displayLarge = display(72),
    displayMedium = display(52),
    displaySmall = display(40),
    headlineLarge = display(34),
    headlineMedium = display(28),
    headlineSmall = display(24, FontWeight.SemiBold),
    titleLarge = display(22, FontWeight.SemiBold, 0.3),
    titleMedium = body(16, FontWeight.SemiBold),
    titleSmall = body(14, FontWeight.SemiBold),
    bodyLarge = body(16),
    bodyMedium = body(14),
    bodySmall = body(12, line = 16),
    labelLarge = body(14, FontWeight.SemiBold),
    labelMedium = display(13, FontWeight.SemiBold, 1.4),
    labelSmall = body(11, FontWeight.Medium, 14),
)

private val colors = darkColorScheme(
    primary = Chalk,
    onPrimary = Iron,
    primaryContainer = PlateHigh,
    onPrimaryContainer = Chalk,
    secondary = Muted,
    secondaryContainer = Seam,
    onSecondaryContainer = Chalk,
    background = Iron,
    onBackground = Chalk,
    surface = Iron,
    onSurface = Chalk,
    surfaceVariant = Plate,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Iron,
    surfaceContainerLow = Plate,
    surfaceContainer = Plate,
    surfaceContainerHigh = PlateHigh,
    surfaceContainerHighest = Seam,
    outline = Faint,
    outlineVariant = Seam,
    error = Down,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun TrainingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Plate)
            .border(BorderStroke(1.dp, Seam), shape)
            .then(if (accent != null) Modifier.drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) } else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun Tag(text: String, color: Color = Muted, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(1.dp, color.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Segments(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(if (on) Chalk else Plate)
                    .border(1.dp, if (on) Chalk else Seam, MaterialTheme.shapes.small)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(label(option).uppercase(), style = MaterialTheme.typography.labelMedium, color = if (on) Iron else Muted)
            }
        }
    }
}

@Composable
fun Slab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .height(52.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (filled) Chalk else Plate)
            .border(1.dp, if (filled) Chalk else Seam, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (filled) Iron else Chalk) }
}
