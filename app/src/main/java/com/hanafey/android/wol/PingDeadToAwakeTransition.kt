package com.hanafey.android.wol

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hanafey.android.wol.magic.WolHost
import java.time.Duration
import java.time.Instant

class PingDeadToAwakeTransition(val host: WolHost) {
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
     * Observe this to be able to response to host alive/dead changes.
     */
    val aliveDeadTransition: LiveData<WolHostSignal>
        get() = _aliveDeadTransition

    /**
     * Must be odd number, no ties allowed. This number of pings must be in the buffer to render an awake / asleep signal.
     */
    private val bufferSize = 15
    private val minSignalGoingUp = bufferSize - 3
    private val minSignalGoingDown = minSignalGoingUp - 4
    private var transitionCount = 0

    /**
     * If the next ping is more than this time away from the previous ping the buffer is reset to having just one entry.
     */
    private val maxDelayOrResetMillies = mSecFromMinutes(1)

    /**
     * The buffer is maintained in circular order.
     */
    private val buffer = IntArray(bufferSize)

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
    private val testingReportPeriod = mSecFromMinutes(0)

    private val _aliveDeadTransition = MutableLiveData(WolHostSignal(host, WHS.NOTHING))

    init {
        resetBuffer()
    }

    /**
     * Sets to the state at construction, empty buffer no current or previous signal
     */
    private fun resetBuffer() {
        dog { "resetBuffer" }
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
     * Record ping assessment of wakefulness by sending a 1 for responded, -1 for no response, and 0 for problem sending ping.
     *
     * @param pmz Plus, Minus, or Zero for ping response, no ping response, exception trying to ping host.
     * @return the New signal. Normally this is observer via [aliveDeadTransition] live data.
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
        dog { "${host.title}  transitions = $transitionCount $previousBufferSignal -> $currentBufferSignal" }
        if (currentBufferSignal != WHS.NOTHING) {
            // Interesting Transition
            transitionCount++
            if (transitionCount > 1 && currentBufferSignal != previousBufferSignal && previousBufferSignal != WHS.NOTHING) {
                // Do not report the first transition because is is from unknown to something.
                lastPingReportedMillies = now
                _aliveDeadTransition.value = WolHostSignal(host, currentBufferSignal)
                dog { "signal!" }
            }
        } else if (testingReportPeriod > 0 && now - lastPingReportedMillies > testingReportPeriod) {
            lastPingReportedMillies = now
            _aliveDeadTransition.value = WolHostSignal(host, WHS.NOISE, "Pinged (period=${mSecToMinutes(testingReportPeriod)})")
            dog { "noise!" }
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
        buffer.forEach { pmz ->
            when (pmz) {
                -1 -> nc++
                1 -> pc++
                0 -> zc++
            }
        }
        val tc = nc + pc + zc

        return if (tc < bufferSize || zc > 0) {
            WHS.NOTHING
        } else {
            if (currentState != WHS.AWOKE) {
                if (pc > minSignalGoingUp) {
                    WHS.AWOKE
                } else {
                    currentState
                }
            } else {
                if (pc <= minSignalGoingDown) {
                    WHS.DIED
                } else {
                    currentState
                }
            }
        }
    }

    companion object {
        private const val tag = "PingDeadToAwake.."
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