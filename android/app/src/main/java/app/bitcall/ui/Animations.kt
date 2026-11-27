package app.bitcall.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Swipe to answer
// ---------------------------------------------------------------------------------------------

/**
 * A pixel handset on a track: drag it up to answer, down to decline. It leaves a spark trail,
 * tints green/red as it travels, and hops when idle to hint that it moves. Tapping the labels
 * also works.
 */
@Composable
fun SwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val track = 230.dp
    val reach = with(density) { (track / 2 - 44.dp).toPx() }
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val trail = remember { mutableStateListOf<Float>() }
    val hint = rememberInfiniteTransition(label = "hint")
    val hop by hint.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "hop")
    val idleY = if (!dragging && hop < 0.12f) -with(density) { 10.dp.toPx() } else 0f
    val t = (offset.value / reach).coerceIn(-1f, 1f)
    val face = when {
        t < 0 -> lerp(p.surface2, p.good, -t)
        else -> lerp(p.surface2, p.bad, t)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("▲ ANSWER", style = px(10, p.good), modifier = Modifier.clickable(onClick = onAnswer).padding(8.dp))
        Box(
            Modifier
                .width(110.dp)
                .height(track)
                .drawWithContent {
                    val px = ART_PX.toPx()
                    // Dashed pixel track down the middle.
                    var y = 0f
                    while (y < size.height) {
                        drawRect(p.surface2, Offset(size.width / 2 - px, y), Size(2 * px, 2 * px))
                        y += 5 * px
                    }
                    // Spark trail behind the handset.
                    trail.forEachIndexed { i, ty ->
                        val a = (i + 1f) / trail.size
                        val c = if (ty < 0) p.good else p.bad
                        val s = px * (1 + (i % 2))
                        drawRect(c.copy(alpha = a * 0.7f), Offset(size.width / 2 - s / 2 + ((i % 3) - 1) * px * 3, size.height / 2 + ty), Size(s, s))
                    }
                    drawContent()
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val v = offset.value
                            when {
                                v < -reach * 0.8f -> onAnswer()
                                v > reach * 0.8f -> onDecline()
                            }
                            scope.launch {
                                offset.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
                                trail.clear()
                            }
                        },
                        onDragCancel = { dragging = false; scope.launch { offset.animateTo(0f); trail.clear() } },
                    ) { change, dy ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + dy).coerceIn(-reach, reach)) }
                        trail.add(offset.value)
                        if (trail.size > 14) trail.removeAt(0)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .offset { IntOffset(0, (offset.value + idleY).roundToInt()) }
                    .size(84.dp),
            ) {
                PixelButton(face, Modifier.fillMaxSize(), onClick = {}) {
                    Icon(Icons.Filled.Call, null, tint = onColor(face), modifier = Modifier.size(38.dp).rotate(135f * t.coerceAtLeast(0f)))
                }
            }
        }
        Text("▼ DECLINE", style = px(10, p.bad), modifier = Modifier.clickable(onClick = onDecline).padding(8.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// READY… GO!  and the end-of-call card
// ---------------------------------------------------------------------------------------------

/** Full-screen "READY…" → "GO!" flash, played once when [trigger] flips to true. */
@Composable
fun BoxScope.ReadyGo(trigger: Boolean) {
    val p = LocalPalette.current
    var phase by remember { mutableIntStateOf(0) } // 0 hidden, 1 ready, 2 go
    val scale = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (!trigger) return@LaunchedEffect
        phase = 1; fade.snapTo(1f)
        delay(550)
        phase = 2; scale.snapTo(2.2f)
        scale.animateTo(1f, spring(dampingRatio = 0.4f))
        delay(250)
        fade.animateTo(0f, tween(350))
        phase = 0
    }
    if (phase == 0) return
    Text(
        if (phase == 1) "READY…" else "GO!",
        style = px(if (phase == 1) 18 else 30, if (phase == 1) p.text else p.good, glow = if (phase == 2) p.good else null),
        modifier = Modifier.align(Alignment.Center).scale(scale.value).alpha(fade.value),
    )
}

/**
 * Shown when a call ends: the caller's sprite waves goodbye over a "CALL OVER" card with the call
 * length and the XP it earned, counting up.
 */
@Composable
fun CallOverCard(name: String, photoUri: String?, enemy: Boolean, seconds: Long, incoming: Boolean) {
    val p = LocalPalette.current
    val xp = when {
        seconds <= 0 -> 0
        incoming -> 5 + (seconds / 60).toInt() * 2
        else -> 10 + (seconds / 60).toInt() * 2
    }
    val counted = remember { Animatable(0f) }
    LaunchedEffect(xp) { delay(300); counted.animateTo(xp.toFloat(), tween(900)) }
    val t = rememberInfiniteTransition(label = "wave")
    val wave by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "w")
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f)) }
    Card(Modifier.scale(pop.value)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Box(Modifier.rotate(if (wave > 0) 8f else -8f)) { PixelAvatar(name, photoUri, 96.dp, enemy) }
            Text("👋 BYE!", style = px(10, p.dim), modifier = Modifier.padding(top = 8.dp))
            Text(if (seconds > 0) "CALL OVER" else "NO ANSWER", style = px(18, p.text, glow = p.accent), modifier = Modifier.padding(top = 6.dp))
            if (seconds > 0) Text(android.text.format.DateUtils.formatElapsedTime(seconds), style = px(12, p.dim), modifier = Modifier.padding(top = 4.dp))
            if (xp > 0) Text("+${counted.value.roundToInt()} XP", style = px(16, p.good, glow = p.good), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Boss-fight warning for spam calls
// ---------------------------------------------------------------------------------------------

/** Red siren light sweeping across the screen, with flashing red edges. */
@Composable
fun SirenOverlay() {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "siren")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "s")
    Canvas(Modifier.fillMaxSize()) {
        val cell = ART_PX.toPx().toInt().coerceAtLeast(1)
        val w = size.width
        // A dithered red beam that travels left → right, then a second one right → left.
        val beamW = w * 0.35f
        val x1 = -beamW + (w + beamW) * sweep
        val x2 = w - (w + beamW) * sweep
        drawRect(ditherBrush(p.bad.copy(alpha = 0.55f), 4, cell), Offset(x1, 0f), Size(beamW, size.height))
        drawRect(ditherBrush(p.bad.copy(alpha = 0.35f), 2, cell), Offset(x2, 0f), Size(beamW, size.height))
        // Edges pulse twice per sweep.
        val edge = if ((sweep * 4).toInt() % 2 == 0) p.bad.copy(alpha = 0.7f) else Color.Transparent
        val e = ART_PX.toPx() * 3
        drawRect(edge, Offset.Zero, Size(w, e)); drawRect(edge, Offset(0f, size.height - e), Size(w, e))
        drawRect(edge, Offset.Zero, Size(e, size.height)); drawRect(edge, Offset(w - e, 0f), Size(e, size.height))
    }
}

