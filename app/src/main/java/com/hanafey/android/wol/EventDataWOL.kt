package com.hanafey.android.wol

import com.hanafey.android.ax.EventDataMo
import com.hanafey.android.wol.magic.WolHost

/**
 * Wake On Lan detected signal.
 */
class EventDataWOL(value: WolHost, isHandled: Boolean = false) :
    EventDataMo<WolHost>(value, 2, isHandled) {

    fun onceValueForNotify() = onceValue(0)
    fun hasBeenHandledByNotify() = hasBeenHandled(0)

    fun onceValueForNavigation() = onceValue(1)
    fun hasBeenHandledByNavigation() = hasBeenHandled(1)
}