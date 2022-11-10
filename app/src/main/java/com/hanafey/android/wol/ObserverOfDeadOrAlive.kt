package com.hanafey.android.wol

import androidx.lifecycle.Observer
import com.hanafey.android.ax.Dog
import java.time.Instant

/**
 * The observer of dead / alive transitions.
 *
 *
 */
class ObserverOfDeadOrAlive(
    private val mvm: MainViewModel,
    private val hostStateNotification: HostStateNotification
) : Observer<EventDataDAT> {
    private val ltag = "MVM.OOHS"
    private val lon = BuildConfig.LON_ObserverOfHostState

    override fun onChanged(ed: EventDataDAT) {
        val whs: PingDeadToAwakeTransition.WolHostSignal? = ed.onceValueForDeadToAlive()
        if (whs != null) {
            when (whs.signal) {
                PingDeadToAwakeTransition.WHS.AWOKE -> {
                    val host = whs.host
                    val (then, ack) = host.lastWolSentAt.consume()
                    val isAwokeByWOL = if (then != Instant.EPOCH && !ack) {
                        // Only if 'then' is not epoch does the result still apply because WOL can be cancelled,
                        // It is only the first awake event (while the sent at time remain unconsumed) that we have
                        // an WAKE on lan event. In other words a host can sleep then wake but if the user did not
                        // WOL it it is not not a wake up event.
                        // Update the history and commit to settings
                        val now = Instant.now()
                        host.lastWolWakeAt.update(now)
                        val deltaMilli = now.toEpochMilli() - then.toEpochMilli()
                        host.wolToWakeHistory = host.wolToWakeHistory + deltaMilli.toInt()
                        mvm.settingsData.writeTimeToWakeHistory(host)
                        Dog.bark(ltag, lon) { "${host.pingName} wake history written to settings." }

                        // Signal that observers of the host awoke event that the time is NOW!
                        host.signalWakeOnLanEvent()
                        true
                    } else {
                        false
                    }

                    if (isAwokeByWOL) {
                        if (whs.host.wolNotifications) {
                            hostStateNotification.makeAwokeNotification(whs.host, "${whs.host.title} Awoke on WOL", "")
                        }
                    } else {
                        if (whs.host.datNotifications) {
                            hostStateNotification.makeAwokeNotification(whs.host, "${whs.host.title} Alive", "")
                        }
                    }
                }

                PingDeadToAwakeTransition.WHS.DIED -> {
                    if (whs.host.datNotifications) {
                        hostStateNotification.makeAsleepNotification(whs.host, "${whs.host.title} Ping Unresponsive", "")
                    }
                }

                PingDeadToAwakeTransition.WHS.NOISE -> {
                    if (whs.host.datNotifications) {
                        hostStateNotification.makeAwokeNotification(whs.host, "${whs.host.title} ${whs.extra}", "${Instant.now()}")
                    }
                }

                else -> {}
            }
        }
    }
}