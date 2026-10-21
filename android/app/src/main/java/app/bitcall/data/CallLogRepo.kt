package app.bitcall.data

import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val durationSec: Long,
) {
    val missed get() = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE
    val outgoing get() = type == CallLog.Calls.OUTGOING_TYPE
}

data class CallStats(
    val xp: Int,
    val level: Int,
    val levelProgress: Float,
    val made: Int,
    val answered: Int,
    val missed: Int,
    val talkMinutes: Long,
    val streakDays: Int,
    val badges: List<String>,
)

object CallLogRepo {
    suspend fun recent(context: Context, limit: Int = 300): List<CallEntry> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext() && size < limit) add(
                        CallEntry(c.getLong(0), c.getString(1).orEmpty(), c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5))
                    )
                }
            }
        }.getOrNull().orEmpty()
    }

    fun delete(context: Context, id: Long) {
        runCatching { context.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(id.toString())) }
    }

    /** XP: 10 per call made, 5 per call answered, 2 per minute talked. */
    fun stats(calls: List<CallEntry>): CallStats {
        val made = calls.count { it.outgoing }
        val answered = calls.count { it.type == CallLog.Calls.INCOMING_TYPE && it.durationSec > 0 }
        val missed = calls.count { it.missed }
        val minutes = calls.sumOf { it.durationSec } / 60
        val xp = (made * 10 + answered * 5 + minutes * 2).toInt()
        val levelF = sqrt(xp / 50.0)
        val level = levelF.toInt() + 1
        val day = 86_400_000L
        val days = calls.filter { it.outgoing }.map { it.date / day }.distinct().sortedDescending()
        var streak = 0
        var expect = System.currentTimeMillis() / day
        for (d in days) {
            if (d == expect || (streak == 0 && d == expect - 1)) { streak++; expect = d - 1 } else if (d < expect) break
        }
        val badges = buildList {
            if (made >= 1) add("👾 FIRST DIAL")
            if (made >= 100) add("🕹 100 CALLS")
            if (minutes >= 60) add("⏱ 1 HOUR TALKER")
            if (minutes >= 600) add("🏆 MARATHON")
            if (streak >= 7) add("🔥 7-DAY STREAK")
            if (calls.any { it.durationSec >= 3600 }) add("🐉 BOSS CALL (1H+)")
        }
        return CallStats(xp, level, (levelF - levelF.toInt()).toFloat(), made, answered, missed, minutes, streak, badges)
    }
}

/** System block list. Telecom enforces it automatically; only the default dialer may edit it. */
object Blocklist {
    fun all(context: Context): List<Pair<Long, String>> = runCatching {
        context.contentResolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID, BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null, null, null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) } }
    }.getOrNull().orEmpty()

    fun block(context: Context, number: String): Boolean = runCatching {
        context.contentResolver.insert(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
        ) != null
    }.getOrDefault(false)

    fun unblock(context: Context, number: String) {
        runCatching { BlockedNumberContract.unblock(context, number) }
    }
}
