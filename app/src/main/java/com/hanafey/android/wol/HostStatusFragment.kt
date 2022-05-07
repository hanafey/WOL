package com.hanafey.android.wol

import android.content.res.ColorStateList
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
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

class HostStatusFragment : Fragment() {

    private val ltag = "HostStatusFragment"
    private val mvm: MainViewModel by activityViewModels()

    private var _binding: FragmentHostStatusBinding? = null
    private val ui: FragmentHostStatusBinding
        get() = _binding!!

    private lateinit var wh: WolHost
    private lateinit var pingResponsiveTint: ColorStateList
    private lateinit var pingUnResponsiveTint: ColorStateList
    private lateinit var pingOtherTint: ColorStateList
    private var wolLateColor = 0
    private lateinit var wolStats: WolStats
    private var showWol: Boolean = false
    private val preamble: String by lazy { getString(R.string.error_wake_failed_preamble) }
    private val postamble: String by lazy { getString(R.string.error_wake_failed_postamble) }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHostStatusBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        if (args != null) {
            showWol = args.getBoolean("show_wol", false)
        }

        wolLateColor = MaterialColors.getColor(view, R.attr.colorError)
        pingUnResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_un_responsive_dialog)!!
        pingResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_responsive_dialog)!!
        pingOtherTint = ContextCompat.getColorStateList(requireContext(), R.color.mtrl_btn_bg_color_selector)!!

        val wft = mvm.wolFocussedTarget
        if (wft != null) {
            wh = wft
            wolStats = WolStats(wh)
            updateUi(wh)
            ui.wolButton.setOnLongClickListener {
                mvm.viewModelScope.launch {
                    mvm.wakeTarget(wh)
                }
                (ui.wolButton.icon as AnimatedVectorDrawable).let { anim ->
                    if (anim.isRunning) {
                        anim.stop()
                        anim.reset()
                    }
                    anim.start()
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
                        wolStats.cancelWaitingToAwake()
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
            /* Note that this looks similar, but this 'lifecycleScope' is too long. It continues
               after the view is destroyed.

            lifecycleScope.launch { }
            */

            observePingLiveData()
            observeWakeLiveData()
        } else {
            // Presumable we got here by rotation
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mvm.wolFocussedTarget = null
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
        if (wolStats.isWaitingToAwake() && wolStats.isDefined) {
            // Track progress
            ui.wolProgress.visibility = View.VISIBLE
            val progress = wolStats.progress(Instant.now())
            ui.wolProgress.progress = progress
            if (progress > 150) {
                ui.wolProgress.setIndicatorColor(wolLateColor)
            } else {
                ui.wolProgress.setIndicatorColor()
            }
        } else {
            ui.wolProgress.visibility = View.GONE
            (ui.wolButton.icon as AnimatedVectorDrawable).let { anim ->
                if (anim.isRunning) {
                    anim.stop()
                    anim.reset()
                }
            }
        }
    }

    private fun wolMacMessage(wh: WolHost): String {
        return "MAC: ${wh.macAddress}"
    }

    private fun wolStatusMessage(): String {
        return wolStats.latencyHistoryMessage
    }

    private fun wolWaitingMessage(wh: WolHost): String {
        val (lastSent, _) = wh.lastWolSentAt.state()
        return if (lastSent == Instant.EPOCH) {
            "No elapsed time to report..."
        } else {
            String.format(
                "It has been %1.1f sec since WOL...",
                Duration.between(lastSent, Instant.now()).toMillis() / 1000.0
            )
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
        mvm.targetPingChangedLiveData.observe(viewLifecycleOwner) { px ->
            if (wh.pKey == px) {
                updateUi(wh)
            }
        }
    }

    /**
     * Observe completion of sending WOL packets to our host, and report any exception.
     * Absent problem, do nothing -- the host will repond or not, and pinging will
     * monitor when and if it comes online.
     */
    private fun observeWakeLiveData() {
        mvm.targetWakeChangedLiveData.observe(viewLifecycleOwner) { px ->
            if (wh.pKey == px) {
                mvm.viewModelScope.launch {
                    val ex = wh.mutex.withLock {
                        val x = wh.wakeupException
                        // Only report exection once.
                        wh.wakeupException = null
                        x
                    }

                    if (ex != null) {
                        val report = getString(R.string.error_wake_failed_meat_general, ex.localizedMessage)

                        Bundle().let { bundle ->
                            bundle.putString("error_report", wakeMessageComposer(report))
                            findNavController().navigate(R.id.ErrorReportFragment, bundle)
                        }
                    }
                }
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

    private class WolStats(val wh: WolHost) {
        val wolLastSentAt: Instant
            get() = wh.lastWolSentAt.state().first
        val isDefined: Boolean
        val aveLatency: Double
        val medianLatency: Double
        val latencyHistoryMessage: String

        init {
            val n = wh.wolToWakeHistory.size
            if (n > 0) {
                aveLatency = wh.wolToWakeAverage() / 1000.0
                medianLatency = wh.wolToWakeMedian() / 1000.0
                isDefined = true
            } else {
                aveLatency = Double.NaN
                medianLatency = Double.NaN
                isDefined = false
            }

            val message = when (n) {
                0 -> {
                    "\nNo history to inform WOL to wake latency."
                }
                1 -> {
                    String.format("\nA single previous WOL to wake took %1.1f sec", aveLatency)
                }
                else -> {
                    String.format(
                        "\nWOL to Wake latency (%d samples)\n %1.1f median, %1.1f ave [sec]",
                        n,
                        medianLatency,
                        aveLatency
                    )
                }
            }
            latencyHistoryMessage = DateTimeFormatter.ofPattern("'WOL at - 'hh:mm:ss a").format(
                LocalDateTime.ofInstant(wolLastSentAt, ZoneId.systemDefault())
            ) + message
        }

        fun isAwake(): Boolean {
            val (instant, ack) = wh.lastWolSentAt.state()
            return instant != Instant.EPOCH && ack
        }

        fun isWaitingToAwake(): Boolean {
            val (instant, ack) = wh.lastWolSentAt.state()
            return instant != Instant.EPOCH && !ack
        }

        suspend fun cancelWaitingToAwake() {
            wh.mutex.withLock {
                wh.lastWolSentAt.update(Instant.EPOCH)
                wh.lastWolWakeAt.update(Instant.EPOCH)
            }
        }

        fun progress(now: Instant): Int {
            return if (isDefined) {
                val duration = Duration.between(wolLastSentAt, now).seconds.toDouble()
                ((duration / medianLatency) * 100).toInt()
            } else {
                100
            }
        }
    }
}