package com.hanafey.android.wol

import androidx.lifecycle.LiveData
import com.hanafey.android.ax.Dog
import com.hanafey.android.ax.Live
import com.hanafey.android.wol.magic.WolHost

/**
 * You must call [setBufferParameters] after creating an instance of this class. This is delayed because `SettingsData` is
 * needed to supply the DAT buffer information.
 */
class PingDeadToAwakeTransition(val host: WolHost) {
    private val ltag = "PingDeadTo..."
    private val lon = BuildConfig.LON_PingDeadToAwakeTransition

    /**
     * Signals that hosts can emit.
     */
    enum class WHS {
        /**
         * A non-signal. This should be ignored by any observers. It means there is not
         * enough data to have an opinion.
         */
        NOTHING,

        /**
         * Host transitioned from alive to dead.
         */
        DIED,

        /**
         * Host transitioned from dead to alive.
         */
        AWOKE,

        /**
         * Host made some reportable noise for debugging purposes
         */
        NOISE
    }

    /**
     * @param host The host doing the signaling.
     * @param signal The signal.
     * @param extra Event specific details.The [WHS.DIED] and [WHS.AWOKE] events have no details. The [WHS.NOISE] event
     * should include a short message of the event, and at this point this is just for testing notifications.
     */
    data class WolHostSignal(val host: WolHost, val signal: WHS, val extra: String = "")

    /**
     * Observe this to be able to response to host alive/dead changes. Multiple observers see the `onceValue` [EventDataDAT].
     */
    val aliveDeadTransition: LiveData<EventDataDAT>
        get() = _aliveDeadTransition
    private val _aliveDeadTransition = Live(EventDataDAT(WolHostSignal(host, WHS.NOTHING), true))

    /**
     * Must be odd number, no ties allowed. This number of pings must be in the buffer to render an awake / asleep signal.
     * These are properly initialized by [setBufferParameters] which must be called after class construction.
     */
    private var bufferSize = 0
    private var minSignalGoingUp = 0
    private var minSignalGoingDown = 0

    /**
     * Zero until the state is assessed, and incremented for each state. Only if the count is bigger than
     * 1 do we have a real transitions from an confirmed state to another confirmed state.
     */
    private var transitionCount = 0

    /**
     * If the next ping is more than this time away from the previous ping the buffer is reset to having just one entry.
     */
    private val maxDelayOrResetMillies = mSecFromMinutes(1)

    /**
     * The buffer is maintained in circular order.
     */
    private var buffer = IntArray(bufferSize)

    /**
     * The index is the last position stored in, or -1 for empty. The next value stored goes in [bufferIx]+1 with wrap
     * around at [bufferSize]
     */
    private var bufferIx = -1

    private var previousBufferSignal = WHS.NOTHING
    private var currentBufferSignal = WHS.NOTHING
    private var lastPingMillies = 0L
    private var lastPingReportedMillies = 0L

    /**
     * Special value zero means no test reporting. This must be zero for any released version.
     */
    private val testingReportPeriod = if (BuildConfig.DEBUG) mSecFromMinutes(0) else mSecFromMinutes(0)

    /**
     * Sets to the state at construction, empty buffer no current or previous signal
     */
    fun resetBuffer() {
        Dog.bark(ltag, lon) { "resetBuffer()" }
        lastPingReportedMillies = 0L
        lastPingMillies = 0L
        previousBufferSignal = WHS.NOTHING
        currentBufferSignal = WHS.NOTHING
        transitionCount = 0
        bufferIx = -1
        for (ix in buffer.indices) {
            buffer[ix] = Int.MIN_VALUE
        }
    }

    /**
     * Set the buffer parameters, and reset the buffer. This means the buffer history reverts to empty, and the
     * parameters that control alive / dead transitions are set.
     * @throws [IllegalArgumentException] if the parameters do not specify a valid history buffer with appropriate
     * hysteresis.
     */
    fun setBufferParameters(wh: WolHost) {
        val problem = validateBufferSettings(wh.datBufferSize, wh.datDeadAt, wh.datAliveAt)
        if (problem.isNotEmpty()) {
            throw IllegalArgumentException(problem)
        }

        if (wh.datBufferSize != bufferSize) {
            buffer = IntArray(wh.datBufferSize)
        }
        bufferSize = wh.datBufferSize
        minSignalGoingUp = wh.datAliveAt
        minSignalGoingDown = wh.datDeadAt
        resetBuffer()
    }

