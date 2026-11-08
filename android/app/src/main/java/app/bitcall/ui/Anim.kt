package app.bitcall.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// Every animation here moves in whole steps at a low frame rate, like a sprite, never a smooth
// blur, so the pixel art stays crisp. ANIMATIONS: FULL plays all of them, LESS keeps only the
// ones that help (ringing waves, the answer arrow), OFF stops everything.
private val animLevel = mutableStateOf("FULL")

fun setAnimLevel(level: String) { animLevel.value = level }
val animOn: Boolean get() = animLevel.value != "OFF"
val animFull: Boolean get() = animLevel.value == "FULL"

/** Counts up [fps] times a second while [running] (and animations are on); otherwise stays 0. */
@Composable
fun frameTick(fps: Int, running: Boolean = true): Int {
    var f by remember { mutableIntStateOf(0) }
    val on = running && animOn
    LaunchedEffect(on, fps) {
        f = 0
        if (!on) return@LaunchedEffect
        while (true) { delay(1000L / fps); f++ }
    }
    return f
}

/** The ringing phone shake: a burst of side-to-side hops, then a rest, in whole-dp steps. */
@Composable
fun Modifier.ringShake(active: Boolean): Modifier {
    val t = frameTick(14, active && animFull)
    val xs = listOf(0, -4, 4, -4, 4, -3, 3, -2, 2, 0)
    val ys = listOf(0, -2, 0, -2, 0, -1, 0, -1, 0, 0)
    val i = t % 22
    return if (i < xs.size) offset(x = xs[i].dp, y = ys[i].dp) else this
}

/** Draws a chunky 4-point pixel sparkle centred on [c], [cell] px per pixel, [arm] pixels long. */
fun DrawScope.sparkle(c: Offset, cell: Float, arm: Int, color: Color) {
    drawRect(color, Offset(c.x - cell / 2, c.y - cell / 2), Size(cell, cell))
    for (k in 1..arm) {
        val a = if (k == arm) color.copy(alpha = 0.6f) else color
        drawRect(a, Offset(c.x - cell / 2 + k * cell, c.y - cell / 2), Size(cell, cell))
        drawRect(a, Offset(c.x - cell / 2 - k * cell, c.y - cell / 2), Size(cell, cell))
        drawRect(a, Offset(c.x - cell / 2, c.y - cell / 2 + k * cell), Size(cell, cell))
        drawRect(a, Offset(c.x - cell / 2, c.y - cell / 2 - k * cell), Size(cell, cell))
    }
}

/** ★ favorites: gold sparkles twinkling around their photo. */
@Composable
fun Sparkles(size: Dp, color: Color = Color(0xFFFFD60A)) {
    val t = frameTick(6, animFull)
    if (!animFull) return
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / 48f
        val spots = listOf(0.12f to 0.18f, 0.88f to 0.14f, 0.92f to 0.62f, 0.08f to 0.70f, 0.50f to 0.03f, 0.30f to 0.95f)
        spots.forEachIndexed { i, (x, y) ->
            val phase = (t + i * 2) % 6
            val arm = when (phase) { 0 -> 0; 1 -> 1; 2 -> 2; 3 -> 1; else -> -1 }
            if (arm >= 0) sparkle(Offset(this.size.width * x, this.size.height * y), cell, arm, color)
        }
    }
}

/** Spam caller: "⚠ BOSS LEVEL" scrolling across in 8dp steps. */
@Composable
fun BossMarquee(color: Color) {
    val t = frameTick(10, animFull)
    val text = "⚠ BOSS LEVEL ⚠ POSSIBLE SPAM ⚠ BOSS LEVEL ⚠ POSSIBLE SPAM ⚠ "
    Box(Modifier.fillMaxWidth().padding(top = 10.dp).background(color).clipToBounds().padding(vertical = 6.dp)) {
        Text(text + text, style = px(9, onColor(color)), maxLines = 1, softWrap = false, modifier = Modifier.offset(x = (-(t * 6) % 600).dp))
    }
}

/** A pixel ▶ that slides across the ANSWER button, pointing the way in the dark. */
@Composable
fun SlideHint(color: Color) {
    val t = frameTick(8)
    val x = listOf(-24, -16, -8, 0, 8, 16, 24, 24)[t % 8]
    Text("▶", style = px(14, color.copy(alpha = if (t % 8 == 7) 0f else 0.8f)), modifier = Modifier.offset(x = x.dp))
}

/** Dialing: a pixel loading bar whose blocks fill one by one. */
@Composable
fun LoadingBlocks(color: Color, dim: Color) {
    val t = frameTick(8)
    Row(Modifier.padding(top = 10.dp)) {
        repeat(10) { i ->
            Box(Modifier.padding(horizontal = 2.dp).size(10.dp).background(if (i <= t % 11) color else dim))
        }
    }
}

