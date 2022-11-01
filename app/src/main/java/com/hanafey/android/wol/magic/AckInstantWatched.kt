package com.hanafey.android.wol.magic

import java.time.Instant

/**
 * A watched version of [AckInstant] for use only inside of the [WolHost] class.
 *
 * The watched version is used if updating the instant must also signal the change via
 * [WolHost.hostChangedLive]
 */
class AckInstantWatched(private val wh: WolHost) : AckInstant() {
    override fun update(inst: Instant) {
        val changed = inst != this.instant
        super.update(inst)
        if (changed) wh.hostChangedLive.postSignal()
    }
}