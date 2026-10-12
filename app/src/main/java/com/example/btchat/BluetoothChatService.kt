package com.example.btchat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic (RFCOMM) chat link between two phones running this app.
 * Every phone listens for incoming connections; one phone taps "Connect"
 * and picks the other from the paired list.
 *
 * Messages are sent with writeUTF/readUTF, so any language (Arabic, emoji...) works.
 * All Listener callbacks arrive on the main thread.
 */
@SuppressLint("MissingPermission") // permissions are requested in MainActivity before use
class BluetoothChatService(
    private val adapter: BluetoothAdapter,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(text: String)
        fun onConnected(deviceName: String)
        fun onDisconnected()
        fun onMessage(text: String)
    }

    companion object {
        // Both phones must use the same UUID. It is just this app's private "channel id".
        val APP_UUID: UUID = UUID.fromString("8f3c1a52-6b7d-4e8a-9c21-5d0e7a4b9f13")
        private const val SERVICE_NAME = "BTChat"
    }

    private val main = Handler(Looper.getMainLooper())

    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    /** Start listening for an incoming connection. Safe to call repeatedly. */
    @Synchronized
    fun start() {
        connectThread?.cancel(); connectThread = null
        if (connectedThread == null && acceptThread == null) {
            acceptThread = AcceptThread().also { it.start() }
            post { listener.onStatus("Waiting for a connection…") }
        }
    }

    /** Connect out to a paired device that is also running this app. */
    @Synchronized
    fun connect(device: BluetoothDevice) {
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
        connectThread = ConnectThread(device).also { it.start() }
        post { listener.onStatus("Connecting to ${safeName(device)}…") }
    }

    fun send(text: String): Boolean {
        val t = synchronized(this) { connectedThread } ?: return false
        return t.write(text)
    }

    @Synchronized
    fun stop() {
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
        acceptThread?.cancel(); acceptThread = null
    }

    // ---- internals -------------------------------------------------------

    @Synchronized
    private fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
        acceptThread?.cancel(); acceptThread = null
        connectedThread = ConnectedThread(socket).also { it.start() }
        post { listener.onConnected(safeName(device)) }
    }

    private fun connectionLost() {
        synchronized(this) {
            connectedThread = null
            connectThread = null
        }
        post {
            listener.onDisconnected()
            start() // go back to listening
        }
    }

    private fun connectionFailed() {
        synchronized(this) { connectThread = null }
        post {
            listener.onStatus("Could not connect. Is the other phone running the app?")
            start()
        }
    }

    private fun safeName(d: BluetoothDevice): String = d.name ?: d.address

    private fun post(block: () -> Unit) = main.post(block)

    private inner class AcceptThread : Thread() {
        private var server: BluetoothServerSocket? = try {
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
        } catch (e: IOException) {
            null
        }

        override fun run() {
            val s = server ?: return
            while (true) {
                val socket = try {
                    s.accept()
                } catch (e: IOException) {
                    break
                }
                synchronized(this@BluetoothChatService) {
                    if (connectedThread == null) {
                        connected(socket, socket.remoteDevice)
                        try { s.close() } catch (_: IOException) {}
                        return
                    } else {
                        try { socket.close() } catch (_: IOException) {}
                    }
                }
            }
        }

        fun cancel() {
            try { server?.close() } catch (_: IOException) {}
        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? = try {
            device.createRfcommSocketToServiceRecord(APP_UUID)
        } catch (e: IOException) {
            null
        }

        override fun run() {
            val s = socket
            if (s == null) { connectionFailed(); return }
            adapter.cancelDiscovery()
            try {
                s.connect()
            } catch (e: IOException) {
                try { s.close() } catch (_: IOException) {}
                connectionFailed()
                return
            }
            connected(s, device)
        }

        fun cancel() {
            try { socket?.close() } catch (_: IOException) {}
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val input = DataInputStream(socket.inputStream)
        private val output = DataOutputStream(socket.outputStream)

        override fun run() {
            while (true) {
                try {
                    val msg = input.readUTF()
                    post { listener.onMessage(msg) }
                } catch (e: IOException) {
                    connectionLost()
                    break
                }
            }
        }

        fun write(text: String): Boolean = try {
            synchronized(output) {
                output.writeUTF(text)
                output.flush()
            }
            true
        } catch (e: IOException) {
            false
        }

        fun cancel() {
            try { socket.close() } catch (_: IOException) {}
        }
    }
}
