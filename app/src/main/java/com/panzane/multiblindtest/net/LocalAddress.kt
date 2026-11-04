package com.panzane.multiblindtest.net

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Adresse IPv4 du téléphone sur le réseau local : wifi, ou point d'accès s'il partage sa connexion.
 * Null s'il n'est sur aucun réseau local (données mobiles seules).
 */
fun localIpv4Address(): String? {
    val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .flatMap { iface ->
            iface.inetAddresses.toList()
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .map { iface.name to it.hostAddress }
        }
    // wlan* : wifi ; ap*/swlan*/softap* : point d'accès. Les interfaces mobiles (rmnet…) n'ont pas d'adresse privée utile.
    val preferred = candidates.firstOrNull { (name, _) ->
        WIFI_PREFIXES.any { name.startsWith(it) }
    }
    return (preferred ?: candidates.firstOrNull())?.second
}

private val WIFI_PREFIXES = listOf("wlan", "swlan", "ap", "softap")
