package com.hanafey.android.wol.settings

import android.content.SharedPreferences
import com.hanafey.android.wol.MainViewModel
import com.hanafey.android.wol.magic.WolHost

class SettingsData(val spm: SharedPreferences) {

    companion object {
        /**
         * A short history is biased too much with recent history, but a long history is slow to change if host wake
         * time evolves.
         */
        private const val MAX_WOL_HISTORY = 25
    }

    var pingDelayMillis = 1000L
    var pingResponseWaitMillis = 500
    var pingKillDelayMinutes = 5
    var pingIgnoreWiFiState = false

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

        prefName = PrefNames.VERSION_ACKNOWLEDGED.pref()
        versionAcknowledged = spm.getInt(prefName, versionAcknowledged)

        for (wh in mvm.targets) {
            prefName = PrefNames.HOST_ENABLED.pref(wh.pKey)
            wh.enabled = spm.getBoolean(prefName, wh.enabled)

            prefName = PrefNames.HOST_DAT_NOTIFY.pref(wh.pKey)
            wh.datNotifications = spm.getBoolean(prefName, wh.datNotifications)

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

            prefName = PrefNames.HOST_WOL_BUNDLE_COUNT.pref(wh.pKey)
            wh.wolBundleCount = spm.getString(prefName, wh.wolBundleCount.toString())?.toInt() ?: wh.wolBundleCount

            prefName = PrefNames.HOST_WOL_BUNDLE_SPACING.pref(wh.pKey)
            wh.wolBundleSpacing = spm.getString(prefName, wh.wolBundleSpacing.toString())?.toLong() ?: wh.wolBundleSpacing

            prefName = PrefNames.HOST_DAT_BUFFER_SIZE.pref(wh.pKey)
            wh.datBufferSize = spm.getString(prefName, wh.datBufferSize.toString())?.toInt() ?: wh.datBufferSize

            prefName = PrefNames.HOST_DAT_BUFFER_ALIVE_AT.pref(wh.pKey)
            wh.datAliveAt = spm.getString(prefName, wh.datAliveAt.toString())?.toInt() ?: wh.datAliveAt

            prefName = PrefNames.HOST_DAT_BUFFER_DEAD_AT.pref(wh.pKey)
            wh.datDeadAt = spm.getString(prefName, wh.datDeadAt.toString())?.toInt() ?: wh.datDeadAt
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