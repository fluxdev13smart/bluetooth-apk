package com.example.btchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.btchat.core.Engine
import com.example.btchat.core.Saved
import com.example.btchat.core.SavedItem

/** Tiny navigation flag: the Saved screen slides over whatever is showing. */
object Nav {
    var saved by mutableStateOf(false)
}

@Composable
fun ActionChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    BtText(
        label, Ty.small.copy(fontWeight = FontWeight.SemiBold), P.accentText,
        Modifier
            .pressable(haptic = true, onClick = onClick)
            .clip(shape)
            .background(P.surface)
            .border(0.5.dp, P.line, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        maxLines = 1
    )
}

@Composable
fun SavedScreen(connected: Boolean) {
    val items by Saved.items.collectAsState()
    var editor by remember { mutableStateOf<SavedItem?>(null) }
    BackHandler { if (editor != null) editor = null else Nav.saved = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(P.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    Modifier.align(Alignment.CenterStart).pressable(onClick = { Nav.saved = false }).padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(Glyph.Back, P.accentText)
                    Spacer(Modifier.width(4.dp))
                    BtText("Back", Ty.body, P.accentText)
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).size(44.dp).pressable(haptic = true, onClick = { editor = SavedItem(0L, "", "") }),
                    contentAlignment = Alignment.Center
                ) { GlyphIcon(Glyph.Plus, P.accentText) }
            }
            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GlyphIcon(Glyph.Star, P.accentText, 44.dp)
                    Spacer(Modifier.size(16.dp))
                    BtText("Nothing saved yet", Ty.title, align = TextAlign.Center)
                    BtText(
                        "Keep places, addresses or phrases here. Copy them, or send them to the other phone in one tap. For example: Park, with its coordinates.",
                        Ty.body, P.text2, Modifier.padding(top = 6.dp, bottom = 20.dp), align = TextAlign.Center
                    )
                    Pill("Add one", { editor = SavedItem(0L, "", "") }, Modifier.width(180.dp), glyph = Glyph.Plus)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
                            BtText("Saved", Ty.large)
                            BtText(
                                if (connected) "Tap Send to put it straight into the chat." else "Copy, then paste anywhere.",
                                Ty.small, P.text2, Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    items(items, key = { it.id }) { s -> SavedCard(s, connected) { editor = s } }
                }
            }
        }
        editor?.let { Editor(it) { editor = null } }
    }
}

@Composable
private fun SavedCard(s: SavedItem, connected: Boolean, onEdit: () -> Unit) {
    Group {
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BtText(s.title, Ty.bodyB, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextBtn("Edit", onClick = onEdit)
            }
            BtText(s.text, Ty.body, P.text2, Modifier.padding(end = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(end = 6.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Copy", { Engine.copy(s.text) }, Modifier.weight(1f), filled = !connected, height = 42.dp)
                if (connected) Pill("Send", { Engine.sendText(s.text); Engine.toast("Sent") }, Modifier.weight(1f), height = 42.dp)
            }
        }
    }
}

@Composable
private fun Editor(item: SavedItem, onClose: () -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var text by remember { mutableStateOf(item.text) }
    val isNew = item.id == 0L
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Group(
            Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BtText(if (isNew) "New saved item" else "Edit saved item", Ty.title)
                Field(title, { title = it }, "Heading, e.g. Park", true)
                Field(text, { text = it }, "Text to copy, e.g. coordinates or an address", false)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isNew) {
                        Pill("Delete", { Saved.delete(item.id); onClose() }, Modifier.weight(1f), filled = false, danger = true, height = 46.dp)
                    }
                    Pill(
                        "Save",
                        { Saved.upsert(item.id, title, text); onClose() },
                        Modifier.weight(1f), enabled = text.isNotBlank(), height = 46.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(P.high)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = Ty.body.copy(color = P.text),
            cursorBrush = SolidColor(P.accent),
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BtText(hint, Ty.body, P.text3)
                    inner()
                }
            }
        )
    }
}
