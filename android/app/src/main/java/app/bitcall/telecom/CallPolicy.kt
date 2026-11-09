package app.bitcall.telecom

import android.content.Context
import app.bitcall.ai.BitAi
import app.bitcall.data.CallLogRepo
import app.bitcall.data.ContactsRepo
import kotlinx.coroutines.runBlocking
import app.bitcall.data.Phone
import app.bitcall.data.Settings
import java.util.Calendar

/** One place that decides how an incoming call is treated; shared by screening and ringing. */
object CallPolicy {
    /** [breaksDnd]: this call rings through Do Not Disturb (phone DND and bitcall's quiet hours). */
    data class Decision(val reject: Boolean, val silence: Boolean, val enemy: Boolean, val reason: String?, val breaksDnd: Boolean = false)

    const val REPEAT_WINDOW_MS = 3 * 60_000L

    /**
     * Who gets through Do Not Disturb: the chosen VIPs (★ favorites, all contacts, or everyone who
     * isn't spam) and, separately, anyone calling again within 3 minutes. Spam never does.
     */
    fun breaksThrough(allow: Boolean, who: String, starred: Boolean, isContact: Boolean, enemy: Boolean, repeat: Boolean, allowRepeat: Boolean): Boolean {
        if (enemy) return false
        val vip = allow && when (who) {
            "EVERYONE" -> true
            "CONTACTS" -> isContact
            else -> starred
        }
        return vip || (allowRepeat && repeat)
    }

    /** Whether [number] already called within the last 3 minutes (from the call log). */
    fun isRepeat(number: String?, history: List<app.bitcall.data.CallEntry>, now: Long = System.currentTimeMillis()): Boolean {
        val key = Phone.key(number)
        if (key.isEmpty()) return false
        return history.any { Phone.key(it.number) == key && now - it.date in 0..REPEAT_WINDOW_MS && it.type != android.provider.CallLog.Calls.OUTGOING_TYPE }
    }

    fun decide(context: Context, number: String?, verificationFailed: Boolean): Decision {
        val s = Settings.now
        val contact = ContactsRepo.lookup(context, number)
        val flagged = Phone.isSpam(number)
        val history = runBlocking { CallLogRepo.recent(context, if (contact == null) 300 else 20) }
        val score = BitAi.spamScore(number.orEmpty(), contact != null, history, verificationFailed)
        val enemy = contact == null && (flagged || verificationFailed || score >= 70)
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val repeat = isRepeat(number, history)
        val vip = breaksThrough(s.dndAllowFavorites, s.dndWho, contact?.starred == true, contact != null, enemy, repeat, s.dndRepeatCallers)
        val dnd = Settings.inDndWindow(minute) && !vip
        return when {
            s.rejectSpam && contact == null && flagged -> Decision(true, false, true, "Blocked spam")
            dnd -> Decision(false, true, enemy, "Quiet hours")
            s.silenceUnknown && contact == null && !vip -> Decision(false, true, enemy, "Unknown caller silenced")
            else -> Decision(false, false, enemy, when {
                verificationFailed && contact == null -> "Number may be spoofed"
                score >= 70 -> "Spam score $score"
                repeat && vip && contact?.starred != true -> "Called again within 3 min: ringing through Do Not Disturb"
                else -> null
            }, breaksDnd = vip)
        }
    }
}
