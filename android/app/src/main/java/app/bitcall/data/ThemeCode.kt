package app.bitcall.data

import android.util.Base64
import org.json.JSONObject

/**
 * Bitcall's look as a short text code ("BITCALL1:" + base64 JSON) so a theme can be shared or kept
 * as a backup. Only look settings travel; calls, contacts and Call Assist options never do.
 */
object ThemeCode {
    private const val PREFIX = "BITCALL1:"

    fun encode(s: BitSettings): String {
        val o = JSONObject()
            .put("palette", s.palette)
            .put("colors", JSONObject(s.colorOverrides))
            .put("keys", JSONObject(s.keyColors.mapKeys { it.key.toString() }))
            .put("corners", s.corners)
            .put("cardSize", s.cardSize)
            .put("pixelDetail", s.pixelDetail)
            .put("textScale", s.textScale.toDouble())
            .put("crt", s.crt)
            .put("keyLetters", s.keyLetters)
        return PREFIX + Base64.encodeToString(o.toString().toByteArray(), Base64.NO_WRAP)
    }

    /** The settings with the code's look applied, or null if [code] isn't a theme code. */
    fun decode(code: String, base: BitSettings): BitSettings? = runCatching {
        val body = code.trim().takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return null
        val o = JSONObject(String(Base64.decode(body, Base64.DEFAULT)))
        val colors = o.optJSONObject("colors")
        val keys = o.optJSONObject("keys")
        base.copy(
            palette = o.optString("palette", base.palette),
            colorOverrides = colors?.let { c -> c.keys().asSequence().associateWith { c.getLong(it) } } ?: emptyMap(),
            keyColors = keys?.let { k -> k.keys().asSequence().associate { it.toInt().coerceIn(0, 11) to k.getLong(it) } } ?: emptyMap(),
            corners = o.optString("corners", base.corners),
            cardSize = o.optString("cardSize", base.cardSize),
            pixelDetail = o.optInt("pixelDetail", base.pixelDetail).coerceIn(8, 64),
            textScale = o.optDouble("textScale", base.textScale.toDouble()).toFloat().coerceIn(0.7f, 1.6f),
            crt = o.optBoolean("crt", base.crt),
            keyLetters = o.optBoolean("keyLetters", base.keyLetters),
        )
    }.getOrNull()
}
