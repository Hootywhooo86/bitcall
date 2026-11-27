package app.bitcall.telecom

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import app.bitcall.ui.InCallActivity

/** Bound by Telecom while Bitcall is the default phone app; owns every call on the device. */
class BitcallInCallService : InCallService() {
    // Android 14+ describes audio outputs as CallEndpoints; older versions use CallAudioState.
    private var endpoints: List<CallEndpoint> = emptyList()
    private var endpoint: CallEndpoint? = null
    private var lastAudioState: CallAudioState? = null

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

    override fun onSilenceRinger() {
        CallTrace.note("Android asked bitcall to silence the ringer (volume key or system)")
        Ringer.stop()
    }

    // --- audio routing -----------------------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        lastAudioState = audioState
        CallManager.onMuted(audioState.isMuted)
        // Older Android, or a phone that never sends the Android 14 endpoint list: use this state.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || endpoints.isEmpty()) publishLegacy(audioState)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(available: List<CallEndpoint>) {
        endpoints = available
        publishEndpoints()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        endpoint = callEndpoint
        publishEndpoints()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMuteStateChanged(isMuted: Boolean) = CallManager.onMuted(isMuted)

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun publishEndpoints() {
        fun route(e: CallEndpoint) = AudioRoute(
            key = e.identifier.toString(),
            kind = kindOf(e),
            name = e.endpointName.toString(),
            isWatch = e.endpointType == CallEndpoint.TYPE_BLUETOOTH && AudioPolicy.isWatch(this, e.endpointName.toString()),
        )
        CallManager.onRoutes(endpoints.map(::route), endpoint?.let(::route))
    }

    @SuppressLint("MissingPermission") // device names need BLUETOOTH_CONNECT; we fall back to "Bluetooth"
    @Suppress("DEPRECATION")
    private fun publishLegacy(s: CallAudioState) {
        val routes = buildList {
            if (s.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) add(AudioRoute("earpiece", RouteKind.EARPIECE, "Phone"))
            if (s.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(AudioRoute("wired", RouteKind.WIRED, "Headphones"))
            if (s.supportedRouteMask and CallAudioState.ROUTE_SPEAKER != 0) add(AudioRoute("speaker", RouteKind.SPEAKER, "Speaker"))
            s.supportedBluetoothDevices.forEach { d ->
                val name = runCatching { d.name }.getOrNull() ?: "Bluetooth"
                val btClass = runCatching { d.bluetoothClass }.getOrNull()
                add(AudioRoute(d.address, RouteKind.BLUETOOTH, name, AudioPolicy.isWatch(this@BitcallInCallService, name, btClass)))
            }
        }
        val current = when (s.route) {
            CallAudioState.ROUTE_SPEAKER -> routes.firstOrNull { it.kind == RouteKind.SPEAKER }
            CallAudioState.ROUTE_WIRED_HEADSET -> routes.firstOrNull { it.kind == RouteKind.WIRED }
            CallAudioState.ROUTE_BLUETOOTH -> routes.firstOrNull { it.key == s.activeBluetoothDevice?.address }
                ?: routes.firstOrNull { it.kind == RouteKind.BLUETOOTH }
            else -> routes.firstOrNull { it.kind == RouteKind.EARPIECE }
        }
        CallManager.onRoutes(routes, current)
    }

    /** Sends call audio to [route]: earpiece, speaker, wired headphones or a specific Bluetooth device. */
    @Suppress("DEPRECATION")
    fun selectRoute(route: AudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Ids can be re-issued between refreshes, so fall back to matching kind + name.
            val target = endpoints.firstOrNull { it.identifier.toString() == route.key }
                ?: endpoints.firstOrNull { it.endpointName.toString() == route.name && kindOf(it) == route.kind }
            if (target != null) {
                requestCallEndpointChange(target, mainExecutor, object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) {}
                    override fun onError(error: CallEndpointException) { legacySelect(route) }
                })
                return
            }
        }
        legacySelect(route)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun kindOf(e: CallEndpoint) = when (e.endpointType) {
        CallEndpoint.TYPE_EARPIECE -> RouteKind.EARPIECE
        CallEndpoint.TYPE_SPEAKER -> RouteKind.SPEAKER
        CallEndpoint.TYPE_WIRED_HEADSET -> RouteKind.WIRED
        CallEndpoint.TYPE_BLUETOOTH -> RouteKind.BLUETOOTH
        else -> RouteKind.OTHER
    }

    @SuppressLint("MissingPermission") // name lookup is wrapped in runCatching; address match needs no permission
    @Suppress("DEPRECATION")
    private fun legacySelect(route: AudioRoute) {
        when (route.kind) {
            RouteKind.BLUETOOTH -> lastAudioState?.supportedBluetoothDevices
                ?.firstOrNull { it.address == route.key || runCatching { it.name }.getOrNull() == route.name }
                ?.let { requestBluetoothAudio(it) } ?: setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
            RouteKind.SPEAKER -> setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            RouteKind.WIRED -> setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET)
            else -> setAudioRoute(CallAudioState.ROUTE_EARPIECE)
        }
    }
}
