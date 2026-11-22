package dev.simone.training.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.simone.training.data.KmKind
import dev.simone.training.data.Run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private const val TILE = 256
private const val TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png"

private val DARK_TILES = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.21f, -0.42f, -0.08f, 0f, 205f,
            -0.20f, -0.41f, -0.08f, 0f, 205f,
            -0.19f, -0.38f, -0.07f, 0f, 200f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private data class LatLon(val lat: Double, val lon: Double)

private fun decodePolyline(encoded: String): List<LatLon> {
    val out = mutableListOf<LatLon>()
    var i = 0
    var lat = 0
    var lon = 0
    fun next(): Int {
        var result = 0
        var shift = 0
        var b: Int
        do {
            b = encoded[i++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        return if (result and 1 != 0) (result shr 1).inv() else result shr 1
    }
    while (i < encoded.length) {
        lat += next()
        lon += next()
        out.add(LatLon(lat / 1e5, lon / 1e5))
    }
    return out
}

private fun meters(a: LatLon, b: LatLon): Double {
    val p1 = Math.toRadians(a.lat)
    val p2 = Math.toRadians(b.lat)
    val dl = Math.toRadians(b.lon - a.lon)
    val h = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * 6_371_000 * asin(sqrt(h))
}

/** Web Mercator position in tiles at zoom 0, 0..1 on both axes. */
private fun mercator(p: LatLon): Offset {
    val x = (p.lon + 180) / 360
    val r = Math.toRadians(p.lat)
    val y = (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2
    return Offset(x.toFloat(), y.toFloat())
}

private data class TileKey(val z: Int, val x: Int, val y: Int)

private fun fetchTile(cache: File, key: TileKey): ImageBitmap? {
    val file = File(cache, "${key.z}_${key.x}_${key.y}.png")
    if (!file.exists()) {
        val connection = URL(TILE_URL.format(key.z, key.x, key.y)).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "training-app")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode != 200) return null
            val tmp = File(cache, "${file.name}.tmp")
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
        } finally {
            connection.disconnect()
        }
    }
    return BitmapFactory.decodeFile(file.path)?.asImageBitmap()
}

@Composable
fun RouteMap(run: Run, kindColor: (KmKind) -> Color, modifier: Modifier = Modifier) {
    val encoded = run.track ?: return
    val points = remember(encoded) { decodePolyline(encoded) }
    if (points.size < 2) return
    val segments = remember(points, run.kms) {
        var dist = 0.0
        points.zipWithNext { a, b ->
            val kind = run.kms.getOrNull((dist / 1000).toInt())?.kind ?: KmKind.NORMAL
            dist += meters(a, b)
            Triple(mercator(a), mercator(b), kind)
        }
    }
    val context = LocalContext.current
    val tiles = remember(encoded) { mutableStateMapOf<TileKey, ImageBitmap>() }
    BoxWithConstraints(modifier.fillMaxWidth().height(280.dp).clip(MaterialTheme.shapes.medium).background(Plate)) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val pad = with(density) { 28.dp.toPx() }
        val view = remember(segments, width, height) {
            val xs = segments.flatMap { listOf(it.first.x, it.second.x) }
            val ys = segments.flatMap { listOf(it.first.y, it.second.y) }
            val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-7f)
            val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-7f)
            val worldPx = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY)
            val zoom = log2(worldPx / TILE).coerceIn(3f, 18f)
            MapView(
                zoom = floor(zoom).toInt(),
                worldPx = TILE * 2f.pow(zoom),
                center = Offset((xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2),
                width = width,
                height = height,
            )
        }
        LaunchedEffect(view) {
            val cache = File(context.cacheDir, "osm-tiles").apply { mkdirs() }
            withContext(Dispatchers.IO) {
                view.tileKeys().map { key ->
                    async { runCatching { fetchTile(cache, key) }.getOrNull()?.let { tiles[key] = it } }
                }.awaitAll()
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val tilePx = view.worldPx / (1 shl view.zoom)
            tiles.forEach { (key, image) ->
                if (key.z != view.zoom) return@forEach
                val topLeft = view.toScreen(Offset(key.x.toFloat() / (1 shl key.z), key.y.toFloat() / (1 shl key.z)))
                drawImage(
                    image,
                    dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = IntSize(tilePx.toInt() + 1, tilePx.toInt() + 1),
                    colorFilter = DARK_TILES,
                )
            }
            val outline = 7.dp.toPx()
            val line = 4.dp.toPx()
            segments.forEach { (a, b, _) -> drawLine(Iron, view.toScreen(a), view.toScreen(b), outline, StrokeCap.Round) }
            segments.forEach { (a, b, kind) -> drawLine(kindColor(kind), view.toScreen(a), view.toScreen(b), line, StrokeCap.Round) }
            drawCircle(Iron, 7.dp.toPx(), view.toScreen(segments.first().first))
            drawCircle(Up, 5.dp.toPx(), view.toScreen(segments.first().first))
            drawCircle(Iron, 7.dp.toPx(), view.toScreen(segments.last().second))
            drawCircle(Chalk, 5.dp.toPx(), view.toScreen(segments.last().second))
        }
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )
    }
}

private data class MapView(val zoom: Int, val worldPx: Float, val center: Offset, val width: Float, val height: Float) {
    fun toScreen(p: Offset): Offset = Offset((p.x - center.x) * worldPx + width / 2, (p.y - center.y) * worldPx + height / 2)

    fun tileKeys(): List<TileKey> {
        val n = 1 shl zoom
        val left = center.x - width / 2 / worldPx
        val top = center.y - height / 2 / worldPx
        val right = center.x + width / 2 / worldPx
        val bottom = center.y + height / 2 / worldPx
        val xs = floor(left * n).toInt().coerceAtLeast(0)..floor(right * n).toInt().coerceAtMost(n - 1)
        val ys = floor(top * n).toInt().coerceAtLeast(0)..floor(bottom * n).toInt().coerceAtMost(n - 1)
        return xs.flatMap { x -> ys.map { y -> TileKey(zoom, x, y) } }
    }
}
