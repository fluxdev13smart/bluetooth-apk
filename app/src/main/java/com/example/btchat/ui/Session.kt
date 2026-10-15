package com.example.btchat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Size as AndroidSize
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.btchat.core.Engine
import com.example.btchat.core.EngineState
import com.example.btchat.core.FileItem
import com.example.btchat.core.FileStatus
import com.example.btchat.core.Item
import com.example.btchat.core.NoteItem
import com.example.btchat.core.Peer
import com.example.btchat.core.TextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SessionScreen(st: EngineState, peer: Peer, act: Actions) {
    val list = st.items
    val listState = rememberLazyListState()
    var text by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val active = list.any { it is FileItem && it.status == FileStatus.Active }
    val view = LocalView.current

    LaunchedEffect(list.size) { if (list.isNotEmpty()) listState.animateScrollToItem(list.size - 1) }
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
    val leave: () -> Unit = { if (active) { confirm = true } else { Engine.disconnect() } }
    BackHandler(onBack = leave)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(peer.name, leave)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (list.size <= 1) {
                    Empty(peer.name, act)
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { item -> TimelineRow(item, peer.name) }
                    }
                }
            }
            Composer(text, { text = it }, act) {
                Engine.sendText(text)
                text = ""
            }
        }
        if (confirm) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { confirm = false },
                contentAlignment = Alignment.Center
            ) {
                Group(Modifier.padding(28.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BtText("A file is still sending", Ty.title)
                        BtText("If you disconnect now, the transfer stops.", Ty.body, P.text2)
                        Spacer(Modifier.size(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pill("Keep going", { confirm = false }, Modifier.weight(1f), filled = false, height = 46.dp)
                            Pill("Disconnect", { confirm = false; Engine.disconnect() }, Modifier.weight(1f), danger = true, height = 46.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(name: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
            Modifier.align(Alignment.CenterStart).pressable(onClick = onBack).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(Glyph.Back, P.accentText)
            Spacer(Modifier.width(4.dp))
            BtText("Disconnect", Ty.body, P.accentText)
        }
        Column(Modifier.align(Alignment.Center).widthIn(max = 180.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BtText(name, Ty.bodyB, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(P.ok))
                Spacer(Modifier.width(5.dp))
                BtText("Connected", Ty.small, P.text2)
            }
        }
    }
}

@Composable
private fun Empty(name: String, act: Actions) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LogoTile(64.dp)
        Spacer(Modifier.size(20.dp))
        BtText("You're connected", Ty.title, align = TextAlign.Center)
        BtText(
            "Say hi to $name, or send photos, videos and files straight over Bluetooth.",
            Ty.body, P.text2, Modifier.padding(top = 6.dp, bottom = 20.dp), align = TextAlign.Center
        )
        Pill("Choose files", act.pickFiles, glyph = Glyph.Plus)
    }
}

@Composable
private fun Composer(text: String, onText: (String) -> Unit, act: Actions, onSend: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(44.dp).pressable(haptic = true, onClick = act.pickFiles).clip(CircleShape).background(P.high),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(Glyph.Plus, P.text) }
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(P.surface)
                .border(0.5.dp, P.line, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = Ty.body.copy(color = P.text),
                cursorBrush = SolidColor(P.accent),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) BtText("Message", Ty.body, P.text3)
                        inner()
                    }
                }
            )
        }
        AnimatedVisibility(
            visible = text.isNotBlank(),
            enter = scaleIn(bouncy<Float>()) + fadeIn(),
            exit = scaleOut(quick<Float>()) + fadeOut()
        ) {
            Box(
                Modifier.size(44.dp).pressable(haptic = true, onClick = onSend).clip(CircleShape).background(P.accent),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(Glyph.Up, Color.White) }
        }
    }
}

