package app.bitcall

import app.bitcall.ui.BITTER_FRAMES
import app.bitcall.ui.BitterMood
import app.bitcall.ui.bitterSequence
import app.bitcall.ui.crtOffScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimTest {
    @Test fun crtSwitchOffSquashesToALineThenADotThenBlack() {
        assertEquals(1f to 1f, crtOffScale(0f))
        val (lx, ly) = crtOffScale(0.5f)
        assertEquals(1f, lx, 0.01f); assertTrue(ly < 0.02f)          // a full-width bright line
        val (dx, dy) = crtOffScale(0.85f)
        assertTrue(dx < 0.2f && dy < 0.02f)                         // shrinking to a dot
        assertEquals(0f to 0f, crtOffScale(1f))                     // gone
    }

    @Test fun bitterHasEveryFrameHisMoodsUse() {
        BitterMood.entries.forEach { m -> bitterSequence(m).forEach { (f, _) -> assertTrue(f in BITTER_FRAMES) } }
        BITTER_FRAMES.values.forEach { rows -> assertEquals(32, rows.size); rows.forEach { assertEquals(32, it.length) } }
    }
}
