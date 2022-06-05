package com.hanafey.android.wol

import android.content.SharedPreferences
import com.hanafey.android.wol.magic.WolHost

class SettingsData(val spm: SharedPreferences) {

    companion object {
        /**
         * A short history is biased too much with recent history, but a long history is slow to change if host wake
         * time evolves.
         */
        private const val MAX_WOL_HISTORY = 25
    }

    /**
     * Before navigating to [SettingsFragment] set to false. If any host data is changed [SettingsFragment] will set
     * it to true. When navigating back to MainFragment, if this is true then the hosts are re-pinged based on the current
     * (and changed) settings.
     */
    var hostDataChanged = false

    /**
     * Before navigating to [SettingsFragment] set to false. If any DAT buffer setting  is changed [SettingsFragment] will set
     * it to true. When navigating back to MainFragment, if this is true then the hosts [PingDeadToAwakeTransition] setting
     * must be updated before re-pinging hosts. When this is set true, [hostDataChanged] must also be set true
     */
    var datBufferChanged = false

    var pingDelayMillis = 1000L
    var pingResponseWaitMillis = 500
    var pingKillDelayMinutes = 5
    var pingIgnoreWiFiState = false

    var datBufferSize = 17
    var datBufferAliveAt = 14
    var datBufferDeadAt = 5

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
        var prefName: String

        prefName = PrefNames.PING_DELAY.pref()
        pingDelayMillis = spm.getString(prefName, pingDelayMillis.toString())?.toLong() ?: pingDelayMillis

        prefName = PrefNames.PING_WAIT.pref()
        pingResponseWaitMillis = spm.getString(prefName, pingResponseWaitMillis.toString())?.toInt() ?: pingResponseWaitMillis

        prefName = PrefNames.PING_SUSPEND_DELAY.pref()
        pingKillDelayMinutes = spm.getString(prefName, pingKillDelayMinutes.toString())?.toInt() ?: pingKillDelayMinutes

        prefName = PrefNames.PING_IGNORE_WIFI_STATE.pref()
        pingIgnoreWiFiState = spm.getBoolean(prefName, pingIgnoreWiFiState)

        prefName = PrefNames.DAT_BUFFER_SIZE.pref()
        datBufferSize = spm.getString(prefName, datBufferSize.toString())?.toInt() ?: datBufferSize

        prefName = PrefNames.DAT_BUFFER_ALIVE_AT.pref()
        datBufferAliveAt = spm.getString(prefName, datBufferAliveAt.toString())?.toInt() ?: datBufferAliveAt

        prefName = PrefNames.DAT_BUFFER_DEAD_AT.pref()
        datBufferDeadAt = spm.getString(prefName, datBufferDeadAt.toString())?.toInt() ?: datBufferDeadAt


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
                // Fake history. Better than empty because at least the
                // progress bar shows for the new user.
                listOf("10000", "15000")
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