package app.bitcall.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bitcall.data.CallEntry
import app.bitcall.data.CallLogRepo
import app.bitcall.data.Contact
import app.bitcall.data.ContactsRepo
import app.bitcall.data.DialPad
import app.bitcall.data.Phone
import app.bitcall.telecom.CallManager
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Number handed to us by a tel: link or ACTION_DIAL. */
    private val incomingDial = MutableStateFlow<String?>(null)

    private val permissions = buildList {
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.WRITE_CONTACTS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.WRITE_CALL_LOG)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    /** Set when Android refuses the dialer role (sideloaded apps hit "restricted settings" on Android 13+). */
    val roleDenied = MutableStateFlow(false)

    private val askRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleDenied.value = !isDefaultDialer()
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) askPermissions.launch(permissions)
    }

    fun openAppInfo() = startActivity(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)),
    )

    /** Android 14+ can switch off full-screen notifications per app; incoming calls need them. */
    fun canShowCallsFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()

    fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return
        runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.fromParts("package", packageName, null)))
        }.onFailure { openAppInfo() }
    }

    fun isDefaultDialer() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

    fun requestDefaultDialer() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            askRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else askPermissions.launch(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        if (!isDefaultDialer()) requestDefaultDialer()
        else if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) askPermissions.launch(permissions)
        setContent { BitTheme { Home(this, incomingDial) } }
    }

    /** Leaving Bitcall (home, back, swiping it away) clears the keypad; going to a call keeps it. */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        DialPad.clearUnlessCalling(CallManager.calls.value.any { it.state != android.telecom.Call.STATE_DISCONNECTED })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "tel") incomingDial.value = data.schemeSpecificPart
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    KEYPAD("KEYPAD", Icons.Filled.Dialpad),
    RECENTS("RECENTS", Icons.Filled.History),
    CONTACTS("PEOPLE", Icons.Filled.People),
    VOICEMAIL("VMAIL", Icons.Filled.Voicemail),
}

@Composable
fun Home(activity: MainActivity, incomingDial: MutableStateFlow<String?>) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.KEYPAD) }
    var settings by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Long?>(null) }
    var settingsSection by remember { mutableStateOf<Section?>(null) }
    val digits by DialPad.digits.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var calls by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var isDefault by remember { mutableStateOf(activity.isDefaultDialer()) }
    var fullScreenOk by remember { mutableStateOf(activity.canShowCallsFullScreen()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { refresh++; isDefault = activity.isDefaultDialer(); fullScreenOk = activity.canShowCallsFullScreen() } }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(refresh) {
        contacts = ContactsRepo.all(context)
        calls = CallLogRepo.recent(context)
    }
    LaunchedEffect(Unit) {
        incomingDial.collect { n -> if (n != null) { DialPad.set(n); tab = Tab.KEYPAD; settings = false; incomingDial.value = null } }
    }
    BackHandler(settings) { settings = false }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (settings) {
            SettingsScreen(onBack = { settings = false; settingsSection = null }, onChanged = { refresh++ }, initial = settingsSection)
            return@Column
        }
        details?.let { id ->
            ContactDetailsScreen(id, calls, onBack = { details = null }, onChanged = { refresh++ })
            return@Column
        }
        TopBar(contacts.filter { it.starred }, onSettings = { settings = true }, onInfo = { details = it.id }) { Phone.call(context, it) }
        val denied by activity.roleDenied.collectAsStateWithLifecycle()
        if (!isDefault && denied) {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
                Text("⚠ ANDROID BLOCKED IT. 30-SECOND FIX:", style = px(9, p.bad))
                Text(
                    "1. Tap OPEN APP INFO\n2. Tap ⋮ (top-right corner)\n3. Tap \"Allow restricted settings\" and confirm\n4. Come back and tap TRY AGAIN\n\nAndroid does this for apps installed from an APK instead of an app store.",
                    style = px(7, p.text), modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelChip("OPEN APP INFO", true) { activity.openAppInfo() }
                    PixelChip("TRY AGAIN", false) { activity.requestDefaultDialer() }
                }
            }
        } else if (!isDefault) {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clickable { activity.requestDefaultDialer() }) {
                Text("▶ TAP TO MAKE BITCALL YOUR PHONE APP", style = px(9, p.accent))
                Text("Needed to place, answer, screen and record calls.", style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (isDefault && !fullScreenOk) {
            // Without this, incoming calls only show as a small pop-up and never wake a locked phone.
            Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth()) {
                Text("⚠ CALLS CAN'T WAKE YOUR SCREEN", style = px(9, p.bad))
                Text("Allow full-screen calls so an incoming call lights up the phone and shows big ANSWER / DECLINE buttons over the lock screen.",
                    style = px(7, p.text), modifier = Modifier.padding(vertical = 8.dp))
                Row { PixelChip("ALLOW FULL-SCREEN CALLS", true) { activity.openFullScreenSettings() } }
            }
        }
        val bit = LocalBitSettings.current
        if (!bit.aiAsked) {
            // Post-install opt-in: AI stays off unless the user chooses it.
            Card(Modifier.padding(12.dp).fillMaxWidth()) {
                Text("🧠 NEW: ON-DEVICE AI", style = px(9, p.accent))
                Text("Voicemail summaries, \"who called me last week?\" search and smart replies, all running privately on your phone. Optional.",
                    style = px(7, p.dim), modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelChip("NOT NOW", false) { app.bitcall.data.Settings.update { it.copy(aiAsked = true) } }
                    PixelChip("SET UP", true) {
                        app.bitcall.data.Settings.update { it.copy(aiAsked = true) }
                        settingsSection = Section.AI; settings = true
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.KEYPAD -> DialerScreen(
                    digits, DialPad::set, contacts,
                    lastDialed = calls.firstOrNull { it.type == android.provider.CallLog.Calls.OUTGOING_TYPE }?.number,
                    onChanged = { refresh++ },
                )
                Tab.RECENTS -> RecentsScreen(calls, contacts, onChanged = { refresh++ }, onOpen = { details = it })
                Tab.CONTACTS -> ContactsScreen(contacts, onChanged = { refresh++ }, onOpen = { details = it.id })
                Tab.VOICEMAIL -> VoicemailScreen()
            }
        }
        // Pixel rule on top of the tab bar; the selected tab gets a chunky pixel bar over its icon.
        Box(Modifier.fillMaxWidth().height(3.dp).background(p.border))
        Row(Modifier.fillMaxWidth().background(p.tabBar).padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { t ->
                val sel = t == tab
                Column(
                    Modifier.clickable { tab = t }.widthIn(min = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(if (sel) p.tabActive else Color.Transparent))
                    Icon(t.icon, t.label, tint = if (sel) p.tabActive else p.dim, modifier = Modifier.padding(top = 6.dp).size(26.dp))
                    Text(t.label, style = px(7, if (sel) p.tabActive else p.dim), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Title bar with the ★ favourites drop-down (starred contacts sync with Google Contacts). */
@Composable
private fun TopBar(favorites: List<Contact>, onSettings: () -> Unit, onInfo: (Contact) -> Unit, onCall: (String) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTitle()
        Spacer(Modifier.weight(1f))
        Box {
            PixelButton(p.surface2, onClick = { open = true }) {
                Text("★ FAVES", style = px(9, p.text), modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
            }
        }
        if (open) {
            FavoritesSheet(
                favorites,
                onDismiss = { open = false },
                onInfo = { open = false; onInfo(it) },
                onCall = { open = false; onCall(it) },
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = p.text) }
    }
}
