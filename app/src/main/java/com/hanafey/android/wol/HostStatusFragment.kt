package com.hanafey.android.wol

import android.content.res.ColorStateList
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hanafey.android.ax.Dog
import com.hanafey.android.wol.databinding.FragmentHostStatusBinding
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HostStatusFragment : Fragment(),
    LifecycleEventObserver,
    NavController.OnDestinationChangedListener {
    private val ltag = "HostStatusFragment"
    private val lon = BuildConfig.LON_HostStatusFragment

    private val mvm: MainViewModel = WolApplication.instance.mvm

    private var _binding: FragmentHostStatusBinding? = null
    private val ui: FragmentHostStatusBinding
        get() = _binding!!

    private lateinit var wh: WolHost
    private lateinit var pingResponsiveTint: ColorStateList
    private lateinit var pingUnResponsiveTint: ColorStateList
    private lateinit var pingOtherTint: ColorStateList
    private var wolLateColor = 0
    private var showWol: Boolean = false
    private val preamble: String by lazy { getString(R.string.error_wake_failed_preamble) }
    private val postamble: String by lazy { getString(R.string.error_wake_failed_postamble) }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHostStatusBinding.inflate(inflater, container, false)

        val args = arguments
        if (args != null) {
            showWol = args.getBoolean("show_wol", false)
        }

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_host_status, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        // Up button clicked
                        false
                    }

                    R.id.mi_reset_history -> {
                        val ad: AlertDialog = MaterialAlertDialogBuilder(requireContext()).apply {
                            setTitle("Reset WOL Time To Wake History for ${wh.title}")
                            setMessage("These are details of what you can do.")
                            setPositiveButton("Do it") { _, _ ->
                                mvm.settingsData.resetTimeToWakeHistory(wh)
                                updateUi(wh)

                                Snackbar
                                    .make(ui.root, "Wake history for ${wh.title} reinitialized", Snackbar.LENGTH_LONG)
                                    .show()
                            }
                            setNegativeButton("CANCEL") { _, _ ->
                                Snackbar
                                    .make(ui.root, "Cancelled: Wake history for ${wh.title} unchanged.", Snackbar.LENGTH_SHORT)
                                    .show()
                            }
                        }.create()
                        ad.show()
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

        wolLateColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorError)
        pingUnResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_un_responsive_dialog)!!
        pingResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_responsive_dialog)!!
        pingOtherTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_other_dialog)!!

        val wft = mvm.wolFocussedTarget
        if (wft != null) {
            wh = wft
            wh.updateWolStats()
            updateUi(wh)

            ui.mainLayout.setOnClickListener {
                // Just for debugging
            }

            ui.wolButton.setOnLongClickListener {
                mvm.viewModelScope.launch {
                    if (!mvm.wakeTarget(wh)) {
                        Snackbar.make(
                            ui.root, "This is host is ping responsive. No WOL sent!", Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                true
            }

            ui.wolButton.setOnClickListener {
                (ui.wolButton.icon as AnimatedVectorDrawable).let { anim ->
                    if (anim.isRunning) {
                        anim.stop()
                        anim.reset()
                    }
                    mvm.viewModelScope.launch {
                        wh.mutex.withLock {
                            wh.cancelWaitingToAwake()
                        }
                    }
                }
                Snackbar.make(view, "LONG press if you mean to wake host up!", Snackbar.LENGTH_LONG).show()
            }

            viewLifecycleOwner.lifecycleScope.launch {
                while (true) {
                    updateProgress()
                    delay(100L)
                }
            }

            observePingLiveData()
            observeWolDetected()
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Dog.bark(ltag, lon, "lifecycle") { event.name }
        when (event) {
            Lifecycle.Event.ON_START -> {
                findNavController().addOnDestinationChangedListener(this)
                mvm.cancelKillPingTargetsAfterWaiting(WolApplication.instance.mainScope, false)
            }
            Lifecycle.Event.ON_STOP -> {
                findNavController().removeOnDestinationChangedListener(this)
            }
            else -> {}
        }
    }


    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUi(wh: WolHost) {
        ui.wolStatusTitle.text = getString(R.string.host_title, wh.title)
        ui.wolStatusAddress.text = getString(R.string.host_address, wh.pingName, wh.pingedCountAlive, wh.pingedCountDead, wh.broadcastIp)

        ui.pingStatus.text = when (wh.pingState) {
            WolHost.PingStates.NOT_PINGING -> {
                ui.wolStatusTitle.backgroundTintList = pingOtherTint
                "Not pinging, state unknown"
            }
            WolHost.PingStates.INDETERMINATE -> {
                ui.wolStatusTitle.backgroundTintList = pingOtherTint
                "Not pinging, state unknown"
            }
            WolHost.PingStates.ALIVE -> {
                ui.wolStatusTitle.backgroundTintList = pingResponsiveTint
                "Alive, responded to last ping"
            }
            WolHost.PingStates.DEAD -> {
                ui.wolStatusTitle.backgroundTintList = pingUnResponsiveTint
                "Sleeping, no response to last ping"
            }
            WolHost.PingStates.EXCEPTION -> {
                ui.wolStatusTitle.backgroundTintList = pingOtherTint
                "Error attempting to ping"
            }
        }

        if (wh.pingState == WolHost.PingStates.EXCEPTION) {
            ui.pingException.text = wh.pingException?.localizedMessage ?: "No exception message."
            ui.pingException.visibility = View.VISIBLE
        } else {
            ui.pingException.text = ""
            ui.pingException.visibility = View.INVISIBLE
        }

        ui.wolButtonGroup.visibility = if (showWol) View.VISIBLE else View.GONE

        ui.wolMacAddress.text = wolMacMessage(wh)
        ui.wolSentAt.text = wolStatusMessage()
        ui.wolWaiting.text = wolWaitingMessage(wh)
        ui.wolWakeAt.text = wolAwokeMessage(wh)
    }

    private fun updateProgress() {
        if (wh.isWaitingToAwake()) {
            if (wh.wolStats.isDefined) {
                // Track progress
                val progress = wh.wolStats.progress(Instant.now())
                ui.wolProgress.progress = progress
                if (progress > 150) {
                    ui.wolProgress.setIndicatorColor(wolLateColor)
                } else {
                    ui.wolProgress.setIndicatorColor()
                }
            }
        }
    }

    private fun wolMacMessage(wh: WolHost): String {
        return "MAC: ${wh.macAddress}"
    }

    private fun wolStatusMessage(): String {
        return wh.wolStats.latencyHistoryMessage
    }

    /**
     * If [WolHost.lastWolSentAt] is the EPOCH, return blank string.
     *
     * If above is false then if [WolHost.lastWolWakeAt] is not the EPOCH we are actively waiting for a host to
     * wake up.
     *
     * If both wake times are not the EPOCH it records the last wake up operation, with times from WOL sent to
     * dead to alive transition.
     */
    private fun wolWaitingMessage(wh: WolHost): String {
        val (lastSent, _) = wh.lastWolSentAt.state()
        val (lastWake, _) = wh.lastWolWakeAt.state()
        return when {
            lastSent == Instant.EPOCH -> {
                ""
            }

            lastWake != Instant.EPOCH -> {
                String.format(
                    "Host Awoke %1.1f secs ago (%1.1f secs to wake up).",
                    Duration.between(lastWake, Instant.now()).toMillis() / 1000.0,
                    Duration.between(lastSent, lastWake).toMillis() / 1000.0
                )
            }

            else -> {
                String.format(
                    "It has been %1.1f sec since WOL...",
                    Duration.between(lastSent, Instant.now()).toMillis() / 1000.0
                )
            }
        }
    }

    private fun wolAwokeMessage(wh: WolHost): String {
        val (lastSent, _) = wh.lastWolSentAt.state()
        val (lastAwake, _) = wh.lastWolWakeAt.state()
        return when {
            lastSent == Instant.EPOCH -> {
                "No wake attempt yet..."
            }
            lastAwake == Instant.EPOCH -> {
                "Not awake yet..."
            }
            else -> {
                DateTimeFormatter.ofPattern("'Awake at - 'hh:mm:ss a").format(
                    LocalDateTime.ofInstant(lastAwake, ZoneId.systemDefault())
                )
            }
        }
    }

    private fun observePingLiveData() {
        wh.hostChangedLive.observe(viewLifecycleOwner) { target ->

            if (wh.pKey == target.pKey) {
                // Reflect the current host status in the UI, because this is a ping result from our focussed host.
                updateUi(wh)

                val megaPhoneAnimatedIcon = ui.wolButton.icon as AnimatedVectorDrawable
                val running = megaPhoneAnimatedIcon.isRunning
                if (wh.isWaitingToAwake()) {
                    if (!running) {
                        // Animation runs while we are waiting.
                        megaPhoneAnimatedIcon.start()
                    }
                    ui.wolProgress.visibility = View.VISIBLE
                } else {
                    if (running) {
                        megaPhoneAnimatedIcon.stop()
                        megaPhoneAnimatedIcon.reset()
                    }
                    ui.wolProgress.visibility = View.GONE
                }
            }

            // --------------------------------------------------------------------------------
            // Navigate to error fragment if wake up exception occurred.
            // --------------------------------------------------------------------------------
            val ex = wh.wakeupException.onceValue()
            if (ex != null) {
                val report = getString(R.string.error_wake_failed_meat_general, ex.localizedMessage)

                Bundle().let { bundle ->
                    bundle.putString("error_report", wakeMessageComposer(report))
                    findNavController().navigate(R.id.ErrorReportFragment, bundle)
                }
            }
            // --------------------------------------------------------------------------------
        }
    }

    /**
     * Navigate to [HostAwokeFragment] one time when getting the [PingDeadToAwakeTransition.WHS.AWOKE] event
     */
    private fun observeWolDetected() {
        val lun = "observeWolDetected()"

        wh.wolDetectedLive.observe(viewLifecycleOwner) { ed ->
            if (ed.onceValueForNavigation() != null) {
                Dog.bark(ltag, lon, lun) { "navigate to awoke for ${wh.title}" }
                findNavController().navigate(
                    R.id.action_HostStatusFragment_to_HostAwokeFragment,
                    Bundle().apply {
                        putInt("wh_pkey", wh.pKey)
                        putString("title", wh.title)
                    }
                )
            }
        }
    }

    private fun wakeMessageComposer(meat: String): String {
        val sb = StringBuilder(meat.length + preamble.length + postamble.length + 64)
        sb.append(preamble)
        sb.append(meat)
        sb.append(postamble)
        return sb.toString()
    }
}