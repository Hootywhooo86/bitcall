package app.bitcall

import android.app.NotificationManager
import android.provider.CallLog
import app.bitcall.data.CallEntry
import app.bitcall.telecom.CallPolicy
import app.bitcall.telecom.RingPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DndRulesTest {
    private fun through(who: String, starred: Boolean = false, contact: Boolean = false, enemy: Boolean = false, repeat: Boolean = false, allow: Boolean = true, allowRepeat: Boolean = true) =
        CallPolicy.breaksThrough(allow, who, starred, contact, enemy, repeat, allowRepeat)

    @Test fun favoritesModeLetsOnlyStarredThrough() {
        assertTrue(through("FAVORITES", starred = true, contact = true))
        assertFalse(through("FAVORITES", contact = true))
        assertFalse(through("FAVORITES"))
    }

    @Test fun contactsAndEveryoneModes() {
        assertTrue(through("CONTACTS", contact = true))
        assertFalse(through("CONTACTS"))
        assertTrue(through("EVERYONE"))
    }

    @Test fun spamNeverGetsThrough() {
        assertFalse(through("EVERYONE", enemy = true))
        assertFalse(through("FAVORITES", enemy = true, repeat = true))
    }

    @Test fun repeatCallersGetThroughOnTheirOwnSwitch() {
        assertTrue(through("FAVORITES", repeat = true))
        assertFalse(through("FAVORITES", repeat = true, allowRepeat = false))
        assertTrue(through("FAVORITES", repeat = true, allow = false)) // VIP switch off, repeat still works
        assertFalse(through("EVERYONE", allow = false))
    }

    @Test fun repeatMeansCalledInTheLastThreeMinutes() {
        val now = 10_000_000L
        val missed = { ago: Long -> CallEntry(1, "+1 (780) 555-0101", null, CallLog.Calls.MISSED_TYPE, now - ago, 0) }
        assertTrue(CallPolicy.isRepeat("7805550101", listOf(missed(60_000)), now))
        assertFalse(CallPolicy.isRepeat("7805550101", listOf(missed(5 * 60_000)), now))
        assertFalse(CallPolicy.isRepeat("7805550199", listOf(missed(60_000)), now))
        // Your own outgoing call doesn't make them a repeat caller.
        val outgoing = CallEntry(2, "7805550101", null, CallLog.Calls.OUTGOING_TYPE, now - 60_000, 0)
        assertFalse(CallPolicy.isRepeat("7805550101", listOf(outgoing), now))
    }

    @Test fun throughCallsRingUnderEveryDndLevel() {
        listOf(NotificationManager.INTERRUPTION_FILTER_PRIORITY, NotificationManager.INTERRUPTION_FILTER_ALARMS, NotificationManager.INTERRUPTION_FILTER_NONE)
            .forEach { f ->
                assertTrue(RingPlan.willRing(f, breaksDnd = true))
                assertFalse(RingPlan.willRing(f, breaksDnd = false))
            }
        assertTrue(RingPlan.willRing(NotificationManager.INTERRUPTION_FILTER_ALL, breaksDnd = false))
    }
}
