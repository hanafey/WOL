package com.hanafey.android.wol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hanafey.android.wol.magic.MagicPacket
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val LTAG = "MainViewModel"

    val targets: List<WolHost> = listOf(
        WolHost(0, "FAKE", "192.168.1.16", "aa:bb:cc:dd:ee:ff", "192.168.1.255"),
        WolHost(1, "NASA", "192.168.1.250", "001132F00ECF", "192.168.1.255"),
        WolHost(2, "HOG", "192.168.1.252", "001132F00ECF", "192.168.1.16"),
    ).sorted()

    private val _targetPingChanged = MutableLiveData(-1)
    val targetPingChangedLiveData: LiveData<Int>
        get() = _targetPingChanged

    private val _targetWakeChanged = MutableLiveData(-1)
    val targetWakeChangedLiveData: LiveData<Int>
        get() = _targetWakeChanged

    var pingDelayMillis = 2500L

    private var pingJobs: List<Job> = emptyList()
    private var pingActive = false

    fun signalPingTargetChanged(wh: WolHost) {
        _targetPingChanged.value = wh.pKey
    }

    private fun pingTarget(host: WolHost): Job {
        host.resetPingState()
        _targetPingChanged.value = host.pKey

        return viewModelScope.launch(Dispatchers.IO) {
            var address: InetAddress? = null

            while (pingActive) {
                if (host.pingMe) {
                    if (address == null) {
                        address = try {
                            InetAddress.getByName(host.pingName)
                        } catch (ex: Throwable) {
                            host.pingException = ex
                            host.pingState = WolHost.PingStates.EXCEPTION
                            _targetPingChanged.postValue(host.pKey)
                            null
                        }
                    }
                    if (address != null) {
                        try {
                            if (MagicPacket.ping(address)) {
                                host.pingState = WolHost.PingStates.ALIVE
                                host.pingedCount++
                            } else {
                                host.pingState = WolHost.PingStates.DEAD
                            }
                        } catch (e: Throwable) {
                            host.pingState = WolHost.PingStates.EXCEPTION
                            host.pingException = e
                        }

                        _targetPingChanged.postValue(host.pKey)
                    }
                }
                delay(pingDelayMillis)
            }
            host.resetPingState()
            _targetPingChanged.postValue(host.pKey)
        }
    }


    /**
     * Returns the first host that wants to be pinged. If null, no host wants to be pinged.
     */
    fun firstPingMe(): WolHost? {
        return targets.firstOrNull { it.pingMe }
    }


    /**
     * Returns the number of hosts that wants to be pinged. Zero means nobody is pinged.
     */
    fun countPingMe(): Int {
        return targets.fold(0) { acc, wh ->
            if (wh.pingMe) acc + 1 else acc
        }
    }

    fun wakeTarget(host: WolHost): Job {

        return viewModelScope.launch(Dispatchers.IO) {
            try {
                MagicPacket.sendWol(host.macAddress)
                host.wakeupCount++
                dlog(LTAG) { "wakeTarget: ${host.title} count=${host.wakeupCount}" }
            } catch (ex: Throwable) {
                dlog(LTAG) { "wakeTarget: ${host.title} ex=$ex" }
                host.wakeupException = ex
            }
            _targetWakeChanged.postValue(host.pKey)
        }
    }

    fun pingTargets() {
        pingActive = true
        pingJobs = targets.map { wh ->
            pingTarget(wh)
        }
    }

    fun resetPingStats() {
        targets.map { wh ->
            wh.resetState()
        }
    }

    fun killPingTargets() {
        pingActive = false
        /* TODO: Just let them end...
        pingJobs.forEach { (job, ex) ->
            job?.cancel("Cancelled on request.")
        }
        */
    }
}