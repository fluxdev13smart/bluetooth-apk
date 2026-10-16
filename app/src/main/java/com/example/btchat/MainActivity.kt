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
import androidx.activity.compose.setContent
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
            enableBt = { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            makeVisible = {
                visibleReq.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                )
            },
            pickFiles = { pick.launch(arrayOf("*/*")) },
            openLocation = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            openDownloads = { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) },
            quit = { Engine.shutdown(); finishAndRemoveTask() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readPerms()
        handleShare(intent)
        setContent { BtRoot(perms, actions) }
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
