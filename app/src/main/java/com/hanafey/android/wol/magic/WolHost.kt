package com.hanafey.android.wol.magic

import com.hanafey.android.ax.EventData
import com.hanafey.android.wol.PingDeadToAwakeTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * You must call [PingDeadToAwakeTransition.setBufferParameters] on [deadAliveTransition] after instantiating this class.
 * Data in this class is persisted in settings, see [com.hanafey.android.wol.settings.SettingsData.initializeModel]
 * @param pKey See [pKey]
 * @param title See [title]
 * @param pingName See [pingName]
 * @param macString The MAC address to create magic WOL packet for. Example: "001132F00EC1" or
 * "00:11:32:F0:0E:C1". Used to initialize [macAddress]
 * @param broadcastIp See [broadcastIp]
 * @param coScope A coroutine scope under which we signal via [hostChangedLive] that host status has changed and thus
 * information about the host displayed to the user should be updated. These signals are sent only when these is at
 * least one active observer so it is correct to use an activity scope for this purpose. See [WolEventLiveData] for
 * details.
 */
@Suppress("CanBePrimaryConstructorProperty")
class WolHost(
    pKey: Int,
    title: String,
    pingName: String,
    macString: String,
    broadcastIp: String,
    coScope: CoroutineScope
) : Comparable<WolHost> {

    // --------------------------------------------------------------------------------
    //region Settings
    // --------------------------------------------------------------------------------

    /**
     * A unique key for each host that also orders a set of hosts. Currently this
     * is used in conjunction with a list, and [pKey] must be array index position.
     */
    val pKey: Int = pKey

    /**
     * User understandable name for the WOL target.
     */
    var title: String = title

    /**
     *  Name of ip address of WOL target. This is used to ping to see if host is
     * awake. Examples "192.168.1.250", "nasa"
     */
    var pingName: String = pingName

    /**
     * The broadcast address for WOL magic packets. Example: "192.168.1.255"
     */
    var broadcastIp: String = broadcastIp

    /**
     * If false this host is not shown. If a host is not enabled you should also set [pingMe] false. This cannot be
     * done internally because both [enabled] and [pingMe] are persisted as settings.
     */
    var enabled = true

    /**
     * If this host is being pinged, or should be pinged, this is true.
     */
    var pingMe = true

    /**
     * The MAC address in standard format.
     */
    var macAddress = MagicPacket.standardizeMac(macString)

    /**
     * Number of magic packets in a bundle.
     */
    var wolBundleCount = 3

    /**
     * Magic packet bundle spacing (mSec)
     */
    var wolBundleSpacing = 100L

    /**
     * Send notifications when dead / alive transitions are detected.
     */
    var datNotifications = true

    /**
     * Alive / Dead transition hysteresis buffer size.
     */
    var datBufferSize = 15

    /**
     * Alive / Dead transition hysteresis alive threshold.
     */
    var datAliveAt = 12

    /**
     * Alive / Dead transition hysteresis dead threshold.
     */
    var datDeadAt = 3
    //endregion
    // --------------------------------------------------------------------------------


    // --------------------------------------------------------------------------------
    //region Host State
    // --------------------------------------------------------------------------------
    /**
     * Wake on Lan stats
     */
    var wolStats: WolStats // Initialized in late init block because it depends on 'this'
        private set

    /**
     * The number of wake up magic packets sent to [macAddress]
     */
    var wakeupCount = 0
        set(value) {
            field = value
            hostChangedLive.postSignal()
        }

    /**
     * The number of times host was pinged since last reset that were successful
     */
    var pingedCountAlive = 0
        set(value) {
            field = value
            hostChangedLive.postSignal()
        }

    /**
     * The number of times host was pinged since last reset that were unsuccessful
     */
    var pingedCountDead = 0
        set(value) {
            field = value
            hostChangedLive.postSignal()
        }

    /**
     * Records the instant the last ping was attempted.
     */
    val lastPingSentAt = AckInstant()

    /**
     * Records the instant the ping was acknowledged, or EPOCH if it went
     * un-acknowledged.
     */
    val lastPingResponseAt = AckInstant()

    /**
     * Records the instant the last WOL was sent.
     */
    val lastWolSentAt = AckInstantWatched(this)

    /**
     * Records the instant of the first live ping following the instant when the
     * WOL was sent.
     */
    val lastWolWakeAt = AckInstantWatched(this)

    /**
     *  0 means status not known, 1 means responded to last ping, -1 means last ping timed out and
     *  -2 means attempt to ping threw exception.
     */
    var pingState = PingStates.INDETERMINATE

    /**
     * The exception that produced [pingState] of [PingStates.EXCEPTION]
     */
    var pingException: Throwable? = null
        set(value) {
            field = value
            hostChangedLive.postSignal()
        }

    /**
     * If the last wake up attempt threw an exception, this is it.
     */
    var wakeupException: EventData<Throwable?> = EventData(null)
        set(value) {
            field = value
            hostChangedLive.postSignal()
        }

    //endregion
    // --------------------------------------------------------------------------------

    /**
     * Used to control mutation of properties that may changed on another thread.
     */
    val mutex = Mutex()

    val deadAliveTransition = PingDeadToAwakeTransition(this)

    /**
     * History of milliseconds from WOL to successful ping, with most recent history at the end.
     * The purpose is to give an idea of how long the host takes to wake up to the ping responsive
     * state.
     */
    var wolToWakeHistory = emptyList<Int>()
        set(value) {
            val changed = field != value
            field = value
            if (changed) {
                wolStats = WolStats(this)
                hostChangedLive.signal()
            }
        }


    /**
     * Set true when history is added, and set false when history is saved to settings.
     */
    val wolToWakeHistoryChanged = AtomicBoolean(false)

    val hostChangedLive = WolEventLiveData(coScope, this, emptyList())

    init {
        // IMPORTANT: This depends on 'this' and so it is instantiated at the end of object initialization.
        wolStats = WolStats(this)
    }

    /**
     * Ensures [wolStats] is up to date with respect to the data currently in this instance
     * of [WolHost]. Note that setting the wake history already updates [wolStats] and this
     * is the only update that currently matters. This method is provided just to make sure
     * the stats are up to date and it makes sense to use when a fragment is created and you
     * want to be very certain the stats are up to date. It is really the responsibility of
     * this class to ensure that any change that affects [wolStats] internally updates it.
     */
    fun updateWolStats() {
        wolStats = WolStats(this)
    }

    fun isAwake(): Boolean {
        val (instant, ack) = lastWolSentAt.state()
        return instant != Instant.EPOCH && ack
    }

    /**
     * True if [WolHost.lastWolSentAt]`.state` show an instant when WOL was sent, and not yet acknowledged
     */
    fun isWaitingToAwake(): Boolean {
        val (instant, ack) = lastWolSentAt.state()
        return instant != Instant.EPOCH && !ack
    }

    fun cancelWaitingToAwake() {
        lastWolSentAt.update(Instant.EPOCH)
        lastWolWakeAt.update(Instant.EPOCH)
    }


    fun resetState() {
        pingedCountAlive = 0
        pingedCountDead = 0
        if (!pingMe) {
            pingState = PingStates.NOT_PINGING
        }
        // pingState = if (pingMe) PingStates.INDETERMINATE else PingStates.NOT_PINGING
        pingException = null
        wakeupCount = 0
        wakeupException = EventData(null)
        lastWolWakeAt.update(Instant.EPOCH)
        lastWolSentAt.update(Instant.EPOCH)
        // lastPingSentAt
        // lastPingResponseAt
    }


    /**
     * Resets [pingedCountAlive], [pingState], [pingException].
     */
    fun resetPingState() {
        pingedCountAlive = 0
        pingedCountDead = 0
        if (!pingMe) {
            pingState = PingStates.NOT_PINGING
        }
        pingException = null
    }


    override fun compareTo(other: WolHost): Int {
        return pKey - other.pKey
    }

    override fun toString(): String {
        return "WolHost(pKey=$pKey, title='$title', pingName='$pingName', enabled=$enabled, pingMe=$pingMe)"
    }

    enum class PingStates {
        /**
         * Not pinging the host at this time.
         */
        NOT_PINGING,

        /**
         * Unknown, no ping result yet.
         */
        INDETERMINATE,


        /**
         * Responded within the timeout to a reachability test.
         */
        ALIVE,


        /**
         * No response within timeout to a reachability test.
         */
        DEAD,


        /**
         * Attempt to ping produced an exception.
         */
        EXCEPTION
    }


    companion object {

        /**
         * @param history The list of milli seconds to wake.
         * @return Average milliseconds from WOL to ping acknowledged, or NaN if not enough data.
         */
        fun wolToWakeAverage(history: List<Int>): Double {
            val sum = history.fold(0.0) { z, milli ->
                z + milli
            }
            return if (history.isEmpty()) {
                Double.NaN
            } else {
                sum / history.size
            }
        }

        /**
         * @param history The list of milli seconds to wake.
         * @return Median milliseconds from WOL to ping acknowledged, or NaN if not enough data.
         */
        fun wolToWakeMedian(history: List<Int>): Double {
            return if (history.isEmpty()) {
                return Double.NaN
            } else {
                val sorted = history.sorted()
                val sz = sorted.size
                when {
                    sz == 1 -> {
                        sorted[0].toDouble()
                    }
                    sz % 2 == 0 -> {
                        (sorted[sz / 2] + sorted[sz / 2 - 1]) / 2.0
                    }
                    else -> {
                        sorted[sz / 2].toDouble()
                    }
                }
            }
        }

        /**
         * Purge the history data of values that are far from the median. The assumption is a host
         * may not come online because it is unplugged, or it comes online very quickly because it was already
         * on but slow to get ping response back. These events should not contaminate the history.
         * @param history  The history of milli seconds to wake.
         * @param tooLittleHistory If less than this number of samples do nothing.
         * @param tooQuickFactor Divide median by this to get shortest time cutoff.
         * @param tooSlowFactor Multiply median by this to get longest time cutoff.
         */
        fun purgeWolToWakeAberrations(
            history: List<Int>,
            tooLittleHistory: Int,
            tooQuickFactor: Double,
            tooSlowFactor: Double
        ): List<Int> {
            return if (history.isEmpty()) {
                // No data
                history
            } else if (history.size < tooLittleHistory) {
                // Too little data
                history
            } else {
                val median = wolToWakeMedian(history)
                val lowLimit = (median / tooQuickFactor).roundToInt()
                val highLimit = (median * tooSlowFactor).roundToInt()
                history.filter { it in lowLimit..highLimit }
            }
        }
    }
}