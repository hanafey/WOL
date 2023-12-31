package com.hanafey.android.wol

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import com.hanafey.android.ax.Dog
import com.hanafey.android.wol.databinding.FragmentMainBinding
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class MainFragment : Fragment(), NavController.OnDestinationChangedListener, LifecycleEventObserver {
    private val ltag = "MainFragment"
    private val lon = BuildConfig.LON_MainFragment

    private val mvm: MainViewModel = WolApplication.instance.mvm

    private var _binding: FragmentMainBinding? = null
    private val ui get() = _binding!!

    // The UI defines a fixed upper limit on the number of hosts, and we put these components in a list so
    // we can iterate over them. MainViewModel.targets determine how many are active.
    private lateinit var uiHosts: List<ViewGroup>
    private lateinit var uiPingEnabled: List<SwitchMaterial>
    private lateinit var uiPingCounts: List<MaterialTextView>
    private lateinit var uiPingState: List<MaterialButton>
    private lateinit var uiWake: List<MaterialButton>

    private lateinit var pingOffTint: ColorStateList
    private lateinit var pingFrozenTint: ColorStateList
    private lateinit var pingResponsiveTint: ColorStateList
    private lateinit var pingUnResponsiveTint: ColorStateList
    private lateinit var pingExceptionTint: ColorStateList

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
                val versionMi = menu.findItem(R.id.mi_version)
                versionMi.title = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        // Up button clicked
                        false
                    }

                    R.id.mi_web_site -> {
                        openWebPageIntent()
                        true
                    }

                    R.id.mi_settings -> {
                        findNavController().navigate(R.id.ng_Settings)
                        true
                    }

                    R.id.mi_test_notification -> {
                        mvm.viewModelScope.launch {
                            val h1 = mvm.targets[0]
                            val h2 = mvm.targets[1]

                            delay(mSecFromSeconds(2))
                            mvm.hostStateNotification.makeAsleepNotification(h1, "${h1.title} Asleep", "Test of host went to sleep.")
                            delay(mSecFromSeconds(2))
                            mvm.hostStateNotification.makeAwokeNotification(h2, "${h2.title}  Awoke", "Test of host woke up.")
                        }
                        true
                    }

                    R.id.mi_test_audio -> {
                        val target = mvm.targets[0]
                        mvm.audioTrackController.playTrackIfNeeded(mvm.getApplication(), target)
                        findNavController().navigate(
                            R.id.action_MainFragment_to_HostAwokeFragment,
                            Bundle().apply {
                                putInt("wh_pkey", target.pKey)
                                putString("title", target.title)
                            }
                        )
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Put the static names into lists
        uiHosts = listOf(
            ui.hostHo01,
            ui.hostHo02,
            ui.hostHo03,
            ui.hostHo04,
            ui.hostHo05,
        )

        uiPingEnabled = listOf(
            ui.pingEnabledHo01,
            ui.pingEnabledHo02,
            ui.pingEnabledHo03,
            ui.pingEnabledHo04,
            ui.pingEnabledHo05,
        )

        uiPingCounts = listOf(
            ui.pingCountsHo01,
            ui.pingCountsHo02,
            ui.pingCountsHo03,
            ui.pingCountsHo04,
            ui.pingCountsHo05,
        )

        uiPingState = listOf(
            ui.pingStateHo01,
            ui.pingStateHo02,
            ui.pingStateHo03,
            ui.pingStateHo04,
            ui.pingStateHo05,
        )

        uiWake = listOf(
            ui.wakeHo01,
            ui.wakeHo02,
            ui.wakeHo03,
            ui.wakeHo04,
            ui.wakeHo05,
        )


        // if (savedInstanceState == null) initializeView(view)

        pingOffTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_off)!!
        pingFrozenTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_frozen)!!
        pingResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_responsive)!!
        pingUnResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_un_responsive)!!
        pingExceptionTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_exception)!!

        mvm.targets.forEach { wh ->
            val ix = wh.pKey
            uiHosts[ix].visibility = if (wh.enabled) View.VISIBLE else View.GONE

            uiPingEnabled[ix].isChecked = wh.pingMe

            uiPingEnabled[ix].text = wh.title
            uiPingEnabled[ix].setOnCheckedChangeListener(PingCheckedChangeListener(wh))

            uiPingState[ix].setOnClickListener(PingStateClickListener(wh, false))
            uiPingState[ix].isEnabled = wh.pingMe

            uiWake[ix].isEnabled = wh.pingMe
            uiWake[ix].setOnClickListener(PingStateClickListener(wh, true))

            uiWake[ix].setOnLongClickListener {
                mvm.viewModelScope.launch {
                    if (!mvm.wakeTarget(wh)) {
                        Snackbar.make(
                            ui.root, "This is host is ping responsive. No WOL sent!", Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                true
            }
        }

        mvm.networkStateLiveData.observe(viewLifecycleOwner) { ns ->
            val alpha = if ((ns.isAvailable && ns.isWifi) || mvm.settingsData.pingIgnoreWiFiState) {
                1.0f
            } else {
                0.4f
            }

            mvm.targets.forEach { wh ->
                val ix = wh.pKey
                if (wh.enabled) {
                    uiHosts[ix].alpha = alpha
                }
            }
        }

        observePingLiveData()
        observeWolDetected()

        // The intent that is set by start by notification has bundle data that
        // determines which host status to show.
        val intentSayShowHostIx = requireActivity().intent.extras?.let {
            val hostIx = it.getInt("HOST_IX", -1)
            if (hostIx > -1 && hostIx < uiHosts.size) {
                hostIx
            } else {
                -1
            }
        } ?: -2

        if (intentSayShowHostIx >= 0) {
            // Clear the intent set by the notification or else we navigate the next time the activity starts, e.g
            // because the up button was pushed.
            requireActivity().intent = Intent()
            uiWake[intentSayShowHostIx].callOnClick()
        } else {
            if (mvm.firstVisit && mvm.settingsData.versionAcknowledged < BuildConfig.VERSION_CODE) {
                findNavController().navigate(R.id.FirstTimeInformationFragment)
                mvm.firstVisit = false
            } else {
                // The warning that this can be replaced by mvm.targets.isEmpty() is bullshit!
                if (mvm.targets.count { it.enabled } == 0) {
                    Snackbar.make(ui.root, getString(R.string.info_no_hosts_enabled), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {

        when (event) {
            Lifecycle.Event.ON_CREATE -> Unit

            Lifecycle.Event.ON_RESUME -> Unit

            Lifecycle.Event.ON_START -> {
                Dog.bark(ltag, lon, "lifecycle") { "addOnDestinationChangedListener $event" }
                findNavController().addOnDestinationChangedListener(this)
            }

            Lifecycle.Event.ON_PAUSE -> Unit

            Lifecycle.Event.ON_STOP -> {
                Dog.bark(ltag, lon, "lifecycle") { "removeOnDestinationChangedListener $event" }
                findNavController().removeOnDestinationChangedListener(this)
            }

            Lifecycle.Event.ON_DESTROY -> Unit

            Lifecycle.Event.ON_ANY -> {
                // If when is complete this is never reached.
            }
        }
    }

    private fun openWebPageIntent() {
        val url = "https://wol-bliss.hanafey.com"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            val report = getString(R.string.error_doc_site, e.localizedMessage)
            val bundle = Bundle().apply {
                putString("error_report", report)
            }
            findNavController().navigate(R.id.ErrorReportFragment, bundle)
        }
    }

    private fun observePingLiveData() {
        mvm.targetPingChangedLiveData.observe(viewLifecycleOwner) { ix ->
            val (target, wakeAnimal) = when {
                ix == -1 -> {
                    return@observe
                }
                mvm.targets.size > ix -> {
                    mvm.targets[ix] to (uiWake[ix].icon as AnimatedVectorDrawable)
                }
                else -> {
                    throw IllegalArgumentException("observePingLiveData: $ix is invalid target index")
                }
            }

            mvm.viewModelScope.launch {
                target.mutex.withLock {
                    if (target.enabled) {
                        if (uiHosts[ix].visibility != View.VISIBLE) {
                            // Changed to enabled
                            uiHosts[ix].visibility = View.VISIBLE
                            uiPingEnabled[ix].isChecked = target.pingMe
                        }
                    } else {
                        if (uiHosts[ix].visibility != View.GONE) {
                            // Changed to not enabled
                            uiHosts[ix].visibility = View.GONE
                            if (target.pingMe) {
                                // Not enabled also means not pingMe
                                target.pingMe = false
                                mvm.settingsData.savePingMe(target)
                                target.pingState = WolHost.PingStates.INDETERMINATE
                                uiPingEnabled[ix].isChecked = target.pingMe
                            }
                        }
                    }

                    val psb = uiPingState[ix]
                    val pingCounts = uiPingCounts[ix]

                    when (target.pingState) {
                        WolHost.PingStates.NOT_PINGING -> {
                            target.resetState()
                            psb.backgroundTintList = pingOffTint
                            psb.icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_baseline_device_unknown_24)
                        }

                        WolHost.PingStates.INDETERMINATE -> {
                            psb.backgroundTintList = pingOffTint
                            psb.icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_baseline_device_unknown_24)
                        }

                        WolHost.PingStates.ALIVE -> {
                            if (mvm.pingFocussedTarget == target) {
                                psb.backgroundTintList = pingFrozenTint
                            } else {
                                psb.backgroundTintList = pingResponsiveTint
                            }
                            psb.icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_baseline_thumb_up_24)
                        }

                        WolHost.PingStates.DEAD -> {
                            if (mvm.pingFocussedTarget == target) {
                                psb.backgroundTintList = pingFrozenTint
                            } else {
                                psb.backgroundTintList = pingUnResponsiveTint
                            }
                            psb.icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_baseline_thumb_down_24)
                        }

                        WolHost.PingStates.EXCEPTION -> {
                            psb.backgroundTintList = pingExceptionTint
                            psb.icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_baseline_error_24)
                        }
                    }
                    val wakeMessage = if (target.pingMe) {
                        "%d/%d".format(target.pingedCountAlive, target.pingedCountDead)
                    } else {
                        ""
                    }
                    pingCounts.text = wakeMessage
                }
            }

            val running = wakeAnimal.isRunning
            if (target.isWaitingToAwake()) {
                if (!running) {
                    // Animation runs while we are waiting.
                    wakeAnimal.start()
                }
            } else {
                if (running) {
                    wakeAnimal.stop()
                    wakeAnimal.reset()
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {}

    /**
     * Navigate to [HostAwokeFragment] one time when getting the [PingDeadToAwakeTransition.WHS.AWOKE] event
     */
    private fun observeWolDetected() {
        val lun = "observeWolDetected()"
        // Only one navigation can be done, and when the up button is clicked in HostAwoke the main fragment
        // is created again, and then the next one is done, etc
        var firstNavigationDone = false
        mvm.targets.forEach { wh ->
            wh.wolDetectedLive.observe(viewLifecycleOwner) { ed ->

                if (!firstNavigationDone) {
                    if (ed.onceValueForNavigation() != null) {
                        Dog.bark(ltag, lon, lun) { "navigate to awoke for ${wh.title}" }
                        firstNavigationDone = true
                        findNavController().navigate(
                            R.id.action_MainFragment_to_HostAwokeFragment,
                            Bundle().apply {
                                putInt("wh_pkey", wh.pKey)
                                putString("title", wh.title)
                            }
                        )
                    }
                }
            }
        }
    }

    inner class PingStateClickListener(private val wh: WolHost, private val showWol: Boolean) : View.OnClickListener {
        override fun onClick(v: View?) {
            mvm.viewModelScope.launch {
                wh.mutex.withLock {
                    mvm.wolFocussedTarget = wh
                    findNavController().navigate(
                        if (showWol)
                            R.id.action_MainFragment_to_HostStatusFragment_wol
                        else
                            R.id.action_MainFragment_to_HostStatusFragment_status
                    )
                }
            }
        }
    }

    inner class PingCheckedChangeListener(private val target: WolHost) : CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(v: CompoundButton?, isChecked: Boolean) {
            require(v is SwitchMaterial) { "This listener requires SwitchMaterial as the owner." }

            mvm.viewModelScope.launch {
                target.mutex.withLock {
                    val ix = target.pKey
                    if (target.pingMe != v.isChecked) {
                        // State is changing
                        target.pingMe = v.isChecked
                        mvm.settingsData.savePingMe(target)
                    }

                    uiPingState[ix].isEnabled = v.isChecked
                    uiWake[ix].isEnabled = v.isChecked

                    if (!target.pingMe) {
                        // Ping is disabled for this host
                        target.resetState()
                        target.deadOrAliveTransitionDetector.resetBuffer()
                        uiPingState[ix].backgroundTintList = pingOffTint
                        uiPingState[ix].icon = ContextCompat.getDrawable(
                            requireActivity(),
                            R.drawable.ic_baseline_device_unknown_24
                        )
                    }
                }

                mvm.signalPingTargetChanged(target)
            }
        }
    }
}
