package app.bitcall.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Bitcall's detailed pixel-art toolkit: one "art pixel" is ART_PX on screen. Shapes have stepped
 * corners, surfaces are shaded with a highlight, a shadow and ordered dithering instead of smooth
 * gradients, the way 16/32-bit era sprites were drawn.
 */

val ART_PX = 3.dp

/** Rectangle with two-step pixel corners, like a hand-drawn sprite frame. */
fun pixelShape(step: Dp = ART_PX): Shape = PixelShape(step)

private class PixelShape(private val step: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { step.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(2 * s, 0f); lineTo(w - 2 * s, 0f); lineTo(w - 2 * s, s); lineTo(w - s, s); lineTo(w - s, 2 * s); lineTo(w, 2 * s)
            lineTo(w, h - 2 * s); lineTo(w - s, h - 2 * s); lineTo(w - s, h - s); lineTo(w - 2 * s, h - s); lineTo(w - 2 * s, h)
            lineTo(2 * s, h); lineTo(2 * s, h - s); lineTo(s, h - s); lineTo(s, h - 2 * s); lineTo(0f, h - 2 * s)
            lineTo(0f, 2 * s); lineTo(s, 2 * s); lineTo(s, s); lineTo(2 * s, s); close()
        }
        return Outline.Generic(path)
    }
    override fun equals(other: Any?) = other is PixelShape && other.step == step
    override fun hashCode() = step.hashCode()
}

fun lighten(c: Color, f: Float = 0.35f) = lerp(c, Color.White, f)

// 4x4 Bayer matrix: the classic ordered-dither pattern.
private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** Normalised Bayer threshold (0..1) for cell (x, y). */
fun bayer(x: Int, y: Int): Float = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f

private val patternCache = HashMap<Triple<Int, Int, Int>, ImageBitmap>()

/**
 * A tiling brush that covers [level] (0..16) of every 16 cells with [color], using the Bayer
 * pattern: one draw call paints a whole dithered band.
 */
fun ditherBrush(color: Color, level: Int, cellPx: Int): ShaderBrush {
    val key = Triple(color.toArgb(), level, cellPx)
    val bmp = patternCache.getOrPut(key) {
        val n = 4 * cellPx
        val b = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
        val argb = color.toArgb()
        for (y in 0 until n) for (x in 0 until n) {
            if (BAYER[(y / cellPx) * 4 + (x / cellPx)] < level) b.setPixel(x, y, argb)
        }
        b.asImageBitmap()
    }
    return ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}

/**
 * Shades a surface like a sprite tile: 1-art-pixel highlight along the top and left, shadow
 * along the bottom, and a dithered darkening over the lower half. Draw behind content.
 */
fun DrawScope.pixelBevel(face: Color, pressed: Boolean) {
    val p = ART_PX.toPx()
    val w = size.width
    val h = size.height
    val hi = if (pressed) face else lighten(face, 0.35f)
    val lo = darken(face, 0.72f)
    // Dithered lower half (skip the stepped corners).
    drawRect(ditherBrush(darken(face, 0.82f), 6, p.toInt().coerceAtLeast(1)), Offset(p, h * 0.55f), Size(w - 2 * p, h * 0.45f - 2 * p))
    drawRect(hi, Offset(2 * p, p), Size(w - 4 * p, p))           // top highlight
    drawRect(hi.copy(alpha = 0.6f), Offset(p, 2 * p), Size(p, h * 0.45f)) // left highlight
    drawRect(lo, Offset(2 * p, h - 2 * p), Size(w - 4 * p, p))   // bottom shadow
    drawRect(lo.copy(alpha = 0.6f), Offset(w - 2 * p, h * 0.5f), Size(p, h * 0.5f - 3 * p)) // right shadow
}

/** A soft light band that sweeps across, like a shiny cartridge label. */
@Composable
fun Modifier.shimmer(): Modifier {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(2400, delayMillis = 900, easing = LinearEasing)), label = "x")
    return drawWithContent {
        drawContent()
        val p = ART_PX.toPx()
        clipRect {
            val cx = size.width * x
            // A stepped diagonal band drawn as stacked art-pixel rows.
            var y = 0f
            while (y < size.height) {
                val off = (size.height - y) * 0.5f
                drawRect(Color.White.copy(alpha = 0.22f), Offset(cx + off, y), Size(p * 4, p))
                drawRect(Color.White.copy(alpha = 0.12f), Offset(cx + off + p * 5, y), Size(p * 2, p))
                y += p
            }
        }
    }
}

/** Pixel sparks that burst out of a key when it's pressed. Call [fire] on each press. */
class SparkState {
    val progress = Animatable(1f)
    var seed = 0
    suspend fun fire() {
        seed++
        progress.snapTo(0f)
        progress.animateTo(1f, tween(420, easing = LinearEasing))
    }
}

fun DrawScope.drawSparks(state: SparkState, color: Color) {
    val t = state.progress.value
    if (t >= 1f) return
    val p = ART_PX.toPx()
    val rnd = Random(state.seed)
    val cx = size.width / 2
    val cy = size.height / 2
    repeat(10) { i ->
        val a = (i / 10f) * 2 * PI.toFloat() + rnd.nextFloat() * 0.5f
        val dist = (size.minDimension * 0.35f + rnd.nextFloat() * size.minDimension * 0.4f) * t
        val s = if (i % 3 == 0) 2 * p else p
        val c = if (i % 2 == 0) color else Color.White
        drawRect(
            c.copy(alpha = 1f - t),
            Offset(cx + cos(a) * (size.width * 0.3f + dist) - s / 2, cy + sin(a) * (size.height * 0.3f + dist) - s / 2),
            Size(s, s),
        )
    }
}