/** 1-UP burst: pixel stars fly out from the centre once when [trigger] changes (skipped at 0). */
@Composable
fun PixelBurst(trigger: Int, size: Dp, colors: List<Color>) {
    var frame by remember { mutableIntStateOf(-1) }
    LaunchedEffect(trigger) {
        if (trigger == 0 || !animFull) return@LaunchedEffect
        for (f in 0..12) { frame = f; delay(45) }
        frame = -1
    }
    if (frame < 0) return
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / 40f
        val c = center
        repeat(16) { i ->
            val a = i * Math.PI * 2 / 16
            val d = (frame * 1.6f + 4) * cell
            val p = Offset(c.x + (cos(a) * d).toFloat(), c.y + (sin(a) * d).toFloat())
            val col = colors[i % colors.size].copy(alpha = if (frame > 9) 0.4f else 1f)
            if (i % 2 == 0) sparkle(p, cell, if (frame < 6) 1 else 0, col)
            else drawRect(col, Offset(p.x - cell, p.y - cell), Size(cell * 2, cell * 2))
        }
    }
}

/** LEVEL UP: pixel confetti raining down for a moment. */
@Composable
fun Confetti(trigger: Int, colors: List<Color>, modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(-1) }
    LaunchedEffect(trigger) {
        if (trigger == 0 || !animFull) return@LaunchedEffect
        for (f in 0..40) { frame = f; delay(60) }
        frame = -1
    }
    if (frame < 0) return
    Canvas(modifier) {
        val cell = 6.dp.toPx()
        val rnd = java.util.Random(7)
        repeat(60) { i ->
            val x = rnd.nextFloat() * size.width
            val speed = 0.6f + rnd.nextFloat()
            val y = (frame * cell * speed * 1.5f) - rnd.nextFloat() * size.height * 0.5f
            if (y < -cell || y > size.height) return@repeat
            val wobble = if ((frame + i) % 4 < 2) 0f else cell
            drawRect(colors[i % colors.size], Offset((x + wobble).coerceIn(0f, size.width - cell), y), Size(cell, if ((frame + i) % 3 == 0) cell / 2 else cell))
        }
    }
}

/** Old-TV switch-off: the screen squashes to a bright line, then a dot, then black. 0..1. */
fun crtOffScale(progress: Float): Pair<Float, Float> = when {
    progress <= 0f -> 1f to 1f
    progress < 0.5f -> 1f to (1f - progress * 2f).coerceAtLeast(0.008f)
    progress < 0.9f -> (1f - (progress - 0.5f) / 0.4f).coerceAtLeast(0.01f) to 0.008f
    else -> 0f to 0f
}

/** Slow pixel stars drifting down behind the app (Settings → Theme → Starfield). */
@Composable
fun Starfield(color: Color, modifier: Modifier = Modifier) {
    val t = frameTick(4)
    Canvas(modifier) {
        val cell = 3.dp.toPx()
        val rnd = java.util.Random(128)
        repeat(50) { i ->
            val x = (rnd.nextFloat() * size.width / cell).toInt() * cell
            val speed = 1 + rnd.nextInt(3)
            val rows = (size.height / cell).toInt().coerceAtLeast(1)
            val y = (((rnd.nextFloat() * rows).toInt() + t * speed / 2) % rows) * cell
            val twinkle = (t + i) % 9 == 0
            drawRect(color.copy(alpha = if (twinkle) 0.9f else 0.35f + 0.1f * speed), Offset(x, y), Size(cell, cell))
        }
    }
}

/** The once-a-day boot screen: logo drops in, waves light up, BITCALL types itself, PRESS START. */
@Composable
fun BootSplash(onDone: () -> Unit) {
    val p = LocalPalette.current
    var f by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 0..26) { f = i; delay(60) }
        onDone()
    }
    val drop = listOf(-220, -160, -110, -70, -36, -12, 0, -10, 0, -4, 0)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelLogo(128.dp, Modifier.offset(y = drop[f.coerceAtMost(drop.lastIndex)].dp), waves = ((f - 10) / 2).coerceIn(0, 3))
            val word = "BITCALL"
            val shown = ((f - 14).coerceAtLeast(0)).coerceAtMost(word.length)
            Row(Modifier.padding(top = 20.dp).height(32.dp)) {
                Text(word.take(minOf(shown, 3)), style = px(22, p.logo, glow = p.logo))
                Text(word.drop(3).take((shown - 3).coerceAtLeast(0)), style = px(22, Color.White))
            }
            Text(
                if (f > 21 && f % 2 == 0) "PRESS START" else " ", style = px(9, Color.White), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
