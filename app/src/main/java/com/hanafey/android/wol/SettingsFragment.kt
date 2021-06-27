package com.hanafey.android.wol

import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.*

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private val LTAG = "SettingsFragment"
    private val mvm: MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        for ((k, wh) in mvm.targets) {
            require(k == wh.pKey) { "Developer error: The WolHost.pKey MUST be same as map key in MainViewModel" }
            screen.addPreference(
                SwitchPreference(context).apply {
                    key = PrefNames.HOST_ENABLED.pref(k)
                    title = "Bugger Include host $k"
                    isChecked = wh.enabled
                    onPreferenceChangeListener = this@SettingsFragment
                }
            )

            screen.let { ps ->
                PreferenceCategory(context).let { pc ->
                    pc.key = PrefNames.HOST_SECTION.pref(k)
                    pc.title = "Host $k: ${wh.title}"
                    pc.isVisible = wh.enabled
                    ps.addPreference(pc)

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_TITLE.pref(k)
                            title = "Host Name"
                            setDefaultValue(wh.title)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_PING_NAME.pref(k)
                            title = "Host IP address or name"
                            setDefaultValue(wh.pingName)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_MAC_STRING.pref(k)
                            title = "WOL MAC address (eg: a1:b2:c3:d4:e5:f6)"
                            setDefaultValue(wh.macAddress)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_BROADCAST_IP.pref(k)
                            title = "WOL Broadcast address (eg: 192.168.1.255)"
                            setDefaultValue(wh.broadcastIp)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        }
                    )

                    pc
                }
            }

        }

        preferenceScreen = screen
    }

    override fun onPreferenceChange(pref: Preference?, newValue: Any?): Boolean {
        if (pref == null || newValue == null) return true

        val (pk, pki) = PrefNames.fromString(pref.key)

        return when (pk) {
            PrefNames.HOST_ENABLED -> {
                val isEnabled = newValue as Boolean
                val sectionKey = PrefNames.HOST_SECTION.pref(pki)
                val target = mvm.targets[pki]!!
                findPreference<PreferenceCategory>(sectionKey)?.isVisible = isEnabled
                target.enabled = isEnabled
                if (!isEnabled) {
                    target.pingMe = false
                }
                mvm.settingsData.savePingEnabled(target)
                dlog(LTAG) { "Signaling ${target.pKey} ${target.title}" }
                mvm.signalPingTargetChanged(target)
                true
            }

            else -> {
                true
            }
        }
    }
}
