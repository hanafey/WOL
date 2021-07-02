package com.hanafey.android.wol

import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.*
import com.google.android.material.snackbar.Snackbar
import com.hanafey.android.wol.magic.MagicPacket

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private val LTAG = "SettingsFragment"
    private val mvm: MainViewModel by activityViewModels()
    private val ipNameRegEx = Regex("""(^\d+\.\d+\.\d+\.\d+$)|(^[a-z][a-z\d]*$)""", RegexOption.IGNORE_CASE)
    private val broadcastRegEx = Regex("""^\d+\.\d+\.\d+\.\d+$""")
    private val integerRegEx = Regex("""\d+""")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(
            EditTextPreference(context).apply {
                key = PrefNames.PING_DELAY.pref(-1)
                title = "Delay between pings (mSec)"
                setDefaultValue(mvm.settingsData.pingDelayMillis.toString())
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                onPreferenceChangeListener = this@SettingsFragment
            }
        )

        for ((k, wh) in mvm.targets) {
            require(k == wh.pKey) { "Developer error: The WolHost.pKey MUST be same as map key in MainViewModel" }
            screen.addPreference(
                SwitchPreference(context).apply {
                    key = PrefNames.HOST_ENABLED.pref(k)
                    title = "Include host $k (${wh.title})"
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
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_PING_NAME.pref(k)
                            title = "Host IP address or name"
                            setDefaultValue(wh.pingName)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_MAC_STRING.pref(k)
                            title = "WOL MAC address (eg: a1:b2:c3:d4:e5:f6)"
                            setDefaultValue(wh.macAddress)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_BROADCAST_IP.pref(k)
                            title = "WOL Broadcast address (eg: 192.168.1.255)"
                            setDefaultValue(wh.broadcastIp)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                    )

                    pc
                }
            }

        }

        preferenceScreen = screen
        mvm.settingsData.spm.registerOnSharedPreferenceChangeListener(this)
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
                mvm.signalPingTargetChanged(target)
                true
            }

            PrefNames.HOST_TITLE -> {
                val value = (newValue as String).trim()
                if (value.isEmpty()) {
                    // Invalid
                    Snackbar.make(requireView(), "Cannot set host name to blank!", Snackbar.LENGTH_LONG).show()
                    false
                } else {
                    mvm.targets[pki]?.title = value
                    true
                }
            }

            PrefNames.HOST_PING_NAME -> {
                val value = (newValue as String).trim()

                when {
                    value.isEmpty() -> {
                        Snackbar.make(requireView(), "Cannot set ping address to blank!", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    !ipNameRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Must be like 192.168.1.1 or a name", Snackbar.LENGTH_LONG).show()
                        false
                    }
                    else -> {
                        mvm.targets[pki]?.pingName = value
                        true
                    }
                }
            }

            PrefNames.HOST_BROADCAST_IP -> {
                val value = (newValue as String).trim()

                when {
                    value.isEmpty() -> {
                        Snackbar.make(requireView(), "Cannot set broadcast address to blank!", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    !broadcastRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Must be like 192.168.1.255", Snackbar.LENGTH_LONG).show()
                        false
                    }
                    else -> {
                        mvm.targets[pki]?.broadcastIp = value
                        true
                    }
                }
            }

            PrefNames.HOST_MAC_STRING -> {
                val value = (newValue as String).trim()

                when {
                    value.isEmpty() -> {
                        Snackbar.make(requireView(), "Cannot set broadcast address to blank!", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val mac = MagicPacket.standardizeMac(value)
                            mvm.targets[pki]?.macAddress = mac
                            true
                        } catch (_: Exception) {
                            Snackbar.make(requireView(), "Must be like a1:b2:c3:d4:e5:f6", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.PING_DELAY -> {
                val value = (newValue as String).trim()

                when {
                    !integerRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Positive Integer required", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val intValue = value.toInt()
                            if (intValue < 500) {
                                Snackbar.make(requireView(), "Ping delay must be at least 500 mSec", Snackbar.LENGTH_LONG).show()
                                false
                            } else {
                                mvm.settingsData.pingDelayMillis = intValue.toLong()
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Ping delay must be an integer", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            // Non UI settings
            PrefNames.HOST_TIME_TO_WAKE -> true
            PrefNames.HOST_SECTION -> true
            PrefNames.HOST_PKEY -> true
            PrefNames.HOST_PING_ME -> true
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
        val (pk, pki) = PrefNames.fromString(key)
        when (pk) {
            PrefNames.HOST_TITLE -> {
                val sectionKey = PrefNames.HOST_ENABLED.pref(pki)
                val wh = mvm.targets[pki]!!
                findPreference<Preference>(sectionKey)?.title = "Include host $pki (${wh.title})"
            }

            else -> {
                // Ignore others
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mvm.settingsData.spm.unregisterOnSharedPreferenceChangeListener(this)
    }
}
