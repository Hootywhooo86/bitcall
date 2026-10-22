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

/**
 * How an incoming call sounds under the phone's Do Not Disturb. Phone DND mutes the ringtone
 * sound itself (and Android 15+ won't let apps switch the user's DND off), so ★ favorites ring
 * on the alarm sound, which DND lets through. Everyone else follows phone DND.
 */
object RingPlan {
    const val SILENT = 0
    const val RINGTONE = 1
    const val ALARM = 2

    fun decide(filter: Int, starred: Boolean, allowFavorites: Boolean): Int = when {
        filter == NotificationManager.INTERRUPTION_FILTER_ALL || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> RINGTONE
        starred && allowFavorites -> ALARM
        filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY && starred -> RINGTONE
        else -> SILENT
    }
}

/** Bitcall rings itself (IN_CALL_SERVICE_RINGING) so it can play the chiptune ringtone. */
object Ringer {
    private var track: AudioTrack? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    fun start(context: Context, decision: CallPolicy.Decision, starred: Boolean) {
        stop()
        if (decision.silence || decision.reject) return
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val plan = RingPlan.decide(nm.currentInterruptionFilter, starred, Settings.now.dndAllowFavorites)
        if (plan == RingPlan.SILENT) return
        val usage = if (plan == RingPlan.ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        val attrs = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

        // The phone's own silent / vibrate switch still wins, even for favorites.
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
        track?.runCatching { stop(); release() }
        track = null
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }
}
