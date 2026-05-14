package com.chibiclaw.world.observers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.chibiclaw.agent.initiative.EventBus
import com.chibiclaw.agent.initiative.TriggerEvent
import com.chibiclaw.agent.initiative.trigger.EventType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NetworkObserver — ConnectivityManager.registerNetworkCallback emit
 * NETWORK_AVAILABLE / NETWORK_LOST ke EventBus.
 */
@Singleton
class NetworkObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: EventBus,
) {

    private val cm by lazy { context.getSystemService(ConnectivityManager::class.java) }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            eventBus.emit(TriggerEvent(EventType.NETWORK_AVAILABLE))
            Timber.v("Network available")
        }

        override fun onLost(network: Network) {
            eventBus.emit(TriggerEvent(EventType.NETWORK_LOST))
            Timber.v("Network lost")
        }
    }

    @Volatile private var registered = false

    @Synchronized
    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onSuccess { registered = true; Timber.i("NetworkObserver registered") }
            .onFailure { Timber.w(it, "NetworkObserver register failed") }
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        runCatching { cm.unregisterNetworkCallback(callback) }
        registered = false
        Timber.i("NetworkObserver unregistered")
    }
}
