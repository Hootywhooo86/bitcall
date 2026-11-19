package app.bitcall.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.bitcall.data.ContactsRepo
import app.bitcall.data.Settings
import app.bitcall.telecom.CallNotifications
import app.bitcall.telecom.CallTrace
import app.bitcall.telecom.Ringer
import app.bitcall.telecom.RingPlan

/** One line of the ring-through checklist: ok = true ✓, false ✗ (with the fix), null = just information. */
data class RingCheckItem(val label: String, val ok: Boolean?, val detail: String, val fix: (() -> Unit)? = null, val fixLabel: String? = null)

/** Everything a ★ favorite's call needs to get through phone DND, read live from the phone. */
fun ringChecks(context: Context): List<RingCheckItem> {
    val nm = context.getSystemService(NotificationManager::class.java)
    val audio = context.getSystemService(AudioManager::class.java)
    val telecom = context.getSystemService(TelecomManager::class.java)
    val filter = nm.currentInterruptionFilter
    val dndOn = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    CallNotifications.favoriteChannel(context) // make sure it exists so its DND bypass can be read
    val bypass = nm.getNotificationChannel(CallNotifications.CH_FAVORITE)?.canBypassDnd() == true
    val alarmsAllowed = if (Build.VERSION.SDK_INT >= 31) {
        nm.consolidatedNotificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
    } else true
    val alarmVol = audio.getStreamVolume(AudioManager.STREAM_ALARM)
    fun open(action: String, pkgExtra: Boolean = false, pkgData: Boolean = false): () -> Unit = {
        runCatching {
            context.startActivity(Intent(action).apply {
                if (pkgExtra) putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                if (pkgData) data = android.net.Uri.parse("package:" + context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
    return listOfNotNull(
        RingCheckItem("bitcall is your phone app", telecom.defaultDialerPackage == context.packageName,
            "Only the default phone app rings for calls."),
        RingCheckItem("Let ★ favorites through", Settings.now.dndAllowFavorites, "The switch above."),
        RingCheckItem("Phone Do Not Disturb", null, RingPlan.filterName(filter)),
        RingCheckItem("Do Not Disturb access", nm.isNotificationPolicyAccessGranted,
            "Lets the call screen pop up over DND.", open(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "ALLOW"),
        RingCheckItem("Call screen shows over DND", bypass,
            if (bypass) "Favorites' calls pop up even with DND on." else "Needs DND access, then reopen this screen.",
            open(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, pkgExtra = true), "OPEN"),
        if (Build.VERSION.SDK_INT >= 34) RingCheckItem("Full-screen incoming calls", nm.canUseFullScreenIntent(),
            "Lets a call take over the screen.", open(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgData = true), "ALLOW") else null,
        RingCheckItem("Phone DND lets alarms through", if (dndOn) alarmsAllowed else null,
            if (dndOn) (if (alarmsAllowed) "Favorites ring on the alarm sound, so this must be on." else "Turn on Alarms in your phone's Do Not Disturb settings.")
            else "Checked while DND is on.",
            open(android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS), "OPEN"),
        RingCheckItem("Alarm volume", alarmVol > 0, "$alarmVol of ${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}. During DND favorites ring at this volume."),
        RingCheckItem("Ringtone", null, if (Settings.now.chipRingtone) "Chiptune" else "Your phone's ringtone (chiptune off) — works the same way."),
    )
}

@Composable
fun RingThroughCheck() {
    val p = LocalPalette.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val checks = remember(tick, Settings.now.dndAllowFavorites) { ringChecks(context) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var number by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text("RING-THROUGH CHECK", style = px(10, p.accent))
        Text("Everything a ★ favorite's call needs to reach you with phone DND on.", style = px(7, p.dim), modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        checks.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    when (c.ok) { true -> "✓"; false -> "✗"; null -> "·" },
                    style = px(12, when (c.ok) { true -> p.good; false -> p.bad; null -> p.dim }),
                    modifier = Modifier.width(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(c.label, style = px(9, p.text))
                    Text(c.detail, style = px(7, p.dim), modifier = Modifier.padding(top = 4.dp))
                }
                if (c.ok == false && c.fix != null) PixelChip(c.fixLabel ?: "FIX", false) { c.fix.invoke() }
            }
        }

        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PixelChip("▶ TEST RING (5 SEC)", true) { testResult = Ringer.test(context) }
        }
        testResult?.let { Text("$it Rings exactly like a ★ favorite would right now.", style = px(7, p.text), modifier = Modifier.padding(top = 8.dp)) }

        Text("CHECK A NUMBER", style = px(9, p.text), modifier = Modifier.padding(top = 20.dp))
        Text("Type your favorite's number to see how bitcall treats their calls.", style = px(7, p.dim), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        OutlinedTextField(number, { number = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = px(10, p.text))
        if (number.length >= 3) {
            val c = remember(number) { ContactsRepo.lookup(context, number) }
            Text(
                when {
                    c == null -> "✗ Not in your contacts: follows phone DND."
                    c.starred && Settings.now.dndAllowFavorites -> "✓ ${c.name} is a ★ favorite: rings through DND."
                    c.starred -> "✗ ${c.name} is a ★ favorite, but \"Let ★ favorites through\" is off."
                    else -> "✗ ${c.name} is not starred: follows phone DND. Star them in Contacts."
                },
                style = px(8, if (c?.starred == true && Settings.now.dndAllowFavorites) p.good else p.bad),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text("LAST CALLS", style = px(9, p.text), modifier = Modifier.padding(top = 20.dp))
        val recent = remember(tick, testResult) { CallTrace.recent(context) }
        if (recent.isEmpty()) Text("No incoming calls recorded yet.", style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp))
        recent.forEach { Text(it, style = px(7, p.dim), modifier = Modifier.padding(top = 10.dp)) }
    }
}
