package com.animehub.tv.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** Busca las TVs con AnimeHub en la red de casa (desde la tablet). */
interface TvFinder {
    val tvs: StateFlow<List<RemoteTv>>
    fun start()
    fun stop()
}

/** Anuncia esta TV en la red para que la tablet la encuentre. */
interface ServiceAdvertiser {
    fun advertise(name: String, port: Int, attributes: Map<String, String>)
    fun withdraw()
}

/**
 * Búsqueda con NSD (mDNS / DNS-SD, el mismo mecanismo que usan las impresoras y Chromecast).
 * Resuelve los servicios de a uno (Android no permite resolver varios a la vez) y se salta a sí
 * mismo por su identificador.
 */
@Singleton
class NsdTvFinder @Inject constructor(
    @ApplicationContext context: Context,
    private val identity: DeviceIdentity,
) : TvFinder {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val _tvs = MutableStateFlow<List<RemoteTv>>(emptyList())
    override val tvs: StateFlow<List<RemoteTv>> = _tvs.asStateFlow()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null
    private val ownId by lazy { runBlocking { identity.id() } }

    override fun start() {
        if (listener != null || nsd == null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { listener = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = enqueue(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _tvs.update { list -> list.filterNot { it.name == info.serviceName } }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(RemoteProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { listener = null }
    }

    override fun stop() {
        listener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        listener = null
        synchronized(queue) { queue.clear() }
        _tvs.value = emptyList()
    }

    private fun enqueue(info: NsdServiceInfo) {
        synchronized(queue) {
            queue.addLast(info)
            if (resolving) return
            resolving = true
        }
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(queue) {
            queue.removeFirstOrNull() ?: run { resolving = false; return }
        }
        runCatching {
            nsd?.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = resolveNext()
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val id = info.attributes[RemoteProtocol.ATTR_ID]?.let { String(it, Charsets.UTF_8) } ?: info.serviceName
                    val host = hostOf(info)
                    if (id != ownId && host != null) {
                        val tv = RemoteTv(id, info.serviceName, host, info.port)
                        _tvs.update { list -> list.filterNot { it.id == id } + tv }
                    }
                    resolveNext()
                }
            })
        }.onFailure { resolveNext() }
    }

    @Suppress("DEPRECATION")
    private fun hostOf(info: NsdServiceInfo): String? =
        if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull()?.hostAddress else info.host?.hostAddress
}

/** Anuncio NSD de la TV: "_animehub._tcp" con su nombre, su puerto y su identificador. */
@Singleton
class NsdAdvertiser @Inject constructor(@ApplicationContext context: Context) : ServiceAdvertiser {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    override fun advertise(name: String, port: Int, attributes: Map<String, String>) {
        if (listener != null || nsd == null) return
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = RemoteProtocol.SERVICE_TYPE
            setPort(port)
            attributes.forEach { (k, v) -> setAttribute(k, v) }
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { listener = null }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        listener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { listener = null }
    }

    override fun withdraw() {
        listener?.let { l -> runCatching { nsd?.unregisterService(l) } }
        listener = null
    }
}
