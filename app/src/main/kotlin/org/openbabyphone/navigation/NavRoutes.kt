package org.openbabyphone.navigation

import kotlinx.serialization.Serializable

@Serializable
object Start

@Serializable
object Monitor

@Serializable
object Settings

@Serializable
object Discover

@Serializable
data class DiscoverAddress(val requestId: String = "")

@Serializable
data class DiscoverWifiDirect(val requestId: String = "")

@Serializable
enum class ConnectionHelpMode {
    Parent,
    Child
}

@Serializable
data class ConnectionHelp(
    val mode: ConnectionHelpMode,
    val requestId: String = ""
)

@Serializable
data class Listen(
    val requestId: String = "",
    val expectedChildId: String = "",
    val expectedPairingId: String = "",
    val resumeOnly: Boolean = false
)
