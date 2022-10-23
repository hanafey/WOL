package com.hanafey.android.wol

import android.net.ConnectivityManager
import com.hanafey.android.ax.Live
import com.hanafey.android.wol.NetworkStateTracker.NetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [ConnectivityManager] is quite chatty about network changes, but the bottom line thing is the network
 * available, and is the default network WiFi. This is needed for WOL, and even pinging of hosts on the
 * local network. Your [ConnectivityManager.NetworkCallback] must report `onAvailable` and `onLost` results
 * to [isAvailable], and `onCapabilitiesChanged` to [isWifi]. Because some change in the network typically results
 * in a flurry of these callbacks [NetworkStateTracker] must get all of them, but it only reports changes
 * that endure for some minimum time before reporting those via Live Data.
 * @param settleTimeMillies A change must be followed by this many milliseconds before it is related via [liveData]. It
 * is still possible that more than one [NetState] will be passed to this Live Data, so normally you version should
 * impose a report on distinct states to avoid duplicate observations.
 * @param cos The coroutine scope used to delay the [settleTimeMillies] interval.
 * @param liveData Live data that the client will observer for significant network change events. Ideally force
 * distinct only semantics on the live data.
 */
class NetworkStateTracker(
    private val settleTimeMillies: Long,
    private val cos: CoroutineScope,
    private val liveData: Live<NetState>
) {
    /**
     * Equality and hash code is based only on [isAvailable] and [isWifi]
     * @param ts Milliseconds since unix epoch when the value was last set.
     * @param isAvailable [ConnectivityManager] last reported network is available.
     * @param isWifi [ConnectivityManager] last reported network is WiFi.
     */
    data class NetState(
        val ts: Long,
        val isAvailable: Boolean,
        val isWifi: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NetState

            if (isAvailable != other.isAvailable) return false
            if (isWifi != other.isWifi) return false

            return true
        }

        override fun hashCode(): Int {
            var result = isAvailable.hashCode()
            result = 31 * result + isWifi.hashCode()
            return result
        }
    }

    private var lastChangeAt = 0L
    private var delayJob: Job? = null

    var isAvailable = false
        set(value) {
            field = value
            lastChangeAt = System.currentTimeMillis()
            notifyOnQuiet()
        }

    var isWifi = false
        set(value) {
            field = value
            lastChangeAt = System.currentTimeMillis()
            notifyOnQuiet()
        }

    private fun notifyOnQuiet() {
        delayJob?.cancel()
        delayJob = cos.launch {
            delay(settleTimeMillies)
            liveData.value = NetState(lastChangeAt, isAvailable, isWifi)
            delayJob = null
        }
    }
}