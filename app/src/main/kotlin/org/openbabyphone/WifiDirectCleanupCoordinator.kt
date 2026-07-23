/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

class WifiDirectCleanupCoordinator {
    private var session: WifiDirectSession? = null
    private var ownershipToken = 0L

    @Synchronized
    fun handoff(newSession: WifiDirectSession) {
        if (session === newSession) return
        session?.stop()
        session = newSession
        ownershipToken++
    }

    @Synchronized
    fun claimForListenSession(): Long? {
        if (session == null) return null
        return ++ownershipToken
    }

    fun cleanup(expectedToken: Long? = null) {
        val owned = synchronized(this) {
            if (expectedToken != null && expectedToken != ownershipToken) {
                null
            } else {
                session.also { session = null }
            }
        }
        owned?.stop()
    }

    @Synchronized
    internal fun hasOwnership(): Boolean = session != null
}