/** New items settle in with a small spring instead of just appearing. */
@Composable
private fun Entrance(time: Long, content: @Composable () -> Unit) {
    val calm = com.example.btchat.ui.LocalCalm.current
    val fresh = remember { System.currentTimeMillis() - time < 800 }
    val a = remember { Animatable(if (fresh && !calm) 0f else 1f) }
    LaunchedEffect(Unit) { if (a.value < 1f) a.animateTo(1f, bouncy()) }
    Box(
        Modifier.graphicsLayer {
            alpha = a.value.coerceIn(0f, 1f)
            translationY = (1f - a.value) * 24.dp.toPx()
            val s = 0.96f + 0.04f * a.value
            scaleX = s
            scaleY = s
        }
    ) { content() }
}

@Composable
private fun TimelineRow(item: Item, peerName: String) {
    Entrance(item.time) {
        when (item) {
            is NoteItem -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                BtText(item.text, Ty.small, P.text3)
            }
            is TextItem -> Bubble(item)
            is FileItem -> FileCard(item, peerName)
        }
    }
}

@Composable
private fun Bubble(i: TextItem) {
    val out = i.outgoing
    val shape = RoundedCornerShape(20.dp, 20.dp, if (out) 6.dp else 20.dp, if (out) 20.dp else 6.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (out) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (out) P.accent else P.surface)
                .then(if (out) Modifier else Modifier.border(0.5.dp, P.line, shape))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) { BtText(i.text, Ty.body, if (out) Color.White else P.text) }
    }
}

@Composable
private fun FileCard(i: FileItem, peerName: String) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val ext = i.name.substringAfterLast('.', "").take(4).uppercase()
    val meta = buildString {
        append(fmtSize(i.size))
        if (ext.isNotEmpty()) append(" · ").append(ext)
    }
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (i.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 340.dp)
                .clip(shape)
                .background(P.surface)
                .border(0.5.dp, P.line, shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(i)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BtText(i.name, Ty.bodyB, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BtText(
                        if (i.status == FileStatus.Offered) "$peerName wants to send you this · $meta" else meta,
                        Ty.small, P.text2
                    )
                }
            }
            when (i.status) {
                FileStatus.Offered -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Decline", { view.hapticNo(); Engine.decline(i.id) }, Modifier.weight(1f), filled = false, height = 44.dp)
                    Pill("Accept", { Engine.accept(i.id) }, Modifier.weight(1f), height = 44.dp)
                }
                FileStatus.Waiting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    BtText("Waiting for $peerName to accept…", Ty.small, P.text2, Modifier.weight(1f))
                    TextBtn("Cancel", P.text2) { Engine.cancel(i.id) }
                }
                FileStatus.Active -> {
                    Bar(if (i.size > 0) i.done.toFloat() / i.size else 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val eta = if (i.speed > 0) " · " + fmtEta((i.size - i.done) / i.speed) else ""
                        val speed = if (i.speed > 0) " · ${fmtSize(i.speed)}/s" else ""
                        BtText("${fmtSize(i.done)} of ${fmtSize(i.size)}$speed$eta", Ty.small, P.text2, Modifier.weight(1f))
                        TextBtn("Cancel", P.text2) { Engine.cancel(i.id) }
                    }
                }
                FileStatus.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Check, P.ok, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    BtText(if (i.outgoing) "Sent" else "Saved to Downloads / BT Chat", Ty.small, P.text2, Modifier.weight(1f))
                    if (!i.outgoing && i.uri != null) TextBtn("Open") { openFile(ctx, i.uri, i.mime) }
                }
                else -> BtText(
                    i.note ?: i.status.name,
                    Ty.small,
                    if (i.status == FileStatus.Failed) P.bad else P.text2
                )
            }
        }
    }
}

@Composable
private fun Thumb(i: FileItem) {
    val ctx = LocalContext.current
    val src = i.uri
    val media = i.mime.startsWith("image/") || i.mime.startsWith("video/")
    val bmp by produceState<ImageBitmap?>(null, src, i.status) {
        value = null
        if (src != null && media && (i.outgoing || i.status == FileStatus.Done)) {
            value = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.loadThumbnail(Uri.parse(src), AndroidSize(160, 160), null).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(P.soft), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else GlyphIcon(Glyph.File, P.accentText)
    }
}

private fun openFile(ctx: Context, uri: String, mime: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(uri), mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}
