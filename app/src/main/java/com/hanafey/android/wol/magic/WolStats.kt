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
 */
class WolStats internal constructor(private val wh: WolHost) {
    private val ltag = "WolStats"
    private val lon = BuildConfig.LON_WolStats

    private val wolLastSentAt: Instant
        get() = wh.lastWolSentAt.state().first
    val isDefined: Boolean
    val aveLatency: Double
    val medianLatency: Double
    val latencyHistoryMessage: String

    init {
        val n = wh.wolToWakeHistory.size
        if (n > 0) {
            aveLatency = WolHost.wolToWakeAverage(wh.wolToWakeHistory) / 1000.0
            medianLatency = WolHost.wolToWakeMedian(wh.wolToWakeHistory) / 1000.0
            isDefined = true
        } else {
            aveLatency = Double.NaN
            medianLatency = Double.NaN
            isDefined = false
        }

        val lastWolAt = if (wolLastSentAt == Instant.EPOCH) {
            "No WOL Pending"
        } else {
            DateTimeFormatter.ofPattern("'WOL at - 'hh:mm:ss a")
                .format(LocalDateTime.ofInstant(wolLastSentAt, ZoneId.systemDefault()))
        }

        latencyHistoryMessage = when (n) {
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

        Dog.bark(ltag, lon) { "Init: host=${wh.title} history size=${wh.wolToWakeHistory.size}" }
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