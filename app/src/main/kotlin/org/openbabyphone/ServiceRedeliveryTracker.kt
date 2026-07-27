/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

internal class ServiceRedeliveryTracker {
    private var pendingClaim: WorkerClaim? = null

    fun record(claim: WorkerClaim, redelivered: Boolean) {
        pendingClaim = claim.takeIf { redelivered }
    }

    fun markRecovered(claim: WorkerClaim) {
        if (pendingClaim == claim) pendingClaim = null
    }

    fun consumeFailure(claim: WorkerClaim): Boolean = (pendingClaim == claim).also { matches ->
        if (matches) pendingClaim = null
    }

    fun clear() {
        pendingClaim = null
    }
}