/** Nervous jitter, in art pixels: for the invader while a spam call rings. */
@Composable
fun Modifier.shake(active: Boolean): Modifier {
    if (!active) return this
    val t = rememberInfiniteTransition(label = "shake")
    val f by t.animateFloat(0f, 4f, infiniteRepeatable(tween(240, easing = LinearEasing)), label = "f")
    val dx = when (f.toInt()) { 0 -> -1; 1 -> 1; 2 -> 0; else -> 1 }
    return this.offset(x = ART_PX * dx)
}

// ---------------------------------------------------------------------------------------------
// Hold for Me sprite
// ---------------------------------------------------------------------------------------------

/** A creature tapping its foot to the hold music; it jumps with a "!" when a person picks up. */
@Composable
fun HoldSprite(humanFound: Boolean, size: Dp = 56.dp) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "hold")
    val beat by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (humanFound) 420 else 600, easing = LinearEasing)), label = "b")
    val up = beat < 0.5f
    val note by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "n")
    Box(Modifier.size(size + 28.dp, size + 24.dp), contentAlignment = Alignment.BottomCenter) {
        // Jumping when a human is found, a small tap-bob otherwise.
        val y = if (humanFound) (if (up) -18 else 0) else (if (up) -3 else 0)
        Box(Modifier.offset(y = y.dp)) { PixelAvatar("hold-for-me", null, size) }
        if (humanFound) {
            Text("!", style = px(16, p.accent, glow = p.accent), modifier = Modifier.align(Alignment.TopEnd).offset(y = (y - 4).dp))
        } else {
            Text("♪", style = px(10, p.accent), modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4 * note).dp, y = (-14 * note).dp).alpha(1f - note))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Keypad number: dropping digits + backspace shatter