    /**
     * Record ping assessment of wakefulness by sending a 1 for responded, -1 for no response, and 0 for problem sending ping.
     *
     * @param pmz Plus, Minus, or Zero for ping response, no ping response, exception trying to ping host.
     * @return the New signal. Normally this is observed via [aliveDeadTransition] live data.
     */
    fun addPingResult(pmz: Int): WHS {
        val now = System.currentTimeMillis()
        val delta = now - lastPingMillies
        if (delta > maxDelayOrResetMillies) resetBuffer()
        lastPingMillies = now

        bufferIx++
        if (bufferIx >= bufferSize) bufferIx = 0
        buffer[bufferIx] = pmz
        previousBufferSignal = currentBufferSignal
        currentBufferSignal = assessBuffer(previousBufferSignal)
        if (currentBufferSignal != WHS.NOTHING) {
            // Interesting Transition
            transitionCount++
            if (transitionCount > 1 && currentBufferSignal != previousBufferSignal && previousBufferSignal != WHS.NOTHING) {
                // Do not report the first transition because is is from unknown to something.
                lastPingReportedMillies = now
                _aliveDeadTransition.postValue(EventDataDAT(WolHostSignal(host, currentBufferSignal)))
                Dog.bark(ltag, lon) { "_aliveDeadTransition ${host.title} post $currentBufferSignal!" }
            }
        } else if (testingReportPeriod > 0 && now - lastPingReportedMillies > testingReportPeriod) {
            // For testing only. This should only run for debugging purposes
            lastPingReportedMillies = now
            _aliveDeadTransition.postValue(
                EventDataDAT(WolHostSignal(host, WHS.NOISE, "Pinged (period=${mSecToMinutes(testingReportPeriod)})"))
            )
            Dog.bark(ltag, lon) { "_aliveDeadTransition: noise!" }
        }
        return currentBufferSignal
    }

    /**
     * Returns 0 if last sample is ambiguous, 1 if it signals alive, or -1 if it signals dead.
     *
     * A positive signal is stronger than a negative signal because the latter can result from failures other than
     * in the host. A ping can be lost coming or going. A response can be delayed. In contrast a response means the
     * host did respond.
     */
    private fun assessBuffer(currentState: WHS): WHS {
        var pc = 0
        var nc = 0
        var zc = 0
        val bs = buffer.size

        buffer.forEach { pmz ->
            when (pmz) {
                -1 -> nc++
                1 -> pc++
                0 -> zc++
            }
        }

        val tc = nc + pc + zc

        return if (tc < bs || zc > 0) {
            // Buffer is not filled with +1 and -1
            WHS.NOTHING
        } else {
            when (currentState) {
                WHS.NOTHING -> {
                    if (pc > minSignalGoingUp) {
                        WHS.AWOKE
                    } else if (nc >= minSignalGoingDown) {
                        WHS.DIED
                    } else {
                        // Not possible with valid settings. Buffer is full of pos and neg, and settings do
                        // not allow an ambiguous state.
                        WHS.NOTHING
                    }
                }
                WHS.DIED -> {
                    if (pc > minSignalGoingUp) {
                        WHS.AWOKE
                    } else {
                        WHS.DIED
                    }
                }
                WHS.AWOKE -> {
                    if (pc <= minSignalGoingDown) {
                        WHS.DIED
                    } else {
                        WHS.AWOKE
                    }
                }
                WHS.NOISE -> {
                    // Should never happen
                    WHS.NOTHING
                }
            }
        }
    }

    companion object {
        /**
         * Tests the validity of ping history transition buffer and returns empty string if values are valid, or a
         * string describing the error.
         */
        fun validateBufferSettings(size: Int, lowerThreshold: Int, upperThreshold: Int): String {
            return when {
                size < 3 -> "Ping sample size too small (must be >= 3)"
                size > 120 -> "Ping sample size too big ( must be <= 120"
                lowerThreshold < 0 -> "Lower threshold must be >= 0"
                upperThreshold >= size -> "Upper threshold must be < sample size"
                lowerThreshold > upperThreshold -> "The upper threshold must be the same or bigger than lower threshold"
                else -> ""
            }
        }

        @Suppress("unused")
        private fun datBufferToString(buffer: IntArray, cix: Int): String {
            val z = buffer.size
            val sa = StringBuilder(z)
            var bix: Int
            for (i in buffer.indices) {
                bix = cix - i
                if (bix < 0) {
                    bix = z - 1
                }
                val b = buffer[bix]
                sa.append(
                    when (b) {
                        1 -> 'A'
                        -1 -> 'd'
                        0 -> '.'
                        else -> '-'
                    }
                )
            }
            return sa.toString()
        }
    }
}