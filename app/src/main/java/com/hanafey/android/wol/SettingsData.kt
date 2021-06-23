package com.hanafey.android.wol

import android.content.SharedPreferences
import com.hanafey.android.wol.magic.WolHost

class SettingsData(val spm: SharedPreferences) {

    private val MAX_WOL_HISTORY = 25

    fun initializeModel(mvm: MainViewModel) {
        readTimeToWakeHistory(mvm)
    }

    private fun readTimeToWakeHistory(mvm: MainViewModel) {
        for (wh in mvm.targets) {
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
        for (wh in mvm.targets) {
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