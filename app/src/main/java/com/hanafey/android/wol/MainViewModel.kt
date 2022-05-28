package com.hanafey.android.wol

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.hanafey.android.wol.magic.MagicPacket
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

class MainViewModel(
    application: WolApplication,
) : AndroidViewModel(application) {

    val targets = defaultHostList()

    val hostStateNotification = HostStateNotification(application)
    private val observerOfHostState = ObserverOfHostState(hostStateNotification)

    val settingsData = SettingsData(PreferenceManager.getDefaultSharedPreferences(application))

    // FIX: _targetPingChanged is not private
    val _targetPingChanged = MutableLiveData(-1)
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

    /**
     * The delayed kill job kills pinging after waiting for a delay when the
     * [MainFragment] state changes to stopped. This job is killed if the
     * MainFragment returns to running before the timeout expires. Other fragments
     * can also request the pinging continue, for example the [HostStatusFragment]
     * depends on pinging to monitor host status.
     *
     * We kill pings? No point sending pings if nobody is displaying the result of
     * the ping.
     */
    private var delayedKillJob: Job? = null
    private val delayedKillMutex = Mutex()


    var pingActive = false
        private set

    var pingFocussedTarget: WolHost? = null

    var wolFocussedTarget: WolHost? = null

    var firstVisit = true

    private fun defaultHostList(): List<WolHost> {
        return if (BuildConfig.BUILD_TYPE == "debug") {
            listOf(
                WolHost(0, "Router", "192.168.1.1", "a1:b1:c1:d1:e1:f1", "192.168.1.255"),
                WolHost(1, "Nasa", "192.168.1.250", "00:11:32:f0:0e:c1", "192.168.1.255"),
                WolHost(2, "SpaceX", "192.168.1.202", "00:11:32:3a:52:e3", "192.168.1.255"),
                WolHost(3, "Phony", "192.168.1.5", "a4:b4:c4:d4:e4:f4", "192.168.1.255"),
                WolHost(4, "HOST 5", "192.168.1.15", "a5:b5:c5:d5:e5:f5", "192.168.1.255"),
            )
        } else {
            listOf(
                WolHost(0, "Router", "192.168.1.1", "a1:b1:c1:d1:e1:f1", "192.168.1.255"),
                WolHost(1, "HOST 2", "192.168.1.12", "a2:b2:c2:d2:e2:f2", "192.168.1.255"),
                WolHost(2, "HOST 3", "192.168.1.13", "a3:b3:c3:d3:e3:f3", "192.168.1.255"),
                WolHost(3, "HOST 4", "192.168.1.14", "a4:b4:c4:d4:e4:f4", "192.168.1.255"),
                WolHost(4, "HOST 5", "192.168.1.15", "a5:b5:c5:d5:e5:f5", "192.168.1.255"),
            )
        }
    }

    @MainThread
    fun signalPingTargetChanged(wh: WolHost) {
        _targetPingChanged.value = wh.pKey
    }

    /**
     * Adds a forever observer to each [targets].
     */
    fun observeAliveDeadTransitions() {
        targets.forEach { wh ->
            wh.deadAliveTransition.aliveDeadTransition.observeForever(observerOfHostState)
        }
    }

    /**
     * Start ping jobs on all hosts. Pinging will only happen if a host is [WolHost.pingMe] true.
     * [pingJobsStateLiveData] will register an event only if pinging is not already active.
     */
    fun pingTargetsIfNeeded(scope: CoroutineScope, resetState: Boolean) {
        if (pingActive) return // ======================================== >>>

        dog { "pingTargetsIfNeeded" }
        pingActive = true
        pingJobs = targets.map { wh ->
            pingTarget(scope, wh, resetState)
        }
        _pingJobsState.value = 1
    }

    /**
     * Stops ping jobs, and restarts them. Stopping is delayed, so starting is also delayed. [pingJobsStateLiveData]
     * can be observed to react to the changed states. If pinging is not active only the transition to active will
     * be registered.
     */
    fun pingTargetsAgain(scope: CoroutineScope, resetState: Boolean) {
        dog { "pingTargetsAgain" }
        scope.launch {
            if (pingJobs.isNotEmpty()) {
                pingActive = false
                joinAll(*pingJobs.toTypedArray())
                pingJobs = emptyList()
                _pingJobsState.value = 0
            }
            pingTargetsIfNeeded(scope, resetState)
        }
    }

    fun killPingTargetsAfterWaiting(scope: CoroutineScope) {
        if (settingsData.pingKillDelayMinutes <= 0) {
            return // ======================================== >>>
        }

        val exitingKillJob = delayedKillJob

        delayedKillJob = scope.launch {
            delayedKillMutex.withLock {
                exitingKillJob?.cancelAndJoin()
            }

            dog { "killPingTargetsAfterWaiting: Will delay ${settingsData.pingKillDelayMinutes} minutes" }
            delay(mSecFromMinutes(settingsData.pingKillDelayMinutes))
            dog { "killPingTargetsAfterWaiting: Delay ${settingsData.pingKillDelayMinutes} minutes done. Kill now!" }

            delayedKillMutex.withLock {
                if (pingActive) {
                    pingActive = false
                    joinAll(*pingJobs.toTypedArray())
                    pingJobs = emptyList()
                    _pingJobsState.value = 0
                }
                // NOTE: This is delayed, so it resets the value set at launch time.
                delayedKillJob = null
            }
        }
    }

    fun cancelKillPingTargetsAfterWaiting(scope: CoroutineScope) {
        scope.launch {
            delayedKillMutex.withLock {
                delayedKillJob?.cancelAndJoin()
                delayedKillJob = null
            }
        }
    }

    private fun pingTarget(scope: CoroutineScope, host: WolHost, resetState: Boolean): Job {

        return scope.launch {
            if (resetState) {
                host.mutex.withLock {
                    host.resetPingState()
                }
                _targetPingChanged.value = host.pKey
            }

            var address: InetAddress? = null
            var pingName: String = host.pingName // host.pingName may be changed in a settings and the address must be looked up.
            var exception: Throwable? = null

            while (pingActive) {
                var pingUsedMillis = 0L
                if (host.enabled && host.pingMe) {
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
                            } catch (e: Exception) {
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
                                    host.deadAliveTransition.addPingResult(1)
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
                                    host.deadAliveTransition.addPingResult(-1)

                                }
                            }

                            else -> {
                                host.mutex.withLock {
                                    host.pingState = WolHost.PingStates.EXCEPTION
                                    host.pingException = exception
                                    host.lastPingResponseAt.update(Instant.EPOCH)
                                    host.deadAliveTransition.addPingResult(0)
                                }
                            }
                        }
                    }
                    _targetPingChanged.value = host.pKey
                }

                val neededDelay = settingsData.pingDelayMillis - pingUsedMillis
                if (neededDelay > 10) delay(neededDelay)
            }

            if (resetState) {
                host.mutex.withLock {
                    host.resetPingState()
                }
            }
            _targetPingChanged.value = host.pKey
        }
    }

    /**
     * Runs in [Dispatchers.IO] context with a mutex lock on the host. Sends a WOL bundle to the host.
     * Observe [targetWakeChangedLiveData] to respond after all the WOL packets have been sent (or
     * [WolHost.wakeupException] will be non-null.)
     *
     * If host responded to a ping inside of [SettingsData.pingResponseWaitMillis], no WOL is sent and the
     * return is false.
     *
     * @return True if WOL bundle was sent and [targetWakeChangedLiveData] was set to the [WolHost.pKey.
     */
    suspend fun wakeTarget(host: WolHost): Boolean {
        val isSendWol = withContext(Dispatchers.IO) {
            host.mutex.withLock {
                if (host.lastPingResponseAt.age(host.lastPingSentAt) > settingsData.pingResponseWaitMillis * 2 &&
                    host.lastPingSentAt.age() < settingsData.pingDelayMillis * 3
                ) {
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
                    true
                } else {
                    false
                }
            }
        }

        if (isSendWol) {
            _targetWakeChanged.value = host.pKey
        }

        return isSendWol
    }

    override fun onCleared() {
        super.onCleared()
        // FIX: Should not be needed. We add these at the application level,
        targets.forEach { wh ->
            wh.deadAliveTransition.aliveDeadTransition.removeObserver(observerOfHostState)
        }
    }


    class ObserverOfHostState(private val hostStateNotification: HostStateNotification) : Observer<PingDeadToAwakeTransition.WolHostSignal> {
        override fun onChanged(t: PingDeadToAwakeTransition.WolHostSignal) {
            dog { "ObserverOfHostState: $t" }
            when (t.signal) {
                PingDeadToAwakeTransition.WHS.NOTHING -> {}

                PingDeadToAwakeTransition.WHS.AWOKE -> {
                    hostStateNotification.makeAwokeNotification("${t.host.title} Awoke", "${t.host.title} transitioned to awake")
                }

                PingDeadToAwakeTransition.WHS.DIED -> {
                    hostStateNotification.makeAsleepNotification("${t.host.title} Unresponsive", "${t.host.title} transitioned to unresponsive")
                }

                PingDeadToAwakeTransition.WHS.NOISE -> {
                    hostStateNotification.makeAwokeNotification("${t.host.title} ${t.extra}", "${Instant.now()}")
                }
            }
        }
    }

    companion object {
        private const val tag = "MainViewModel"
        private const val debugLoggingEnabled = true
        private const val uniqueIdentifier = "DOGLOG"

        private fun dog(message: () -> String) {
            if (debugLoggingEnabled) {
                if (BuildConfig.DOG_ON && BuildConfig.DEBUG) {
                    if (Log.isLoggable(tag, Log.ERROR)) {
                        val duration = Duration.between(WolApplication.APP_EPOCH, Instant.now()).toMillis() / 1000.0
                        val durationString = "[%8.3f]".format(duration)
                        Log.println(Log.ERROR, tag, durationString + uniqueIdentifier + ":" + message())
                    }
                }
            }
        }
    }
}