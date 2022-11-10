package com.hanafey.android.wol.magic

import androidx.annotation.MainThread
import androidx.lifecycle.MediatorLiveData
import com.hanafey.android.ax.Dog
import com.hanafey.android.ax.Live
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Combines any number of component live data elements that when changed result in this live data changing.
 * [signal] and [postSignal] can also be used to trigger live data observation.
 * @param scope Scope used to launch a coroutine that loops on a ~100 msec interval and sets live data if any signals came in during the
 * previous interval. This looping happens only while there is at least one active observer (emphasis on active, not just added).
 * @param wh The wol host that is reported via live data.
 * @param basedOn A list of other live data who setting cascades to this live data, just like a direct call to [signal] or [postSignal].
 */
class WolEventLiveData(
    private val scope: CoroutineScope,
    private val wh: WolHost,
    basedOn: List<Live<out Any>>
) : MediatorLiveData<WolHost>() {

    private val ltag = "WolEventLiveData"
    private val lon = true
    private val lun = wh.title

    private val signallingOn = AtomicBoolean(false)
    private val signalNextTime = AtomicBoolean(false)

    init {
        basedOn.forEach {
            addSource(it) {
                signalNextTime.set(true)
            }
        }
    }

    @MainThread
    fun signal() {
        signalNextTime.set(true)
    }

    fun postSignal() {
        signalNextTime.set(true)
    }

    override fun onActive() {
        super.onActive()
        Dog.bark(ltag, lon, lun) { "onActive(): start emitting." }
        signallingOn.set(true)

        // This is the loop where we ensure observers are called if any triggering
        // event happens in the interval we delay to accumulated the related
        // instigating events.
        scope.launch {
            while (signallingOn.get()) {
                delay(250L)
                if (signalNextTime.getAndSet(false)) {
                    Dog.bark(ltag, lon, lun) { "onActive(): Set live data now." }
                    this@WolEventLiveData.value = wh
                }
            }
        }
    }

    override fun onInactive() {
        Dog.bark(ltag, lon, lun) { "onInactive()" }
        if (signallingOn.getAndSet(false)) {
            Dog.bark(ltag, lon, lun) { "onInactive(): Set live data now." }
            this@WolEventLiveData.value = wh
        }
        super.onInactive()
    }
}