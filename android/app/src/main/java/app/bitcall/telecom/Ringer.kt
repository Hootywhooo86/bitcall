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

    fun start(context: Context, decision: CallPolicy.Decision, starred: Boolean) {
        stop()
        if (decision.silence || decision.reject) return
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val filter = nm.currentInterruptionFilter
        if (filter == NotificationManager.INTERRUPTION_FILTER_NONE || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS) return
        if (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY && !starred) return

        if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            vibrator = context.getSystemService(Vibrator::class.java)?.also {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 1000), 0),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            }
        }
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (Settings.now.chipRingtone) {
            track = runCatching { ChipSound.ringtoneTrack().also { it.play() } }.getOrNull()
        } else {
            ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE))
                ?.apply {
                    audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
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
