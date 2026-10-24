package app.bitcall.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import java.util.Locale

object Phone {
    /** Stable key for matching numbers across formats: last 10 digits. */
    fun key(number: String?): String =
        number.orEmpty().filter { it.isDigit() }.takeLast(10)

    fun pretty(number: String?): String {
        if (number.isNullOrBlank()) return "Unknown"
        return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
    }

    fun isSpam(number: String?): Boolean =
        key(number).let { it.isNotEmpty() && it in Settings.now.spamNumbers }

    fun call(context: Context, number: String) {
        if (number.isBlank()) return
        val uri = if (number == "voicemail") Uri.fromParts("voicemail", "", null)
        else Uri.fromParts("tel", number, null)
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
        DialPad.callPlacedAt = System.currentTimeMillis()
        context.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
    }

    private val T9 = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2', 'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4', 'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6', 'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8', 'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9',
    )

    fun t9(name: String): String =
        name.lowercase().map { T9[it] ?: if (it.isDigit()) it else ' ' }.joinToString("")

    /** T9 match: typed digits start any word of the name, or appear in the number. */
    fun t9Matches(digits: String, name: String, number: String): Boolean {
        if (digits.isEmpty()) return false
        if (number.filter { it.isDigit() }.contains(digits)) return true
        val words = t9(name).split(' ').filter { it.isNotEmpty() }
        return words.any { it.startsWith(digits) } || t9(name).replace(" ", "").startsWith(digits)
    }
}
