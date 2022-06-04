package com.hanafey.android.wol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkStateTracker(private val cos: CoroutineScope, private val liveData: Live<NetState>) {
    data class NetState(val ts: Long, val isAvailable: Boolean, val isWifi: Boolean) {
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

    private val settleTimeMillies = 1_500L
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