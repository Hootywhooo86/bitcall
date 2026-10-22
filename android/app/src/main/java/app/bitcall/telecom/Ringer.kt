package app.bitcall.telecom

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
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

    /**
     * Whether the phone's ringer switch silences this ring. A favorite ringing through DND ignores
     * it, because DND itself can flip the ringer to silent; outside DND your switch always wins.
     */
    fun ringerSilences(plan: Int, ringerMode: Int): Boolean =
        plan != ALARM && ringerMode != AudioManager.RINGER_MODE_NORMAL

    fun filterName(filter: Int) = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "on (priority only)"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "on (alarms only)"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "on (total silence)"
        else -> "unknown"
    }
}

/** Bitcall rings itself (IN_CALL_SERVICE_RINGING) so it can play the chiptune ringtone. */
object Ringer {
    private var track: AudioTrack? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val main = Handler(Looper.getMainLooper())

    fun start(context: Context, decision: CallPolicy.Decision, starred: Boolean) {
        stop()
        if (decision.silence || decision.reject) {
            CallTrace.note("not ringing: ${decision.reason ?: "silenced by bitcall"}")
            return
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val filter = nm.currentInterruptionFilter
        val plan = RingPlan.decide(filter, starred, Settings.now.dndAllowFavorites)
        CallTrace.note("phone DND ${RingPlan.filterName(filter)} · favorite=$starred · let favorites through=${Settings.now.dndAllowFavorites}")
        if (plan == RingPlan.SILENT) {
            CallTrace.note("not ringing: phone DND and not a ring-through favorite")
            return
        }
        val usage = if (plan == RingPlan.ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        val ringer = audio.ringerMode
        val stream = if (plan == RingPlan.ALARM) AudioManager.STREAM_ALARM else AudioManager.STREAM_RING
        CallTrace.note(
            "ring on ${if (plan == RingPlan.ALARM) "ALARM sound" else "ringtone sound"} · ringer mode ${ringerName(ringer)} · " +
                "volume ${audio.getStreamVolume(stream)}/${audio.getStreamMaxVolume(stream)}",
        )
        play(context, usage, vibrate = plan == RingPlan.ALARM || ringer != AudioManager.RINGER_MODE_SILENT,
            sound = !RingPlan.ringerSilences(plan, ringer))
    }

    /** Settings → Ring-through check: rings like a ★ favorite would right now, for a few seconds. */
    fun test(context: Context, seconds: Int = 5): String {
        stop()
        val nm = context.getSystemService(NotificationManager::class.java)
        val plan = RingPlan.decide(nm.currentInterruptionFilter, starred = true, allowFavorites = Settings.now.dndAllowFavorites)
        if (plan == RingPlan.SILENT) return "Silent: \"Let ★ favorites through\" is off."
        val usage = if (plan == RingPlan.ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        val audio = context.getSystemService(AudioManager::class.java)
        val result = play(context, usage, vibrate = true, sound = !RingPlan.ringerSilences(plan, audio.ringerMode))
        main.postDelayed({ stop() }, seconds * 1000L)
        return result
    }

    private fun play(context: Context, usage: Int, vibrate: Boolean, sound: Boolean): String {
        val attrs = AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        if (vibrate) {
            vibrator = context.getSystemService(Vibrator::class.java)?.also {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 1000), 0), attrs)
            }
        }
        if (!sound) {
            CallTrace.note("vibrate only: phone ringer is set to ${ringerName(context.getSystemService(AudioManager::class.java).ringerMode)}")
            return "Vibrate only: your phone's ringer switch is on vibrate/silent."
        }
        if (!Settings.now.chipRingtone) {
            // Your phone's own ringtone, played on the chosen sound (alarm through DND).
            val r = runCatching {
                RingtoneManager.getRingtone(context, RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE))
                    ?.apply { audioAttributes = attrs; isLooping = true; play() }
            }.getOrNull()
            if (r != null) {
                ringtone = r
                CallTrace.note("playing your phone's ringtone")
                return "Playing your phone's ringtone."
            }
            CallTrace.note("phone ringtone couldn't play, using the chiptune instead")
        }
        track = runCatching { ChipSound.ringtoneTrack(usage).also { it.play() } }.getOrNull()
        CallTrace.note(if (track != null) "playing the chiptune ringtone" else "no sound could play")
        return if (track != null) "Playing the chiptune ringtone." else "No sound could play."
    }

    private fun ringerName(m: Int) = when (m) {
        AudioManager.RINGER_MODE_NORMAL -> "sound"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "silent"
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        track?.runCatching { stop(); release() }
        track = null
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }
}
