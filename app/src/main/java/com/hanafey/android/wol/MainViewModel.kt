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
import com.hanafey.android.wol.settings.SettingsData
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
    val networkStateLiveData: LiveData<NetworkStateTracker.NetState>
) : AndroidViewModel(application) {

    val targets = defaultHostList()

    val hostStateNotification = HostStateNotification(application)
    private val observerOfHostState = ObserverOfHostState(hostStateNotification)

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
    private val pingJobsStateLiveData: LiveData<Int>
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

    /**
     * When false the loop that pings a host ends on it's next iteration. The ping loop
     * includes the ping spacing delay, and IO delay as the ping response is waits for
     * a response, so the ping job does not end immediately when this is set to false.
     */
    private var pingActive = false

    /**
     * Set by an observer of network state.
     */
    var wiFiOn = false
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

    /**
     * Must be called after settings are read from the preference store to initialize parts of the model that depend
     * on settings. This is complicated but the fact that part of the main model drives what settings are read -- in
     * particular the list of available host slots.
     *
     * Currently this requires only that the [targets] part of the model is available.
     */
    fun initializeFromSettings() {
        targets.forEach { wh ->
            wh.deadAliveTransition.setBufferParameters(wh)
        }
    }

    /**
     * Call in a forever observer of network state.
     */
    fun onNetworkStateChanged(ns: NetworkStateTracker.NetState) {
        dog { "onNetworkStateChanged: $ns" }
        wiFiOn = ns.isAvailable && ns.isWifi
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
    private fun pingTargetsIfNeeded(scope: CoroutineScope, resetState: Boolean) {
        if (pingActive) return // ======================================== >>>

        dog { "pingTargetsBecauseNeeded." }
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

    fun cancelKillPingTargetsAfterWaiting(scope: CoroutineScope, restartJobs: Boolean) {
        scope.launch {
            delayedKillMutex.withLock {
                dog { "cancelKillPingTargets." }
                delayedKillJob?.cancelAndJoin()
                delayedKillJob = null
            }
            if (restartJobs) {
                pingTargetsIfNeeded(scope, false)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun pingTarget(scope: CoroutineScope, host: WolHost, resetState: Boolean): Job {

        return scope.launch {
            withContext(Dispatchers.IO) {
                if (resetState) {
                    host.mutex.withLock {
                        host.resetPingState()
                    }
                    _targetPingChanged.postValue(host.pKey)
                }

                var address: InetAddress? = null
                var pingName: String = host.pingName // host.pingName may be changed in a settings and the address must be looked up.
                var exception: Throwable? = null

                while (pingActive) {
                    val pingUsedMillisOrigin = System.currentTimeMillis()
                    if (host.enabled && host.pingMe && (settingsData.pingIgnoreWiFiState || wiFiOn)) {
                        if (address == null || pingName != host.pingName) {
                            address = try {
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

                        if (address != null) {
                            host.lastPingSentAt.update(Instant.now())
                            dog { "DOG1667: ping ${host.title}" }
                            val pingResult = try {
                                val x = MagicPacket.ping(address, settingsData.pingResponseWaitMillis)
                                if (x) 1 else 0
                            } catch (e: Exception) {
                                exception = e
                                -1
                            }

                            when (pingResult) {
                                1 -> {
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
                        _targetPingChanged.postValue(host.pKey)
                    }

                    val neededDelay = settingsData.pingDelayMillis - (System.currentTimeMillis() - pingUsedMillisOrigin)
                    delay(neededDelay.coerceAtLeast(100L))
                }

                if (resetState) {
                    host.mutex.withLock {
                        host.resetPingState()
                    }
                }
                _targetPingChanged.postValue(host.pKey)
            }
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
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun wakeTarget(host: WolHost): Boolean {
        val isSendWol = withContext(Dispatchers.IO) {
            host.mutex.withLock {
                if (host.lastPingResponseAt.age(host.lastPingSentAt) > settingsData.pingResponseWaitMillis * 2 &&
                    host.lastPingSentAt.age() < settingsData.pingDelayMillis * 3
                ) {
                    try {
                        repeat(host.wolBundleCount) {
                            if (it > 1) delay(host.wolBundleSpacing)
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
        // Should not be needed. We add these at the application level. Harmless -- never executed.
        targets.forEach { wh ->
            wh.deadAliveTransition.aliveDeadTransition.removeObserver(observerOfHostState)
        }
    }


    class ObserverOfHostState(private val hostStateNotification: HostStateNotification) : Observer<PingDeadToAwakeTransition.WolHostSignal> {
        override fun onChanged(whs: PingDeadToAwakeTransition.WolHostSignal) {
            dog { "ObserverOfHostState: $whs" }
            if (whs.host.datNotifications) {
                when (whs.signal) {
                    PingDeadToAwakeTransition.WHS.NOTHING -> {}

                    PingDeadToAwakeTransition.WHS.AWOKE -> {
                        hostStateNotification.makeAwokeNotification(whs.host, "${whs.host.title} Awoke", "${whs.host.title} transitioned to awake")
                    }

                    PingDeadToAwakeTransition.WHS.DIED -> {
                        hostStateNotification.makeAsleepNotification(whs.host, "${whs.host.title} Unresponsive", "${whs.host.title} transitioned to unresponsive")
                    }

                    PingDeadToAwakeTransition.WHS.NOISE -> {
                        hostStateNotification.makeAwokeNotification(whs.host, "${whs.host.title} ${whs.extra}", "${Instant.now()}")
                    }
                }
            }
        }
    }

    @Suppress("unused")
    companion object {
        private const val tag = "MainViewModel"
        private const val debugLoggingEnabled = false
        private const val uniqueIdentifier = "DOGLOG"

        @Suppress("unused")
        private fun dog(forceOn: Boolean = false, message: () -> String) {
            if (BuildConfig.DEBUG && (forceOn || (debugLoggingEnabled && BuildConfig.DOG_ON))) {
                if (Log.isLoggable(tag, Log.ERROR)) {
                    val duration = Duration.between(WolApplication.APP_EPOCH, Instant.now()).toMillis() / 1000.0
                    val durationString = "[%8.3f]".format(duration)
                    Log.println(Log.ERROR, tag, durationString + uniqueIdentifier + ":" + message())
                }
            }
        }

        @Suppress("unused")
        private inline fun die(errorIfTrue: Boolean, message: () -> String) {
            if (BuildConfig.DEBUG) {
                require(errorIfTrue, message)
            }
        }
    }
}