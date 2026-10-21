package app.bitcall.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chunky arcade button: solid colour, hard drop shadow, sinks when pressed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PixelButton(
    color: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth = 5.dp
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.padding(bottom = depth)) {
        Box(Modifier.matchParentSize().offset(y = depth).clip(shape).background(darken(color)))
        Box(
            Modifier
                .matchParentSize()
                .offset(y = if (pressed) depth else 0.dp)
                .clip(shape)
                .background(color)
                .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick),
        )
        Box(Modifier.offset(y = if (pressed) depth else 0.dp).align(Alignment.Center)) { content() }
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

@Composable
fun EnemyBadge(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        "👾 ENEMY",
        style = px(8, Color.White),
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(p.bad).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

private val spriteCache = LruCache<String, ImageBitmap>(64)

/**
 * Contact photo crushed down to a 16x16 sprite and drawn without smoothing; contacts without a
 * photo get a symmetric 8x8 invader generated from their name. Enemies always get the red invader.
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
                Bitmap.createScaledBitmap(src, 16, 16, true).asImageBitmap().also { spriteCache.put(photoUri, it) }
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size / 6)
    val frame = Modifier.size(size).clip(shape).border(2.dp, if (enemy) p.bad else p.surface2, shape)
    val s = sprite
    if (s != null && !enemy) {
        Image(s, null, frame, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
    } else {
        val seed = if (enemy) 0x5EED else name.hashCode()
        val color = if (enemy) p.bad else p.keys[Math.floorMod(seed, p.keys.size)]
        Canvas(frame.background(p.surface)) {
            val cell = this.size.width / 10f
            for (y in 0 until 8) for (x in 0 until 4) {
                val on = if (enemy) INVADER[y][x] == '#' else ((seed ushr ((y * 4 + x) % 31)) and 1) == 1 || (y in 2..5 && x == 3)
                if (!on) continue
                for (mx in listOf(x, 7 - x)) drawRect(color, Offset(cell * (mx + 1), cell * (y + 1)), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

// Left half of an 8x8 space invader; the right half is mirrored.
private val INVADER = listOf("  # ", "   #", "  ##", " # #", "####", "# ##", "# # ", "  # ")

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(text.uppercase(), style = px(9, p.accent), modifier = modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(p.surface).border(3.dp, p.surface2, RoundedCornerShape(10.dp)).padding(12.dp),
    ) { content() }
}
