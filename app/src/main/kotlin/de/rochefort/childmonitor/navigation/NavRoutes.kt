package de.rochefort.childmonitor.navigation

import kotlinx.serialization.Serializable

@Serializable
object Start

@Serializable
object Monitor

@Serializable
object Discover

@Serializable
object DiscoverAddress

@Serializable
data class Listen(
    val address: String = "",
    val port: Int = 0,
    val name: String = "",
    val pairingCode: String = "",
    val resumeOnly: Boolean = false
)
