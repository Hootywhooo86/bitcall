package app.bitcall.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bitcall.assist.ChipSound
import app.bitcall.data.Contact
import app.bitcall.data.Phone
import app.bitcall.data.Settings

val KEYS = listOf(
    "1" to "◌◌", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

@Composable
fun DialerScreen(digits: String, setDigits: (String) -> Unit, contacts: List<Contact>, onChanged: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    var speedDialFor by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    // T9 smart search: typed digits match names (2=ABC...) or numbers.
    val matches = remember(digits, contacts) {
        if (digits.length < 1) emptyList()
        else contacts.filter { c -> c.numbers.any { Phone.t9Matches(digits, c.name, it) } }.take(4)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            items(matches, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { Phone.call(context, c.primary) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelAvatar(c.name, c.photoUri, 34.dp, enemy = Phone.isSpam(c.primary))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(c.name, style = px(10, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Phone.pretty(c.primary), style = px(8, p.dim), modifier = Modifier.padding(top = 4.dp))
                    }
                    if (c.starred) Text("★", style = px(12, p.accent))
                }
            }
        }

        // Number display: tap to clear, like the landing page.
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { adding = true }, enabled = digits.isNotEmpty()) {
                Icon(Icons.Filled.PersonAdd, "Add contact", tint = if (digits.isNotEmpty()) p.dim else p.bg)
            }
            Text(
                digits,
                style = px(if (digits.length > 12) 14 else 20, p.accent, glow = p.accent),
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f).clickable { setDigits("") },
            )
            IconButton(onClick = { setDigits(digits.dropLast(1)) }, enabled = digits.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = if (digits.isNotEmpty()) p.dim else p.bg)
            }
        }

        Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KEYS.chunked(3).forEachIndexed { row, keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    keys.forEachIndexed { col, (d, sub) ->
                        val index = row * 3 + col
                        val color = p.key(index, s)
                        val digit = d.single()
                        PixelButton(
                            color,
                            Modifier.weight(1f).aspectRatio(1.45f),
                            onLongClick = {
                                when {
                                    digit == '1' -> Phone.call(context, "voicemail")
                                    digit == '0' -> setDigits(digits + "+")
                                    digit in '2'..'9' -> {
                                        val n = s.speedDial[digit - '0']
                                        if (n != null) Phone.call(context, n) else speedDialFor = digit - '0'
                                    }
                                }
                            },
                            onClick = {
                                if (s.keySounds) ChipSound.keyBlip(index)
                                if (digits.length < 24) setDigits(digits + d)
                            },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d, style = px(18, onColor(color)))
                                val speed = s.speedDial[digit - '0']
                                Text(
                                    if (speed != null && digit in '2'..'9') "⚡" else sub,
                                    style = px(6, onColor(color).copy(alpha = 0.7f)), modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(56.dp))
            PixelButton(p.good, Modifier.size(width = 120.dp, height = 64.dp), onClick = {
                if (digits.isNotEmpty()) Phone.call(context, digits)
            }) {
                Icon(Icons.Filled.Call, "Call", tint = onColor(p.good), modifier = Modifier.size(28.dp))
            }
            Box(Modifier.width(56.dp))
        }
    }

    speedDialFor?.let { slot ->
        SpeedDialPicker(slot, digits, contacts, onDismiss = { speedDialFor = null })
    }
    if (adding) AddContactDialog(initialNumber = digits, onDismiss = { adding = false }, onSaved = { adding = false; onChanged() })
}

@Composable
fun SpeedDialPicker(slot: Int, typed: String, contacts: List<Contact>, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    fun assign(n: String) { Settings.update { it.copy(speedDial = it.speedDial + (slot to n)) }; onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("SPEED DIAL $slot", style = px(12, p.accent)) },
        text = {
            LazyColumn(Modifier.height(320.dp)) {
                if (typed.isNotEmpty()) item {
                    Text("⚡ Use $typed", style = px(9, p.text), modifier = Modifier.fillMaxWidth().clickable { assign(typed) }.padding(vertical = 10.dp))
                }
                items(contacts.sortedByDescending { it.starred }, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { assign(c.primary) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PixelAvatar(c.name, c.photoUri, 28.dp)
                        Text(c.name, style = px(9, p.text), modifier = Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL", style = px(9, p.dim)) } },
    )
}
