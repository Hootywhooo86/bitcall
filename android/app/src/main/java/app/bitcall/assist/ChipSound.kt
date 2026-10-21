package app.bitcall.assist

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Tiny square-wave synth for 8-bit keypad blips and the chiptune ringtone. */
object ChipSound {
    private const val RATE = 22_050

    // Pentatonic-ish ladder so any key sequence sounds musical.
    private val keyFreqs = doubleArrayOf(523.3, 587.3, 659.3, 784.0, 880.0, 1046.5, 1174.7, 1318.5, 1568.0, 1760.0, 440.0, 2093.0)
    private val blips = HashMap<Int, AudioTrack>()

    private fun square(freq: Double, ms: Int, volume: Double = 0.25, duty: Double = 0.5): ShortArray {
        val n = RATE * ms / 1000
        val period = RATE / freq
        val fadeOut = n / 5
        return ShortArray(n) { i ->
            val on = (i % period) < period * duty
            val env = if (i > n - fadeOut) (n - i).toDouble() / fadeOut else 1.0
            // Quantise to 4-bit steps for an authentic crunchy NES-style edge.
            val v = ((if (on) 1.0 else -1.0) * volume * env * 8).toInt() / 8.0
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun track(pcm: ShortArray, usage: Int, loop: Boolean = false): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        if (loop) t.setLoopPoints(0, pcm.size, -1)
        return t
    }

    fun keyBlip(index: Int) {
        runCatching {
            val t = blips.getOrPut(index) { track(square(keyFreqs[index % keyFreqs.size], 70), AudioAttributes.USAGE_ASSISTANCE_SONIFICATION) }
            t.stop(); t.reloadStaticData(); t.play()
        }
    }

    /** Short "1-UP" jingle, used by Hold for Me when a human picks up. */
    fun powerUp() {
        runCatching {
            val notes = listOf(659.3, 784.0, 1318.5, 1046.5, 1174.7, 1568.0)
            val pcm = notes.flatMap { square(it, 90, 0.35).asList() }.toShortArray()
            val t = track(pcm, AudioAttributes.USAGE_NOTIFICATION_EVENT)
            t.play()
            Thread { Thread.sleep(800); t.release() }.start()
        }
    }

    fun ringtoneTrack(): AudioTrack {
        // A looping two-bar chiptune phrase (Hz, ms); 0 = rest.
        val melody = listOf(
            784.0 to 120, 0.0 to 30, 784.0 to 120, 0.0 to 30, 1046.5 to 240, 0.0 to 60,
            988.0 to 120, 880.0 to 120, 784.0 to 240, 0.0 to 60,
            659.3 to 120, 784.0 to 120, 880.0 to 120, 1046.5 to 360, 0.0 to 700,
        )
        val pcm = melody.flatMap { (f, ms) ->
            if (f == 0.0) ShortArray(RATE * ms / 1000).asList() else square(f, ms, 0.4, 0.25).asList()
        }.toShortArray()
        return track(pcm, AudioAttributes.USAGE_NOTIFICATION_RINGTONE, loop = true)
    }
}
