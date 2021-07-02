package com.hanafey.android.wol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.hanafey.android.wol.magic.MagicPacket
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.*
import java.net.InetAddress
import java.time.Instant
import kotlin.concurrent.withLock

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val LTAG = "MainViewModel"

    val targets = sortedMapOf(
        1 to WolHost(1, "FAKE", "192.168.1.11", "aa:bb:cc:dd:ee:ff", "192.168.1.255"),
        2 to WolHost(2, "NASA", "192.168.1.250", "00:11:32:F0:0E:C1", "192.168.1.255"),
        3 to WolHost(3, "SPACEX", "192.168.1.202", "00:11:32:3a:52:e3", "192.168.1.255"),
        4 to WolHost(4, "UNSET3", "192.168.1.202", "00:11:32:3a:52:e3", "192.168.1.255"),
        5 to WolHost(5, "UNSET4", "192.168.1.202", "00:11:32:3a:52:e3", "192.168.1.255"),
    )

    val settingsData = SettingsData(PreferenceManager.getDefaultSharedPreferences(application))

    private val _targetPingChanged = MutableLiveData(-1)
    val targetPingChangedLiveData: LiveData<Int>
        get() = _targetPingChanged

    private val _targetWakeChanged = MutableLiveData(-1)
    val targetWakeChangedLiveData: LiveData<Int>
        get() = _targetWakeChanged

    private var pingJobs: List<Job> = emptyList()
    var pingActive = false
        private set

    var pingFocussedTarget: WolHost? = null

    var wolFocussedTarget: WolHost? = null

    fun signalPingTargetChanged(wh: WolHost) {
        _targetPingChanged.value = wh.pKey
    }

    /**
     * Start ping jobs on all hosts. Pinging will only happen if a host is [WolHost.enabled] and [WolHost.pingMe]
     * both true.
     */
    fun pingTargets() {
        pingActive = false
        viewModelScope.launch {
            joinAll(*pingJobs.toTypedArray())
            pingActive = true
            pingJobs = targets.map { (_, wh) ->
                pingTarget(wh)
            }
        }
    }

    fun resetPingStats() {
        targets.map {
            it.value.resetState()
        }
    }

    /**
     * Stop the ping targets jobs.
     */
    fun killPingTargets() {
        pingActive = false
        viewModelScope.launch {
            joinAll(*pingJobs.toTypedArray())
            pingJobs = emptyList()
        }
        /* TODO: Just let them end...
              pingJobs.forEach { (job, ex) ->
                  job?.cancel("Cancelled on request.")
              }
              */
    }

    private fun pingTarget(host: WolHost): Job {
        host.resetPingState()
        _targetPingChanged.value = host.pKey

        return viewModelScope.launch(Dispatchers.IO) {
            var address: InetAddress? = null
            var pingName: String = "" // host.pingName may be changed in a settings and the address must be looked up.

            while (pingActive) {
                if (host.pingMe) {
                    if (address == null || pingName != host.pingName) {
                        try {
                            address = InetAddress.getByName(host.pingName)
                            pingName = host.pingName
                        } catch (ex: Throwable) {
                            host.lock.withLock {
                                host.pingException = ex
                                host.pingState = WolHost.PingStates.EXCEPTION
                                host.lastPingResponseAt.update(Instant.EPOCH)
                            }
                            _targetPingChanged.postValue(host.pKey)
                            address = null
                            pingName = ""
                        }
                    }
                    if (address != null) {
                        try {
                            host.lastPingSentAt.update(Instant.now())
                            if (MagicPacket.ping(address, settingsData.pingResponseWaitMillis)) {
                                host.lock.withLock {
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
                                }
                            } else {
                                host.lastPingResponseAt.update(Instant.EPOCH)
                                // TODO: ping non response is delayed. Do we need to worry about pingMe state changing?
                                host.lock.withLock {
                                    if (host.pingMe) {
                                        // Ping can take time, and host may have been turned off while waiting result
                                        host.pingState = WolHost.PingStates.DEAD
                                        host.pingedCountDead++
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            host.lock.withLock {
                                host.pingState = WolHost.PingStates.EXCEPTION
                                host.pingException = e
                                host.lastPingResponseAt.update(Instant.EPOCH)
                            }
                        }

                        _targetPingChanged.postValue(host.pKey)
                    }
                }
                delay(settingsData.pingDelayMillis)
            }

            host.resetPingState()
            _targetPingChanged.postValue(host.pKey)
        }
    }


    /**
     * Returns the first host that wants to be pinged. If null, no host wants to be pinged.
     */
    fun firstPingMe(): WolHost? {
        targets.forEach {
            if (it.value.pingMe) return it.value
        }
        return null
    }


    /**
     * Returns the number of hosts that wants to be pinged. Zero means nobody is pinged.
     */
    fun countPingMe(): Int {
        var count = 0
        targets.forEach { (_, wh) ->
            if (wh.pingMe) count++
        }
        return count
    }

    fun wakeTarget(host: WolHost): Job {

        return viewModelScope.launch(Dispatchers.IO) {
            try {
                MagicPacket.sendWol(host.macAddress)
                host.lastWolSentAt.update(Instant.now())
                host.wakeupCount++
            } catch (ex: Throwable) {
                host.wakeupException = ex
            }
            _targetWakeChanged.postValue(host.pKey)
        }
    }

}