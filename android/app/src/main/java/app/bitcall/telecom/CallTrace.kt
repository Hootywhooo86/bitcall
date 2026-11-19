package app.bitcall.telecom

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What bitcall decided for each of the last few incoming calls, step by step, so a call that
 * didn't ring can be explained from the phone itself (Settings → Do Not Disturb → Ring-through check).
 * Kept on this device only.
 */
object CallTrace {
    private const val KEEP = 5
    private val lines = ArrayList<String>()
    private var app: Context? = null

    fun begin(context: Context, who: String) {
        app = context.applicationContext
        lines.clear()
        lines += SimpleDateFormat("EEE HH:mm:ss", Locale.US).format(Date()) + " · incoming " + who
        save()
    }

    fun note(text: String) {
        if (app == null) return
        lines += "• $text"
        save()
    }

    fun recent(context: Context): List<String> {
        val raw = prefs(context).getString("calls", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun save() {
        val ctx = app ?: return
        val list = ArrayList(recent(ctx))
        val text = lines.joinToString("\n")
        // The current call is always the first entry; replace it as notes come in.
        if (list.isNotEmpty() && list[0].lineSequence().first() == lines.first()) list[0] = text else list.add(0, text)
        prefs(ctx).edit().putString("calls", JSONArray(list.take(KEEP)).toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("bitcall.calltrace", Context.MODE_PRIVATE)
}
