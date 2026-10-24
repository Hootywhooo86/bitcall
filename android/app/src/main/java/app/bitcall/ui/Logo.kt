package app.bitcall.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Bitcall logo: a 32x32 pixel handset with three sound waves. Same pixels as the launcher icon
 * (res/drawable/ic_launcher_fg.xml); here they take the theme's LOGO and WAVES colours.
 * O outline, G body, L highlight, D shadow, W glint, A/B/C waves (inner to outer).
 */
val LOGO = listOf(
    "................................",
    ".....OOOOOO.....................",
    "....OLLLLLLO....................",
    "...OLGGGGGGLO...................",
    "..OLGWWWGGGGLO..................",
    "..OLWGGGGGGGDO.....CC...........",
    ".OLGWGGGGGGGDO.....CCCC.........",
    ".OLGGGGGGGGGDO.......CCC........",
    ".OLGGGGGGGGGDO..BB....CCC.......",
    "..OLGGGGGGGDO...BBBB...CCC......",
    "...OLGGGGGDO......BBB...CC......",
    "....OLGGGGDO.......BBB...CC.....",
    ".....OLGGGDO...AA...BBB..CC.....",
    "......OLGGGLO..AAAA..BB.........",
    "......OLGGGDO....AA...BB........",
    ".......OLGGGLO....AA..BB........",
    ".......OLGGGDO....AA............",
    "........OLGGGLO.................",
    ".........OLGGGLOO......OOOOO....",
    ".........OLGGGGLLOO...OLLLLLO...",
    "..........OLGGGGGLLOOOLGGGGGLO..",
    "...........OLDGGGGGLLLGGGGGGGLO.",
    "............OOLGGGGGGGGGGGGGGDO.",
    "..............OLDGGGGGGGGGGGGDO.",
    "...............OOLDGGGGGGGGGGDO.",
    ".................OOLGGGGGGGGGDO.",
    "...................OLGGGGGGGGDO.",
    "....................OLGGGGGGDO..",
    ".....................OLGGGDDO...",
    "......................OLDDOO....",
    ".......................OOO......",
    "................................",
)

@Composable
fun PixelLogo(size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val body = p.logo
    val colors = mapOf(
        'O' to darken(body, 0.12f),
        'G' to body,
        'L' to lerp(body, Color.White, 0.45f),
        'D' to darken(body, 0.7f),
        'W' to lerp(body, Color.White, 0.9f),
        'A' to lerp(p.waves, Color.White, 0.35f),
        'B' to p.waves,
        'C' to darken(p.waves, 0.75f),
    )
    Canvas(modifier.size(size)) {
        val cell = this.size.width / LOGO.size
        LOGO.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                // +0.5px overlap so neighbouring cells never show a hairline gap between them.
                colors[ch]?.let { drawRect(it, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f)) }
            }
        }
    }
}

/** Logo + BITCALL wordmark for the top bar. */
@Composable
fun LogoTitle(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PixelLogo(36.dp)
        Text("BIT", style = px(14, p.logo, glow = p.logo), modifier = Modifier.padding(start = 8.dp))
        Text("CALL", style = px(14, p.text))
    }
}
