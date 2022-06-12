package com.hanafey.android.wol.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import com.hanafey.android.wol.BuildConfig
import com.hanafey.android.wol.MainViewModel
import com.hanafey.android.wol.R
import com.hanafey.android.wol.WolApplication
import java.time.Duration
import java.time.Instant

class SettingsFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    NavController.OnDestinationChangedListener,
    LifecycleEventObserver {

    private val mvm: MainViewModel = WolApplication.instance.mvm
    private val fvm: SettingsViewModel by navGraphViewModels(R.id.ng_Settings)
    private val integerRegEx = Regex("""\d+""")

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(
            Preference(context).apply {
                layoutResource = R.layout.preference_link
                title = "Host List"
                fragment = "SettingsHostListFragment"
                summary = "Click to open list of hosts"
            }
        )

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
                title = "Suspend ping when in background after this many minutes (zero means never)"
                setDefaultValue(mvm.settingsData.pingKillDelayMinutes.toString())
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                onPreferenceChangeListener = this@SettingsFragment
                setOnBindEditTextListener { editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER }
            }
        )

        screen.addPreference(
            SwitchPreference(context).apply {
                key = PrefNames.PING_IGNORE_WIFI_STATE.pref()
                title = "Ping even without WiFi"
                isChecked = mvm.settingsData.pingIgnoreWiFiState
                onPreferenceChangeListener = this@SettingsFragment
            }
        )

        preferenceScreen = screen
        mvm.settingsData.spm.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceChange(pref: Preference, newValue: Any?): Boolean {

        return when (PrefNames.fromString(pref.key).first) {

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
                                fvm.hostDataChanged = true
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
                                fvm.hostDataChanged = true
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
                                    requireView(), "Ping suspend must be at least 1 minute, or zero for never", Snackbar.LENGTH_LONG
                                ).show()
                                false
                            } else if (intValue > 60) {
                                Snackbar.make(
                                    requireView(), "Ping suspend must be an hour or less (60 minutes)", Snackbar.LENGTH_LONG
                                ).show()
                                false
                            } else {
                                mvm.settingsData.pingKillDelayMinutes = intValue
                                fvm.hostDataChanged = true
                                true
                            }
                        } catch (ex: Exception) {
                            Snackbar.make(requireView(), "Ping suspend delay must be an integer", Snackbar.LENGTH_LONG).show()
                            false
                        }
                    }
                }
            }

            PrefNames.PING_IGNORE_WIFI_STATE -> {
                mvm.settingsData.pingIgnoreWiFiState = newValue as Boolean
                true
            }

            else -> {
                die(true) { "${pref.key} NOT EXPECTED!!" }
                false
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {}

    override fun onDestroyView() {
        super.onDestroyView()
        mvm.settingsData.spm.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        when (destination.id) {
            R.id.MainFragment -> {
                if (fvm.datBufferChanged) {
                    mvm.targets.forEach { wh ->
                        wh.deadAliveTransition.setBufferParameters(wh)
                    }
                }

                if (fvm.hostDataChanged) {
                    mvm.pingTargetsAgain(WolApplication.instance.mainScope, false)
                    dog { "onDestinationChanged: RE-PING $destination" }
                } else {
                    dog { "onDestinationChanged: NO RE-PING $destination" }
                }
            }
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                // Essential because if no changes in child fragments up navigation
                // throws up!
                fvm.forceInstantiation()
            }
            Lifecycle.Event.ON_START -> {
                findNavController().addOnDestinationChangedListener(this)
            }
            Lifecycle.Event.ON_STOP -> {
                findNavController().removeOnDestinationChangedListener(this)
            }
            else -> {}
        }
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        dog { "onPreferenceStartFragment: $pref" }
        require(pref.fragment != null)

        return when (pref.fragment) {
            "SettingsHostListFragment" -> {
                findNavController().navigate(R.id.nga_Settings_SettingsHostList)
                true
            }
            else -> {
                die(true) { "SettingsFragment does not handle fragment '${pref.fragment}'" }
                false
            }
        }
    }

    companion object {
        private const val tag = "SettingsFragment"
        private const val debugLoggingEnabled = false
        private const val uniqueIdentifier = "DOGLOG"

        @Suppress("unused")
        private fun dog(forceOn: Boolean = false, message: () -> String) {
            if (BuildConfig.DEBUG && (forceOn || (debugLoggingEnabled && BuildConfig.DOG_ON))) {
                if (Log.isLoggable(tag, Log.ERROR)) {
                    val duration = Duration.between(WolApplication.APP_EPOCH, Instant.now()).toMillis() / 1000.0
                    val durationString = "[%8.3f]".format(duration)
                    Log.println(Log.ERROR, tag, durationString + uniqueIdentifier + ":" + message())
                }
            }
        }

        @Suppress("unused")
        private inline fun die(errorIfTrue: Boolean, message: () -> String) {
            if (BuildConfig.DEBUG) {
                require(errorIfTrue, message)
            }
        }
    }
}
