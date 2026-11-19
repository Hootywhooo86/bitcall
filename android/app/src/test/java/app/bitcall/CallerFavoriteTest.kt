package app.bitcall

import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import app.bitcall.data.Contact
import app.bitcall.data.ContactsRepo
import app.bitcall.telecom.RingPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerFavoriteTest {
    private fun c(id: Long, name: String, starred: Boolean) = Contact(id, name, listOf("+15551234567"), starred, null)

    // A number saved in two contacts (a duplicate, a SIM copy) must still count as a favorite
    // when the starred copy isn't the first one Android returns.
    @Test fun starredCopyWinsWhicheverOrderAndroidReturns() {
        val son = ContactsRepo.pickForCaller(listOf(c(1, "Son (old)", false), c(2, "Son", true)))!!
        assertTrue(son.starred)
        assertEquals("Son", son.name)
        assertTrue(ContactsRepo.pickForCaller(listOf(c(2, "Son", true), c(1, "Son (old)", false)))!!.starred)
    }

    @Test fun unstarredStaysUnstarred() {
        assertFalse(ContactsRepo.pickForCaller(listOf(c(1, "Plumber", false)))!!.starred)
        assertNull(ContactsRepo.pickForCaller(emptyList()))
    }

    // DND can flip the ringer to silent by itself, so a favorite ringing through DND ignores it.
    @Test fun favoritesThroughDndIgnoreTheRingerSwitch() {
        for (m in listOf(RINGER_MODE_NORMAL, RINGER_MODE_VIBRATE, RINGER_MODE_SILENT)) {
            assertFalse(RingPlan.ringerSilences(RingPlan.ALARM, m))
        }
    }

    @Test fun outsideDndTheRingerSwitchWins() {
        assertFalse(RingPlan.ringerSilences(RingPlan.RINGTONE, RINGER_MODE_NORMAL))
        assertTrue(RingPlan.ringerSilences(RingPlan.RINGTONE, RINGER_MODE_VIBRATE))
        assertTrue(RingPlan.ringerSilences(RingPlan.RINGTONE, RINGER_MODE_SILENT))
    }
}
