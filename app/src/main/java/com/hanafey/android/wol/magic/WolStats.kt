package com.hanafey.android.wol.magic

import com.hanafey.android.ax.Dog
import com.hanafey.android.wol.BuildConfig
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * This class must be instantiated  if the wake history changes or else it does not reflect the current
 * host state.
 * @param wh The host the stats apply to. At construction the only parts of [WolHost] that are referenced
 * are [WolHost.wolToWakeHistory].
 */
class WolStats internal constructor(private val wh: WolHost) {
    private val ltag = "WolStats"
    private val lon = BuildConfig.LON_WolStats

    /**
     * True if there is enough history to define average and median, else these are NaN.
     */
    val isDefined: Boolean

    /**
     * The average latency in seconds.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val aveLatency: Double

    /**
     * The median latency in seconds.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val medianLatency: Double

    /**
     * The minimum latency in seconds.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val minLatency: Double

    /**
     * The maximum latency in seconds.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val maxLatency: Double

    /**
     * A message that describes the latency in two lines.
     */
    val latencyHistoryMessage: String
        get() = computeLatencyMessage()

    private val wolLastSentAt: Instant
        get() = wh.lastWolSentAt.state().first

    init {
        val n = wh.wolToWakeHistory.size
        if (n > 0) {
            aveLatency = WolHost.wolToWakeAverage(wh.wolToWakeHistory) / 1000.0
            medianLatency = WolHost.wolToWakeMedian(wh.wolToWakeHistory) / 1000.0
            minLatency = (wh.wolToWakeHistory.minOrNull() ?: 0) / 1000.0
            maxLatency = (wh.wolToWakeHistory.maxOrNull() ?: 0) / 1000.0
            isDefined = true
        } else {
            aveLatency = Double.NaN
            medianLatency = Double.NaN
            minLatency = Double.NaN
            maxLatency = Double.NaN
            isDefined = false
        }

        Dog.bark(ltag, lon) { "init block: host=${wh.title} history size=${wh.wolToWakeHistory.size}" }
    }

    private fun computeLatencyMessage(): String {
        val lastWolAt = if (wolLastSentAt == Instant.EPOCH) {
            "No WOL Pending"
        } else {
            DateTimeFormatter.ofPattern("'WOL at - 'hh:mm:ss a")
                .format(LocalDateTime.ofInstant(wolLastSentAt, ZoneId.systemDefault()))
        }

        return when (val n = wh.wolToWakeHistory.size) {
            0 -> {
                "$lastWolAt\nNo history to inform WOL to wake latency."
            }
            1 -> {
                String.format("%s\nA single previous WOL to wake took %1.1f sec", lastWolAt, aveLatency)
            }
            else -> {
                String.format(
                    "%s\nWOL to Wake latency (%d samples)\n %1.1f median, %1.1f ave [sec]",
                    lastWolAt,
                    n,
                    medianLatency,
                    aveLatency
                )
            }
        }
    }

    fun progress(now: Instant): Int {
        return if (isDefined) {
            val duration = Duration.between(wolLastSentAt, now).seconds.toDouble()
            ((duration / medianLatency) * 100).toInt()
        } else {
            100
        }
    }
}