package app.bitcall

import android.provider.CallLog
import app.bitcall.ai.BitAi
import app.bitcall.ai.RuleParser
import app.bitcall.data.BitSettings
import app.bitcall.data.CallEntry
import app.bitcall.data.Contact
import app.bitcall.data.Phone
import app.bitcall.data.Settings
import app.bitcall.telecom.HoldDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val contacts = listOf(
        Contact(1, "Mom", listOf("5551234567"), true, null),
        Contact(2, "Grandma Rose", listOf("5559990000"), true, null),
        Contact(3, "Dr. Patel Dentist", listOf("5550001111"), false, null),
    )

    @Test fun t9MatchesNamesAndNumbers() {
        assertTrue(Phone.t9Matches("666", "Mom", "5551234567"))       // M-O-M
        assertTrue(Phone.t9Matches("7673", "Grandma Rose", "555"))     // R-O-S-E word start
        assertTrue(Phone.t9Matches("1234", "Nobody", "555-123-4567"))  // number digits
        assertFalse(Phone.t9Matches("999", "Mom", "5551234567"))
    }

    @Test fun keyNormalisesFormats() {
        assertEquals(Phone.key("+1 (555) 123-4567"), Phone.key("5551234567"))
    }

    @Test fun parsesPlainLanguageQuestions() {
        val q = RuleParser.parse("who called me last week?", contacts)
        assertEquals(14, q.sinceDays); assertEquals(7, q.untilDays)
        assertTrue(CallLog.Calls.INCOMING_TYPE in q.types)

        val missed = RuleParser.parse("missed calls from grandma today", contacts)
        assertEquals("Grandma", missed.name)
        assertEquals(1, missed.sinceDays)
        assertTrue(CallLog.Calls.MISSED_TYPE in missed.types)

        val call = RuleParser.parse("call mom's cell", contacts)
        assertTrue(call.call); assertEquals("Mom", call.name)
    }

    @Test fun spamScoreFlagsPatterns() {
        val n = "+18885550199"
        val history = List(4) { CallEntry(it.toLong(), n, null, CallLog.Calls.MISSED_TYPE, 0, 0) }
        assertTrue(BitAi.spamScore(n, false, history) >= 50)
        assertEquals(0, BitAi.spamScore(n, true, history))
        assertTrue(BitAi.spamScore("5551112222", false, emptyList()) < 70)
    }

    @Test fun dndWindowWrapsMidnight() {
        val s = BitSettings(dndEnabled = true, dndStart = 22 * 60, dndEnd = 7 * 60)
        assertTrue(Settings.inDndWindow(23 * 60, s))
        assertTrue(Settings.inDndWindow(3 * 60, s))
        assertFalse(Settings.inDndWindow(12 * 60, s))
    }

    @Test fun holdDetectorIgnoresHoldMusic() {
        assertFalse(HoldDetector.isHuman("your call is important to us please continue to hold"))
        assertTrue(HoldDetector.isHuman("hi thanks for holding my name is Sam"))
    }

    @Test fun nudgesFavoritesNotCalledRecently() {
        val old = System.currentTimeMillis() - 22L * 86_400_000
        val calls = listOf(CallEntry(1, "5559990000", "Grandma Rose", CallLog.Calls.OUTGOING_TYPE, old, 120))
        assertEquals(listOf("You haven't called Grandma in 3 weeks."), BitAi.nudges(contacts, calls))
    }
}