// ---------------------------------------------------------------------------------------------

/** The typed number, each digit dropping into place with a little bounce like a falling block. */
@Composable
fun DroppingDigits(text: String, sizeSp: Int, color: Color, shatter: ShatterState) {
    Row(
        Modifier.drawWithContent { drawContent(); drawShatter(shatter, color) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEachIndexed { i, ch ->
            androidx.compose.runtime.key(i, ch) {
                val drop = remember { Animatable(-22f) }
                LaunchedEffect(Unit) { drop.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)) }
                Text(ch.toString(), style = px(sizeSp, color, glow = color), modifier = Modifier.offset(y = drop.value.dp))
            }
        }
    }
}

/** Pixel shards that fly off the end of the number and fall when a digit is deleted. */
class ShatterState {
    val progress = Animatable(1f)
    var seed = 0
    suspend fun fire() {
        seed++
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = LinearEasing))
    }
}

fun DrawScope.drawShatter(state: ShatterState, color: Color) {
    val t = state.progress.value
    if (t >= 1f) return
    val p = ART_PX.toPx()
    val r = Random(state.seed)
    val x0 = size.width + p * 4
    val y0 = size.height / 2
    repeat(12) { i ->
        val vx = (r.nextFloat() * 2f - 0.3f) * p * 22
        val vy = -(r.nextFloat() * p * 26 + p * 6)
        val g = p * 90
        val x = x0 + vx * t
        val y = y0 + vy * t + g * t * t // gravity pulls the bits down
        val s = if (i % 4 == 0) 2 * p else p
        drawRect((if (i % 3 == 0) Color.White else color).copy(alpha = 1f - t), Offset(x, y), Size(s, s))
    }
}

// ---------------------------------------------------------------------------------------------
// Screen changes, refresh coin, stars, achievements, badges
// ---------------------------------------------------------------------------------------------

/** Dithered dissolve over new content whenever [key] changes (tab switches). */
@Composable
fun PixelDissolve(key: Any, color: Color) {
    val level = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) { first = false; return@LaunchedEffect }
        level.snapTo(16f)
        level.animateTo(0f, tween(320, easing = LinearEasing))
    }
    val l = level.value.roundToInt()
    if (l <= 0) return
    Canvas(Modifier.fillMaxSize()) {
        drawRect(ditherBrush(color, l, ART_PX.toPx().toInt().coerceAtLeast(2)), Offset.Zero, size)
    }
}

/** A spinning pixel coin, used as the pull-to-refresh indicator. */
@Composable
fun PixelCoin(spinning: Boolean, pull: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "coin")
    val angle by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "a")
    val turn = if (spinning) angle else pull * 6.283f
    val gold = Color(0xFFFFC83D)
    Canvas(modifier.size(34.dp)) {
        val n = 12
        val cell = size.width / n
        val squash = abs(cos(turn)).coerceAtLeast(0.15f) // 3D spin: the coin narrows edge-on
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x - 5.5f) / 6f
            val dy = (y - 5.5f) / 6f
            val d = dx * dx + dy * dy
            if (d > 1f) continue
            val c = when {
                d > 0.72f -> darken(gold, 0.55f)                       // rim
                x in 5..6 && y in 3..8 -> darken(gold, 0.7f)           // embossed bar
                dx + dy < -0.6f -> lighten(gold, 0.5f)                 // shine
                else -> gold
            }
            val cx = size.width / 2 + (x * cell - size.width / 2 + cell / 2) * squash
            drawRect(c, Offset(cx - cell * squash / 2, y * cell), Size(cell * squash + 0.5f, cell + 0.5f))
        }
    }
}

