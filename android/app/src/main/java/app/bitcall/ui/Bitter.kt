package app.bitcall.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Bitter, the 128bit family's pixel ghost: 32x32 frames, mirror-symmetric silhouette, drawn cell by
 * cell with no smoothing. O outline, B body, S scanline, D shade, H highlight, W eye white,
 * P pupil, L pupil shine, M mouth, T tongue, C blush. Frames shift 1px for the float bob.
 * The same frames as bitmessenger's Bitter (generated from its sprite sheet script).
 */
val BITTER_FRAMES: Map<String, List<String>> = mapOf(
    "idle1" to listOf(
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWWWWSSSSWWWWWWSSSDO...",
        "...OBBBBWWWWWWBBBBWWWWWWBBBDO...",
        "...OSSSSWWLPWWSSSSWWLPWWSSSDO...",
        "..OBBBBBWWPPWWBBBBWWPPWWBBBBDO..",
        "..OSSSSSWWPPWWSSSSWWPPWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSMSSSSSSMSSSSSSSSDO..",
        "..OBBBBBBBBBBMMMMMMBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "...OBBBBBBDOOBBBBBDOOBBBBBBDO...",
        "....OSSSSDO..OSSSDO..OSSSSDO....",
        "....OBBBBDO..OBBBDO..OBBBBDO....",
        ".....OOOOO....OOOO....OOOOO.....",
        "................................",
        "................................",
    ),
    "idle2" to listOf(
        "................................",
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWWWWSSSSWWWWWWSSSDO...",
        "...OBBBBWWWWWWBBBBWWWWWWBBBDO...",
        "...OSSSSWWLPWWSSSSWWLPWWSSSDO...",
        "..OBBBBBWWPPWWBBBBWWPPWWBBBBDO..",
        "..OSSSSSWWPPWWSSSSWWPPWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSMSSSSSSMSSSSSSSSDO..",
        "..OBBBBBBBBBBMMMMMMBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBDOOBBBBBDOOBBBBBDOOBBBDO..",
        "..OSSDO..OSSSDO..OSSSDO..OSSDO..",
        "..OBBDO..OBBBDO..OBBBDO..OBBDO..",
        "...OOO....OOOO....OOOO....OOO...",
        "................................",
    ),
    "blink" to listOf(
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBBBBBBBBBBBBBBBBBBBDO...",
        "...OSSSSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBBMMBBBBBBBBMMBBBBBDO...",
        "...OSSSSSMSSMSSSSSSMSSMSSSSDO...",
        "..OBBBBBMBBBBMBBBBMBBBBMBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSMSSSSSSMSSSSSSSSDO..",
        "..OBBBBBBBBBBMMMMMMBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "...OBBBBBBDOOBBBBBDOOBBBBBBDO...",
        "....OSSSSDO..OSSSDO..OSSSSDO....",
        "....OBBBBDO..OBBBDO..OBBBBDO....",
        ".....OOOOO....OOOO....OOOOO.....",
        "................................",
        "................................",
    ),
    "talk1" to listOf(
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWWWWSSSSWWWWWWSSSDO...",
        "...OBBBBWWWWWWBBBBWWWWWWBBBDO...",
        "...OSSSSWWLPWWSSSSWWLPWWSSSDO...",
        "..OBBBBBWWPPWWBBBBWWPPWWBBBBDO..",
        "..OSSSSSWWPPWWSSSSWWPPWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSSMMMMMMSSSSSSSSSDO..",
        "..OBBBBBBBBBMMMMMMMMBBBBBBBBDO..",
        "..OSSSSSSSSSMMMTTMMMSSSSSSSSDO..",
        "..OBBBBBBBBBBMTTTTMBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "...OBBBBBBDOOBBBBBDOOBBBBBBDO...",
        "....OSSSSDO..OSSSDO..OSSSSDO....",
        "....OBBBBDO..OBBBDO..OBBBBDO....",
        ".....OOOOO....OOOO....OOOOO.....",
        "................................",
        "................................",
    ),
    "talk2" to listOf(
        "................................",
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWWWWSSSSWWWWWWSSSDO...",
        "...OBBBBWWWWWWBBBBWWWWWWBBBDO...",
        "...OSSSSWWLPWWSSSSWWLPWWSSSDO...",
        "..OBBBBBWWPPWWBBBBWWPPWWBBBBDO..",
        "..OSSSSSWWPPWWSSSSWWPPWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSSSSMMSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBMMMMBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSMMMMSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBMMBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBDOOBBBBBDOOBBBBBDOOBBBDO..",
        "..OSSDO..OSSSDO..OSSSDO..OSSDO..",
        "..OBBDO..OBBBDO..OBBBDO..OBBDO..",
        "...OOO....OOOO....OOOO....OOO...",
        "................................",
    ),
    "think1" to listOf(
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWLPWSSSSWWWLPWSSSDO...",
        "...OBBBBWWWPPWBBBBWWWPPWBBBDO...",
        "...OSSSSWWWPPWSSSSWWWPPWSSSDO...",
        "..OBBBBBWWWWWWBBBBWWWWWWBBBBDO..",
        "..OSSSSSWWWWWWSSSSWWWWWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSSSSMMSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBMMMMBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSMMMMSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBMMBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "...OBBBBBBDOOBBBBBDOOBBBBBBDO...",
        "....OSSSSDO..OSSSDO..OSSSSDO....",
        "....OBBBBDO..OBBBDO..OBBBBDO....",
        ".....OOOOO....OOOO....OOOOO.....",
        "................................",
        "................................",
    ),
    "think2" to listOf(
        "................................",
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWLPWWWSSSSWLPWWWSSSDO...",
        "...OBBBBWPPWWWBBBBWPPWWWBBBDO...",
        "...OSSSSWPPWWWSSSSWPPWWWSSSDO...",
        "..OBBBBBWWWWWWBBBBWWWWWWBBBBDO..",
        "..OSSSSSWWWWWWSSSSWWWWWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSSSSMMSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBMMMMBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSMMMMSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBMMBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBDOOBBBBBDOOBBBBBDOOBBBDO..",
        "..OSSDO..OSSSDO..OSSSDO..OSSDO..",
        "..OBBDO..OBBBDO..OBBBDO..OBBDO..",
        "...OOO....OOOO....OOOO....OOO...",
        "................................",
    ),
)

