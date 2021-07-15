package com.hanafey.android.wol

import android.content.SharedPreferences
import com.hanafey.android.wol.magic.WolHost

class SettingsData(val spm: SharedPreferences) {

    companion object {
        private const val MAX_WOL_HISTORY = 25
    }

    /**
     * Before navigating to [SettingsFragment] set to false. If any host data is changed [SettingsFragment] will set
     * it to true. When [MainFragment] is created if this is false then pinging must be stopped and restarted.
     */
    var hostDataChanged = false

    /**
     * Normally a pingable host is running thus a WOL packet is meaningless, but is is also
     * harmless. Good for testing so the WOL packet can be observed on the network.
     */
    val wakePingableHosts: Boolean = true

    var pingDelayMillis = 1000L
    var pingResponseWaitMillis = 500
    var versionAcknowledged = 0

    fun initializeModel(mvm: MainViewModel) {
        readSettings(mvm)
    }

    fun savePingMe(wh: WolHost) {
        val editor = spm.edit()
        val prefName = PrefNames.HOST_PING_ME.pref(wh.pKey)
        editor.putBoolean(prefName, wh.pingMe)
        editor.apply()
    }

    private fun readSettings(mvm: MainViewModel) {
        var prefName = PrefNames.PING_DELAY.pref()
        pingDelayMillis = spm.getString(prefName, pingDelayMillis.toString())?.toLong() ?: pingDelayMillis
        prefName = PrefNames.PING_WAIT.pref()
        pingResponseWaitMillis = spm.getString(prefName, pingResponseWaitMillis.toString())?.toInt() ?: pingResponseWaitMillis
        prefName = PrefNames.VERSION_ACKNOWLEDGED.pref()
        versionAcknowledged = spm.getInt(prefName, versionAcknowledged)

        for (wh in mvm.targets) {
            prefName = PrefNames.HOST_ENABLED.pref(wh.pKey)
            wh.enabled = spm.getBoolean(prefName, wh.enabled)
            prefName = PrefNames.HOST_PING_ME.pref(wh.pKey)
            wh.pingMe = spm.getBoolean(prefName, wh.pingMe)
            if (wh.pingMe) {
                wh.pingState = WolHost.PingStates.INDETERMINATE
            } else {
                wh.pingState = WolHost.PingStates.NOT_PINGING
            }
            prefName = PrefNames.HOST_TITLE.pref(wh.pKey)
            wh.title = spm.getString(prefName, wh.title) ?: wh.title
            prefName = PrefNames.HOST_PING_NAME.pref(wh.pKey)
            wh.pingName = spm.getString(prefName, wh.pingName) ?: wh.pingName
            prefName = PrefNames.HOST_MAC_STRING.pref(wh.pKey)
            wh.macAddress = spm.getString(prefName, wh.macAddress) ?: wh.macAddress
            prefName = PrefNames.HOST_BROADCAST_IP.pref(wh.pKey)
            wh.broadcastIp = spm.getString(prefName, wh.broadcastIp) ?: wh.broadcastIp
        }

        readTimeToWakeHistory(mvm)
    }

    private fun readTimeToWakeHistory(mvm: MainViewModel) {
        for (wh in mvm.targets) {
            val prefName = PrefNames.HOST_TIME_TO_WAKE.pref(wh.pKey)
            val string: String = spm.getString(prefName, "") ?: ""
            val strings = if (string.isNotBlank()) {
                string.split(',')
            } else {
                // debugger: No defaults are appropriate.
                // emptyList()
                listOf("30000", "35000", "25000")
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
        for (wh in mvm.targets) {
            writeTimeToWakeHistory(wh)
        }
    }

    fun writeTimeToWakeHistory(wh: WolHost) {
        val prefName = PrefNames.HOST_TIME_TO_WAKE.pref(wh.pKey)
        val truncatedHistory = if (wh.wolToWakeHistory.size > MAX_WOL_HISTORY) {
            wh.wolToWakeHistory.subList(wh.wolToWakeHistory.size - MAX_WOL_HISTORY, wh.wolToWakeHistory.size)
        } else {
            wh.wolToWakeHistory
        }
        wh.wolToWakeHistory = truncatedHistory
        val strings = truncatedHistory.map { it.toString() }
        val string = strings.joinToString(",")
        with(spm.edit()) {
            putString(prefName, string)
            apply()
        }
    }

    fun writeVersionAcknowledged(version: Int) {
        val prefName = PrefNames.VERSION_ACKNOWLEDGED.pref()
        with(spm.edit()) {
            putInt(prefName, version)
            apply()
        }
    }
}