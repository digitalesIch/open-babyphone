/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.openbabyphone

import android.content.Intent
import org.openbabyphone.navigation.Listen
import java.security.SecureRandom
import java.util.Base64
import java.util.LinkedHashMap

internal object InternalListenRouteRegistry {
    private const val MAX_ROUTES = 16
    private val random = SecureRandom()
    private val routes = LinkedHashMap<String, Listen>()

    @Synchronized
    fun put(route: Listen): String {
        while (routes.size >= MAX_ROUTES) routes.remove(routes.keys.first())
        var id: String
        do {
            id = ByteArray(18).also(random::nextBytes)
                .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        } while (routes.containsKey(id))
        routes[id] = route
        return id
    }

    @Synchronized
    fun consume(id: String): Listen? = routes.remove(id)

    @Synchronized
    fun clear() = routes.clear()
}

internal fun consumeInternalListenRoute(intent: Intent): Listen? {
    val routeId = intent.getStringExtra(MainActivity.EXTRA_INTERNAL_ROUTE_ID) ?: return null
    intent.removeExtra(MainActivity.EXTRA_INTERNAL_ROUTE_ID)
    return InternalListenRouteRegistry.consume(routeId)
}
