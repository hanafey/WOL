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
         * A non-signal. This should be ignored by any observers.
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

    val aliveDeadTransition: LiveData<WolHostSignal>
        get() = _aliveDeadTransition

    /**
     * Must be odd number, no ties allowed. This number of pings must be in the buffer to render an awake / asleep signal.
     */
    val bufferSize = 5

    /**
     * If the next ping is more than this time away from the previous ping the buffer is reset to having just one entry.
     */
    val maxDelayOrResetMillies = 60L * 1000L

    /**
     * The minimum signal size. This is the smallest majority of the odd numbered sized buffer.
     */
    private val minSignal = bufferSize / 2 + 1

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
    private val testingReportPeriod = mSecFromMinutes(0)

    private val _aliveDeadTransition = MutableLiveData(WolHostSignal(host, WHS.NOTHING))

    init {
        resetBuffer()
    }

    /**
     * Sets to the state at construction, empty buffer no current or previous signal
     */
    private fun resetBuffer() {
        lastPingReportedMillies = 0L
        lastPingMillies = 0L
        previousBufferSignal = WHS.NOTHING
        currentBufferSignal = WHS.NOTHING
        bufferIx = -1
        for (ix in buffer.indices) {
            buffer[ix] = Int.MIN_VALUE
        }
    }

    /**
     * Record ping assessment of wakefulness by sending a 1 for responded, -1 for no response, and 0 for problem sending ping.
     *
     * @param pmz Plus, Minus, or Zero for ping response, no ping response, unable to access ping response.
     * @return the New signal. Normally this is observer via [aliveDeadTransition] live data.
     */
    fun addPingResult(pmz: Int): WHS {
        dog { "addPingResult" }
        val now = System.currentTimeMillis()
        val delta = now - lastPingMillies
        if (delta > maxDelayOrResetMillies) resetBuffer()
        lastPingMillies = now

        bufferIx++
        if (bufferIx >= bufferSize) bufferIx = 0
        buffer[bufferIx] = pmz

        previousBufferSignal = currentBufferSignal
        currentBufferSignal = assessBuffer()
        val accessedSignal = accessBufferSignal()
        dog { "${host.title} delta=$delta pmz=$pmz ix=$bufferIx buf=${buffer.toList()} $previousBufferSignal -> $currentBufferSignal => $accessedSignal" }
        if (accessedSignal != WHS.NOTHING) {
            // Interesting Transition
            lastPingReportedMillies = now
            _aliveDeadTransition.value = WolHostSignal(host, accessedSignal)
        } else if (testingReportPeriod > 0 && now - lastPingReportedMillies > testingReportPeriod) {
            lastPingReportedMillies = now
            _aliveDeadTransition.value = WolHostSignal(host, WHS.NOISE, "Pinged (period=${mSecToMinutes(testingReportPeriod)})")
        }
        return accessedSignal
    }

    /**
     * Returns 0 if last sample is ambiguous, 1 if it signals alive, or -1 if it signals dead.
     */
    private fun assessBuffer(): WHS {
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

        return if (tc < bufferSize) {
            WHS.NOTHING
        } else if (pc >= minSignal) {
            WHS.AWOKE
        } else if (nc >= minSignal) {
            WHS.DIED
        } else {
            WHS.NOTHING
        }
    }

    /**
     * Returns 0 for no definite signal, or 1 for dead to alive, or -1 for alive to dead.
     */
    private fun accessBufferSignal(): WHS {
        return when {
            previousBufferSignal == WHS.NOTHING || currentBufferSignal == WHS.NOTHING -> WHS.NOTHING
            previousBufferSignal == currentBufferSignal -> WHS.NOTHING
            else -> currentBufferSignal
        }
    }

    companion object {
        private const val tag = "PingDeadToAwakeTransition"
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