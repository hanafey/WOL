package com.hanafey.android.wol.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import com.hanafey.android.ax.Dog
import com.hanafey.android.wol.BuildConfig
import com.hanafey.android.wol.MainViewModel
import com.hanafey.android.wol.PingDeadToAwakeTransition
import com.hanafey.android.wol.R
import com.hanafey.android.wol.WolApplication
import com.hanafey.android.wol.magic.MagicPacket

class SettingsHostFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    NavController.OnDestinationChangedListener,
    LifecycleEventObserver {
    private val ltag = "SettingsHostFragment"
    private val lon = BuildConfig.LON_SettingsHostFragment

    private var hostIx: Int = -1
    private val mvm: MainViewModel = WolApplication.instance.mvm
    private val fvm: SettingsViewModel by navGraphViewModels(R.id.ng_Settings)
    private val ipNameRegEx = Regex("""(^\d+\.\d+\.\d+\.\d+$)|(^[a-z][a-z\d]*$)""", RegexOption.IGNORE_CASE)
    private val broadcastRegEx = Regex("""^\d+\.\d+\.\d+\.\d+$""")
    private val integerRegEx = Regex("""\d+""")

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val args = arguments
        require(args != null)
        hostIx = args.getInt("HOST_IX", -1)
        require(mvm.targets.indices.contains(hostIx))

        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        val wh = mvm.targets[hostIx]
        // Names of GUI elements on one-based.

        screen.let { ps ->
            PreferenceCategory(context).let { pc ->
                pc.key = PrefNames.HOST_SECTION.pref(hostIx)
                pc.title = "Host ${wh.title}"
                ps.addPreference(pc)

                pc.addPreference(
                    SwitchPreference(context).apply {
                        key = PrefNames.HOST_ENABLED.pref(hostIx)
                        title = "Include host ${wh.title}"
                        isChecked = wh.enabled
                        onPreferenceChangeListener = this@SettingsHostFragment
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_TITLE.pref(hostIx)
                        title = "Host Name"
                        setDefaultValue(wh.title)
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_PING_NAME.pref(hostIx)
                        title = "Host IP address or name"
                        setDefaultValue(wh.pingName)
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_MAC_STRING.pref(hostIx)
                        title = "WOL MAC address (eg: a1:b2:c3:d4:e5:f6)"
                        setDefaultValue(wh.macAddress)
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_BROADCAST_IP.pref(hostIx)
                        title = "WOL Broadcast address (eg: 192.168.1.255)"
                        setDefaultValue(wh.broadcastIp)
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                    }
                )
                pc.addPreference(
                    PreferenceCategory(context).apply {
                        title = "WOL Packet Settings"
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_WOL_BUNDLE_COUNT.pref(hostIx)
                        title = "Number of WOL packets in wake up message (1 to 25)"
                        setDefaultValue(wh.wolBundleCount.toString())
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                        setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_WOL_BUNDLE_SPACING.pref(hostIx)
                        title = "Delay between WOL packets in mSec (10 to 1000)"
                        setDefaultValue(wh.wolBundleSpacing.toString())
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                        setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
                    }
                )

                pc.addPreference(
                    PreferenceCategory(context).apply {
                        title = "Up / Down Detection Settings"
                    }
                )

                pc.addPreference(
                    SwitchPreference(context).apply {
                        key = PrefNames.HOST_DAT_NOTIFY.pref(hostIx)
                        title = "Send Notifications of Dead/Alive transitions"
                        isChecked = wh.datNotifications
                        onPreferenceChangeListener = this@SettingsHostFragment
                    }
                )

                pc.addPreference(
                    SwitchPreference(context).apply {
                        key = PrefNames.HOST_WOL_NOTIFY.pref(hostIx)
                        title = "Send Notifications of Wake-On-Lan success"
                        isChecked = wh.wolNotifications
                        onPreferenceChangeListener = this@SettingsHostFragment
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_DAT_BUFFER_SIZE.pref(hostIx)
                        title = "Length of Alive / Dead Transition Detection Buffer"
                        setDefaultValue(wh.datBufferSize.toString())
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                        setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_DAT_BUFFER_ALIVE_AT.pref(hostIx)
                        title = "Alive Threshold (< size-1)"
                        setDefaultValue(wh.datAliveAt.toString())
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                        setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
                    }
                )

                pc.addPreference(
                    EditTextPreference(context).apply {
                        key = PrefNames.HOST_DAT_BUFFER_DEAD_AT.pref(hostIx)
                        title = "Dead Threshold (> 0)"
                        setDefaultValue(wh.datDeadAt.toString())
                        summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        onPreferenceChangeListener = this@SettingsHostFragment
                        setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
                    }
                )

                pc
            }
        }

        preferenceScreen = screen
        mvm.settingsData.spm.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceChange(pref: Preference, newValue: Any?): Boolean {

        val wolHost = mvm.targets[hostIx]

        return when (PrefNames.fromString(pref.key).first) {
            PrefNames.HOST_ENABLED -> {
                val isEnabled = newValue as Boolean
                wolHost.enabled = isEnabled
                if (!isEnabled && wolHost.pingMe) {
                    // Never ping a not enabled host.
                    wolHost.pingMe = false
                }
                fvm.hostDataChanged = true
                true
            }

            PrefNames.HOST_TITLE -> {
                val value = (newValue as String).trim()
                if (value.isEmpty()) {
                    // Invalid
                    Snackbar.make(requireView(), "Cannot set host name to blank!", Snackbar.LENGTH_LONG).show()
                    false
                } else {
                    wolHost.title = value
                    fvm.hostDataChanged = true
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
                        wolHost.pingName = value
                        fvm.hostDataChanged = true
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
                        wolHost.broadcastIp = value
                        fvm.hostDataChanged = true
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
                            wolHost.macAddress = mac
                            fvm.hostDataChanged = true
                            true
                        } catch (_: Exception) {
                            Snackbar.make(requireView(), "Must be like a1:b2:c3:d4:e5:f6", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.HOST_WOL_BUNDLE_COUNT -> {
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
                                wolHost.wolBundleCount = intValue
                                fvm.hostDataChanged = true
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Positive Integer required", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.HOST_WOL_BUNDLE_SPACING -> {
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
                                fvm.hostDataChanged = true
                                false
                            } else {
                                wolHost.wolBundleSpacing = intValue.toLong()
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Positive Integer required", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.HOST_DAT_NOTIFY -> {
                val isEnabled = newValue as Boolean
                wolHost.datNotifications = isEnabled
                fvm.hostDataChanged = true
                true
            }

            PrefNames.HOST_WOL_NOTIFY -> {
                val isEnabled = newValue as Boolean
                wolHost.wolNotifications = isEnabled
                fvm.hostDataChanged = true
                true
            }

            PrefNames.HOST_DAT_BUFFER_SIZE -> {
                val value = (newValue as String).trim()

                when {
                    !integerRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Buffer size must be an integer", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val intValue = value.toInt()
                            val problem = PingDeadToAwakeTransition.validateBufferSettings(
                                intValue,
                                wolHost.datDeadAt,
                                wolHost.datAliveAt
                            )
                            if (problem.isNotEmpty()) {
                                Snackbar.make(requireView(), problem, Snackbar.LENGTH_LONG).show()
                                false
                            } else {
                                wolHost.datBufferSize = intValue
                                fvm.hostDataChanged = true
                                fvm.datBufferChanged = true
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Buffer size  must be an integer", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.HOST_DAT_BUFFER_ALIVE_AT -> {
                val value = (newValue as String).trim()

                when {
                    !integerRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Threshold  must be an integer", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val intValue = value.toInt()
                            val problem = PingDeadToAwakeTransition.validateBufferSettings(
                                wolHost.datBufferSize,
                                wolHost.datDeadAt,
                                intValue
                            )
                            if (problem.isNotEmpty()) {
                                Snackbar.make(requireView(), problem, Snackbar.LENGTH_LONG).show()
                                false
                            } else {
                                wolHost.datAliveAt = intValue
                                fvm.hostDataChanged = true
                                fvm.datBufferChanged = true
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Threshold must be an integer", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.HOST_DAT_BUFFER_DEAD_AT -> {
                val value = (newValue as String).trim()

                when {
                    !integerRegEx.matches(value) -> {
                        Snackbar.make(requireView(), "Threshold  must be an integer", Snackbar.LENGTH_LONG).show()
                        false
                    }

                    else -> {
                        try {
                            val intValue = value.toInt()
                            val problem = PingDeadToAwakeTransition.validateBufferSettings(
                                wolHost.datBufferSize,
                                intValue,
                                wolHost.datAliveAt
                            )
                            if (problem.isNotEmpty()) {
                                Snackbar.make(requireView(), problem, Snackbar.LENGTH_LONG).show()
                                false
                            } else {
                                wolHost.datDeadAt = intValue
                                fvm.hostDataChanged = true
                                fvm.datBufferChanged = true
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Threshold must be an integer", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            else -> {
                Dog.die(true) { "${pref.key} NOT EXPECTED!!" }
                false
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {
        val wolHost = mvm.targets[hostIx]
        when (PrefNames.fromString(key).first) {
            PrefNames.HOST_TITLE -> {
                // Change the title on the enable host switch to reflect new host name.
                val sectionKey = PrefNames.HOST_ENABLED.pref(hostIx)
                findPreference<Preference>(sectionKey)?.title = "Include host  ${wolHost.title}"
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
        when (destination.id) {
            R.id.SettingsFragment -> {
                Dog.bark(ltag, lon) { "onDestinationChanged(): Back to SettingsFragment. No action needed." }
            }
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> Unit

            Lifecycle.Event.ON_START -> {
                findNavController().addOnDestinationChangedListener(this)
            }

            Lifecycle.Event.ON_RESUME -> {}
            Lifecycle.Event.ON_PAUSE -> {}

            Lifecycle.Event.ON_STOP -> {
                findNavController().removeOnDestinationChangedListener(this)
            }

            Lifecycle.Event.ON_DESTROY -> {}
            Lifecycle.Event.ON_ANY -> {}
        }
    }
}
