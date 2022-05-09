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
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val ltag = "MainViewModel"

    val targets = listOf(
        WolHost(0, "Router", "192.168.1.1", "a1:b1:c1:d1:e1:f1", "192.168.1.255"),
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


    /**
     * A value of 0 means ping jobs not running. A value of 1 means they are.
     */
    val pingJobsStateLiveData: LiveData<Int>
        get() = _pingJobsState
    private val _pingJobsState = MutableLiveData(0)

    private var pingJobs: List<Job> = emptyList()
    var pingActive = false
        private set

    var pingFocussedTarget: WolHost? = null

    var wolFocussedTarget: WolHost? = null

    var firstVisit = true

    @MainThread
    fun signalPingTargetChanged(wh: WolHost) {
        _targetPingChanged.value = wh.pKey
    }


    /**
     * Start ping jobs on all hosts. Pinging will only happen if a host is [WolHost.pingMe] true.
     * [pingJobsStateLiveData] will register an event only if pinging is not already active.
     */
    fun pingTargetsIfNeeded() {
        if (pingActive) return // ======================================== >>>

        pingActive = true
        pingJobs = targets.map { wh ->
            pingTarget(wh)
        }
        _pingJobsState.value = 1
    }

    /**
     * Stops ping jobs, and restarts them. Stopping is delayed, so starting is also delayed. [pingJobsStateLiveData]
     * can be observed to react to the changed states. If pinging is not active only the transition to active will
     * be registered.
     */
    fun pingTargetsAgain() {
        tlog(ltag) { "pingTargetsAgain: kill" }
        viewModelScope.launch {
            if (pingJobs.isNotEmpty()) {
                pingActive = false
                joinAll(*pingJobs.toTypedArray())
                pingJobs = emptyList()
                _pingJobsState.value = 0
            }

            tlog(ltag) { "pingTargetsAgain: ping" }

            pingActive = true
            pingJobs = targets.map { wh ->
                pingTarget(wh)
            }
            _pingJobsState.value = 1
        }
    }

    fun killTargets() {
        pingActive = false
        viewModelScope.launch {
            joinAll(*pingJobs.toTypedArray())
            _pingJobsState.value = 0
        }
    }

    fun resetPingStats() {
        viewModelScope.launch {
            targets.map {
                it.resetState()
            }
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

        return viewModelScope.launch {
            host.mutex.withLock {
                host.resetPingState()
            }
            _targetPingChanged.value = host.pKey

            var address: InetAddress? = null
            var pingName: String = host.pingName // host.pingName may be changed in a settings and the address must be looked up.
            var exception: Throwable? = null

            while (pingActive) {
                var pingUsedMillis = 0L
                if (host.pingMe) {
                    if (address == null || pingName != host.pingName) {
                        address = withContext(Dispatchers.IO) {
                            try {
                                val inetAddress = InetAddress.getByName(host.pingName)
                                pingName = host.pingName
                                inetAddress
                            } catch (ex: Throwable) {
                                host.mutex.withLock {
                                    host.pingException = ex
                                    host.pingState = WolHost.PingStates.EXCEPTION
                                    host.lastPingResponseAt.update(Instant.EPOCH)
                                }
                                null
                            }
                        }
                    }

                    if (address != null) {
                        host.lastPingSentAt.update(Instant.now())
                        val pingResult = withContext(Dispatchers.IO) {
                            try {
                                val pingResult = MagicPacket.ping(address, settingsData.pingResponseWaitMillis)
                                if (pingResult) 1 else 0
                            } catch (e: IOException) {
                                exception = e
                                -1
                            }
                        }

                        when (pingResult) {
                            1 -> {
                                pingUsedMillis = Duration.between(host.lastPingSentAt.state().first, Instant.now()).toMillis()
                                host.mutex.withLock {
                                    if (host.pingMe) {
                                        // Ping can take time, and host may have been turned off while waiting result
                                        host.pingState = WolHost.PingStates.ALIVE
                                        host.pingedCountAlive++
                                        host.pingException = null
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
                                    }
                                }
                            }

                            0 -> {
                                pingUsedMillis = Duration.between(host.lastPingSentAt.state().first, Instant.now()).toMillis()
                                // TODO: ping non response is delayed. Do we need to worry about pingMe state changing?
                                host.mutex.withLock {
                                    host.lastPingResponseAt.update(Instant.EPOCH)
                                    if (host.pingMe) {
                                        // Ping can take time, and host may have been turned off while waiting result
                                        host.pingState = WolHost.PingStates.DEAD
                                        host.pingedCountDead++
                                        host.pingException = null
                                    }
                                }
                            }

                            else -> {
                                host.mutex.withLock {
                                    host.pingState = WolHost.PingStates.EXCEPTION
                                    host.pingException = exception
                                    host.lastPingResponseAt.update(Instant.EPOCH)
                                }
                            }
                        }
                    }
                    _targetPingChanged.value = host.pKey
                }

                val neededDelay = settingsData.pingDelayMillis - pingUsedMillis
                if (neededDelay > 10) delay(neededDelay)
            }

            host.mutex.withLock {
                host.resetPingState()
            }
            _targetPingChanged.value = host.pKey
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
        return viewModelScope.launch {
            withContext(Dispatchers.IO) {
                host.mutex.withLock {
                    try {
                        repeat(host.magicPacketBundleCount) {
                            if (it > 1) delay(host.magicPacketBundleSpacing)
                            MagicPacket.sendWol(host.macAddress)
                        }
                        host.lastWolSentAt.update(Instant.now())
                        host.wakeupCount++
                    } catch (ex: IOException) {
                        host.wakeupException = ex
                    }
                }
            }
            _targetWakeChanged.value = host.pKey
        }
    }

}