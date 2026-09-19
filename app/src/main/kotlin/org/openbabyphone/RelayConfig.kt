package org.openbabyphone

object RelayConfig {
    const val WSS_ENDPOINT = "wss://babyphone.duckdns.org/relay"
    const val CONNECT_TIMEOUT_MS = 15_000
    const val RELAY_HANDSHAKE_WAIT_MS = 24L * 60L * 60L * 1000L
}
