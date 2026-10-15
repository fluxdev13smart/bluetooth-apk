package com.example.btchat.core

import android.net.Uri

data class Device(
    val address: String,
    val name: String,
    val bonded: Boolean,
    val rssi: Int?,
    val seen: Boolean,
    val computer: Boolean
)

data class Peer(val address: String, val name: String)

sealed interface Link {
    data object Idle : Link
    data class Connecting(val address: String, val name: String) : Link
    data class Connected(val peer: Peer) : Link
}

enum class FileStatus { Offered, Waiting, Active, Done, Declined, Cancelled, Failed }

sealed interface Item {
    val id: Long
    val time: Long
    val outgoing: Boolean
}

data class TextItem(
    override val id: Long,
    override val time: Long,
    override val outgoing: Boolean,
    val text: String
) : Item

data class NoteItem(
    override val id: Long,
    override val time: Long,
    val text: String
) : Item {
    override val outgoing: Boolean get() = false
}

data class FileItem(
    override val id: Long,
    override val time: Long,
    override val outgoing: Boolean,
    val name: String,
    val size: Long,
    val mime: String,
    val status: FileStatus,
    val done: Long = 0,
    val speed: Long = 0,
    val uri: String? = null,
    val note: String? = null
) : Item

data class PendingFile(val uri: Uri, val name: String, val size: Long, val mime: String)

data class EngineState(
    val btOn: Boolean = false,
    val link: Link = Link.Idle,
    val scanning: Boolean = false,
    val visible: Boolean = false,
    val devices: List<Device> = emptyList(),
    val items: List<Item> = emptyList(),
    val pending: List<PendingFile> = emptyList(),
    val notice: String? = null,
    val carMode: Boolean = false,
    val myName: String = ""
)
