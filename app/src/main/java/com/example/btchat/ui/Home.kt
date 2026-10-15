package com.example.btchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.btchat.core.Device
import com.example.btchat.core.Engine
import com.example.btchat.core.EngineState
import com.example.btchat.core.Link

class Actions(
    val askPermissions: () -> Unit,
    val enableBt: () -> Unit,
    val makeVisible: () -> Unit,
    val pickFiles: () -> Unit,
    val openLocation: () -> Unit,
    val openDownloads: () -> Unit,
    val quit: () -> Unit
)

class Perms(val bluetooth: Boolean, val needsLocation: Boolean, val locationOn: Boolean)

@Composable
fun HomeScreen(st: EngineState, perms: Perms, act: Actions) {
    val connecting = st.link as? Link.Connecting
    val ready = perms.bluetooth && st.btOn && (!perms.needsLocation || perms.locationOn)
    val nearby = st.devices.filter { it.seen }
    val paired = st.devices.filter { it.bonded && !it.seen }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 170.dp)
        ) {
            item { Header(st) }
            item { Hero(st, perms, act, connecting, ready) }
            if (ready && nearby.isNotEmpty()) {
                item { SectionLabel("Nearby") }
                item { DeviceGroup(nearby, connecting) }
            }
            if (ready && paired.isNotEmpty()) {
                item { SectionLabel("Paired") }
                item { DeviceGroup(paired, connecting) }
            }
            item { SectionLabel("Settings") }
            item { SettingsGroup(st, act) }
        }
        BottomBar(st, perms, act, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(st: EngineState) {
    Column(Modifier.statusBarsPadding().padding(top = 14.dp, start = 4.dp)) {
        LogoTile(38.dp)
        Spacer(Modifier.height(18.dp))
        BtText("Nearby", Ty.large)
        val sub = if (st.myName.isEmpty()) "Send messages and files over Bluetooth"
        else if (st.visible) "Visible to everyone nearby as ${st.myName}"
        else "Showing as ${st.myName} to paired devices"
        BtText(sub, Ty.small, P.text2, Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Hero(st: EngineState, perms: Perms, act: Actions, connecting: Link.Connecting?, ready: Boolean) {
    when {
        !perms.bluetooth -> Gate(
            "Allow Bluetooth",
            "BT Chat uses Bluetooth to find people nearby and send messages and files straight to their phone. Nothing goes through the internet.",
            "Allow", act.askPermissions
        )
        !st.btOn -> Gate("Bluetooth is off", "Turn it on to find devices nearby.", "Turn on", act.enableBt)
        !ready -> Gate(
            "Turn on Location",
            "Android 10 and 11 only let apps find nearby Bluetooth devices while Location is on. BT Chat never uses or stores your location.",
            "Open settings", act.openLocation
        )
        else -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Radar(st.scanning && connecting == null, st.devices.filter { it.seen }, Modifier.fillMaxWidth().height(240.dp))
            val title = when {
                connecting != null -> "Connecting to ${connecting.name}…"
                st.devices.any { it.seen } -> "Pick a device"
                st.scanning -> "Looking nearby…"
                else -> "Nobody in range yet"
            }
            BtText(title, Ty.title, align = TextAlign.Center)
            BtText(
                "On the other phone, open BT Chat and tap Make me visible.",
                Ty.small, P.text2, Modifier.padding(top = 4.dp, bottom = 4.dp), align = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Gate(title: String, body: String, button: String, onClick: () -> Unit) {
    Group(Modifier.padding(top = 28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BtText(title, Ty.title)
            BtText(body, Ty.body, P.text2)
            Spacer(Modifier.height(8.dp))
            Pill(button, onClick, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DeviceGroup(list: List<Device>, connecting: Link.Connecting?) {
    Group {
        list.forEachIndexed { i, d ->
            if (i > 0) Divider()
            DeviceRow(d, connecting)
        }
    }
}

@Composable
private fun DeviceRow(d: Device, connecting: Link.Connecting?) {
    val busy = connecting?.address == d.address
    val sub = buildString {
        append(if (d.bonded) "Paired" else "Not paired yet")
        d.rssi?.let {
            append(" · ")
            append(if (it >= -60) "Very close" else if (it >= -75) "Close" else "Further away")
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = connecting == null, haptic = true) { Engine.connect(d.address) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(P.soft), contentAlignment = Alignment.Center) {
            GlyphIcon(if (d.computer) Glyph.Laptop else Glyph.Phone, P.accentText)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BtText(d.name, Ty.bodyB, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BtText(sub, Ty.small, P.text2)
        }
        Spacer(Modifier.width(8.dp))
        BtText(
            if (busy) "Connecting…" else if (d.bonded) "Connect" else "Pair",
            Ty.bodyB, P.accentText
        )
    }
}

@Composable
private fun SettingsGroup(st: EngineState, act: Actions) {
    Group {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BtText("Read messages aloud", Ty.bodyB)
                BtText(
                    "Handy in the car. If your phone is connected to the car, messages play through its speakers.",
                    Ty.small, P.text2, Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Toggle(st.carMode) { Engine.setCarMode(it) }
        }
        Divider(16.dp)
        Row(
            Modifier.fillMaxWidth().pressable(onClick = act.openDownloads).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                BtText("Received files", Ty.bodyB)
                BtText("Downloads / BT Chat", Ty.small, P.text2, Modifier.padding(top = 2.dp))
            }
        }
        Divider(16.dp)
        Row(Modifier.fillMaxWidth().pressable(onClick = act.quit).padding(16.dp)) {
            BtText("Quit BT Chat", Ty.bodyB, P.bad)
        }
    }
}

@Composable
private fun BottomBar(st: EngineState, perms: Perms, act: Actions, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, P.bg, P.bg)))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (st.pending.isNotEmpty()) {
            val n = st.pending.size
            Group {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.File, P.accentText)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        BtText(if (n == 1) "1 file ready" else "$n files ready", Ty.bodyB)
                        BtText("Pick a device above to send", Ty.small, P.text2)
                    }
                    Box(
                        Modifier.size(40.dp).pressable { Engine.clearPending() },
                        contentAlignment = Alignment.Center
                    ) { GlyphIcon(Glyph.Close, P.text2, 18.dp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill(
                if (st.visible) "Visible" else "Make me visible",
                act.makeVisible, Modifier.weight(1f),
                filled = false, enabled = perms.bluetooth && st.btOn && !st.visible
            )
            Pill("Send files", act.pickFiles, Modifier.weight(1f), glyph = Glyph.Plus, enabled = perms.bluetooth)
        }
    }
}
