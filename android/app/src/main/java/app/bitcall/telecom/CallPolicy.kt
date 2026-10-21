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
    data class Decision(val reject: Boolean, val silence: Boolean, val enemy: Boolean, val reason: String?)

    fun decide(context: Context, number: String?, verificationFailed: Boolean): Decision {
        val s = Settings.now
        val contact = ContactsRepo.lookup(context, number)
        val flagged = Phone.isSpam(number)
        val history = if (contact == null) runBlocking { CallLogRepo.recent(context, 300) } else emptyList()
        val score = BitAi.spamScore(number.orEmpty(), contact != null, history, verificationFailed)
        val enemy = contact == null && (flagged || verificationFailed || score >= 70)
        val cal = Calendar.getInstance()
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dnd = Settings.inDndWindow(minute) && !(s.dndAllowFavorites && contact?.starred == true)
        return when {
            s.rejectSpam && contact == null && flagged -> Decision(true, false, true, "Blocked spam")
            dnd -> Decision(false, true, enemy, "Quiet hours")
            s.silenceUnknown && contact == null -> Decision(false, true, enemy, "Unknown caller silenced")
            else -> Decision(false, false, enemy, when {
                verificationFailed && contact == null -> "Number may be spoofed"
                score >= 70 -> "Spam score $score"
                else -> null
            })
        }
    }
}
