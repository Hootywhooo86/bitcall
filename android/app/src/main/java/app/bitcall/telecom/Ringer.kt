package app.bitcall.telecom

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import app.bitcall.assist.ChipSound
import app.bitcall.data.Settings

/** Bitcall rings itself (IN_CALL_SERVICE_RINGING) so it can play the chiptune ringtone. */
object Ringer {
    private var track: AudioTrack? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var nm: NotificationManager? = null
    /** The phone's DND filter we lifted for a ★ favorite, put back when the ringing stops. */
    private var liftedFilter: Int? = null

    /**
     * Phone DND is on and the caller is a ★ favorite with "Let ★ favorites through" on.
     * Lifts DND for the ring (needs Do Not Disturb access) so the ring and the incoming-call
     * screen both get through; true when it did.
     */
    fun liftDndForFavorite(context: Context, starred: Boolean): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!starred || !Settings.now.dndAllowFavorites) return false
        val filter = nm.currentInterruptionFilter
        if (filter == NotificationManager.INTERRUPTION_FILTER_ALL || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        if (liftedFilter == null) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }.onSuccess {
                liftedFilter = filter
                this.nm = nm
            }
        }
        return liftedFilter != null
    }

    fun start(context: Context, decision: CallPolicy.Decision, starred: Boolean) {
        stopSound()
        if (decision.silence || decision.reject) { restoreDnd(); return }
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val ringThrough = starred && Settings.now.dndAllowFavorites
        val lifted = liftDndForFavorite(context, starred)
        val filter = nm.currentInterruptionFilter
        val dndOn = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        if (dndOn && !ringThrough) {
            if (filter == NotificationManager.INTERRUPTION_FILTER_NONE || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS) return
            if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY && !starred) return
        }
        // Favorites ring through phone DND. Without DND access the ring goes out as an alarm,
        // which DND lets through by default; the phone's own silent/vibrate switch still wins.
        val asAlarm = dndOn && ringThrough && !lifted
        val usage = if (asAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        val attrs = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

        if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            vibrator = context.getSystemService(Vibrator::class.java)?.also {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 1000), 0), attrs)
            }
        }
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (Settings.now.chipRingtone) {
            track = runCatching { ChipSound.ringtoneTrack(usage).also { it.play() } }.getOrNull()
        } else {
            ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE))
                ?.apply {
                    audioAttributes = attrs
                    isLooping = true
                    play()
                }
        }
    }

    fun stop() {
        stopSound()
        restoreDnd()
    }

    private fun stopSound() {
        track?.runCatching { stop(); release() }
        track = null
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun restoreDnd() {
        val f = liftedFilter ?: return
        liftedFilter = null
        // Only put DND back if nobody changed it while the phone rang.
        nm?.runCatching { if (currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) setInterruptionFilter(f) }
        nm = null
    }
}
