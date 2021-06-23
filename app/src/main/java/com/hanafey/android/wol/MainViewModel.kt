package com.hanafey.android.wol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.hanafey.android.wol.magic.MagicPacket
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.time.Instant

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val LTAG = "MainViewModel"

    val targets: List<WolHost> = listOf(
        WolHost(0, "FAKE", "192.168.1.11", "aa:bb:cc:dd:ee:ff", "192.168.1.255"),
        WolHost(1, "NASA", "192.168.1.250", "00:11:32:F0:0E:C1", "192.168.1.255"),
        WolHost(2, "SPACEX", "192.168.1.202", "00:11:32:3a:52:e3", "192.168.1.255"),
    ).sorted()

    val settingsData = SettingsData(PreferenceManager.getDefaultSharedPreferences(application))

    private val _targetPingChanged = MutableLiveData(-1)
    val targetPingChangedLiveData: LiveData<Int>
        get() = _targetPingChanged

    private val _targetWakeChanged = MutableLiveData(-1)
    val targetWakeChangedLiveData: LiveData<Int>
        get() = _targetWakeChanged

    var pingDelayMillis = 1000L

    private var pingJobs: List<Job> = emptyList()
    var pingActive = false
        private set

    var pingFocussedTarget: WolHost? = null

    var wolFocussedTarget: WolHost? = null

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
                            host.lastPingResponseAt.update(Instant.EPOCH)
                            _targetPingChanged.postValue(host.pKey)
                            null
                        }
                    }
                    if (address != null) {
                        try {
                            host.lastPingSentAt.update(Instant.now())
                            if (MagicPacket.ping(address)) {
                                if (host.pingMe) {
                                    // Ping can take time, and host may have been turned off while waiting result
                                    host.pingState = WolHost.PingStates.ALIVE
                                    host.pingedCountAlive++
                                }
                                host.lastPingSentAt.consume()
                                host.lastPingResponseAt.update(Instant.now())
                                val (then, ack) = host.lastWolSentAt.consume()
                                if (!ack) {
                                    val now = Instant.now()
                                    host.lastWolWakeAt.update(now)
                                    val deltaMilli = now.toEpochMilli() - then.toEpochMilli()
                                    host.wolToWakeHistory = host.wolToWakeHistory + deltaMilli.toInt()
                                    host.wolToWakeHistoryChanged = true
                                    _targetWakeChanged.value = host.pKey
                                }
                            } else {
                                host.lastPingResponseAt.update(Instant.EPOCH)
                                if (host.pingMe) {
                                    // Ping can take time, and host may have been turned off while waiting result
                                    host.pingState = WolHost.PingStates.DEAD
                                    host.pingedCountDead++
                                }
                            }
                        } catch (e: Throwable) {
                            host.pingState = WolHost.PingStates.EXCEPTION
                            host.pingException = e
                            host.lastPingResponseAt.update(Instant.EPOCH)
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
                host.lastWolSentAt.update(Instant.now())
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