/** A star toggle that pops and bursts sparkles when switched on. */
@Composable
fun StarToggle(starred: Boolean, sizeSp: Int, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val spark = remember { SparkState() }
    val pop = remember { Animatable(1f) }
    Box(
        Modifier
            .drawWithContent { drawContent(); drawSparks(spark, Color(0xFFFFD84D)) }
            .clickable {
                if (!starred) scope.launch { spark.fire() }
                scope.launch { pop.snapTo(1.7f); pop.animateTo(1f, spring(dampingRatio = 0.35f)) }
                onToggle()
            }
            .padding(12.dp),
    ) {
        Text(if (starred) "★" else "☆", style = px(sizeSp, if (starred) p.accent else p.dim), modifier = Modifier.scale(pop.value))
    }
}

/** "ACHIEVEMENT UNLOCKED" trophy that drops in from the top with a chime, one at a time. */
@Composable
fun BoxScope.AchievementToast(queue: List<String>, onShown: (String) -> Unit) {
    val p = LocalPalette.current
    val current = queue.firstOrNull() ?: return
    val y = remember(current) { Animatable(-160f) }
    LaunchedEffect(current) {
        if (app.bitcall.data.Settings.now.keySounds) app.bitcall.assist.ChipSound.powerUp()
        y.animateTo(0f, spring(dampingRatio = 0.55f))
        delay(2800)
        y.animateTo(-160f, tween(280))
        onShown(current)
    }
    val t = rememberInfiniteTransition(label = "trophy")
    val shine by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "sh")
    Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 16.dp, end = 16.dp).offset(y = y.value.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🏆", style = px(18), modifier = Modifier.scale(1f + shine * 0.12f))
                Column {
                    Text("ACHIEVEMENT UNLOCKED", style = px(8, p.accent))
                    Text(current, style = px(12, p.text, glow = p.accent), textAlign = TextAlign.Start)
                }
            }
        }
    }
}

/** Little red count bubble that wobbles until it's been looked at. */
@Composable
fun WobbleBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "wobble")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "f")
    // Wobble for the first quarter of each cycle, rest otherwise.
    val angle = if (f < 0.25f) listOf(-14f, 12f, -8f, 5f)[((f / 0.25f) * 4).toInt().coerceAtMost(3)] else 0f
    Box(
        modifier.rotate(angle).drawWithContent {
            drawRect(darken(p.bad, 0.5f), Offset(0f, ART_PX.toPx()), size)
            drawRect(p.bad, Offset.Zero, Size(size.width, size.height - ART_PX.toPx()))
            drawContent()
        }.padding(horizontal = 5.dp, vertical = 1.dp),
    ) { Text(if (count > 9) "9+" else "$count", style = px(7, Color.White)) }
}

/** Shared zoom-in for a sprite appearing on a new screen (contact details). */
@Composable
fun ZoomIn(content: @Composable () -> Unit) {
    val s = remember { Animatable(0.3f) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { a.animateTo(1f, tween(180)) }
        s.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(Modifier.scale(s.value).alpha(a.value)) { content() }
}


/** Pull-to-refresh with a spinning pixel coin instead of the stock spinner. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CoinRefresh(onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            onRefresh()
            scope.launch { delay(800); refreshing = false }
        },
        state = state,
        indicator = {
            val pull = state.distanceFraction.coerceIn(0f, 1.5f)
            if (refreshing || pull > 0f) {
                PixelCoin(
                    spinning = refreshing, pull = pull,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (if (refreshing) 18f else pull * 40f - 20f).dp),
                )
            }
        },
    ) { content() }
}
