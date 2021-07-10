package com.hanafey.android.wol

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import com.hanafey.android.wol.databinding.FragmentMainBinding
import com.hanafey.android.wol.magic.WolHost
import kotlin.concurrent.withLock

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class MainFragment : Fragment(), NavController.OnDestinationChangedListener {

    private val LTAG = "MainFragment"

    private val mvm: MainViewModel by activityViewModels()
    private val fvm: MainFragmentViewModel by viewModels()

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)

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

        setHasOptionsMenu(true)

        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) initializeView(view)

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
            uiPingEnabled[ix].setOnClickListener(PingClickListener(wh))

            uiPingState[ix].setOnClickListener(PingStateClickListener(wh, false))
            uiPingState[ix].isEnabled = wh.pingMe

            uiWake[ix].isEnabled = wh.pingMe
            uiWake[ix].setOnClickListener(PingStateClickListener(wh, true))
        }


        findNavController().addOnDestinationChangedListener(this)

        mvm.pingTargetsIfNeeded()

        observePingLiveData()
        observeWakeLiveData()

        if (mvm.firstVisit && mvm.settingsData.versionAcknowledged < BuildConfig.VERSION_CODE) {
            findNavController().navigate(R.id.FirstTimeInformationFragment)
            mvm.firstVisit = false
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.mi_settings -> {
                findNavController().navigate(R.id.SettingsFragment)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeView(v: View) {
    }

    private fun observePingLiveData() {

        mvm.targetPingChangedLiveData.observe(viewLifecycleOwner) { ix ->
            val target = when {
                ix == -1 -> {
                    return@observe
                }
                mvm.targets.size > ix -> {
                    mvm.targets[ix]
                }
                else -> {
                    throw IllegalArgumentException("observePingLiveData: $ix is invalid target index")
                }
            }

            target.lock.withLock {
                uiHosts[ix].visibility = if (target.enabled) View.VISIBLE else View.GONE

                val psb = uiPingState[ix]
                val pingCounts = uiPingCounts[ix]

                tlog(LTAG) { "Ping Observe: ${target.pingState}" }
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
                        if (target.wolToWakeHistoryChanged) {
                            target.wolToWakeHistoryChanged = false
                            mvm.settingsData.writeTimeToWakeHistory(target)
                        }
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
                val wakeMessage = when {
                    target.pingMe -> "%d/%d".format(target.pingedCountAlive, target.pingedCountDead)

                    else -> ""
                }
                pingCounts.text = wakeMessage
            }
        }
    }

    // TODO: Do we need a wake data listener? Ping state shows wake or sleeping...
    private fun observeWakeLiveData() {
        mvm.targetWakeChangedLiveData.observe(viewLifecycleOwner) { ix ->
            if (ix < 0 || ix >= mvm.targets.size) {
                return@observe // ======================================== >>>
            }

            val target = mvm.targets[ix]
            val ex = target.wakeupException

            if (ex != null) {
                Snackbar.make(
                    ui.root,
                    getString(
                        R.string.wake_failed_message,
                        target.title,
                        ex::class.java.simpleName,
                        ex.localizedMessage
                    ),
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                Snackbar.make(
                    ui.root,
                    getString(
                        R.string.wake_attempt_message,
                        target.title,
                        target.macAddress
                    ),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    // TODO: Not currently used. [onClick] set frozen ui, and the dialog resets the frozen state on dismiss.
    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        when (destination.id) {
            R.id.HostStatusDialog -> {
            }

            else -> {
            }
        }
    }

    inner class PingStateClickListener(private val wh: WolHost, private val showWol: Boolean) : View.OnClickListener {
        override fun onClick(v: View?) {
            wh.lock.withLock {
                mvm.wolFocussedTarget = wh
                findNavController().navigate(
                    R.id.HostStatusFragment,
                    Bundle().apply {
                        putBoolean("show_wol", showWol)
                    }
                )
            }
        }
    }

    inner class PingClickListener(private val target: WolHost) : View.OnClickListener {
        override fun onClick(v: View?) {
            require(v is SwitchMaterial) { "This listener requires SwitchMaterial as the owner." }

            target.lock.withLock {
                val ix = target.pKey
                if (target.pingMe != v.isChecked) {
                    // State is changing
                    target.pingMe = v.isChecked
                    mvm.settingsData.savePingEnabled(target)
                }

                uiPingState[ix].isEnabled = v.isChecked
                uiWake[ix].isEnabled = v.isChecked
                if (!target.pingMe) {
                    // Ping is disabled for this host
                    target.resetState()
                    uiPingState[ix].backgroundTintList = pingOffTint
                    uiPingState[ix].icon = ContextCompat.getDrawable(
                        requireActivity(),
                        R.drawable.ic_baseline_device_unknown_24
                    )
                }

                mvm.signalPingTargetChanged(target)
            }
        }
    }
}