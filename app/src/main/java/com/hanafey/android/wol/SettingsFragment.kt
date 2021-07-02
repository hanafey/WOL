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
                key = PrefNames.PING_DELAY.pref()
                title = "Delay between pings (mSec)"
                setDefaultValue(mvm.settingsData.pingDelayMillis.toString())
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                onPreferenceChangeListener = this@SettingsFragment
            }
        )

        for (wh in mvm.targets) {
            // Names of GUI elements on one-based.
            val hn = wh.pKey + 1
            screen.addPreference(
                SwitchPreference(context).apply {
                    key = PrefNames.HOST_ENABLED.pref(wh.pKey)
                    title = "Include host $hn (${wh.title})"
                    isChecked = wh.enabled
                    onPreferenceChangeListener = this@SettingsFragment
                }
            )

            screen.let { ps ->
                PreferenceCategory(context).let { pc ->
                    pc.key = PrefNames.HOST_SECTION.pref(wh.pKey)
                    pc.title = "Host $hn: ${wh.title}"
                    pc.isVisible = wh.enabled
                    ps.addPreference(pc)

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_TITLE.pref(wh.pKey)
                            title = "Host Name"
                            setDefaultValue(wh.title)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_PING_NAME.pref(wh.pKey)
                            title = "Host IP address or name"
                            setDefaultValue(wh.pingName)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_MAC_STRING.pref(wh.pKey)
                            title = "WOL MAC address (eg: a1:b2:c3:d4:e5:f6)"
                            setDefaultValue(wh.macAddress)
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.HOST_BROADCAST_IP.pref(wh.pKey)
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

        val (pk, hn) = PrefNames.fromString(pref.key)
        val ix = hn - 1

        return when (pk) {
            PrefNames.HOST_ENABLED -> {
                val isEnabled = newValue as Boolean
                val sectionKey = PrefNames.HOST_SECTION.pref(ix)
                val target = mvm.targets[ix]
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
                    mvm.targets[ix].title = value
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
                        mvm.targets[ix].pingName = value
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
                        mvm.targets[ix].broadcastIp = value
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
                            mvm.targets[ix].macAddress = mac
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
        val (pk, hn) = PrefNames.fromString(key)
        val ix = hn - 1
        when (pk) {
            PrefNames.HOST_TITLE -> {
                // Change the title on the enable host switch to reflect new host name.
                val sectionKey = PrefNames.HOST_ENABLED.pref(ix)
                val wh = mvm.targets[ix]
                findPreference<Preference>(sectionKey)?.title = "Include host $hn (${wh.title})"
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
