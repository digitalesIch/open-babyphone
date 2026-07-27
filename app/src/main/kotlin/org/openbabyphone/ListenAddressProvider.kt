package org.openbabyphone

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

internal data class NetworkInterfaceSnapshot(
    val isUp: Boolean,
    val isLoopback: Boolean,
    val addresses: List<InetAddress>
)

internal fun collectListenAddresses(
    interfaces: Iterable<NetworkInterfaceSnapshot>
): List<String> = interfaces
    .asSequence()
    .filter { it.isUp && !it.isLoopback }
    .flatMap { it.addresses.asSequence() }
    .filterNot { address ->
        address.isAnyLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
    }
    .mapNotNull(InetAddress::getHostAddress)
    .distinct()
    .sorted()
    .toList()

internal fun currentListenAddresses(): List<String> {
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
    val snapshots = mutableListOf<NetworkInterfaceSnapshot>()
    while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()
        try {
            snapshots += NetworkInterfaceSnapshot(
                isUp = networkInterface.isUp,
                isLoopback = networkInterface.isLoopback,
                addresses = networkInterface.inetAddresses.toList()
            )
        } catch (_: SocketException) {
            // An interface can disappear while the system snapshot is being read.
        }
    }
    return collectListenAddresses(snapshots)
}
