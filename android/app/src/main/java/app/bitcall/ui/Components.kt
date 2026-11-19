package app.bitcall.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Arcade button drawn like a sprite tile: stepped corners, highlight + shadow + dithered shading,
 * a hard drop shadow underneath, and it sinks when pressed. [sparks] bursts pixel sparks on tap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PixelButton(
    color: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    sparks: Boolean = false,
    shine: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val spark = remember { SparkState() }
    val scope = rememberCoroutineScope()
    val depth = ART_PX * 2
    val shape = remember { pixelShape() }
    val sink = if (pressed) depth else 0.dp
    Box(
        modifier
            .padding(bottom = depth)
            .then(if (sparks) Modifier.drawWithContent { drawContent(); drawSparks(spark, lighten(color, 0.4f)) } else Modifier),
    ) {
        Box(Modifier.matchParentSize().offset(y = depth).clip(shape).background(darken(color, 0.45f)))
        Box(
            Modifier
                .matchParentSize()
                .offset(y = sink)
                .clip(shape)
                .background(color)
                .drawBehind { pixelBevel(color, pressed) }
                .border(ART_PX / 1.5f, darken(color, 0.55f), shape)
                .then(if (shine) Modifier.shimmer() else Modifier)
                .combinedClickable(
                    interactionSource = source, indication = null, onLongClick = onLongClick,
                    onClick = { if (sparks) scope.launch { spark.fire() }; onClick() },
                ),
        )
        Box(Modifier.offset(y = sink).align(Alignment.Center)) { content() }
    }
}

fun darken(c: Color, f: Float = 0.55f) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

