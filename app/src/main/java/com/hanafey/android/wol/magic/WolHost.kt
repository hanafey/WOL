package com.hanafey.android.wol.magic

import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * @param pKey A unique key for each host that also orders a set of hosts.
 * @param title User understandable name for the WOL target.
 * @param pingName Name of ip address of WOL target. This is used to ping to see if host is
 * awake. Examples "192.168.1.250", "nasa"
 * @param macString The MAC address to create magic WOL packet for. Example: "001132F00EC1" or
 * "00:11:32:F0:0E:C1"
 * @param broadcastIp The broadcast address for WOL magic packets. Example: "192.168.1.255"
 */
class WolHost(
    val pKey: Int,
    var title: String,
    var pingName: String,
    macString: String,
    var broadcastIp: String,
) : Comparable<WolHost> {
    /**
     * If false this host is not shown. If a host is not enabled you should also set [pingMe] false. This cannot be
     * done internally because both [enabled] and [pingMe] are persisted as settings.
     */
    var enabled = true

    /**
     * The MAC address in standard format.
     */
    var macAddress = MagicPacket.standardizeMac(macString)


    /**
     * If this host is being pinged, or should be pinged, this is true.
     */
    var pingMe = true


    /**
     * The number of times host was pinged since last reset that were successful
     */
    var pingedCountAlive = 0

    /**
     * The number of times host was pinged since last reset that were unsuccessful
     */
    var pingedCountDead = 0

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
    val lastWolSentAt = AckInstant()

    /**
     * Records the instant of the first live ping following the instant when the
     * WOL was sent.
     */
    val lastWolWakeAt = AckInstant()

    /**
     * History of milliseconds from WOL to successful ping, with most recent history at the end.
     * The purpose is to give an idea of how long the host takes to wake up to the ping responsive
     * state.
     */
    var wolToWakeHistory = emptyList<Int>()

    /**
     * Set true when history is added, and set false when history is saved to settings.
     */
    var wolToWakeHistoryChanged = false

    /**
     *  0 means status not known, 1 means responded to last ping, -1 means last ping timed out and
     *  -2 means attempt to ping threw exception.
     */
    var pingState = PingStates.INDETERMINATE


    /**
     * The exception that produced [pingState] of [PingStates.EXCEPTION]
     */
    var pingException: Throwable? = null


    /**
     * The number of wake up magic packets sent to [macAddress]
     */
    var wakeupCount = 0


    /**
     * If the last wake up attempt threw an exception, this is it.
     */
    var wakeupException: Throwable? = null

    fun resetState() {
        pingedCountAlive = 0
        pingedCountDead = 0
        pingState = PingStates.INDETERMINATE
        pingException = null
        wakeupCount = 0
        wakeupException = null
    }


    /**
     * Resets [pingedCountAlive], [pingState], [pingException]
     */
    fun resetPingState() {
        pingedCountAlive = 0
        pingedCountDead = 0
        pingState = PingStates.INDETERMINATE
        pingException = null
    }


    /**
     * Average milliseconds from WOL to ping acknowledged.
     */
    fun wolToWakeAverage(): Double {
        val sum = wolToWakeHistory.fold(0.0) { z, milli ->
            z + milli
        }
        return if (wolToWakeHistory.isEmpty()) {
            Double.NaN
        } else {
            sum / wolToWakeHistory.size
        }
    }

    fun wolToWakeMedian(): Double {
        return if (wolToWakeHistory.isEmpty()) {
            return Double.NaN
        } else {
            val sorted = wolToWakeHistory.sorted()
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

    override fun compareTo(other: WolHost): Int {
        return pKey - other.pKey
    }

    enum class PingStates {
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

    class AckInstant {
        private val lock = ReentrantLock()
        private var instant: Instant = Instant.EPOCH
        private var ack: Boolean = true

        /**
         * Sets the instant and marks it not yet acknowledged.
         */
        fun update(inst: Instant) {
            lock.withLock {
                instant = inst
                ack = false
            }
        }

        /**
         * Returns the instant and if is was already acknowledged and changes nothing
         */
        fun state(): Pair<Instant, Boolean> {
            return lock.withLock {
                val i = instant
                val a = ack
                i to a
            }
        }

        /**
         * Returns the instant and if is was already acknowledged, and set acknowledged to to true
         */
        fun consume(): Pair<Instant, Boolean> {
            return lock.withLock {
                val i = instant
                val a = ack
                ack = true
                i to a
            }
        }
    }
}