package app.bitcall

import androidx.compose.ui.graphics.Color
import app.bitcall.ui.ColorSlot
import app.bitcall.ui.LOGO
import app.bitcall.ui.PALETTES
import app.bitcall.ui.cardScale
import app.bitcall.ui.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun overridesReplaceOnlyTheirSlot() {
        val base = PALETTES[0]
        val red = 0xFFFF0000
        val p = base.with(mapOf(ColorSlot.CALL.name to red))
        assertEquals(Color(red), p.call)
        assertEquals(base.good, p.good)
        assertEquals(base.bg, p.bg)
    }

    @Test fun everySlotCanBeOverridden() {
        val blue = 0xFF0000FF
        val p = PALETTES[0].with(ColorSlot.entries.associate { it.name to blue })
        ColorSlot.entries.forEach { assertEquals(it.name, Color(blue), it.get(p)) }
    }

    @Test fun finerSlotsFollowThePalette() {
        val p = PALETTES[1]
        assertEquals(p.good, p.call)
        assertEquals(p.bad, p.hangup)
        assertEquals(p.accent, p.digits)
    }

    @Test fun cardSizesGrow() {
        assertTrue(cardScale("COMPACT") < cardScale("NORMAL"))
        assertTrue(cardScale("NORMAL") < cardScale("BIG"))
        assertTrue(cardScale("BIG") < cardScale("HUGE"))
    }

    @Test fun logoIsA32x32MirroredSprite() {
        assertEquals(32, LOGO.size)
        LOGO.forEach { assertEquals(32, it.length) }
        // The handset silhouette is symmetric across the top-right to bottom-left diagonal.
        val solid = { x: Int, y: Int -> LOGO[y][x] != '.' }
        for (y in 0 until 32) for (x in 0 until 32) assertEquals("($x,$y)", solid(x, y), solid(31 - y, 31 - x))
    }
}
