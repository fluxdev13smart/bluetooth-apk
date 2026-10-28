package com.example.btchat

import android.Manifest
import android.app.DownloadManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.example.btchat.core.Engine
import com.example.btchat.ui.Actions
import com.example.btchat.ui.BtRoot
import com.example.btchat.ui.Perms

class MainActivity : ComponentActivity() {

    private var perms by mutableStateOf(Perms(bluetooth = false, needsLocation = false, locationOn = true))

    private val askPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        readPerms()
        if (perms.bluetooth) Engine.start()
    }
    private val enableBt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { Engine.refresh() }
    private val visibleReq = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { Engine.refresh() }
    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach {
            try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        if (uris.isNotEmpty()) Engine.queueFiles(uris)
    }

    private val actions by lazy {
        Actions(
            askPermissions = { askPerm.launch(requestList()) },
            enableBt = { external("bt") { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } },
            makeVisible = {
                external("visible") {
                    visibleReq.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    )
                }
            },
            pickFiles = { pick.launch(arrayOf("*/*")) },
            openLocation = { external("location") { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } },
            openDownloads = { external("downloads") { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) } },
            quit = { Engine.shutdown(); finishAndRemoveTask() }
        )
    }

    // ---- screens that throw you out of the app (some car head units) -------------------------

    private val prefs by lazy { getSharedPreferences("btchat", MODE_PRIVATE) }
    private var launchedExternal = false

    private fun manualMessage(key: String): String {
        val what = when (key) {
            "bt" -> "Turn Bluetooth on"
            "visible" -> "Make Bluetooth visible"
            "location" -> "Turn Location on"
            else -> "Open your downloads"
        }
        val where = if (key == "downloads") "in your Files app (Downloads / BT Chat)" else "in your device's settings"
        return "Not supported through the app on this screen. $what manually $where."
    }

    private fun isBlocked(key: String) = prefs.getStringSet("ext_blocked", emptySet())?.contains(key) == true

    private fun block(key: String) {
        val set = (prefs.getStringSet("ext_blocked", emptySet()) ?: emptySet()).toMutableSet().apply { add(key) }
        prefs.edit().putStringSet("ext_blocked", set).apply()
    }

    /**
     * Opens a system screen. We write a marker first; if we come back normally the marker is cleared in onResume.
     * If the app instead starts from scratch with the marker still set, the system screen threw us out,
     * so we remember that and show instructions from then on instead of launching it again.
     */
    private fun external(key: String, launch: () -> Unit) {
        if (isBlocked(key)) {
            Engine.toast(manualMessage(key))
            return
        }
        prefs.edit().putString("ext_key", key).putLong("ext_at", System.currentTimeMillis()).apply()
        launchedExternal = true
        try {
            launch()
        } catch (e: Exception) {
            launchedExternal = false
            prefs.edit().remove("ext_key").apply()
            block(key)
            Engine.toast(manualMessage(key))
        }
    }

    private fun checkKicked(): String? {
        val key = prefs.getString("ext_key", null) ?: return null
        val at = prefs.getLong("ext_at", 0L)
        prefs.edit().remove("ext_key").apply()
        if (System.currentTimeMillis() - at > 180_000L) return null
        block(key)
        return manualMessage(key)
    }

    override fun onResume() {
        super.onResume()
        if (launchedExternal) {
            launchedExternal = false
            prefs.edit().remove("ext_key").apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kicked = checkKicked()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Back from the home screen closes the app for real. On Android 12+ it would otherwise just hide it.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Engine.shutdown()
                finishAndRemoveTask()
            }
        })
        readPerms()
        handleShare(intent)
        setContent { BtRoot(perms, actions) }
        kicked?.let { Engine.toast(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        readPerms()
        if (perms.bluetooth) Engine.start()
        Engine.refresh()
        Engine.setUiVisible(true)
    }

    override fun onDestroy() {
        // Closed for real (back out or swipe away): stop everything. Not on rotation.
        if (isFinishing) Engine.shutdown()
        super.onDestroy()
    }

    override fun onStop() {
        Engine.setUiVisible(false)
        super.onStop()
    }

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun requestList(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) neededPermissions() + Manifest.permission.POST_NOTIFICATIONS else neededPermissions()

    private fun readPerms() {
        val ok = neededPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val needsLoc = Build.VERSION.SDK_INT <= 30
        val locOn = !needsLoc || (getSystemService(LocationManager::class.java)?.isLocationEnabled == true)
        perms = Perms(ok, needsLoc, locOn)
    }

    /** Files shared from Gallery, Files, etc. via the Android share sheet. */
    private fun handleShare(i: Intent?) {
        i ?: return
        val uris: List<Uri> = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            Engine.queueFiles(uris)
            i.action = null
        }
    }
}
