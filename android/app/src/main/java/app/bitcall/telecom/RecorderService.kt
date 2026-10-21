package app.bitcall.telecom

import android.app.Notification
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.bitcall.R
import app.bitcall.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records calls into the phone's Recordings/Bitcall folder.
 *
 * Android blocks third-party apps from the call audio stream, so this records the microphone with
 * the call on speaker. Every recording opens with a spoken notice, since Canadian law requires the
 * consent of at least one party and announcing it keeps everyone informed.
 */
class RecorderService : Service() {
    companion object {
        private const val START = "start"
        private const val STOP = "stop"
        const val NOTICE = "This call is being recorded."

        fun start(context: Context) = runCatching {
            context.startForegroundService(Intent(context, RecorderService::class.java).setAction(START))
        }
        fun stop(context: Context) = runCatching {
            context.startService(Intent(context, RecorderService::class.java).setAction(STOP))
        }
    }

    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> if (recorder == null && tts == null) begin()
            STOP -> { finishRecording(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        val n = Notification.Builder(this, CallNotifications.CH_RECORDER)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("● REC")
            .setContentText("Recording this call")
            .setContentIntent(CallNotifications.openCallScreen(this))
            .build()
        startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (Settings.now.recordOnSpeaker) CallManager.setSpeaker(true)
        CallManager.recording.value = true
        CallManager.recordingSession = CallManager.session.value
        announceThen { startRecorder() }
    }

    /** Speaks the recording notice over the speaker (so the mic carries it into the call). */
    private fun announceThen(next: () -> Unit) {
        var done = false
        val go = { if (!done) { done = true; main.post(next) } }
        main.postDelayed({ go() }, 5_000) // never let a TTS hiccup block recording
        tts = TextToSpeech(this) { status ->
            val t = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { go(); return@TextToSpeech }
            t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { go() }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { go() }
            })
            t.speak(NOTICE, TextToSpeech.QUEUE_FLUSH, null, "notice")
        }
    }

    private fun startRecorder() {
        if (!CallManager.recording.value) return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val who = (CallManager.primary?.let { it.name ?: it.number } ?: "call").replace(Regex("[^A-Za-z0-9+]"), "_")
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Environment.DIRECTORY_RECORDINGS else Environment.DIRECTORY_MUSIC
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Bitcall_${who}_$stamp.m4a")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "$dir/Bitcall")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        runCatching {
            val u = contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
            uri = u
            pfd = contentResolver.openFileDescriptor(u, "w")
            @Suppress("DEPRECATION")
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(pfd!!.fileDescriptor)
                prepare()
                start()
            }
        }.onFailure {
            uri?.let { u -> contentResolver.delete(u, null, null) }
            uri = null
            CallManager.recording.value = false
            stopSelf()
        }
    }

    private fun finishRecording() {
        tts?.shutdown()
        tts = null
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        pfd?.close()
        pfd = null
        val u = uri
        if (u != null) {
            contentResolver.update(u, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            CallManager.onRecordingSaved(u.toString())
        } else {
            CallManager.recording.value = false
        }
        uri = null
    }

    override fun onDestroy() {
        finishRecording()
        super.onDestroy()
    }
}
