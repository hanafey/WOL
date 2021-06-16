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
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.hanafey.android.wol.databinding.FragmentMainBinding
import com.hanafey.android.wol.magic.WolHost

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class MainFragment : Fragment() {

    private val LTAG = "MainFragment"

    private val mvm: MainViewModel by activityViewModels()
    private val fvm: MainFragmentViewModel by viewModels()

    private var _binding: FragmentMainBinding? = null
    private val ui get() = _binding!!
    private lateinit var uiHosts: List<ViewGroup>
    private lateinit var uiPingEnabled: List<SwitchMaterial>
    private lateinit var uiPingState: List<MaterialButton>
    private lateinit var uiWake: List<MaterialButton>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)

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

        uiHosts.forEachIndexed { ix, vg ->
            if (ix < mvm.targets.size) {
                vg.visibility = View.VISIBLE
            } else {
                vg.visibility = View.GONE
            }
        }

        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) initializeView(view)

        observePingLiveData()
        observeWakeLiveData()

        mvm.targets.forEachIndexed { ix, wh ->
            uiPingEnabled[ix].setOnClickListener(PingClickListener(wh))
        }
        mvm.targets.forEachIndexed { ix, wh ->
            uiWake[ix].setOnClickListener(WakeClickListener(wh))
        }
    }

    private fun initializeView(v: View) {
        mvm.targets.forEachIndexed { ix, wh ->
            uiPingEnabled[ix].isChecked = wh.pingMe
        }
    }

    private fun observePingLiveData() {
        mvm.targetPingChangedLiveData.observe(viewLifecycleOwner) { ix ->
            if (ix < 0 || ix >= mvm.targets.size) {
                return@observe // ======================================== >>>
            }

            val target = mvm.targets[ix]
            dlog(LTAG) { "observe Ping of [$ix] ${target.title} state=${target.pingState} ex=${target.pingException}" }

            when (target.pingState) {
                WolHost.PingStates.INDETERMINATE -> {
                    uiPingState[ix].apply {
                        this.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.colorPrimary))
                        icon = ContextCompat.getDrawable(
                            requireActivity(),
                            R.drawable.ic_baseline_device_unknown_24
                        )
                    }
                }

                WolHost.PingStates.ALIVE -> {
                    uiPingState[ix].apply {
                        this.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.colorPingable))
                        icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_baseline_thumb_up_24)
                    }
                }

                WolHost.PingStates.DEAD -> {
                    uiPingState[ix].apply {
                        this.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.colorUnPingable))
                        icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_baseline_thumb_down_24)
                    }
                }

                WolHost.PingStates.EXCEPTION -> {
                    uiPingState[ix].icon = ContextCompat.getDrawable(
                        requireActivity(),
                        R.drawable.ic_baseline_error_24
                    )
                }
            }

            val ex = target.pingException
            if (ex != null) {
                Snackbar.make(
                    ui.root,
                    getString(
                        R.string.ping_failed_message,
                        target.title,
                        ex::class.java.simpleName,
                        ex.localizedMessage
                    ),
                    Snackbar.LENGTH_LONG
                ).show()
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

    inner class PingClickListener(private val target: WolHost) : View.OnClickListener {
        override fun onClick(v: View?) {
            require(v is SwitchMaterial) { "This listener requires SwitchMaterial as the owner." }
            val wasPinging = mvm.countPingMe()
            target.pingMe = v.isChecked
            val nowPinging = mvm.countPingMe()

            when {
                wasPinging > 0 && nowPinging > 0 -> {
                    if (nowPinging < wasPinging) {
                        // Host went offline
                        dlog(LTAG) { "pingClickListener: RESET: $wasPinging -- $nowPinging ${target.title} state=${target.pingState} ex=${target.pingException}" }
                        target.resetPingState()
                        mvm.signalPingTargetChanged(target)
                    } else {
                        // Do nothing -- one more host is ping active it will signal
                        dlog(LTAG) { "pingClickListener: DO NOTHING: $wasPinging -- $nowPinging ${target.title} state=${target.pingState} ex=${target.pingException}" }
                    }
                }

                wasPinging > 0 && nowPinging == 0 -> {
                    // Pinging stops
                    dlog(LTAG) { "pingClickListener: KILL ALL: $wasPinging -- $nowPinging ${target.title} state=${target.pingState} ex=${target.pingException}" }
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
                    // Not expected
                    dlog(LTAG) { "pingClickListener: $wasPinging -- $nowPinging ${target.title} state=${target.pingState} ex=${target.pingException}" }
                }
            }
        }
    }

    inner class WakeClickListener(private val target: WolHost) : View.OnClickListener {
        override fun onClick(v: View?) {
            dlog(LTAG) { "WAKE ${target.title}" }
            val job = mvm.wakeTarget(target)
        }
    }
}