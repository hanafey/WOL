package com.hanafey.android.wol

import android.app.Application
import androidx.annotation.MainThread
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

    val targets = listOf(
        WolHost(0, "HOST 1", "192.168.1.11", "a1:b1:c1:d1:e1:f1", "192.168.1.255"),
        WolHost(1, "HOST 2", "192.168.1.12", "a2:b2:c2:d2:e2:f2", "192.168.1.255"),
        WolHost(2, "HOST 3", "192.168.1.13", "a3:b3:c3:d3:e3:f3", "192.168.1.255"),
        WolHost(3, "HOST 4", "192.168.1.14", "a4:b4:c4:d4:e4:f4", "192.168.1.255"),
        WolHost(4, "HOST 5", "192.168.1.15", "a5:b5:c5:d5:e5:f5", "192.168.1.255"),
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

    @MainThread
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
            pingJobs = targets.map { wh ->
                pingTarget(wh)
            }
        }
    }

    fun resetPingStats() {
        targets.map {
            it.resetState()
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
                                        _targetWakeChanged.postValue(host.pKey)
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
        return targets.firstOrNull { it.pingMe }
    }


    /**
     * Returns the number of hosts that wants to be pinged. Zero means nobody is pinged.
     */
    fun countPingMe(): Int {
        return targets.fold(0) { z, wh -> if (wh.pingMe) z + 1 else z }
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