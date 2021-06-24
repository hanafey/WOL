package com.hanafey.android.wol

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.hanafey.android.wol.databinding.FragmentMainBinding
import com.hanafey.android.wol.magic.WolHost

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

        val hc = mvm.targets.size
        val pingStateListener = PingStateClickListener()

        // Leave visible only the defined hosts.
        uiHosts.forEachIndexed { ix, vg ->
            if (ix < hc) {
                vg.visibility = View.VISIBLE
            } else {
                vg.visibility = View.GONE
            }
        }

        mvm.targets.forEachIndexed { ix, wh ->
            if (ix < hc) {
                uiPingEnabled[ix].isChecked = wh.pingMe
                uiPingEnabled[ix].text = wh.title
                uiPingEnabled[ix].setOnClickListener(PingClickListener(wh))

                uiPingState[ix].setOnClickListener(pingStateListener)
                uiPingState[ix].isEnabled = mvm.targets[ix].pingMe

                uiWake[ix].setOnClickListener(WakeClickListener(wh))
                uiWake[ix].isEnabled = mvm.targets[ix].pingMe
            }
        }


        findNavController().addOnDestinationChangedListener(this)

        if (mvm.countPingMe() > 0 && !mvm.pingActive) {
            mvm.pingTargets()
        }

        observePingLiveData()
        observeWakeLiveData()
    }

    private fun initializeView(v: View) {
    }

    private fun observePingLiveData() {

        mvm.targetPingChangedLiveData.observe(viewLifecycleOwner) { ix ->
            if (ix < 0 || ix >= mvm.targets.size) {
                return@observe // ======================================== >>>
            }

            val target = mvm.targets[ix]
            val psb = uiPingState[ix]

            dlog(LTAG) { "observe Ping of [$ix] ${target.title} state=${target.pingState} ex=${target.pingException}" }

            when (target.pingState) {
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
        }
    }

    private fun observeWakeLiveData() {
        mvm.targetWakeChangedLiveData.observe(viewLifecycleOwner) { ix ->
            if (ix < 0 || ix >= mvm.targets.size) {
                return@observe // ======================================== >>>
            }

            val target = mvm.targets[ix]
            val ex = target.wakeupException
            dlog(LTAG) { "observe wake of [$ix] ${target.title} state=${target.wakeupCount} ex=${target.wakeupException}" }

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
        dlog(LTAG) { "Navigation to $destination" }
        when (destination.id) {
            R.id.HostStatusDialog -> {
            }

            else -> {
            }
        }
    }

    inner class PingStateClickListener : View.OnClickListener {
        override fun onClick(v: View?) {
            val psi = uiPingState.indexOfFirst { button -> button === v }
            if (psi >= 0) {
                mvm.pingFocussedTarget = mvm.targets[psi]
                v?.backgroundTintList = pingFrozenTint
                findNavController().navigate(R.id.HostStatusDialog)
            }
        }
    }

    inner class PingClickListener(private val target: WolHost) : View.OnClickListener {
        override fun onClick(v: View?) {
            require(v is SwitchMaterial) { "This listener requires SwitchMaterial as the owner." }

            val wasPinging = mvm.countPingMe()
            target.pingMe = v.isChecked
            val nowPinging = mvm.countPingMe()

            uiPingState[target.pKey].isEnabled = v.isChecked

            if (!v.isChecked) {
                // Ping is disabled for this host
                target.resetState()
                uiPingState[target.pKey].backgroundTintList = pingOffTint
                uiPingState[target.pKey].icon = ContextCompat.getDrawable(
                    requireActivity(),
                    R.drawable.ic_baseline_device_unknown_24
                )
                uiPingState[target.pKey].isEnabled = false
                uiWake[target.pKey].isEnabled = false
            }

            when {
                wasPinging > 0 && nowPinging == 0 -> {
                    // Pinging stops
                    mvm.signalPingTargetChanged(target)
                    mvm.killPingTargets()
                    Snackbar.make(ui.root, "Pinging Stopped", Snackbar.LENGTH_LONG).show()
                }

                wasPinging == 0 && nowPinging > 0 -> {
                    // Pinging starts
                    dlog(LTAG) { "pingClickListener: START PING: $wasPinging -- $nowPinging ${target.title} state=${target.pingState} ex=${target.pingException}" }
                    mvm.pingTargets()
                    Snackbar.make(ui.root, "Pinging...", Snackbar.LENGTH_SHORT).show()
                }

                else -> {
                    mvm.signalPingTargetChanged(target)
                }
            }
        }
    }

    inner class WakeClickListener(private val target: WolHost) : View.OnClickListener {
        override fun onClick(v: View?) {
            val message = when {
                !target.pingMe -> {
                    "${target.title} is not being pinged, so will not try to wake it up..."
                }

                target.pingState == WolHost.PingStates.ALIVE -> {
                    if (mvm.settingsData.wakePingableHosts) {
                        mvm.wakeTarget(target)
                        "${target.title} alive, but will be woken anyway!\n"
                        "${target.title} WOL to ${target.macAddress} via ${target.broadcastIp}"
                    } else {
                        "${target.title} alive, so does not need to be woken up!"
                    }
                }

                else -> {
                    mvm.wakeTarget(target)
                    "${target.title} WOL to ${target.macAddress} via ${target.broadcastIp}"
                }
            }

            Snackbar.make(ui.root, message, Snackbar.LENGTH_LONG).show()
            mvm.wolFocussedTarget = target
            findNavController().navigate(R.id.WolStatusDialog)
        }
    }
}