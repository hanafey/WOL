package com.hanafey.android.wol

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import com.hanafey.android.wol.magic.MagicPacket

class SettingsFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    NavController.OnDestinationChangedListener,
    LifecycleEventObserver {

    private val ltag = "SettingsFragment"
    private val mvm: MainViewModel = WolApplication.instance.mvm
    private val ipNameRegEx = Regex("""(^\d+\.\d+\.\d+\.\d+$)|(^[a-z][a-z\d]*$)""", RegexOption.IGNORE_CASE)
    private val broadcastRegEx = Regex("""^\d+\.\d+\.\d+\.\d+$""")
    private val integerRegEx = Regex("""\d+""")

    init {
        lifecycle.addObserver(this)
    }

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
                setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
            }
        )

        screen.addPreference(
            EditTextPreference(context).apply {
                key = PrefNames.PING_WAIT.pref()
                title = "Longest wait for ping response (mSec)"
                setDefaultValue(mvm.settingsData.pingResponseWaitMillis.toString())
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                onPreferenceChangeListener = this@SettingsFragment
                setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
            }
        )

        screen.addPreference(
            EditTextPreference(context).apply {
                key = PrefNames.PING_SUSPEND_DELAY.pref()
                title = "Suspend ping when in background after this many seconds (zero means never)"
                setDefaultValue(mvm.settingsData.pingKillDelaySeconds.toString())
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                onPreferenceChangeListener = this@SettingsFragment
                setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
            }
        )

        screen.let { ps ->
            PreferenceCategory(context).let { pc ->
                pc.title = "Included Hosts"
                ps.addPreference(pc)

                for (wh in mvm.targets) {
                    // Names of GUI elements on one-based.
                    val hn = wh.pKey + 1

                    pc.addPreference(
                        SwitchPreference(context).apply {
                            key = PrefNames.HOST_ENABLED.pref(wh.pKey)
                            title = "Include host $hn (${wh.title})"
                            isChecked = wh.enabled
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                    )
                }
            }
        }

        for (wh in mvm.targets) {
            // Names of GUI elements on one-based.
            val hn = wh.pKey + 1

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

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.PING_BUNDLE_COUNT.pref(wh.pKey)
                            title = "Number of WOL packets in wake up message (1 to 25)"
                            setDefaultValue(wh.magicPacketBundleCount.toString())
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                            onPreferenceChangeListener = this@SettingsFragment
                            setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
                        }
                    )

                    pc.addPreference(
                        EditTextPreference(context).apply {
                            key = PrefNames.PING_BUNDLE_DELAY.pref(wh.pKey)
                            title = "Delay between WOL packets in mSec (10 to 1000)"
                            setDefaultValue(wh.magicPacketBundleSpacing.toString())
                            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                            onPreferenceChangeListener = this@SettingsFragment
                            setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
                        }
                    )

                    pc
                }
            }

        }

        preferenceScreen = screen
        mvm.settingsData.spm.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceChange(pref: Preference, newValue: Any?): Boolean {
        val (pk, hn) = PrefNames.fromString(pref.key)
        val ix = hn - 1

        return when (pk) {
            PrefNames.HOST_ENABLED -> {
                val isEnabled = newValue as Boolean
                val sectionKey = PrefNames.HOST_SECTION.pref(ix)
                findPreference<PreferenceCategory>(sectionKey)?.isVisible = isEnabled
                mvm.targets[ix].enabled = isEnabled
                if (!isEnabled && mvm.targets[ix].pingMe) {
                    // Never ping a not enabled host.
                    mvm.targets[ix].pingMe = false
                }
                mvm.settingsData.hostDataChanged = true
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
                    mvm.settingsData.hostDataChanged = true
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
                        mvm.settingsData.hostDataChanged = true
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
                        mvm.settingsData.hostDataChanged = true
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
                            mvm.settingsData.hostDataChanged = true
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

            PrefNames.PING_WAIT -> {
                val value = (newValue as String).trim()

                when {
                    !integerRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Positive Integer required", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val intValue = value.toInt()
                            if (intValue < 100) {
                                Snackbar.make(requireView(), "Ping delay must be at least 100 mSec", Snackbar.LENGTH_LONG).show()
                                false
                            } else {
                                mvm.settingsData.pingResponseWaitMillis = intValue
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Ping response wait  must be an integer", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.PING_SUSPEND_DELAY -> {
                val value = (newValue as String).trim()

                when {
                    !integerRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Ping suspend delay must be an integer", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val intValue = value.toInt()
                            if (intValue < 0) {
                                Snackbar.make(
                                    requireView(), "Ping suspend must be at least 1 sec, or zero for never", Snackbar.LENGTH_LONG
                                ).show()
                                false
                            } else if (intValue > 60 * 60) {
                                Snackbar.make(
                                    requireView(), "Ping suspend must be an hour or less (3600 sec)", Snackbar.LENGTH_LONG
                                ).show()
                                false
                            } else {
                                mvm.settingsData.pingKillDelaySeconds = intValue
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Ping suspend delay must be an integer", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.PING_BUNDLE_COUNT -> {
                val value = (newValue as String).trim()

                when {
                    !integerRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Positive Integer required", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val intValue = value.toInt()

                            if (intValue < 1) {
                                Snackbar.make(requireView(), "Number of WOL packets must be at least one.", Snackbar.LENGTH_LONG).show()
                                false
                            } else if (intValue > 25) {
                                Snackbar.make(requireView(), "Number of WOL packets cannot be more than 25.", Snackbar.LENGTH_LONG).show()
                                false
                            } else {
                                mvm.targets[ix].magicPacketBundleCount = intValue.toInt()
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Positive Integer required", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.PING_BUNDLE_DELAY -> {
                val value = (newValue as String).trim()

                when {
                    !integerRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Positive Integer required", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val intValue = value.toInt()

                            if (intValue < 10) {
                                Snackbar.make(requireView(), "Delay between WOL packets must be at least 10 msec.", Snackbar.LENGTH_LONG).show()
                                false
                            } else if (intValue > 1000) {
                                Snackbar.make(requireView(), "Delay between WOL packets must be less than 1000 msec.", Snackbar.LENGTH_LONG).show()
                                false
                            } else {
                                mvm.targets[ix].magicPacketBundleSpacing = intValue.toLong()
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Positive Integer required", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            // Non UI settings
            PrefNames.HOST_TIME_TO_WAKE -> true
            PrefNames.HOST_SECTION -> true
            PrefNames.HOST_PING_ME -> true
            PrefNames.VERSION_ACKNOWLEDGED -> true
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

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                Unit
            }
            Lifecycle.Event.ON_START -> {
                findNavController().addOnDestinationChangedListener(this)
            }
            Lifecycle.Event.ON_RESUME -> {
                Unit
            }
            Lifecycle.Event.ON_PAUSE -> {
                Unit
            }
            Lifecycle.Event.ON_STOP -> {
                findNavController().removeOnDestinationChangedListener(this)
            }
            Lifecycle.Event.ON_DESTROY -> {
                Unit
            }
            Lifecycle.Event.ON_ANY -> {
                Unit
            }
        }
    }
}
