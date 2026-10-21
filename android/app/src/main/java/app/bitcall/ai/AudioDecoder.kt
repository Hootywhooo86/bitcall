package app.bitcall.ai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodes any audio Android can play (voicemail AMR, call recordings in AAC…) into Whisper's 16 kHz mono floats. */
object AudioDecoder {
    private const val TARGET = 16_000
    private const val MAX_SECONDS = 10 * 60

    fun decode(context: Context, uri: Uri): FloatArray? = runCatching {
        val ex = MediaExtractor().apply { setDataSource(context, uri, null) }
        val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()

        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mono = ArrayList<Float>(TARGET * 60)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else if (o >= 0) {
                val shorts = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                while (shorts.remaining() >= channels) {
                    var sum = 0f
                    repeat(channels) { sum += shorts.get() }
                    mono.add(sum / channels / 32768f)
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                if (mono.size > rate * MAX_SECONDS) break
            }
        }
        codec.stop(); codec.release(); ex.release()
        resample(mono, rate)
    }.getOrNull()

    /** Linear resampling; plenty for speech. */
    private fun resample(src: List<Float>, rate: Int): FloatArray {
        if (rate == TARGET) return src.toFloatArray()
        val n = (src.size.toLong() * TARGET / rate).toInt()
        val step = rate.toDouble() / TARGET
        return FloatArray(n) { i ->
            val x = i * step
            val a = x.toInt().coerceAtMost(src.lastIndex)
            val b = (a + 1).coerceAtMost(src.lastIndex)
            val t = (x - a).toFloat()
            src[a] * (1 - t) + src[b] * t
        }
    }
}
