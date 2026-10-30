package com.example.chitgame.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.coroutines.resume

data class DiscoveredRoom(val name: String, val host: String, val port: Int)

/** mDNS/NSD: advertise the room (host) and find rooms (clients). */
class NsdHelper(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var regListener: NsdManager.RegistrationListener? = null

    fun register(roomName: String, port: Int) {
        val info = NsdServiceInfo().apply { serviceName = roomName; serviceType = TYPE; setPort(port) }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        regListener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun unregister() {
        regListener?.let { runCatching { nsd.unregisterService(it) } }
        regListener = null
    }

    @Suppress("DEPRECATION")
    fun discover(): Flow<DiscoveredRoom> = callbackFlow {
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onServiceFound(s: NsdServiceInfo) { queue.trySend(s) }
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { close() }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        // NsdManager can resolve only one service at a time -> resolve sequentially.
        val resolver = launch {
            for (s in queue) resolveOne(s)?.let { trySend(it) }
        }
        awaitClose {
            resolver.cancel()
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveOne(s: NsdServiceInfo): DiscoveredRoom? =
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) { if (cont.isActive) cont.resume(null) }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val h = i.host?.hostAddress
                    if (cont.isActive) cont.resume(h?.let { DiscoveredRoom(i.serviceName, it, i.port) })
                }
            })
        }

    companion object { const val TYPE = "_chitgame._tcp." }
}

object NetUtil {
    /** Host's own LAN/hotspot IPv4 addresses (shown in the lobby as a manual fallback). */
    fun localIpv4(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    /** When a client is on the host's hotspot, the Wi-Fi gateway IS the host phone. */
    @Suppress("DEPRECATION")
    fun gatewayIp(ctx: Context): String? {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val gw = wm.dhcpInfo?.gateway ?: return null
        if (gw == 0) return null
        return "${gw and 0xff}.${gw shr 8 and 0xff}.${gw shr 16 and 0xff}.${gw shr 24 and 0xff}"
    }
}
