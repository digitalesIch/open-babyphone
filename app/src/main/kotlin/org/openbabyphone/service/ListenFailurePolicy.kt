/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone.service

internal enum class TerminalConnectionFailure {
    Unreachable,
    Lost
}

internal fun classifyTerminalConnectionFailure(hasVerifiedAudio: Boolean): TerminalConnectionFailure =
    if (hasVerifiedAudio) TerminalConnectionFailure.Lost else TerminalConnectionFailure.Unreachable

internal fun shouldConsumePendingConnection(hasDurableTrustedIdentity: Boolean): Boolean =
    hasDurableTrustedIdentity
