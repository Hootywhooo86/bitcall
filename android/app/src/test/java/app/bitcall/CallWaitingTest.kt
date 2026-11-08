package app.bitcall

import android.telecom.Call
import app.bitcall.telecom.CallManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallWaitingTest {
    @Test fun aSecondRingingCallTakesTheScreen() {
        // On a call (ACTIVE) when a second one rings: the ringing one gets the screen and the buttons.
        assertEquals(1, CallManager.focusIndex(listOf(Call.STATE_ACTIVE, Call.STATE_RINGING)))
        assertEquals(0, CallManager.focusIndex(listOf(Call.STATE_RINGING, Call.STATE_ACTIVE)))
    }

    @Test fun afterAnsweringTheLiveCallBeatsTheHeldOne() {
        assertEquals(1, CallManager.focusIndex(listOf(Call.STATE_HOLDING, Call.STATE_ACTIVE)))
    }

    @Test fun endedCallsNeverTakeTheScreen() {
        assertEquals(1, CallManager.focusIndex(listOf(Call.STATE_DISCONNECTED, Call.STATE_HOLDING)))
        assertNull(CallManager.focusIndex(listOf(Call.STATE_DISCONNECTED)))
        assertNull(CallManager.focusIndex(emptyList()))
    }

    @Test fun dialingBeatsHolding() {
        assertEquals(0, CallManager.focusIndex(listOf(Call.STATE_DIALING, Call.STATE_HOLDING)))
    }
}
