package app.bitcall.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.telecom.Call
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.bitcall.R
import app.bitcall.data.Settings
import app.bitcall.ui.InCallActivity
import kotlin.math.abs

/**
 * A floating pixel "☎ 2:31" bubble that stays on top of other apps during a call, so you can look
 * something up and tap straight back into the call screen. Shown only while a call is live and the
 * call screen isn't on screen; needs Android's "Display over other apps" permission.
 */
object CallBubble {
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private val main = Handler(Looper.getMainLooper())
    private const val GREEN = 0xFF00E676.toInt()
    private const val INK = 0xFF0D0D1A.toInt()

    /** Set by the call screen: no bubble while you're looking at the call itself. */
    var callScreenVisible = false
        set(value) { field = value; appContext?.let { update(it) } }
    private var appContext: Context? = null

    fun canShow(context: Context) = AndroidSettings.canDrawOverlays(context)

    fun permissionIntent(context: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Shows or hides the bubble to match the current call. Safe to call often, from the main thread. */
    fun update(context: Context) {
        appContext = context.applicationContext
        val call = CallManager.primary
        val live = call != null && call.state in setOf(Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_CONNECTING)
        if (live && Settings.now.callBubble && !callScreenVisible && canShow(context)) show(context.applicationContext) else hide(context.applicationContext)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(context: Context) {
        if (view != null) return
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt() }
        val tv = TextView(context).apply {
            typeface = ResourcesCompat.getFont(context, R.font.press_start_2p)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(INK)
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            // Square corners and a dark border: a chunky pixel button, easy to spot over any app.
            background = GradientDrawable().apply { setColor(GREEN); setStroke(dp(3f), INK) }
            elevation = dp(8f).toFloat()
            contentDescription = "Return to call"
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12f); y = dp(140f) }
        // Drag it anywhere; a tap (no real drag) opens the call screen.
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) moved = true
                    if (moved) { lp.x = startX - dx.toInt(); lp.y = startY + dy.toInt(); runCatching { wm.updateViewLayout(v, lp) } }
                }
                MotionEvent.ACTION_UP -> if (!moved) openCallScreen(context)
            }
            true
        }
        if (runCatching { wm.addView(tv, lp) }.isFailure) return
        view = tv; params = lp
        tick()
    }

    private fun tick() {
        val tv = view ?: return
        val call = CallManager.primary
        tv.text = when {
            call == null -> "☎"
            call.state == Call.STATE_HOLDING -> "⏸ ON HOLD"
            call.connectTime > 0 -> "☎ " + DateUtils.formatElapsedTime((System.currentTimeMillis() - call.connectTime).coerceAtLeast(0) / 1000)
            else -> "☎ CALLING"
        }
        main.postDelayed(::tick, 1000)
    }

    private fun hide(context: Context) {
        val v = view ?: return
        main.removeCallbacksAndMessages(null)
        runCatching { context.getSystemService(WindowManager::class.java)?.removeView(v) }
        view = null; params = null
    }

    fun openCallScreen(context: Context) {
        runCatching {
            context.startActivity(Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }
}
