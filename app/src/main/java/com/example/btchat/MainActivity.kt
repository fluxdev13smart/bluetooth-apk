package com.example.btchat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

@SuppressLint("MissingPermission") // we request the permissions before touching Bluetooth
class MainActivity : Activity(), BluetoothChatService.Listener, TextToSpeech.OnInitListener {

    private lateinit var adapter: BluetoothAdapter
    private var service: BluetoothChatService? = null

    private lateinit var statusText: TextView
    private lateinit var speakSwitch: Switch
    private lateinit var input: EditText
    private lateinit var messages: ArrayAdapter<String>

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var peerName = "Them"

    private val permRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (btAdapter == null) {
            Toast.makeText(this, "This device has no Bluetooth", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        adapter = btAdapter

        statusText = findViewById(R.id.statusText)
        speakSwitch = findViewById(R.id.speakSwitch)
        input = findViewById(R.id.messageInput)

        messages = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        findViewById<ListView>(R.id.messageList).adapter = messages

        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.pairButton).setOnClickListener {
            // Pairing is done by Android itself, in the system Bluetooth settings.
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        findViewById<Button>(R.id.visibleButton).setOnClickListener {
            val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            startActivity(i)
        }
        findViewById<Button>(R.id.connectButton).setOnClickListener { pickDevice() }
        findViewById<Button>(R.id.sendButton).setOnClickListener { sendCurrentText() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCurrentText(); true } else false
        }

        if (hasBluetoothPermissions()) startChatService() else requestBluetoothPermissions()
    }

    // ---- permissions -------------------------------------------------------

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray() // older Android grants Bluetooth permissions at install time
        }

    private fun hasBluetoothPermissions(): Boolean =
        neededPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun requestBluetoothPermissions() {
        requestPermissions(neededPermissions(), permRequest)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permRequest) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startChatService()
            } else {
                statusText.text = "Bluetooth permission is required"
                Toast.makeText(this, "Please allow Bluetooth permissions", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- chat service ------------------------------------------------------

    private fun startChatService() {
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Turn Bluetooth on first", Toast.LENGTH_LONG).show()
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
        if (service == null) service = BluetoothChatService(adapter, this)
        service?.start()
    }

    private fun pickDevice() {
        if (!hasBluetoothPermissions()) { requestBluetoothPermissions(); return }
        if (!adapter.isEnabled) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); return
        }
        val paired: List<BluetoothDevice> = adapter.bondedDevices.toList()
        if (paired.isEmpty()) {
            Toast.makeText(this, "No paired devices. Tap Pair first.", Toast.LENGTH_LONG).show()
            return
        }
        val names = paired.map { "${it.name ?: "Unknown"}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Connect to…")
            .setItems(names) { _, which ->
                if (service == null) service = BluetoothChatService(adapter, this)
                service?.connect(paired[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendCurrentText() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val ok = service?.send(text) ?: false
        if (ok) {
            messages.add("Me: $text")
            input.setText("")
        } else {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- BluetoothChatService.Listener (all on main thread) -----------------

    override fun onStatus(text: String) {
        statusText.text = text
    }

    override fun onConnected(deviceName: String) {
        peerName = deviceName
        statusText.text = "Connected to $deviceName"
        messages.add("— connected to $deviceName —")
    }

    override fun onDisconnected() {
        statusText.text = "Disconnected"
        messages.add("— disconnected —")
    }

    override fun onMessage(text: String) {
        messages.add("$peerName: $text")
        if (speakSwitch.isChecked && ttsReady) {
            // Audio goes out like music, so if this phone is connected to the car
            // over Bluetooth audio, the message is read aloud through the car speakers.
            tts?.speak("$peerName says: $text", TextToSpeech.QUEUE_ADD, null, "msg")
        }
    }

    // ---- text to speech ----------------------------------------------------

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            ttsReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service?.stop()
        tts?.stop()
        tts?.shutdown()
    }
}
