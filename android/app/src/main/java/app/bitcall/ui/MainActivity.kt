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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.bitcall.data.CallEntry
import app.bitcall.data.CallLogRepo
import app.bitcall.data.Contact
import app.bitcall.data.ContactsRepo
import app.bitcall.data.Phone
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
    private val askRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) askPermissions.launch(permissions)
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
    var settingsSection by remember { mutableStateOf<Section?>(null) }
    var digits by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var calls by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    var isDefault by remember { mutableStateOf(activity.isDefaultDialer()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { refresh++; isDefault = activity.isDefaultDialer() } }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(refresh) {
        contacts = ContactsRepo.all(context)
        calls = CallLogRepo.recent(context)
    }
    LaunchedEffect(Unit) {
        incomingDial.collect { n -> if (n != null) { digits = n; tab = Tab.KEYPAD; settings = false; incomingDial.value = null } }
    }
    BackHandler(settings) { settings = false }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (settings) {
            SettingsScreen(onBack = { settings = false; settingsSection = null }, onChanged = { refresh++ }, initial = settingsSection)
            return@Column
        }
        TopBar(contacts.filter { it.starred }, onSettings = { settings = true }) { Phone.call(context, it) }
        if (!isDefault) {
            Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clickable { activity.requestDefaultDialer() }) {
                Text("▶ TAP TO MAKE BITCALL YOUR PHONE APP", style = px(9, p.accent))
                Text("Needed to place, answer, screen and record calls.", style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp))
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
                Tab.KEYPAD -> DialerScreen(digits, { digits = it }, contacts, onChanged = { refresh++ })
                Tab.RECENTS -> RecentsScreen(calls, contacts, onChanged = { refresh++ })
                Tab.CONTACTS -> ContactsScreen(contacts, onChanged = { refresh++ })
                Tab.VOICEMAIL -> VoicemailScreen()
            }
        }
        Row(Modifier.fillMaxWidth().background(p.surface).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { t ->
                val sel = t == tab
                Column(
                    Modifier.clickable { tab = t }.padding(8.dp).widthIn(min = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.label, tint = if (sel) p.accent else p.dim)
                    Text(t.label, style = px(7, if (sel) p.accent else p.dim), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Title bar with the ★ favourites drop-down (starred contacts sync with Google Contacts). */
@Composable
private fun TopBar(favorites: List<Contact>, onSettings: () -> Unit, onCall: (String) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("BITCALL", style = px(18, p.accent, glow = p.accent))
        Spacer(Modifier.weight(1f))
        Box {
            PixelButton(p.surface2, onClick = { open = true }) {
                Text("★ FAVES ▾", style = px(9, p.text), modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
            }
            DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(p.surface)) {
                if (favorites.isEmpty()) {
                    DropdownMenuItem(text = { Text("No favorites yet.\nStar a contact ☆", style = px(8, p.dim)) }, onClick = { open = false })
                }
                favorites.forEach { c ->
                    DropdownMenuItem(
                        leadingIcon = { PixelAvatar(c.name, c.photoUri, 32.dp) },
                        text = {
                            Column {
                                Text(c.name, style = px(9, p.text))
                                Text(Phone.pretty(c.primary), style = px(7, p.dim), modifier = Modifier.padding(top = 4.dp))
                            }
                        },
                        onClick = { open = false; onCall(c.primary) },
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = p.text) }
    }
}