/** Sky mood for the hour: a horizon glow colour and strength, and how bright the stars are. */
private data class SkyTime(val glow: Color?, val glowLevels: List<Int>, val starAlpha: Float, val dayTint: Color?)

private fun skyFor(hour: Int): SkyTime = when (hour) {
    in 5..7 -> SkyTime(Color(0xFFFF9E5E), listOf(1, 2, 4, 6), 0.55f, null)               // sunrise
    in 8..16 -> SkyTime(null, emptyList(), 0.25f, Color(0xFF3E6FB0))                      // day
    in 17..19 -> SkyTime(Color(0xFFFF5E9E), listOf(1, 3, 5, 7), 0.6f, null)              // sunset
    else -> SkyTime(null, emptyList(), 1f, null)                                          // night
}

/**
 * The app backdrop: a dithered gradient rising from the bottom, a sky that shifts with the time
 * of day (sunrise glow, pale day, pink sunset, starry night), twinkling drifting stars and the
 * occasional shooting star (dark palettes only).
 */
@Composable
fun PixelBackdrop(palette: Palette, stars: Boolean) {
    val hour = remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            hour.intValue = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        }
    }
    val sky = skyFor(hour.intValue)
    Canvas(Modifier.fillMaxSize()) {
        val cell = ART_PX.toPx().toInt().coerceAtLeast(1)
        val h = size.height
        if (palette.dark) {
            sky.dayTint?.let { drawRect(ditherBrush(it.copy(alpha = 0.5f), 3, cell), Offset.Zero, Size(size.width, h * 0.62f)) }
            // Horizon glow just above the ground bands at sunrise / sunset.
            sky.glow?.let { glow ->
                sky.glowLevels.forEachIndexed { i, level ->
                    val top = h * (0.46f + i * 0.04f)
                    drawRect(ditherBrush(glow.copy(alpha = 0.6f), level, cell), Offset(0f, top), Size(size.width, h * 0.04f + 1))
                }
            }
        }
        // Five dithered bands fading the surface colour up from the bottom third.
        listOf(2, 4, 7, 10, 13).forEachIndexed { i, level ->
            val top = h * (0.62f + i * 0.076f)
            drawRect(ditherBrush(palette.surface, level, cell), Offset(0f, top), Size(size.width, h * 0.076f + 1))
        }
    }
    if (stars && palette.dark) Starfield(palette, sky.starAlpha)
}

private class Star(val x: Float, val y: Float, val size: Int, val speed: Float, val phase: Float, val sparkle: Boolean)

@Composable
private fun Starfield(palette: Palette, brightness: Float) {
    val stars = remember {
        val r = Random(128)
        List(70) { Star(r.nextFloat(), r.nextFloat() * 0.7f, if (r.nextInt(6) == 0) 2 else 1, 0.6f + r.nextFloat() * 1.8f, r.nextFloat() * 6.28f, r.nextInt(9) == 0) }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = ART_PX.toPx()
        stars.forEach { s ->
            val tw = 0.25f + 0.75f * abs(sin(time * s.speed + s.phase))
            // Very slow drift so the sky feels alive; wraps over the top 70% of the screen.
            val y = ((s.y + time * 0.004f * s.speed) % 0.7f) * size.height
            val x = s.x * size.width
            val c = if (s.sparkle) palette.accent else palette.text
            val sz = s.size * p
            drawRect(c.copy(alpha = tw * 0.8f * brightness), Offset(x, y), Size(sz, sz))
            if (s.sparkle && tw > 0.8f) { // little "+" glint at peak brightness
                drawRect(c.copy(alpha = (tw - 0.8f) * 4f * 0.6f), Offset(x - p, y), Size(sz + 2 * p, sz))
                drawRect(c.copy(alpha = (tw - 0.8f) * 4f * 0.6f), Offset(x, y - p), Size(sz, sz + 2 * p))
            }
        }
        // Shooting star: one every ~9 s, streaking diagonally for under a second.
        val period = 9f
        val n = (time / period).toInt()
        val local = time - n * period
        if (local < 0.9f && brightness > 0.4f) {
            val r = Random(n)
            val sx = size.width * (0.15f + r.nextFloat() * 0.7f)
            val sy = size.height * (0.05f + r.nextFloat() * 0.3f)
            val dir = if (r.nextBoolean()) 1f else -1f
            val k = local / 0.9f
            val hx = sx + dir * size.width * 0.45f * k
            val hy = sy + size.height * 0.18f * k
            repeat(9) { i -> // head plus a fading tail of art pixels
                val tx = hx - dir * i * p * 2.2f
                val ty = hy - i * p * 0.9f
                val a = (1f - i / 9f) * (1f - k * 0.6f)
                drawRect((if (i == 0) Color.White else palette.text).copy(alpha = a), Offset(tx, ty), Size(if (i == 0) 2 * p else p, if (i == 0) 2 * p else p))
            }
        }
    }
}

/** Gentle up-down idle bob, like a sprite's idle animation. Returns a y offset in art pixels. */
@Composable
fun idleBob(active: Boolean, periodMs: Int = 900): Float {
    if (!active) return 0f
    val t = rememberInfiniteTransition(label = "bob")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse), label = "v")
    return if (v > 0.5f) -2f else 0f // two-frame bob: snappy, not floaty
}
