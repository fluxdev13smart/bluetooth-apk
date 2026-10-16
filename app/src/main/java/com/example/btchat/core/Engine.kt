package com.example.btchat.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.btchat.ConnectionService
import com.example.btchat.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Everything Bluetooth lives here: discovery, pairing, one RFCOMM link, and a tiny
 * framed protocol that carries chat text and files over that single link.
 *
 * Frames: [type byte] + payload. Text and file chunks interleave, so you can chat while a file sends.
 */
@SuppressLint("MissingPermission") // MainActivity asks for the permissions before calling start()
object Engine {
    private val APP_UUID: UUID = UUID.fromString("c1f4a7d2-52b3-4f0e-9a64-3b7e8d1c0a95")
    private const val SERVICE = "BTChat2"
    private const val CHUNK = 16 * 1024
    private const val MAX_CHUNK = 64 * 1024
    private const val MAX_TEXT = 256 * 1024

    private const val T_HELLO = 0
    private const val T_TEXT = 1
    private const val T_OFFER = 2
    private const val T_ACCEPT = 3
    private const val T_DECLINE = 4
    private const val T_CHUNK = 5
    private const val T_END = 6
    private const val T_CANCEL = 7
    private const val T_RECEIVED = 8

    private lateinit var app: Context
    private var adapter: BluetoothAdapter? = null
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private val ids = AtomicLong(1)
    private val io = Executors.newSingleThreadExecutor()
    private val streamer = Executors.newSingleThreadExecutor()

    private val known = LinkedHashMap<String, Device>()
    private val lastSeen = HashMap<String, Long>()
    /** address -> true if the device advertises our service (BT Chat is open there), false if checked and not. */
    private val appState = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val appChecked = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val bondLatch = ConcurrentHashMap<String, CountDownLatch>()
    private val bondResult = ConcurrentHashMap<String, Int>()
    private val outs = ConcurrentHashMap<Long, Out>()
    private val ins = ConcurrentHashMap<Long, In>()
    private val prog = ConcurrentHashMap<Long, LongArray>()

    private var speaker: Speaker? = null
    @Volatile private var session: Session? = null
    @Volatile private var started = false
    @Volatile private var uiVisible = false
    private var wantScan = false
    private var acceptThread: Thread? = null
    private var server: BluetoothServerSocket? = null

    private class Session(val socket: BluetoothSocket, val address: String, var name: String) {
        val input = DataInputStream(BufferedInputStream(socket.inputStream, 32 * 1024))
        val output = DataOutputStream(BufferedOutputStream(socket.outputStream, 32 * 1024))
        private val lock = Any()
        @Volatile var closed = false
        @Volatile var byUser = false

        fun send(block: DataOutputStream.() -> Unit): Boolean {
            if (closed) return false
            return try {
                synchronized(lock) {
                    output.block()
                    output.flush()
                }
                true
            } catch (e: IOException) {
                close()
                false
            }
        }

        fun close() {
            closed = true
            try { socket.close() } catch (_: IOException) {}
        }
    }

    private class Out(val tid: Long, val itemId: Long, val uri: Uri, val size: Long) {
        @Volatile var cancelled = false
    }

    private class In(val tid: Long, val itemId: Long, val name: String, val size: Long, val mime: String) {
        @Volatile var sink: FileStore.Sink? = null
        @Volatile var dead = false
        var got = 0L
    }

    /** Reads incoming messages aloud through whatever audio output is active (phone speaker, headset, car). */
    private class Speaker(private val ctx: Context) {
        private var tts: TextToSpeech? = null
        private var ready = false
        private val queued = ArrayList<String>()
        private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()

