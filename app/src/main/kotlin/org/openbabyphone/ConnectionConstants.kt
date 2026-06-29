/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Babyphone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone

/**
 * Shared connection and discovery constants for Open Babyphone.
 *
 * These constants are used by both the child device (MonitorService) and the
 * parent device (DiscoverViewModel) to ensure consistent NSD service type,
 * service name, and default port across the app.
 */
object ConnectionConstants {
    const val DEFAULT_PORT = 10000
    const val SERVICE_TYPE = "_openbabyphone._tcp."
    const val SERVICE_NAME_PREFIX = "Open Babyphone"

    /**
     * Wi-Fi Direct (P2P) service type used for service discovery. Mirrors the
     * NSD service type but without the trailing dot, as required by
     * `WifiP2pDnsSdServiceInfo`.
     */
    const val WIFI_DIRECT_SERVICE_TYPE = "_openbabyphone._tcp"

    /**
     * TXT record key identifying the Open Babyphone service.
     */
    const val WIFI_DIRECT_TXT_APP = "app"

    /**
     * TXT record value identifying the Open Babyphone service.
     */
    const val WIFI_DIRECT_TXT_APP_VALUE = "openbabyphone"

    /**
     * TXT record key carrying the child's listening port.
     */
    const val WIFI_DIRECT_TXT_PORT = "port"

    /**
     * TXT record key carrying the child's display name.
     */
    const val WIFI_DIRECT_TXT_NAME = "name"
}