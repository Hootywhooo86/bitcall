package app.bitcall.telecom

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * When Bitcall moves call audio on its own. Devices are matched by kind + name, never by the
 * endpoint id: Android can hand out new ids every time it refreshes the list, which used to make
 * a device look "just connected" again and pull audio back after the user had switched away.
 */
object AudioPolicy {
    fun identity(r: AudioRoute) = "${r.kind}:${r.name}"

    private fun autoOk(r: AudioRoute, neverAuto: Set<String>) = r.isHeadset && !r.isWatch && r.name !in neverAuto

    /**
     * The route to switch to automatically, or null to leave audio where it is.
     * 1. Headphones that just connected (not seen before this call) take the call.
     * 2. If Android put the call on a watch or a "never auto" device and the user didn't pick it,
     *    move it to headphones, else back to the phone.
     * Anything the user picked by hand is left alone.
     */
    fun autoMove(
        known: Set<String>,
        routes: List<AudioRoute>,
        current: AudioRoute?,
        userPickedCurrent: Boolean,
        autoHeadphones: Boolean,
        neverAuto: Set<String>,
    ): AudioRoute? {
        if (autoHeadphones) {
            routes.lastOrNull { autoOk(it, neverAuto) && identity(it) !in known }
                ?.takeIf { it.key != current?.key }
                ?.let { return it }
        }
        if (current != null && !userPickedCurrent && (current.isWatch || current.name in neverAuto)) {
            return routes.lastOrNull { autoOk(it, neverAuto) }
                ?: routes.firstOrNull { it.kind == RouteKind.EARPIECE }
                ?: routes.firstOrNull { it.kind == RouteKind.SPEAKER }
        }
        return null
    }

    /** Smartwatches show up as Bluetooth call audio; spot them by Bluetooth class or by name. */
    private val WATCH_NAME = Regex("watch|fitbit|garmin|amazfit|huawei band|mi band|galaxy fit|wear ?os|forerunner|fenix|venu|versa|sense|charge [0-9]", RegexOption.IGNORE_CASE)

    fun isWatchName(name: String) = WATCH_NAME.containsMatchIn(name)

    @Suppress("MissingPermission") // checked just below
    fun isWatch(context: Context, name: String, btClass: BluetoothClass? = null): Boolean {
        if (btClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE) return true
        if (isWatchName(name)) return true
        // Android 14+ endpoints only carry a name: look the bonded device up for its class.
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
                ?.any { it.name == name && it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE } == true
        }.getOrDefault(false)
    }
}
