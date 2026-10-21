package app.bitcall.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import app.bitcall.R
import app.bitcall.data.Phone
import app.bitcall.ui.InCallActivity

object CallNotifications {
    const val CH_INCOMING = "incoming"
    const val CH_ONGOING = "ongoing"
    const val CH_ASSIST = "assist"
    const val CH_RECORDER = "recorder"
    private const val ID_CALL = 1
    private const val ID_ASSIST = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            // Bitcall plays its own ringtone, so the incoming channel itself is silent.
            NotificationChannel(CH_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false) },
            NotificationChannel(CH_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_ASSIST, "Call Assist alerts", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_RECORDER, "Call recording", NotificationManager.IMPORTANCE_LOW),
        ))
    }

    private fun action(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun openCallScreen(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun show(context: Context, ui: CallUi) {
        val ringing = ui.state == Call.STATE_RINGING
        val who = (if (ui.enemy) "👾 " else "") + (ui.name ?: Phone.pretty(ui.number))
        val b = Notification.Builder(context, if (ringing) CH_INCOMING else CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(who)
            .setContentText(if (ringing) (if (ui.enemy) "Possible spam caller" else "Incoming call") else "Ongoing call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openCallScreen(context))
        if (ringing) {
            b.setFullScreenIntent(openCallScreen(context), true)
            b.addAction(Notification.Action.Builder(null, "Decline", action(context, CallActionReceiver.DECLINE, 1)).build())
            // Answer opens the call screen directly (Android 12+ blocks receiver→activity trampolines).
            val answer = PendingIntent.getActivity(
                context, 2,
                Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(InCallActivity.EXTRA_ANSWER, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(Notification.Action.Builder(null, "Answer", answer).build())
        } else {
            if (ui.connectTime > 0) b.setUsesChronometer(true).setWhen(ui.connectTime)
            b.addAction(Notification.Action.Builder(null, "Hang up", action(context, CallActionReceiver.HANGUP, 3)).build())
        }
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_CALL, b.build()) }
    }

    fun humanOnLine(context: Context) {
        val n = Notification.Builder(context, CH_ASSIST)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Someone's on the line!")
            .setContentText("Hold for Me heard a person. Tap to jump back in.")
            .setAutoCancel(true)
            .setContentIntent(openCallScreen(context))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_ASSIST, n) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).run { cancel(ID_CALL); cancel(ID_ASSIST) }
    }
}

class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val DECLINE = "app.bitcall.DECLINE"
        const val HANGUP = "app.bitcall.HANGUP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE, HANGUP -> CallManager.hangup()
        }
    }
}
