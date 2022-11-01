package com.hanafey.android.wol.magic

import java.time.Duration
import java.time.Instant

/**
 * Methods should be called only in the context of the [WolHost.mutex] lock.
 */
open class AckInstant internal constructor() {
    protected var instant: Instant = Instant.EPOCH
    private var ack: Boolean = true

    /**
     * Sets the instant and marks it not yet acknowledged.
     */
    open fun update(inst: Instant) {
        instant = inst
        ack = false
    }

    /**
     * Returns the instant and if is was already acknowledged and changes nothing
     */
    fun state(): Pair<Instant, Boolean> {
        val i = instant
        val a = ack
        return i to a
    }

    /**
     * Returns the instant and if is was already acknowledged, and set acknowledged to to true
     */
    fun consume(): Pair<Instant, Boolean> {
        val i = instant
        val a = ack
        ack = true
        return i to a
    }

    /**
     * Duration between [instant] and now. If [instant] is the epoch, [Long.MAX_VALUE] is returned,
     */
    fun age(): Long {
        return if (instant === Instant.EPOCH) {
            // Not set, we call tnis a long time
            Long.MAX_VALUE
        } else {
            Duration.between(instant, Instant.now()).toMillis()
        }
    }

    fun age(from: AckInstant): Long {
        return if (from.instant !== Instant.EPOCH && instant !== Instant.EPOCH) {
            Duration.between(from.instant, instant).toMillis()
        } else {
            Long.MAX_VALUE
        }
    }
}