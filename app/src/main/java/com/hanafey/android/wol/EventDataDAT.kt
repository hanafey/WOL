package com.hanafey.android.wol

import com.hanafey.android.ax.EventDataMo

/**
 * Dead / Alive signal.
 */
class EventDataDAT(value: PingDeadToAwakeTransition.WolHostSignal, isHandled: Boolean = false) :
    EventDataMo<PingDeadToAwakeTransition.WolHostSignal>(value, 1, isHandled) {

    fun onceValueForDeadToAlive() = onceValue(0)
    fun hasBeenHandledByDeadToAlive() = hasBeenHandled(0)
}
