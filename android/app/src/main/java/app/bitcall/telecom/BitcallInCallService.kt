package app.bitcall.telecom

import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
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
        // Outgoing calls open the call screen directly. Incoming ones ring through the full-screen
        // notification; when the phone is locked or the screen is off we also open the call screen
        // ourselves, so it wakes the screen and shows over the lock screen with ANSWER / DECLINE.
        if (call.state != Call.STATE_RINGING || phoneAsleepOrLocked()) {
            runCatching { startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun phoneAsleepOrLocked(): Boolean =
        !getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked

    override fun onCallRemoved(call: Call) = CallManager.remove(call)

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState) = CallManager.onAudio(audioState)

    override fun onSilenceRinger() {
        CallTrace.note("Android asked bitcall to silence the ringer (volume key or system)")
        Ringer.stop()
    }
}
