package com.example.btchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.core.Engine
import com.example.btchat.core.EngineState
import com.example.btchat.core.Link
import com.example.btchat.core.Saved
import com.example.btchat.core.SavedItem
import com.example.btchat.core.TextItem

/** Big, simple layout for a car display: latest message, one Copy button, saved items as large tiles. */
@Composable
fun CarScreen(st: EngineState) {
    val saved by Saved.items.collectAsState()
    val link = st.link
    val connected = link is Link.Connected
    val last = st.items.lastOrNull { it is TextItem && !it.outgoing } as? TextItem
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { Nav.car = false }

    Column(
        Modifier
            .fillMaxSize()
            .background(P.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (connected) P.ok else P.text3))
            Spacer(Modifier.width(10.dp))
            BtText(
                if (link is Link.Connected) link.peer.name else "Not connected",
                Ty.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Pill("Exit", { Nav.car = false }, Modifier.width(120.dp), filled = false, height = 56.dp)
        }
        Group(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BtText("LATEST MESSAGE", Ty.cap, P.text3)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    BtText(
                        last?.text ?: "Nothing yet",
                        Ty.large.copy(fontSize = 38.sp, lineHeight = 46.sp),
                        if (last != null) P.text else P.text3,
                        maxLines = 5, overflow = TextOverflow.Ellipsis
                    )
                }
                Pill("Copy", { last?.let { Engine.copy(it.text) } }, Modifier.fillMaxWidth(), enabled = last != null, height = 68.dp)
            }
        }
        if (saved.isNotEmpty()) {
            BtText("SAVED", Ty.cap, P.text3)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(saved, key = { it.id }) { s -> CarTile(s, connected) }
            }
        }
    }
}

@Composable
private fun CarTile(s: SavedItem, connected: Boolean) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .width(230.dp)
            .pressable(haptic = true) {
                if (connected) { Engine.sendText(s.text); Engine.toast("Sent") } else Engine.copy(s.text)
            }
            .clip(shape)
            .background(P.surface)
            .border(0.5.dp, P.line, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BtText(s.title, Ty.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BtText(s.text, Ty.body, P.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        BtText(if (connected) "Tap to send" else "Tap to copy", Ty.bodyB, P.accentText)
    }
}
