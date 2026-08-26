package com.seebudget.app.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.seebudget.app.domain.connectivity.ConnectivityObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * "Solo Android" para esta primera pasada (ver KDoc de `ConnectivityObserver`)
 * — `ConnectivityManager.registerNetworkCallback` (API 24+; minSdk de
 * esta app es 26), push real del sistema operativo. Requiere el permiso
 * `ACCESS_NETWORK_STATE` (ver AndroidManifest.xml).
 *
 * Se considera "conectado" solo si la red activa tiene
 * `NET_CAPABILITY_VALIDATED` (el sistema operativo confirmó salida real
 * a Internet, no solo un link físico/Wi-Fi asociado sin Internet
 * real) — evita disparar un resync contra una red que en los hechos no
 * tiene salida.
 */
class AndroidConnectivityObserver(
    private val context: Context,
) : ConnectivityObserver {
    override fun observe(): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun isValidated(network: Network): Boolean =
            connectivityManager.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isValidated(network))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
        }

        // Estado inicial: el primer callback puede tardar en llegar, así
        // que se manda un valor de arranque con lo que ya se sabe.
        val activeNetwork = connectivityManager.activeNetwork
        trySend(activeNetwork != null && isValidated(activeNetwork))

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
