package app.bitcall.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(digits: String, setDigits: (String) -> Unit, contacts: List<Contact>, lastDialed: String?, onChanged: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
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
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(bitShape(12.dp)).background(p.surface)
                        .clickable { Phone.call(context, c.primary) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelAvatar(c.name, c.photoUri, avatarSize(48.dp), enemy = Phone.isSpam(c.primary))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(c.name, style = px(big(11), p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Phone.pretty(c.primary), style = px(big(8), p.dim), modifier = Modifier.padding(top = 6.dp))
                    }
                    if (c.starred) Text("★", style = px(16, p.accent))
                }
            }
        }

        // Number display: tap to clear, like the landing page. Long-press copies the number, or
        // pastes one from the clipboard when nothing is typed yet.
        Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { adding = true }, enabled = digits.isNotEmpty()) {
                Icon(Icons.Filled.PersonAdd, "Add contact", tint = if (digits.isNotEmpty()) p.dim else p.bg)
            }
            // Pixel font glyphs are 1em wide, so the number shrinks only as far as it must to fit.
            BoxWithConstraints(Modifier.weight(1f)) {
            val fit = (maxWidth.value / digits.length.coerceAtLeast(1) / s.textScale).toInt()
            Text(
                digits.ifEmpty { "ENTER A NUMBER" },
                style = if (digits.isEmpty()) px(9, p.dim) else px(minOf(big(24), fit), p.digits, glow = p.digits),
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { setDigits("") },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (digits.isNotEmpty()) {
                            clipboard.setText(AnnotatedString(digits))
                        } else {
                            val pasted = clipboard.getText()?.text.orEmpty().filter { it.isDigit() || it in "+*#" }.take(24)
                            if (pasted.isNotEmpty()) setDigits(pasted)
                        }
                    },
                ),
            )
            }
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
                            Modifier.weight(1f).aspectRatio(1.35f),
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
                                if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (s.keySounds) ChipSound.keyBlip(index)
                                if (digits.length < 24) setDigits(digits + d)
                            },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d, style = px(big(20), onColor(color)))
                                val speed = s.speedDial[digit - '0']
                                if (s.keyLetters || speed != null) Text(
                                    if (speed != null && digit in '2'..'9') "⚡" else sub,
                                    style = px(big(6), onColor(color).copy(alpha = 0.75f)), modifier = Modifier.padding(top = 4.dp),
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
            // Empty keypad + CALL brings back the last number dialed, like most phones.
            PixelButton(p.call, Modifier.size(width = 140.dp, height = 68.dp), onClick = {
                if (digits.isNotEmpty()) Phone.call(context, digits) else lastDialed?.let(setDigits)
            }) {
                Icon(Icons.Filled.Call, "Call", tint = onColor(p.call), modifier = Modifier.size(30.dp))
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
        title = { Text("SPEED DIAL $slot", style = px(16, p.accent)) },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                if (typed.isNotEmpty()) item {
                    Text("⚡ Use $typed", style = px(12, p.text), modifier = Modifier.fillMaxWidth().clickable { assign(typed) }.padding(vertical = 12.dp))
                }
                items(contacts.sortedByDescending { it.starred }, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { assign(c.primary) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PixelAvatar(c.name, c.photoUri, avatarSize(44.dp))
                        Text(c.name, style = px(12, p.text), modifier = Modifier.padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL", style = px(11, p.dim)) } },
    )
}
