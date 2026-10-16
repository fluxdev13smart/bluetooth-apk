package com.example.btchat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.btchat.core.Engine
import com.example.btchat.core.EngineState
import com.example.btchat.core.FileItem
import com.example.btchat.core.FileStatus
import com.example.btchat.core.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps BT Chat alive in the background so connections and transfers continue
 * when you switch apps or the screen turns off. Shows one quiet notification.
 */
class ConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    private data class Summary(val running: Boolean, val title: String, val text: String, val busy: Boolean)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) {
            Engine.shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        Notifier.ensureChannels(this)
        try {
            ServiceCompat.startForeground(
                this, Notifier.ONGOING_ID,
                Notifier.ongoing(this, "BT Chat", "Ready to connect"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = scope.launch {
                Engine.state.map { summarize(it) }.distinctUntilChanged().collect { s ->
                    if (!s.running) {
                        stopSelf()
                        return@collect
                    }
                    Notifier.update(this@ConnectionService, s.title, s.text)
                    setWake(s.busy)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun summarize(st: EngineState): Summary {
        val active = st.items.filterIsInstance<FileItem>().filter { it.status == FileStatus.Active }
        val link = st.link
        val title = when (link) {
            is Link.Connected -> "Connected to ${link.peer.name}"
            is Link.Connecting -> "Connecting to ${link.name}…"
            Link.Idle -> "BT Chat"
        }
        val text = when {
            active.isNotEmpty() -> {
                val done = active.sumOf { it.done }
                val size = active.sumOf { it.size }
                val pct = if (size > 0) (done * 100 / size).toInt() else 100
                val verb = if (active.any { !it.outgoing }) "Receiving" else "Sending"
                "$verb ${active.size} file${if (active.size == 1) "" else "s"} · $pct%"
            }
            link is Link.Connected -> "Tap to open"
            else -> "Ready. Open BT Chat on the other phone"
        }
        return Summary(st.running, title, text, active.isNotEmpty())
    }

    private fun setWake(on: Boolean) {
        if (on && wake == null) {
            val pm = getSystemService(PowerManager::class.java)
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "btchat:transfer").apply { acquire(30 * 60 * 1000L) }
        } else if (!on) {
            wake?.let { if (it.isHeld) it.release() }
            wake = null
        }
    }

    override fun onDestroy() {
        job?.cancel()
        setWake(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_QUIT = "com.example.btchat.QUIT"

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ConnectionService::class.java))
            } catch (e: Exception) {
                // Android may refuse to start it from the background; the app still works while open.
            }
        }
    }
}
