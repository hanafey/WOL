package com.hanafey.android.wol.settings

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.hanafey.android.wol.BuildConfig
import com.hanafey.android.wol.MainViewModel
import com.hanafey.android.wol.R
import com.hanafey.android.wol.WolApplication
import java.time.Duration
import java.time.Instant

class SettingsHostListFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener,
    NavController.OnDestinationChangedListener,
    LifecycleEventObserver {

    private val mvm: MainViewModel = WolApplication.instance.mvm
    private val fvm: SettingsViewModel by navGraphViewModels(R.id.ng_Settings)

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.let { ps ->
            PreferenceCategory(context).let { pc ->
                pc.title = "Hosts"
                ps.addPreference(pc)

                for (wh in mvm.targets) {
                    pc.addPreference(
                        // Note: title added in ON_START lifecycle
                        Preference(context).apply {
                            layoutResource = R.layout.preference_link
                            key = PrefNames.HOST_SECTION.pref(wh.pKey)
                            fragment = "SettingsHostFragment-${wh.pKey}"
                        }
                    )
                }
            }
        }


        preferenceScreen = screen
        mvm.settingsData.spm.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceChange(pref: Preference, newValue: Any?): Boolean {
        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String) {}

    override fun onDestroyView() {
        super.onDestroyView()
        mvm.settingsData.spm.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        when (destination.id) {
            R.id.SettingsHostListFragment -> {
                // Host titles may have changed so update.
                dog { "Back to SettingsHostListFragment. Host data changed? ${fvm.hostDataChanged}" }
                if (fvm.hostDataChanged) {
                    for (wh in mvm.targets) {
                        findPreference<Preference>(PrefNames.HOST_SECTION.pref(wh.pKey))?.apply {
                            title = wh.title
                        }
                    }
                }
            }
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                dog { "ON_CREATE" }
            }
            Lifecycle.Event.ON_START -> {
                dog { "ON_START" }
                findNavController().addOnDestinationChangedListener(this)
                // --------------------------------------------------------------------------------
                // Create the icons for showing included and excluded hosts
                // --------------------------------------------------------------------------------
                val v = requireView()
                val a = requireActivity()
                val drawableHostIncluded = ContextCompat.getDrawable(a, R.drawable.ic_host_included)!!
                drawableHostIncluded.setTint(MaterialColors.getColor(v, R.attr.colorPingable, Color.RED))
                val drawableHostExcluded = ContextCompat.getDrawable(a, R.drawable.ic_host_excluded)!!
                drawableHostExcluded.setTint(MaterialColors.getColor(v, R.attr.colorUnPingable, Color.BLACK))

                for (wh in mvm.targets) {
                    findPreference<Preference>(PrefNames.HOST_SECTION.pref(wh.pKey))?.apply {
                        icon = if (wh.enabled) drawableHostIncluded else drawableHostExcluded
                        title = wh.title
                    }
                }
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

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        dog { "onPreferenceStartFragment: $pref" }

        require(pref.fragment != null)

        val (frag, host) = pref.fragment!!.split('-')
        val hostIx = host.toInt()

        return when (frag) {
            "SettingsHostFragment" -> {
                findNavController().navigate(R.id.nga_Settings_SettingsHost, Bundle().apply { putInt("HOST_IX", hostIx) })
                true
            }
            else -> {
                die(true) { "${pref.key} NOT EXPECTED!!" }
                false
            }
        }
    }

    companion object {
        private const val tag = "SettingsHostList"
        private const val debugLoggingEnabled = false
        private const val uniqueIdentifier = "DOGLOG"

        private fun dog(forceOn: Boolean = false, message: () -> String) {
            if (forceOn || debugLoggingEnabled) {
                if (BuildConfig.DOG_ON && BuildConfig.DEBUG) {
                    if (Log.isLoggable(tag, Log.ERROR)) {
                        val duration = Duration.between(WolApplication.APP_EPOCH, Instant.now()).toMillis() / 1000.0
                        val durationString = "[%8.3f]".format(duration)
                        Log.println(Log.ERROR, tag, durationString + uniqueIdentifier + ":" + message())
                    }
                }
            }
        }

        private inline fun die(errorIfTrue: Boolean, message: () -> String) {
            if (BuildConfig.DEBUG) {
                require(errorIfTrue, message)
            }
        }
    }
}
