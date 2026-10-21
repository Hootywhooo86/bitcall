package app.bitcall.assist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import app.bitcall.data.CallLogRepo
import app.bitcall.data.ContactsRepo
import app.bitcall.data.NotesStore
import app.bitcall.data.Phone
import java.text.DateFormat
import java.util.Date

/**
 * On-device take on Magic Cue: surfaces what the phone already knows about the person on the line
 * (contact details, last calls, your previous call notes, upcoming shared calendar events).
 */
object MagicCue {
    suspend fun cues(context: Context, number: String?): List<String> {
        val out = mutableListOf<String>()
        val contact = ContactsRepo.lookup(context, number)
        if (contact != null) out += ContactsRepo.details(context, contact.id)

        val past = CallLogRepo.recent(context, 500).filter { Phone.key(it.number) == Phone.key(number) }
        if (past.isNotEmpty()) {
            val last = past.first()
            out += "📞 ${past.size} calls · last ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(last.date))}"
        }
        NotesStore.forNumber(number).take(3).forEach { m ->
            val note = m.notes.ifBlank { m.transcript }.lines().firstOrNull { it.isNotBlank() } ?: return@forEach
            out += "🗒 ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(m.start))}: $note"
        }
        if (contact != null) out += calendar(context, contact.name)
        if (out.isEmpty()) out += "No cues yet. Notes you take on this call show up here next time."
        return out
    }

    private fun calendar(context: Context, name: String): List<String> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val first = name.substringBefore(' ')
        val now = System.currentTimeMillis()
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
                "${CalendarContract.Events.DTSTART} BETWEEN ? AND ? AND (${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?)",
                arrayOf((now - 86_400_000).toString(), (now + 14 * 86_400_000L).toString(), "%$first%", "%$first%"),
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext() && size < 3) add("📅 ${c.getString(0)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(c.getLong(1)))}")
                }
            }
        }.getOrNull().orEmpty()
    }
}
