package app.bitcall.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection

/**
 * Android's official call-screening hook. Runs before the phone rings: rejects flagged spam,
 * silences unknown callers / quiet-hours calls. The decision is re-used to show the enemy badge.
 */
class BitcallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val verificationFailed = details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
        val d = CallPolicy.decide(this, details.handle?.schemeSpecificPart, verificationFailed)
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(d.reject)
                .setRejectCall(d.reject)
                .setSkipCallLog(false)
                .setSkipNotification(d.reject)
                .setSilenceCall(d.silence)
                .build(),
        )
    }
}
