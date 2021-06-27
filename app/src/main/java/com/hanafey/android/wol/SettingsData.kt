package com.hanafey.android.wol

import android.content.SharedPreferences
import com.hanafey.android.wol.magic.WolHost

class SettingsData(val spm: SharedPreferences) {

    private val MAX_WOL_HISTORY = 25

    /**
     * Normally a pingable host is running thus a WOL packet is meaningless, but is is also
     * harmless. Good for testing so the WOL packet can be observed on the network.
     */
    val wakePingableHosts: Boolean = true

    var pingDelayMillis = 1000L
    var pingResponseWaitMillis = 500

    fun initializeModel(mvm: MainViewModel) {
        readUiSettings(mvm)
        readTimeToWakeHistory(mvm)
    }

    fun savePingEnabled(wh: WolHost) {
        val editor = spm.edit()
        val prefName = PrefNames.HOST_PING_ME.pref(wh.pKey)
        editor.putBoolean(prefName, wh.pingMe)
        editor.apply()
    }

    private fun readUiSettings(mvm: MainViewModel) {
        var prefName: String = ""
        for ((ix, wh) in mvm.targets) {
            prefName = PrefNames.HOST_ENABLED.pref(wh.pKey)
            wh.enabled = spm.getBoolean(prefName, wh.enabled) ?: wh.enabled
            prefName = PrefNames.HOST_PING_ME.pref(wh.pKey)
            wh.pingMe = spm.getBoolean(prefName, wh.pingMe) ?: wh.pingMe
            prefName = PrefNames.HOST_TITLE.pref(wh.pKey)
            wh.title = spm.getString(prefName, wh.title) ?: wh.title
            prefName = PrefNames.HOST_PING_NAME.pref(wh.pKey)
            wh.pingName = spm.getString(prefName, wh.pingName) ?: wh.pingName
            prefName = PrefNames.HOST_MAC_STRING.pref(wh.pKey)
            wh.macAddress = spm.getString(prefName, wh.macAddress) ?: wh.macAddress
            prefName = PrefNames.HOST_BROADCAST_IP.pref(wh.pKey)
            wh.broadcastIp = spm.getString(prefName, wh.broadcastIp) ?: wh.broadcastIp
        }
    }

    private fun readTimeToWakeHistory(mvm: MainViewModel) {
        for ((ix, wh) in mvm.targets) {
            val prefName = PrefNames.HOST_TIME_TO_WAKE.pref(wh.title)
            val string: String = spm.getString(prefName, "") ?: ""
            val strings = if (string.isNotBlank()) {
                string!!.split(',')
            } else {
                emptyList()
            }

            val ints = strings.map {
                try {
                    it.toInt()
                } catch (_: Exception) {
                    0
                }
            }.filter { it > 0 }

            wh.wolToWakeHistory = ints
        }
    }

    fun writeTimeToWakeHistory(mvm: MainViewModel) {
        for ((ix, wh) in mvm.targets) {
            writeTimeToWakeHistory(wh)
        }
    }

    fun writeTimeToWakeHistory(wh: WolHost) {
        val prefName = PrefNames.HOST_TIME_TO_WAKE.pref(wh.title)
        val truncatedHistory = if (wh.wolToWakeHistory.size > MAX_WOL_HISTORY) {
            wh.wolToWakeHistory.subList(wh.wolToWakeHistory.size - MAX_WOL_HISTORY, wh.wolToWakeHistory.size)
        } else {
            wh.wolToWakeHistory
        }
        val strings = truncatedHistory.map { it.toString() }
        val string = strings.joinToString(",")
        with(spm.edit()) {
            putString(prefName, string)
            apply()
        }
    }
}