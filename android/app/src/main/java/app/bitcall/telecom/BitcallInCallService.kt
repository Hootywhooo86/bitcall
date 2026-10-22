package app.bitcall.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import app.bitcall.ui.InCallActivity

/** Bound by Telecom while Bitcall is the default phone app; owns every call on the device. */
class BitcallInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.add(this, call)
        // Incoming calls surface through the full-screen notification; outgoing open the call screen directly.
        if (call.state != Call.STATE_RINGING) {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) = CallManager.remove(call)

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState) = CallManager.onAudio(audioState)

    override fun onSilenceRinger() {
        CallTrace.note("Android asked bitcall to silence the ringer (volume key or system)")
        Ringer.stop()
    }
}
