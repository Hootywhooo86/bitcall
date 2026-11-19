package app.bitcall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bitcall.data.Contact
import app.bitcall.data.Phone

/**
 * ★ favorites as a bottom sheet: a real scrolling list (the old drop-down couldn't scroll a long
 * list well), one tap to call, ⓘ for details. Rows sit on tone steps with even spacing, no rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(favorites: List<Contact>, onDismiss: () -> Unit, onInfo: (Contact) -> Unit, onCall: (String) -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = favorites.size > 4),
        containerColor = p.bg,
        contentColor = p.text,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("★ FAVORITES", style = px(12, p.accent), modifier = Modifier.weight(1f))
            Text(if (favorites.isEmpty()) "" else "${favorites.size}", style = px(10, p.dim))
        }
        Text(
            "Tap to call. These ring through Do Not Disturb.",
            style = px(7, p.dim), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        )
        if (favorites.isEmpty()) {
            Text(
                "No favorites yet. Star a contact ☆ in Contacts and they'll show here.",
                style = px(9, p.text), modifier = Modifier.padding(20.dp).navigationBarsPadding(),
            )
            Spacer(Modifier.height(24.dp))
            return@ModalBottomSheet
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items(favorites, key = { it.id }) { c -> FavoriteRow(c, onInfo = { onInfo(c) }, onCall = { onCall(c.primary) }) }
        }
    }
}

@Composable
private fun FavoriteRow(c: Contact, onInfo: () -> Unit, onCall: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.surface)
            .clickable(onClick = onCall)
            .heightIn(min = 76.dp)
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelAvatar(c.name, c.photoUri, 48.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(c.name, style = px(12, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(Phone.pretty(c.primary), style = px(9, p.dim), maxLines = 1)
        }
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onInfo),
            contentAlignment = Alignment.Center,
        ) { Text("ⓘ", style = px(16, p.dim)) }
        Box(
            Modifier.padding(end = 6.dp).size(48.dp).clip(RoundedCornerShape(12.dp)).background(p.accent).clickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) { Text("☎", style = px(16, onColor(p.accent))) }
        Spacer(Modifier.width(4.dp))
    }
}
