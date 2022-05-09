package com.hanafey.android.wol

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import com.hanafey.android.wol.databinding.FragmentMainBinding
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class MainFragment : Fragment(), NavController.OnDestinationChangedListener {

    private val ltag = "MainFragment"

    private val mvm: MainViewModel by activityViewModels()

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
        }

        findNavController().addOnDestinationChangedListener(this)

        if (mvm.settingsData.hostDataChanged) {
            mvm.pingTargetsAgain()
        } else {
            mvm.pingTargetsIfNeeded()
        }

        observePingLiveData()

        if (mvm.firstVisit && mvm.settingsData.versionAcknowledged < BuildConfig.VERSION_CODE) {
            findNavController().navigate(R.id.FirstTimeInformationFragment)
            mvm.firstVisit = false
        } else {
            if (mvm.targets.count { it.enabled } == 0) {
                Snackbar.make(ui.root, getString(R.string.info_no_hosts_enabled), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.mi_web_site -> {
                openWebPageIntent("https://wol-bliss.hanafey.com")
                true
            }

            R.id.mi_settings -> {
                mvm.settingsData.hostDataChanged = false
                findNavController().navigate(R.id.SettingsFragment)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openWebPageIntent(url: String) {
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
                    val wakeMessage = if (target.pingMe) {
                        "%d/%d".format(target.pingedCountAlive, target.pingedCountDead)
                    } else {
                        ""
                    }
                    pingCounts.text = wakeMessage
                }
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
            else -> {
            }
        }
    }

    inner class PingStateClickListener(private val wh: WolHost, private val showWol: Boolean) : View.OnClickListener {
        override fun onClick(v: View?) {
            mvm.viewModelScope.launch {
                wh.mutex.withLock {
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