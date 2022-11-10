package com.hanafey.android.wol

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.hanafey.android.ax.Dog
import com.hanafey.android.wol.databinding.FragmentHostAwokeBinding
import com.hanafey.android.wol.magic.WolHost
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HostAwokeFragment : Fragment(),
    LifecycleEventObserver,
    NavController.OnDestinationChangedListener {
    private val ltag = "HostAwokeFragment"
    private val lon = BuildConfig.LON_HostAwokeFragment

    private val mvm: MainViewModel = WolApplication.instance.mvm

    private var _binding: FragmentHostAwokeBinding? = null
    private val ui: FragmentHostAwokeBinding
        get() = _binding!!

    private lateinit var wh: WolHost
    private lateinit var pingResponsiveTint: ColorStateList
    private lateinit var pingUnResponsiveTint: ColorStateList
    private lateinit var pingOtherTint: ColorStateList
    private var wolLateColor = 0
    private var whPkey: Int = -1
    private val preamble: String by lazy { getString(R.string.error_wake_failed_preamble) }
    private val postamble: String by lazy { getString(R.string.error_wake_failed_postamble) }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val args = arguments
        if (args != null) {
            whPkey = args.getInt("wh_pkey", Integer.MIN_VALUE)
        }

        if (whPkey < 0 || whPkey >= mvm.targets.size) {
            throw IllegalArgumentException("Cannot create 'HostAwokeFragment' arg 'wh_pkey'=$whPkey not in [0,${mvm.targets.size})")
        }

        wh = mvm.targets[whPkey]

        _binding = FragmentHostAwokeBinding.inflate(inflater, container, false)

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // FIX: Menu?
                // menuInflater.inflate(R.menu.menu_host_awoke, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        // Up button clicked
                        false
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

        wh.updateWolStats()


        ui.wolButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Dog.bark(ltag, lon, "lifecycle") { event.name }
        when (event) {
            Lifecycle.Event.ON_START -> {}
            Lifecycle.Event.ON_STOP -> {}
            else -> {}
        }
    }


    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUi(wh: WolHost) {
        ui.wolAwokeTitle.text = getString(R.string.host_title, wh.title)
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

    private fun wakeMessageComposer(meat: String): String {
        val sb = StringBuilder(meat.length + preamble.length + postamble.length + 64)
        sb.append(preamble)
        sb.append(meat)
        sb.append(postamble)
        return sb.toString()
    }
}