val BITTER_BLUE = Color(0xFF4FC3F7)

enum class BitterMood { IDLE, TALKING, THINKING }

/** Frame sequence for a mood, with how long each frame shows (ms). */
fun bitterSequence(mood: BitterMood): List<Pair<String, Long>> = when (mood) {
    BitterMood.IDLE -> listOf("idle1" to 520L, "idle2" to 520L, "idle1" to 520L, "idle2" to 520L, "idle1" to 380L, "blink" to 140L, "idle1" to 520L, "idle2" to 520L)
    BitterMood.TALKING -> listOf("talk1" to 150L, "talk2" to 150L)
    BitterMood.THINKING -> listOf("think1" to 480L, "think2" to 480L)
}

@Composable
fun BitterSprite(size: Dp, mood: BitterMood = BitterMood.IDLE, modifier: Modifier = Modifier, animate: Boolean = true) {
    var frame by remember { mutableStateOf("idle1") }
    LaunchedEffect(mood, animate) {
        if (!animate) { frame = "idle1"; return@LaunchedEffect }
        val seq = bitterSequence(mood)
        var i = 0
        while (true) {
            val (f, ms) = seq[i % seq.size]
            frame = f
            delay(ms)
            i++
        }
    }
    val body = BITTER_BLUE
    val colors = remember(body) {
        mapOf(
            'O' to darken(body, 0.15f),
            'B' to body,
            'S' to darken(body, 0.86f),
            'D' to darken(body, 0.62f),
            'H' to lerp(body, Color.White, 0.6f),
            'W' to Color.White,
            'P' to Color(0xFF0D47A1),
            'L' to Color(0xFF90CAF9),
            'M' to darken(body, 0.15f),
            'T' to Color(0xFFFF6F91),
            'C' to Color(0xFFFF8AB3),
        )
    }
    val rows = BITTER_FRAMES.getValue(frame)
    Canvas(modifier.size(size)) {
        val cell = this.size.width / 32f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                colors[ch]?.let { drawRect(it, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f)) }
            }
        }
    }
}

