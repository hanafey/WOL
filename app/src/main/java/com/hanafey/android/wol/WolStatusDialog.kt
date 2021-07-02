package com.hanafey.android.wol

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.hanafey.android.wol.databinding.DialogWolStatusBinding
import com.hanafey.android.wol.magic.WolHost
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WolStatusDialog : BottomSheetDialogFragment() {

    private val mvm: MainViewModel by activityViewModels()

    private var _binding: DialogWolStatusBinding? = null
    private val ui: DialogWolStatusBinding
        get() = _binding!!

    private lateinit var wh: WolHost
    private lateinit var pingResponsiveTint: ColorStateList
    private lateinit var pingUnResponsiveTint: ColorStateList
    private lateinit var pingOtherTint: ColorStateList

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogWolStatusBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pingUnResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_un_responsive_dialog)!!
        pingResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_responsive_dialog)!!
        pingOtherTint = ContextCompat.getColorStateList(requireContext(), R.color.mtrl_btn_bg_color_selector)!!

        val wft = mvm.wolFocussedTarget
        if (wft != null) {
            wh = wft
            updateUi(wh)
            ui.wolButton.setOnClickListener {
                mvm.wakeTarget(wh)
                Snackbar.make(view, "WOL magic packet set to ${wh.title}", Snackbar.LENGTH_SHORT).show()
            }

            observePingLiveData()
        } else {
            // Presumable we got here by rotation
            findNavController().navigateUp()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mvm.wolFocussedTarget = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUi(wh: WolHost) {
        ui.wolStatusTitle.text = "Name: ${wh.title}"
        ui.wolStatusAddress.text = "Address: ${wh.pingName} Ping Count: ${wh.pingedCountAlive}/${wh.pingedCountDead}"
        ui.pingStatus.text = when (wh.pingState) {
            WolHost.PingStates.NOT_PINGING -> {
                ui.root.backgroundTintList = pingOtherTint
                "Not pinging, state unknown"
            }
            WolHost.PingStates.INDETERMINATE -> {
                ui.root.backgroundTintList = pingOtherTint
                "Not pinging, state unknown"
            }
            WolHost.PingStates.ALIVE -> {
                ui.root.backgroundTintList = pingResponsiveTint
                "Alive, responded to last ping"
            }
            WolHost.PingStates.DEAD -> {
                ui.root.backgroundTintList = pingUnResponsiveTint
                "Sleeping, no response to last ping"
            }
            WolHost.PingStates.EXCEPTION -> {
                ui.root.backgroundTintList = pingOtherTint
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

        ui.wolSentAt.text = wolStatusMessage(wh)
        ui.wolWaiting.text = wolWaitingMessage(wh)
        ui.wolWakeAt.text = wolAwokeMessage(wh)
    }

    private fun wolStatusMessage(wh: WolHost): String {
        val (lastSent, _) = wh.lastWolSentAt.state()
        return if (lastSent == Instant.EPOCH) {
            "WOL not sent..."
        } else {
            val wolWakeLatencyAve = wh.wolToWakeAverage() / 1000.0
            val wolWakeLatencyMedian = wh.wolToWakeMedian() / 1000.0
            val n = wh.wolToWakeHistory.size
            val statsMessage = when (n) {
                0 -> {
                    "\nNo history to inform WOL to wake latency."
                }
                1 -> {
                    String.format("\nA single previous WOL to wake took %1.1f sec", wolWakeLatencyAve)
                }
                else -> {
                    String.format(
                        "\nWOL to Wake latency (%d samples)\n %1.1f median, %1.1f ave [sec]",
                        n,
                        wolWakeLatencyMedian,
                        wolWakeLatencyAve
                    )
                }
            }
            DateTimeFormatter.ofPattern("'WOL at - 'hh:mm:ss a").format(
                LocalDateTime.ofInstant(lastSent, ZoneId.systemDefault())
            ) + statsMessage
        }
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
        mvm.targetPingChangedLiveData.observe(viewLifecycleOwner) { ix ->
            if (wh.pKey == ix) {
                updateUi(wh)
            }
        }
    }
}