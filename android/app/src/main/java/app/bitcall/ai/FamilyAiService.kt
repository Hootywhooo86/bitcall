package app.bitcall.ai

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import app.bitcall.data.Settings
import app.bitcall.telecom.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Family AI: lets other 128bit apps borrow the model the user downloaded here, so a 4B model
 * lives on the phone once. Only apps signed with the same key as bitcall get an answer.
 *
 * Protocol (bind with action [ACTION], package app.bitcall):
 *  request  what=1, data { system, user, maxTokens (<= 400), temperature }, replyTo set
 *  reply    what=2, data { ok, text?, error? } with error one of not_family, ai_off, no_model, in_call, failed
 */
class FamilyAiService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val oneAtATime = Mutex()
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(Handler(Looper.getMainLooper()) { msg -> handle(msg); true })
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        if (msg.what != REQUEST) return
        val replyTo = msg.replyTo ?: return
        val data = msg.data
        val system = data.getString("system").orEmpty()
        val user = data.getString("user").orEmpty()
        val maxTokens = data.getInt("maxTokens", 200).coerceIn(1, 400)
        val temperature = data.getFloat("temperature", 0.4f)
        val family = packageManager.checkSignatures(msg.sendingUid, Process.myUid()) == PackageManager.SIGNATURE_MATCH

        val decision = FamilyAiPolicy.decide(family, Settings.now.aiMode, BitAi.familyModelReady(this), CallManager.primary != null)
        if (decision != FamilyAiDecision.RUN) return reply(replyTo, null, decision.code)

        scope.launch {
            val text = runCatching {
                oneAtATime.withLock { BitAi.generateForFamily(this@FamilyAiService, system, user, maxTokens, temperature) }
            }.getOrNull()
            reply(replyTo, text, if (text == null) "failed" else null)
        }
    }

    private fun reply(to: Messenger, text: String?, error: String?) {
        val m = Message.obtain(null, REPLY).apply {
            this.data = Bundle().apply {
                putBoolean("ok", text != null)
                putString("text", text)
                putString("error", error)
            }
        }
        try { to.send(m) } catch (_: RemoteException) { /* the caller went away */ }
    }

    companion object {
        const val ACTION = "app.bit128.family.AI_GENERATE"
        const val REQUEST = 1
        const val REPLY = 2
    }
}
