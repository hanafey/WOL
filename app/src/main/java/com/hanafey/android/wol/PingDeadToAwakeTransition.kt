package com.hanafey.android.wol

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hanafey.android.wol.magic.WolHost

class PingDeadToAwakeTransition(val host: WolHost) {
    private val ltag = this.javaClass.simpleName
    private val lon = true
    private val lunq = "ztpuln"

    val aliveDeadTransition: LiveData<Pair<WolHost, Int>>
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

    private var previousBufferSignal = 0
    private var currentBufferSignal = 0
    private var lastPingMillies = 0L
    private var lastPingReportedMillies = 0L
    private val testingReportPeriod = 30 * 1000L

    private val _aliveDeadTransition = MutableLiveData(Pair(host, 0))

    init {
        resetBuffer()
    }

    /**
     * Sets to the state at construction, empty buffer no current or previous signal
     */
    private fun resetBuffer() {
        lastPingReportedMillies = 0L
        lastPingMillies = 0L
        previousBufferSignal = 0
        currentBufferSignal = 0
        bufferIx = -1
        for (ix in buffer.indices) {
            buffer[ix] = Int.MIN_VALUE
        }
    }

    /**
     * Record ping assessment of wakefulness by sending a 1 for responded, -1 for no response, and 0 for problem sending ping.
     *
     * @param pmz Plus, Minus, or Zero for ping response, no ping response, unable to access ping response.
     * @return the New signal. Normally this is observer via [aliveDeadTransition] live data. An informative
     * signal is +1 or -1, for alive or dead.
     */
    fun addPingResult(pmz: Int): Int {
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
        dlog(ltag, lunq, lon) { "${host.title} delta=$delta pmz=$pmz ix=$bufferIx buf=${buffer.toList()} $previousBufferSignal -> $currentBufferSignal => $accessedSignal" }
        if (accessedSignal != 0) {
            // Interesting Transition
            lastPingReportedMillies = now
            _aliveDeadTransition.value = host to accessedSignal
        } else if (now - lastPingReportedMillies > testingReportPeriod) {
            lastPingReportedMillies = now
            _aliveDeadTransition.value = host to 999
        }
        return accessedSignal
    }

    /**
     * Returns 0 if last sample is ambiguous, 1 if it signals alive, or -1 if it signals dead.
     */
    private fun assessBuffer(): Int {
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
            0
        } else if (pc >= minSignal) {
            1
        } else if (nc >= minSignal) {
            -1
        } else {
            0
        }
    }

    /**
     * Returns 0 for no definite signal, or 1 for dead to alive, or -1 for alive to dead.
     */
    private fun accessBufferSignal(): Int {
        return when {
            previousBufferSignal == 0 || currentBufferSignal == 0 -> 0
            previousBufferSignal == currentBufferSignal -> 0
            else -> currentBufferSignal
        }
    }
}