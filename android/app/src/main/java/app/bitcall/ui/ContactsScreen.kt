package app.bitcall.ui

import android.accounts.AccountManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bitcall.data.Contact
import app.bitcall.data.ContactsRepo
import app.bitcall.data.Phone
import app.bitcall.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ContactsScreen(contacts: List<Contact>, onChanged: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val shown = remember(query, contacts) {
        if (query.isBlank()) contacts
        else contacts.filter { c -> c.name.contains(query, true) || c.numbers.any { Phone.t9Matches(query, c.name, it) } }
    }
    val favorites = shown.filter { it.starred }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it }, Modifier.weight(1f),
                placeholder = { Text("SEARCH", style = px(9, p.dim)) }, singleLine = true, textStyle = px(10, p.text),
            )
            Spacer(Modifier.width(10.dp))
            PixelButton(p.accent, onClick = { adding = true }) {
                Text("+ ADD", style = px(9, onColor(p.accent)), modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (favorites.isNotEmpty()) {
                item { SectionTitle("★ Favorites") }
                items(favorites, key = { "f${it.id}" }) { c -> ContactRow(c) { star(context, scope, c, onChanged) } }
                item { SectionTitle("All contacts") }
            }
            items(shown, key = { it.id }) { c -> ContactRow(c) { star(context, scope, c, onChanged) } }
            if (contacts.isEmpty()) item {
                Text("No contacts yet — tap + ADD.\nNew contacts save to your Google account.", style = px(8, p.dim), modifier = Modifier.padding(16.dp))
            }
        }
    }
    if (adding) AddContactDialog("", onDismiss = { adding = false }, onSaved = { adding = false; onChanged() })
}

private fun star(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, c: Contact, onChanged: () -> Unit) {
    scope.launch {
        withContext(Dispatchers.IO) { runCatching { ContactsRepo.setStarred(context, c.id, !c.starred) } }
        onChanged()
    }
}

@Composable
private fun ContactRow(c: Contact, onStar: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val enemy = c.numbers.any { Phone.isSpam(it) }
    Row(
        Modifier.fillMaxWidth().clickable { Phone.call(context, c.primary) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelAvatar(c.name, c.photoUri, 40.dp, enemy)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(c.name, style = px(10, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Phone.pretty(c.primary), style = px(8, p.dim))
                if (enemy) EnemyBadge(Modifier.padding(start = 6.dp))
            }
        }
        Text(
            if (c.starred) "★" else "☆", style = px(16, if (c.starred) p.accent else p.dim),
            modifier = Modifier.clickable(onClick = onStar).padding(12.dp),
        )
    }
}

/** New contact → written into the chosen Google account, so it syncs to contacts.google.com. */
@Composable
fun AddContactDialog(initialNumber: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf(initialNumber) }
    var email by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var account by remember { mutableStateOf(Settings.now.googleAccount) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        accounts = withContext(Dispatchers.IO) { ContactsRepo.googleAccounts(context) }
        if (account == null || account !in accounts) account = accounts.firstOrNull() ?: account
    }
    val pickAccount = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.let { picked ->
            account = picked
            if (picked !in accounts) accounts = accounts + picked
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("NEW CONTACT", style = px(12, p.accent)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("NAME", style = px(8)) }, singleLine = true, textStyle = px(10, p.text))
                OutlinedTextField(number, { number = it }, label = { Text("PHONE", style = px(8)) }, singleLine = true, textStyle = px(10, p.text))
                OutlinedTextField(email, { email = it }, label = { Text("EMAIL (OPTIONAL)", style = px(8)) }, singleLine = true, textStyle = px(10, p.text))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(favorite, { favorite = it }, colors = CheckboxDefaults.colors(checkedColor = p.accent))
                    Text("★ ADD TO FAVORITES", style = px(8, p.text))
                }
                Text("SAVE TO", style = px(8, p.dim), modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                accounts.forEach { a ->
                    Text(
                        (if (a == account) "● " else "○ ") + a, style = px(8, if (a == account) p.accent else p.text),
                        modifier = Modifier.fillMaxWidth().clickable { account = a }.padding(vertical = 6.dp),
                    )
                }
                Text(
                    "+ choose Google account…", style = px(8, p.accent),
                    modifier = Modifier.clickable {
                        pickAccount.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf(ContactsRepo.GOOGLE), null, null, null, null))
                    }.padding(vertical = 6.dp),
                )
                error?.let { Text(it, style = px(8, p.bad)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || number.isBlank()) { error = "Name and phone needed"; return@TextButton }
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { ContactsRepo.add(context, account, name.trim(), number.trim(), email.trim(), favorite) }.isSuccess
                    }
                    if (ok) {
                        Settings.update { it.copy(googleAccount = account) }
                        onSaved()
                    } else error = "Couldn't save — check contacts permission"
                }
            }) { Text("SAVE", style = px(9, p.accent)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", style = px(9, p.dim)) } },
    )
}
