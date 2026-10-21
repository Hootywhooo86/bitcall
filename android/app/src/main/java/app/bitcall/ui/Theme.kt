package app.bitcall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bitcall.R
import app.bitcall.data.BitSettings
import app.bitcall.data.Settings

val PixelFont = FontFamily(Font(R.font.press_start_2p))

data class Palette(
    val id: String,
    val label: String,
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    val accentDark: Color,
    val good: Color,
    val bad: Color,
    val keys: List<Color>,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

val PALETTES = listOf(
    Palette("PIXEL_NIGHT", "Pixel Night", true, c(0x0d0d1a), c(0x1a1a2e), c(0x2b2b45), c(0xf5f5f5), c(0x9e9e9e), c(0xb388ff), c(0x4a148c), c(0x00e676), c(0xff5252),
        listOf(c(0xff5252), c(0xff9f1c), c(0xffd60a), c(0x00e676), c(0x00e5ff), c(0x448aff), c(0xb388ff), c(0xff4081), c(0x69f0ae), c(0xffab40), c(0x40c4ff), c(0xea80fc))),
    Palette("ARCADE", "Arcade Neon", true, c(0x000000), c(0x10101a), c(0x1c1c2c), c(0xffffff), c(0x8888aa), c(0xff2bd6), c(0x6a005a), c(0x39ff14), c(0xff073a),
        listOf(c(0xff073a), c(0xff6ec7), c(0xfff01f), c(0x39ff14), c(0x04d9ff), c(0xbc13fe), c(0xff9933), c(0x00ffcc), c(0xff2bd6), c(0x7df9ff), c(0xccff00), c(0xff3131))),
    Palette("CRT_GREEN", "CRT Phosphor", true, c(0x020a02), c(0x061806), c(0x0c2a0c), c(0x9cff9c), c(0x4c9a4c), c(0x33ff33), c(0x0a5a0a), c(0x33ff33), c(0xffb000),
        listOf(c(0x33ff33), c(0x2ee62e), c(0x29cc29), c(0x66ff66), c(0x24b324), c(0x80ff80), c(0x33ff99), c(0x99ff33), c(0x1f991f), c(0xb3ffb3), c(0x33ffcc), c(0x00cc66))),
    Palette("GAMEBOY", "Game Boy", false, c(0x9bbc0f), c(0x8bac0f), c(0x7a9a0d), c(0x0f380f), c(0x306230), c(0x306230), c(0x0f380f), c(0x306230), c(0x8b1e1e),
        listOf(c(0x306230), c(0x0f380f), c(0x306230), c(0x0f380f), c(0x306230), c(0x0f380f), c(0x306230), c(0x0f380f), c(0x306230), c(0x0f380f), c(0x306230), c(0x0f380f))),
    Palette("SUNSET", "Sunset Drive", true, c(0x1a0b1e), c(0x2a1030), c(0x3d1844), c(0xffeedd), c(0xb08aa0), c(0xff7a59), c(0x7a1f3d), c(0x2de2e6), c(0xff3864),
        listOf(c(0xff3864), c(0xff6c11), c(0xf9c80e), c(0xff4365), c(0xfd1d53), c(0xf6019d), c(0xd40078), c(0x9700cc), c(0x2de2e6), c(0x261447), c(0xff7a59), c(0x541388))),
    Palette("CANDY", "Candy (light)", false, c(0xfff4f8), c(0xffffff), c(0xffe1ee), c(0x2b1b33), c(0x8a7090), c(0xff4fa3), c(0xb0005e), c(0x00b26f), c(0xe53935),
        listOf(c(0xff8fab), c(0xffb86b), c(0xffe066), c(0x8be28b), c(0x7bdff2), c(0x8fa8ff), c(0xc49bff), c(0xff9ee5), c(0x6ee7b7), c(0xfcd34d), c(0x93c5fd), c(0xf0abfc))),
)

/** Extra colours the keypad can be set to, one tap at a time. */
val SWATCHES = listOf(
    0xffff5252, 0xffff9f1c, 0xffffd60a, 0xff00e676, 0xff00e5ff, 0xff448aff, 0xffb388ff, 0xffff4081,
    0xff69f0ae, 0xffffffff, 0xff9e9e9e, 0xff795548, 0xff33ff33, 0xfff6019d, 0xff2b2b45, 0xff000000,
)

fun paletteOf(id: String) = PALETTES.firstOrNull { it.id == id } ?: PALETTES[0]

val LocalPalette = staticCompositionLocalOf { PALETTES[0] }
val LocalBitSettings = staticCompositionLocalOf { BitSettings() }

fun Palette.key(index: Int, s: BitSettings): Color = s.keyColors[index]?.let { Color(it) } ?: keys[index % keys.size]

/** Readable label colour on top of an arbitrary key colour. */
fun onColor(bg: Color): Color =
    if (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue > 0.55) Color(0xFF0D0D1A) else Color.White

fun px(size: Int, color: Color = Color.Unspecified, glow: Color? = null) = TextStyle(
    fontFamily = PixelFont, fontSize = size.sp, lineHeight = (size * 1.6).sp, color = color,
    shadow = glow?.let { Shadow(it, Offset.Zero, 14f) },
)

@Composable
fun BitTheme(content: @Composable () -> Unit) {
    val s by Settings.state.collectAsStateWithLifecycle()
    val p = paletteOf(s.palette)
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = onColor(p.accent), background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.dim,
        secondary = p.good, error = p.bad,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = onColor(p.accent), background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.dim,
        secondary = p.good, error = p.bad,
    )
    val base = Typography()
    val pixel = Typography(
        titleLarge = px(18), titleMedium = px(13), titleSmall = px(11),
        bodyLarge = px(11), bodyMedium = px(10), bodySmall = px(8),
        labelLarge = px(10), labelMedium = px(9), labelSmall = px(7),
        headlineSmall = px(16), headlineMedium = px(20), headlineLarge = px(26),
        displaySmall = base.displaySmall.copy(fontFamily = PixelFont),
    )
    CompositionLocalProvider(LocalPalette provides p, LocalBitSettings provides s) {
        MaterialTheme(colorScheme = scheme, typography = pixel) {
            Box(Modifier.fillMaxSize().background(p.bg).crt(s.crt && p.dark, p.accent)) { content() }
        }
    }
}

/** Scanlines + soft phosphor vignette drawn over everything when the CRT theme is on. */
fun Modifier.crt(on: Boolean, glow: Color): Modifier = if (!on) this else drawWithContent {
    drawContent()
    val line = Color.Black.copy(alpha = 0.16f)
    var y = 0f
    while (y < size.height) {
        drawRect(line, Offset(0f, y), Size(size.width, 2f))
        y += 5f
    }
    drawRect(
        Brush.radialGradient(
            listOf(glow.copy(alpha = 0.05f), Color.Transparent, Color.Black.copy(alpha = 0.35f)),
            center = center, radius = size.maxDimension * 0.75f,
        ),
    )
}
