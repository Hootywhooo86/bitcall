package app.bitcall

import android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_NONE
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import app.bitcall.telecom.RingPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class RingPlanTest {
    @Test fun noDndRingsNormally() {
        assertEquals(RingPlan.RINGTONE, RingPlan.decide(INTERRUPTION_FILTER_ALL, starred = false, allowFavorites = true))
        assertEquals(RingPlan.RINGTONE, RingPlan.decide(INTERRUPTION_FILTER_ALL, starred = true, allowFavorites = true))
    }

    // Phone DND stays on (e.g. at work); favorites must still be heard. DND mutes the ringtone
    // sound itself, so they ring on the alarm sound, which DND lets through.
    @Test fun favoritesRingThroughEveryDndLevelAsAnAlarm() {
        for (f in listOf(INTERRUPTION_FILTER_PRIORITY, INTERRUPTION_FILTER_ALARMS, INTERRUPTION_FILTER_NONE)) {
            assertEquals(RingPlan.ALARM, RingPlan.decide(f, starred = true, allowFavorites = true))
        }
    }

    @Test fun everyoneElseFollowsPhoneDnd() {
        assertEquals(RingPlan.SILENT, RingPlan.decide(INTERRUPTION_FILTER_PRIORITY, starred = false, allowFavorites = true))
        assertEquals(RingPlan.SILENT, RingPlan.decide(INTERRUPTION_FILTER_NONE, starred = false, allowFavorites = true))
        assertEquals(RingPlan.SILENT, RingPlan.decide(INTERRUPTION_FILTER_ALARMS, starred = false, allowFavorites = true))
    }

    @Test fun withTheSwitchOffFavoritesFollowPhoneDndToo() {
        assertEquals(RingPlan.RINGTONE, RingPlan.decide(INTERRUPTION_FILTER_PRIORITY, starred = true, allowFavorites = false))
        assertEquals(RingPlan.SILENT, RingPlan.decide(INTERRUPTION_FILTER_NONE, starred = true, allowFavorites = false))
    }
}