@Composable
fun PixelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    PixelButton(if (selected) p.accent else p.surface2, onClick = onClick) {
        Text(label, style = px(9, if (selected) onColor(p.accent) else p.text), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

/** Red "ENEMY" tag with a little invader that marches (2-frame walk cycle). */
@Composable
fun EnemyBadge(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.clip(pixelShape(2.dp)).background(p.bad).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InvaderSprite(Modifier.size(width = 16.dp, height = 12.dp), Color.White)
        Text(" ENEMY", style = px(8, Color.White))
    }
}

// 11x8 space invader, two walk frames.
private val INVADER_A = listOf("  #     #  ", "   #   #   ", "  #######  ", " ## ### ## ", "###########", "# ####### #", "# #     # #", "   ## ##   ")
private val INVADER_B = listOf("  #     #  ", "#  #   #  #", "# ####### #", "### ### ###", "###########", " ######### ", "  #     #  ", " #       # ")

@Composable
fun InvaderSprite(modifier: Modifier, color: Color) {
    val t = rememberInfiniteTransition(label = "march")
    val f by t.animateFloat(0f, 2f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "f")
    val frame = if (f < 1f) INVADER_A else INVADER_B
    Canvas(modifier) {
        val cell = minOf(size.width / 11f, size.height / 8f)
        val ox = (size.width - cell * 11) / 2
        val oy = (size.height - cell * 8) / 2
        frame.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch != '#') return@forEachIndexed
                // Top edge of each column catches the light.
                val c = if (y == 0 || frame[y - 1][x] != '#') lighten(color, 0.3f) else color
                drawRect(c, Offset(ox + x * cell, oy + y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

private val spriteCache = LruCache<String, ImageBitmap>(96)

/**
 * Contact photo turned into 32x32 pixel art: downsampled, then posterised with ordered (Bayer)
 * dithering so it reads like a hand-shaded sprite. Contacts without a photo get a generated,
 * shaded 16x16 creature; enemies get the marching invader.
 */
@Composable
fun PixelAvatar(name: String, photoUri: String?, size: Dp, enemy: Boolean = false) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var sprite by remember(photoUri) { mutableStateOf(spriteCache.get(photoUri ?: "")) }
    LaunchedEffect(photoUri) {
        if (photoUri == null || sprite != null) return@LaunchedEffect
        sprite = withContext(Dispatchers.IO) {
            runCatching {
                val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(photoUri))) { d, _, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                pixelArt(src).asImageBitmap().also { spriteCache.put(photoUri, it) }
            }.getOrNull()
        }
    }
    val shape = remember(size) { pixelShape(if (size >= 64.dp) ART_PX else 2.dp) }
    val frame = Modifier.size(size).clip(shape)
    val s = sprite
    Box(Modifier.size(size).border(ART_PX / 1.5f, if (enemy) p.bad else darken(p.surface2, 0.7f), shape)) {
        when {
            enemy -> Box(frame.background(darken(p.bad, 0.35f)), contentAlignment = Alignment.Center) {
                InvaderSprite(Modifier.size(size * 0.7f), p.bad)
            }
            s != null -> Image(s, null, frame, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
            else -> {
                val seed = name.hashCode()
                val grid = remember(seed) { creature(seed) }
                val base = p.keys[Math.floorMod(seed, p.keys.size)]
                Canvas(frame.background(p.surface)) { drawCreature(grid, base, p.surface) }
            }
        }
    }
}

/** 32x32 dithered, posterised (6 levels per channel) version of a photo. */
private fun pixelArt(src: Bitmap): Bitmap {
    val n = 32
    val small = Bitmap.createScaledBitmap(src, n, n, true)
    val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val step = 255f / 5f
    for (y in 0 until n) for (x in 0 until n) {
        val c = small.getPixel(x, y)
        val d = (bayer(x, y) - 0.5f) * step
        fun q(v: Int) = (Math.round((v + d) / step) * step).toInt().coerceIn(0, 255)
        out.setPixel(x, y, android.graphics.Color.rgb(q((c shr 16) and 255), q((c shr 8) and 255), q(c and 255)))
    }
    return out
}

// --- generated creature sprites ------------------------------------------------------------

private const val CS = 16
private const val BODY = 1
private const val EYE = 2
private const val PUPIL = 3

/** A symmetric 16x16 creature mask from a seed: random blob, smoothed, mirrored, with eyes. */
private fun creature(seed: Int): Array<IntArray> {
    val r = Random(seed)
    var g = Array(CS) { y ->
        IntArray(CS) { x ->
            val hx = if (x < CS / 2) x else CS - 1 - x
            val dx = (hx - 7.5f) / 6f
            val dy = (y - 8f) / 6.5f
            val inside = dx * dx + dy * dy < 1f
            if (inside && r.nextFloat() < 0.78f) BODY else if (!inside && r.nextFloat() < 0.06f && y > 2) BODY else 0
        }
    }
    // One smoothing pass so the blob looks drawn, not noisy.
    g = Array(CS) { y ->
        IntArray(CS) { x ->
            var n = 0
            for (oy in -1..1) for (ox in -1..1) if (g.getOrNull(y + oy)?.getOrNull(x + ox) == BODY) n++
            if (n >= 5) BODY else 0
        }
    }
    // Mirror the left half so it's symmetric like a sprite.
    for (y in 0 until CS) for (x in CS / 2 until CS) g[y][x] = g[y][CS - 1 - x]
    // Eyes: a 2x2 white with a dark pupil, on the first row that has room.
    val ey = (5..9).firstOrNull { y -> g[y][5] == BODY && g[y + 1][5] == BODY && g[y][6] == BODY } ?: 7
    for ((x, mx) in listOf(5 to 10, 6 to 9)) {
        g[ey][x] = EYE; g[ey][mx] = EYE; g[ey + 1][x] = EYE; g[ey + 1][mx] = EYE
    }
    g[ey + 1][6] = PUPIL; g[ey + 1][9] = PUPIL
    return g
}

/** Draws a creature with a dark outline, top highlight and bottom shadow. */
private fun DrawScope.drawCreature(g: Array<IntArray>, base: Color, bg: Color) {
    val cell = size.width / (CS + 2)
    val outline = darken(base, 0.3f)
    fun on(x: Int, y: Int) = (g.getOrNull(y)?.getOrNull(x) ?: 0) != 0
    for (y in -1..CS) for (x in -1..CS) {
        val ox = (x + 1) * cell
        val oy = (y + 1) * cell
        val v = g.getOrNull(y)?.getOrNull(x) ?: 0
        val c = when {
            v == EYE -> Color.White
            v == PUPIL -> Color(0xFF101018)
            v == BODY && !on(x, y - 1) -> lighten(base, 0.35f)     // light from above
            v == BODY && !on(x, y + 1) -> darken(base, 0.7f)       // shade below
            v == BODY && !on(x + 1, y) && x >= CS / 2 -> darken(base, 0.82f)
            v == BODY -> base
            on(x - 1, y) || on(x + 1, y) || on(x, y - 1) || on(x, y + 1) -> outline
            else -> continue
        }
        drawRect(c, Offset(ox, oy), Size(cell + 0.5f, cell + 0.5f))
    }
    // Ground shadow, dithered.
    drawRect(ditherBrush(darken(bg, 0.6f), 8, cell.toInt().coerceAtLeast(1)), Offset(cell * 4, cell * (CS + 0.6f)), Size(cell * (CS - 6), cell))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text("▸ " + text.uppercase(), style = px(9, p.accent), modifier = modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
}

/** A panel drawn like an RPG text box: stepped frame, inner highlight line, dithered base. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val shape = remember { pixelShape() }
    Column(
        modifier
            .clip(shape)
            .background(p.surface)
            .drawBehind {
                val px = ART_PX.toPx()
                drawRect(ditherBrush(p.surface2, 3, px.toInt().coerceAtLeast(1)), Offset(0f, size.height * 0.6f), Size(size.width, size.height * 0.4f))
                drawRect(lighten(p.surface, 0.12f), Offset(2 * px, px), Size(size.width - 4 * px, px))
            }
            .border(ART_PX, p.surface2, shape)
            .padding(14.dp),
    ) { content() }
}