        fun speak(text: String) {
            if (tts == null) {
                tts = TextToSpeech(ctx) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    if (ready) {
                        tts?.setAudioAttributes(attrs)
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String?) {}
                            override fun onDone(id: String?) { am.abandonAudioFocusRequest(focus) }
                            @Deprecated("Deprecated in Java")
                            override fun onError(id: String?) { am.abandonAudioFocusRequest(focus) }
                        })
                        queued.forEach { say(it) }
                        queued.clear()
                    }
                }
            }
            if (ready) say(text) else queued.add(text)
        }

        private fun say(text: String) {
            val t = tts ?: return
            val arabic = text.any { it in '\u0600'..'\u06FF' }
            val loc = if (arabic) Locale("ar") else Locale.getDefault()
            if (t.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) t.language = loc
            am.requestAudioFocus(focus)
            t.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
        }
    }

    private fun now() = System.currentTimeMillis()
    private fun seq() = ids.getAndIncrement()

    // ---------------------------------------------------------------- lifecycle

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        val sp0 = app.getSharedPreferences("btchat", Context.MODE_PRIVATE)
        _state.update { it.copy(carMode = sp0.getBoolean("car", false), autoCopy = sp0.getBoolean("autocopy", false)) }
        adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_UUID)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(app, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    /** Call once the Bluetooth permissions are granted. Safe to call repeatedly. */
    fun start() {
        started = true
        _state.update { it.copy(running = true) }
        ConnectionService.start(app)
        refresh()
        listen()
        maybeScan()
    }

    fun shutdown() {
        started = false
        _state.update { it.copy(running = false) }
        stopScan()
        session?.let { it.byUser = true; it.close() }
        closeServer()
    }

    fun refresh() {
        val a = adapter ?: return
        val on = try { a.isEnabled } catch (e: Exception) { false }
        val name = try { a.name } catch (e: Exception) { null } ?: Build.MODEL
        val vis = try { a.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE } catch (e: Exception) { false }
        _state.update { it.copy(btOn = on, myName = name, visible = vis) }
        if (on && started) {
            loadBonded()
            listen()
        }
    }

    fun setUiVisible(v: Boolean) {
        uiVisible = v
        if (v) maybeScan() else stopScan()
    }

    // ---------------------------------------------------------------- discovery

    private fun loadBonded() {
        val a = adapter ?: return
        try {
            for (d in a.bondedDevices) upsert(d, null, false)
        } catch (_: Exception) {}
        publishDevices()
    }

    private fun upsert(d: BluetoothDevice, rssi: Int?, seen: Boolean) {
        val c = try { d.bluetoothClass?.majorDeviceClass } catch (e: Exception) { null }
        val okClass = c == null || c == BluetoothClass.Device.Major.PHONE ||
            c == BluetoothClass.Device.Major.COMPUTER || c == BluetoothClass.Device.Major.UNCATEGORIZED
        if (!okClass) return
        if (seen) lastSeen[d.address] = now()
        val prev = known[d.address]
        val nm = try { d.name } catch (e: Exception) { null } ?: prev?.name ?: "Unknown device"
        known[d.address] = Device(
            address = d.address,
            name = nm,
            bonded = d.bondState == BluetoothDevice.BOND_BONDED,
            rssi = rssi ?: prev?.rssi,
            seen = false,
            computer = c == BluetoothClass.Device.Major.COMPUTER
        )
        publishDevices()
    }

    private fun publishDevices() {
        val t = now()
        val list = known.values
            .map { it.copy(seen = (lastSeen[it.address] ?: 0L) > t - 40_000L, hasApp = appState[it.address]) }
            .sortedWith(compareByDescending<Device> { it.seen }.thenByDescending { it.hasApp == true }.thenByDescending { it.rssi ?: -200 }.thenBy { it.name.lowercase() })
        _state.update { it.copy(devices = list) }
    }

    private fun maybeScan() {
        val s = _state.value
        if (uiVisible && started && session == null && s.link is Link.Idle && s.btOn) startScan()
    }

    private fun startScan() {
        wantScan = true
        val a = adapter ?: return
        try {
            if (a.isEnabled && !a.isDiscovering) a.startDiscovery()
        } catch (_: SecurityException) {}
    }

    private fun stopScan() {
        wantScan = false
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val r = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    upsert(d, if (r == Short.MIN_VALUE) null else r.toInt(), true)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _state.update { it.copy(scanning = true) }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _state.update { it.copy(scanning = false) }
                    publishDevices()
                    checkApps()
                    if (wantScan) main.postDelayed({ if (wantScan) startScan() }, 2000)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                    if (s == BluetoothAdapter.STATE_OFF) teardown()
                    refresh()
                    if (s == BluetoothAdapter.STATE_ON) maybeScan()
                }
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> refresh()
                BluetoothDevice.ACTION_UUID -> {
                    val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    @Suppress("DEPRECATION")
                    val raw = i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID) ?: return
                    val has = raw.any { (it as? android.os.ParcelUuid)?.uuid == APP_UUID }
                    appState[d.address] = has
                    publishDevices()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val st = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    if (st == BluetoothDevice.BOND_BONDED || st == BluetoothDevice.BOND_NONE) {
                        bondResult[d.address] = st
                        bondLatch[d.address]?.countDown()
                    }
                    loadBonded()
                }
            }
        }
    }

    /** Asks nearby devices which services they offer, so we can tell who has BT Chat open. */
    private fun checkApps() {
        if (session != null || _state.value.link !is Link.Idle) return
        val t = now()
        val a = adapter ?: return
        known.values
            .filter { (lastSeen[it.address] ?: 0L) > t - 40_000L && t - (appChecked[it.address] ?: 0L) > 12_000L }
            .take(6)
            .forEach {
                appChecked[it.address] = t
                try { a.getRemoteDevice(it.address).fetchUuidsWithSdp() } catch (_: Exception) {}
            }
    }

    private fun teardown() {
        session?.close()
        closeServer()
        _state.update { it.copy(link = Link.Idle, scanning = false) }
    }

    // ---------------------------------------------------------------- connecting

    @Synchronized
    private fun listen() {
        val a = adapter ?: return
        if (!started || session != null || acceptThread?.isAlive == true) return
        if (!(try { a.isEnabled } catch (e: Exception) { false })) return
        val ss = try {
            a.listenUsingRfcommWithServiceRecord(SERVICE, APP_UUID)
        } catch (e: Exception) {
            return
        }
        server = ss
        acceptThread = Thread {
            while (true) {
                val s = try { ss.accept() } catch (e: IOException) { break }
                if (session == null && _state.value.link is Link.Idle) {
                    try { ss.close() } catch (_: IOException) {}
                    val dev = s.remoteDevice
                    val nm = try { dev.name } catch (e: Exception) { null } ?: dev.address
                    established(s, dev.address, nm)
                    break
                } else {
                    try { s.close() } catch (_: IOException) {}
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun closeServer() {
        try { server?.close() } catch (_: IOException) {}
        server = null
    }

    fun connect(address: String) {
        val a = adapter ?: return
        if (!started || _state.value.link !is Link.Idle || session != null) return
        val dev = try { a.getRemoteDevice(address) } catch (e: Exception) { return }
        val nm = known[address]?.name ?: address
        stopScan()
        _state.update { it.copy(link = Link.Connecting(address, nm)) }
        Thread {
            var sock: BluetoothSocket? = null
            try {
                if (dev.bondState != BluetoothDevice.BOND_BONDED) {
                    val latch = CountDownLatch(1)
                    bondLatch[address] = latch
                    bondResult.remove(address)
                    if (!dev.createBond()) throw IOException("Pairing couldn't start")
                    latch.await(60, TimeUnit.SECONDS)
                    bondLatch.remove(address)
                    if (bondResult[address] != BluetoothDevice.BOND_BONDED) throw IOException("Pairing was cancelled")
                    Thread.sleep(900)
                }
                for (attempt in 1..2) {
                    try {
                        val s = dev.createRfcommSocketToServiceRecord(APP_UUID)
                        try {
                            s.connect()
                            sock = s
                            break
                        } catch (e: IOException) {
                            try { s.close() } catch (_: IOException) {}
                            if (attempt == 1) Thread.sleep(1200)
                        }
                    } catch (e: IOException) {
                        if (attempt == 1) Thread.sleep(1200)
                    }
                }
                val s = sock ?: throw IOException("Couldn't connect. Open BT Chat on $nm and try again.")
                established(s, address, nm)
            } catch (e: Exception) {
                main.post {
                    _state.update { it.copy(link = Link.Idle, notice = e.message ?: "Couldn't connect") }
                    listen()
                    maybeScan()
                }
            }
        }.start()
    }

    private fun established(sock: BluetoothSocket, address: String, name: String) {
        val s = try {
            Session(sock, address, name)
        } catch (e: IOException) {
            try { sock.close() } catch (_: IOException) {}
            main.post { _state.update { it.copy(link = Link.Idle) }; listen() }
            return
        }
        synchronized(this) {
            if (session != null) { s.close(); return }
            session = s
        }
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}
        wantScan = false
        _state.update {
            it.copy(
                link = Link.Connected(Peer(address, name)),
                items = listOf(NoteItem(seq(), now(), "Connected to $name")),
                scanning = false,
                notice = null
            )
        }
        val me = _state.value.myName
        s.send { writeByte(T_HELLO); writeByte(1); writeUTF(me.take(100)) }
        if (!uiVisible) Notifier.message(app, name, "Connected over Bluetooth")
        flushPending()
        Thread { readLoop(s) }.also { it.isDaemon = true; it.start() }
    }

    fun disconnect() {
        session?.let { s -> s.byUser = true; io.execute { s.close() } }
    }

    private fun ended(s: Session) {
        s.close()
        synchronized(this) {
            if (session !== s) return
            session = null
        }
        outs.values.forEach { it.cancelled = true }
        outs.clear()
        ins.values.forEach { it.dead = true; it.sink?.discard() }
        ins.clear()
        val live = setOf(FileStatus.Offered, FileStatus.Waiting, FileStatus.Active)
        _state.update { st ->
            val fixed = st.items.map {
                if (it is FileItem && it.status in live) it.copy(status = FileStatus.Failed, note = "Connection lost", speed = 0) else it
            }
            st.copy(
                link = Link.Idle,
                items = fixed + NoteItem(seq(), now(), if (s.byUser) "Disconnected" else "Connection lost"),
                notice = if (s.byUser) null else "Connection to ${s.name} was lost"
            )
        }
        main.post { listen(); maybeScan() }
    }

    // ---------------------------------------------------------------- reading

    private fun readLoop(s: Session) {
        val buf = ByteArray(MAX_CHUNK)
        try {
            val inp = s.input
            while (true) {
                when (val t = inp.readUnsignedByte()) {
                    T_HELLO -> {
                        inp.readUnsignedByte()
                        val n = inp.readUTF()
                        s.name = n
                        _state.update { st ->
                            val l = st.link
                            if (l is Link.Connected) st.copy(link = Link.Connected(l.peer.copy(name = n))) else st
                        }
                    }
                    T_TEXT -> {
                        val len = inp.readInt()
                        if (len < 0 || len > MAX_TEXT) throw IOException("bad text")
                        val b = ByteArray(len)
                        inp.readFully(b)
                        onText(String(b, Charsets.UTF_8))
                    }
                    T_OFFER -> {
                        val tid = inp.readLong()
                        val name = inp.readUTF()
                        val size = inp.readLong()
                        val mime = inp.readUTF()
                        onOffer(tid, name, size, mime)
                    }
                    T_ACCEPT -> onAccepted(s, inp.readLong())
                    T_DECLINE -> onDeclined(inp.readLong())
                    T_CHUNK -> {
                        val tid = inp.readLong()
                        val len = inp.readInt()
                        if (len < 0 || len > MAX_CHUNK) throw IOException("bad chunk")
                        inp.readFully(buf, 0, len)
                        onChunk(s, tid, buf, len)
                    }
                    T_END -> onEnd(s, inp.readLong())
                    T_CANCEL -> onCancelled(inp.readLong())
                    T_RECEIVED -> {
                        val tid = inp.readLong()
                        onReceived(tid, inp.readBoolean())
                    }
                    else -> throw IOException("unknown frame $t")
                }
            }
        } catch (_: IOException) {
        } finally {
            ended(s)
        }
    }

    private fun peerName(): String = (_state.value.link as? Link.Connected)?.peer?.name ?: "Message"

    private fun onText(t: String) {
        addItem(TextItem(seq(), now(), false, t))
        if (!uiVisible) Notifier.message(app, peerName(), t)
        if (_state.value.autoCopy) main.post { copyQuiet(t) }
        if (_state.value.carMode) {
            val who = peerName()
            main.post {
                val sp = speaker ?: Speaker(app).also { speaker = it }
                sp.speak("$who says: ${t.take(1500)}")
            }
        }
    }

    private fun onOffer(tid: Long, name: String, size: Long, mime: String) {
        val id = seq()
        val safe = FileStore.clean(name)
        ins[tid] = In(tid, id, safe, size, mime)
        addItem(FileItem(id, now(), false, safe, size, mime, FileStatus.Offered))
        if (!uiVisible) Notifier.message(app, peerName(), "wants to send you $safe")
    }

    private fun onChunk(s: Session, tid: Long, buf: ByteArray, len: Int) {
        val inn = ins[tid] ?: return
        val sink = inn.sink ?: return
        if (inn.dead) return
        try {
            sink.write(buf, 0, len)
            inn.got += len
            progress(inn.itemId, inn.got, inn.size)
        } catch (e: IOException) {
            if (inn.dead) return
            inn.dead = true
            sink.discard()
            ins.remove(tid)
            setFile(inn.itemId) { it.copy(status = FileStatus.Failed, note = "Couldn't save the file. Is storage full?", speed = 0) }
            s.send { writeByte(T_CANCEL); writeLong(tid) }
        }
    }

    private fun onEnd(s: Session, tid: Long) {
        val inn = ins.remove(tid) ?: return
        val sink = inn.sink ?: return
        val complete = inn.got == inn.size && !inn.dead
        val uri = if (complete) sink.finish() else { sink.discard(); null }
        val ok = complete && uri != null
        setFile(inn.itemId) {
            it.copy(
                status = if (ok) FileStatus.Done else FileStatus.Failed,
                done = inn.got, speed = 0, uri = uri,
                note = if (ok) null else "The file didn't arrive completely"
            )
        }
        s.send { writeByte(T_RECEIVED); writeLong(tid); writeBoolean(ok) }
    }

    private fun onAccepted(s: Session, tid: Long) {
        val out = outs[tid] ?: return
        setFile(out.itemId) { it.copy(status = FileStatus.Active) }
        streamer.execute { stream(s, out) }
    }

    private fun onDeclined(tid: Long) {
        val out = outs.remove(tid) ?: return
        setFile(out.itemId) { it.copy(status = FileStatus.Declined, note = "${peerName()} declined") }
    }

    private fun onReceived(tid: Long, ok: Boolean) {
        val out = outs.remove(tid) ?: return
        setFile(out.itemId) {
            it.copy(
                status = if (ok) FileStatus.Done else FileStatus.Failed,
                done = it.size, speed = 0,
                note = if (ok) null else "The other phone couldn't save it"
            )
        }
    }

    private fun onCancelled(tid: Long) {
        val out = outs.remove(tid)
        if (out != null) {
            out.cancelled = true
            setFile(out.itemId) { it.copy(status = FileStatus.Cancelled, note = "Cancelled by ${peerName()}", speed = 0) }
        }
        val inn = ins.remove(tid)
        if (inn != null) {
            inn.dead = true
            inn.sink?.discard()
            setFile(inn.itemId) { it.copy(status = FileStatus.Cancelled, note = "Cancelled by ${peerName()}", speed = 0) }
        }
    }

    // ---------------------------------------------------------------- sending

    fun sendText(raw: String) {
        val text = raw.trim().take(20_000)
        if (text.isEmpty()) return
        val s = session ?: return
        addItem(TextItem(seq(), now(), true, text))
        io.execute {
            val b = text.toByteArray(Charsets.UTF_8)
            s.send { writeByte(T_TEXT); writeInt(b.size); write(b) }
        }
    }

    /** Files picked or shared into the app. Sent right away if connected, otherwise held until a device is chosen. */
    fun queueFiles(uris: List<Uri>) {
        io.execute {
            val list = uris.mapNotNull { FileStore.describe(app, it) }
            if (list.size < uris.size) notice("Some files couldn't be read")
            if (list.isEmpty()) return@execute
            if (session != null) list.forEach { offer(it) } else _state.update { it.copy(pending = it.pending + list) }
        }
    }

    fun clearPending() = _state.update { it.copy(pending = emptyList()) }

    /** A message written on the home screen, sent as soon as a device is chosen and connected. */
    fun setPendingText(t: String) = _state.update { it.copy(pendingText = t) }

    private fun flushPending() {
        val p = _state.value.pending
        val t = _state.value.pendingText
        if (p.isEmpty() && t.isBlank()) return
        _state.update { it.copy(pending = emptyList(), pendingText = "") }
        if (t.isNotBlank()) sendText(t)
        io.execute { p.forEach { offer(it) } }
    }

    private fun offer(pf: PendingFile) {
        val s = session ?: return
        val tid = seq()
        val id = seq()
        outs[tid] = Out(tid, id, pf.uri, pf.size)
        addItem(FileItem(id, now(), true, pf.name, pf.size, pf.mime, FileStatus.Waiting, uri = pf.uri.toString()))
        s.send { writeByte(T_OFFER); writeLong(tid); writeUTF(pf.name); writeLong(pf.size); writeUTF(pf.mime) }
    }

    private fun stream(s: Session, out: Out) {
        var sent = 0L
        try {
            val src = app.contentResolver.openInputStream(out.uri) ?: throw IOException("Can't read file")
            src.use { input ->
                val buf = ByteArray(CHUNK)
                while (!out.cancelled && !s.closed) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (!s.send { writeByte(T_CHUNK); writeLong(out.tid); writeInt(n); write(buf, 0, n) }) return
                    sent += n
                    progress(out.itemId, sent, out.size)
                }
            }
            if (out.cancelled || s.closed) return
            if (sent != out.size) {
                s.send { writeByte(T_CANCEL); writeLong(out.tid) }
                outs.remove(out.tid)
                setFile(out.itemId) { it.copy(status = FileStatus.Failed, note = "The file changed while sending", speed = 0) }
                return
            }
            s.send { writeByte(T_END); writeLong(out.tid) }
        } catch (e: Exception) {
            s.send { writeByte(T_CANCEL); writeLong(out.tid) }
            outs.remove(out.tid)
            setFile(out.itemId) { it.copy(status = FileStatus.Failed, note = "Couldn't read the file", speed = 0) }
        }
    }

    // ---------------------------------------------------------------- user actions on files

    fun accept(itemId: Long) = io.execute {
        val inn = ins.values.firstOrNull { it.itemId == itemId } ?: return@execute
        val s = session ?: return@execute
        val sink = FileStore.create(app, inn.name, inn.mime)
        if (sink == null) {
            ins.remove(inn.tid)
            s.send { writeByte(T_DECLINE); writeLong(inn.tid) }
            setFile(itemId) { it.copy(status = FileStatus.Failed, note = "Couldn't create the file") }
            return@execute
        }
        inn.sink = sink
        setFile(itemId) { it.copy(status = FileStatus.Active) }
        s.send { writeByte(T_ACCEPT); writeLong(inn.tid) }
    }

    fun decline(itemId: Long) = io.execute {
        val inn = ins.values.firstOrNull { it.itemId == itemId } ?: return@execute
        ins.remove(inn.tid)
        session?.send { writeByte(T_DECLINE); writeLong(inn.tid) }
        setFile(itemId) { it.copy(status = FileStatus.Declined, note = "You declined") }
    }

    fun cancel(itemId: Long) = io.execute {
        val s = session
        val out = outs.values.firstOrNull { it.itemId == itemId }
        if (out != null) {
            out.cancelled = true
            outs.remove(out.tid)
            s?.send { writeByte(T_CANCEL); writeLong(out.tid) }
            setFile(itemId) { it.copy(status = FileStatus.Cancelled, note = "Cancelled", speed = 0) }
            return@execute
        }
        val inn = ins.values.firstOrNull { it.itemId == itemId } ?: return@execute
        ins.remove(inn.tid)
        inn.dead = true
        inn.sink?.discard()
        s?.send { writeByte(T_CANCEL); writeLong(inn.tid) }
        setFile(itemId) { it.copy(status = FileStatus.Cancelled, note = "Cancelled", speed = 0) }
    }

    // ---------------------------------------------------------------- small helpers

    fun setCarMode(on: Boolean) {
        app.getSharedPreferences("btchat", Context.MODE_PRIVATE).edit().putBoolean("car", on).apply()
        _state.update { it.copy(carMode = on) }
    }

    fun setAutoCopy(on: Boolean) {
        app.getSharedPreferences("btchat", Context.MODE_PRIVATE).edit().putBoolean("autocopy", on).apply()
        _state.update { it.copy(autoCopy = on) }
    }

    private fun copyQuiet(t: String) {
        try {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BT Chat", t))
        } catch (_: Exception) {}
    }

    /** Copies to the clipboard and tells the user. */
    fun copy(text: String) {
        copyQuiet(text)
        notice("Copied")
    }

    fun toast(msg: String) = notice(msg)

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun notice(msg: String) = _state.update { it.copy(notice = msg) }

    private fun addItem(item: Item) = _state.update { it.copy(items = it.items + item) }

    private fun setFile(itemId: Long, f: (FileItem) -> FileItem) {
        _state.update { st ->
            st.copy(items = st.items.map { if (it.id == itemId && it is FileItem) f(it) else it })
        }
    }

    private fun progress(itemId: Long, bytes: Long, size: Long) {
        val t = now()
        val p = prog.getOrPut(itemId) { longArrayOf(t, 0L, 0L) }
        if (t - p[0] < 150 && bytes < size) return
        val dt = (t - p[0]).coerceAtLeast(1)
        val inst = (bytes - p[1]) * 1000 / dt
        p[2] = if (p[2] == 0L) inst else (p[2] * 6 + inst * 4) / 10
        p[0] = t
        p[1] = bytes
        val speed = p[2]
        setFile(itemId) { it.copy(done = bytes, speed = speed) }
    }
}
