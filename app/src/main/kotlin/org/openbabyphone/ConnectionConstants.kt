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
}