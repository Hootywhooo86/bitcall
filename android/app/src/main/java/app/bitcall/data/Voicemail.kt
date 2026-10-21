package app.bitcall.data

import android.content.Context
import android.net.Uri
import android.provider.VoicemailContract.Voicemails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VoicemailEntry(
    val id: Long,
    val number: String,
    val date: Long,
    val durationSec: Long,
    val transcript: String?,
    val uri: Uri,
    val read: Boolean,
)

/**
 * Visual voicemail stored by the carrier's VVM client. As the default dialer Bitcall is granted
 * READ_VOICEMAIL; if the carrier has no visual voicemail this list is simply empty.
 */
object VoicemailRepo {
    suspend fun all(context: Context): List<VoicemailEntry> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                Voicemails.CONTENT_URI,
                arrayOf(Voicemails._ID, Voicemails.NUMBER, Voicemails.DATE, Voicemails.DURATION, Voicemails.TRANSCRIPTION, Voicemails.IS_READ),
                "${Voicemails.DELETED}=0", null, "${Voicemails.DATE} DESC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        add(VoicemailEntry(id, c.getString(1).orEmpty(), c.getLong(2), c.getLong(3), c.getString(4),
                            Uri.withAppendedPath(Voicemails.CONTENT_URI, id.toString()), c.getInt(5) == 1))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }
}
