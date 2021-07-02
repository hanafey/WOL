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
import com.hanafey.android.wol.databinding.DialogHostStatusBinding
import com.hanafey.android.wol.magic.WolHost
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HostStatusDialog : BottomSheetDialogFragment() {

    private val mvm: MainViewModel by activityViewModels()

    private var _binding: DialogHostStatusBinding? = null
    private val ui: DialogHostStatusBinding
        get() = _binding!!

    private lateinit var wh: WolHost
    private lateinit var pingResponsiveTint: ColorStateList
    private lateinit var pingUnResponsiveTint: ColorStateList
    private lateinit var pingOtherTint: ColorStateList

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogHostStatusBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pingUnResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_un_responsive_dialog)!!
        pingResponsiveTint = ContextCompat.getColorStateList(requireContext(), R.color.ping_responsive_dialog)!!
        pingOtherTint = ContextCompat.getColorStateList(requireContext(), R.color.mtrl_btn_bg_color_selector)!!

        val wft = mvm.pingFocussedTarget
        if (wft != null) {
            wh = wft
            updateUi(wh)
            ui.upButton.setOnClickListener {
                findNavController().navigateUp()
            }

            observePingLiveData()
        } else {
            // Presumable we got here by rotation
            findNavController().navigateUp()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mvm.pingFocussedTarget = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUi(wh: WolHost) {
        ui.hostStatusTitle.text = "Name: ${wh.title}"
        ui.hostStatusAddress.text = "Address: ${wh.pingName} Ping Count: ${wh.pingedCountAlive}/${wh.pingedCountDead}"
        ui.pingStatus.text = when (wh.pingState) {
            WolHost.PingStates.NOT_PINGING -> "Not pinging, state unknown"
            WolHost.PingStates.INDETERMINATE -> "Not pinging, state unknown"
            WolHost.PingStates.ALIVE -> "Alive, responded to last ping"
            WolHost.PingStates.DEAD -> "Sleeping, no response to last ping"
            WolHost.PingStates.EXCEPTION -> "Error attempting to ping"
        }
        ui.pingResponse.text = pingResponseMessage(wh)

        if (wh.pingState == WolHost.PingStates.EXCEPTION) {
            ui.pingException.text = wh.pingException?.localizedMessage ?: "No exception message."
            ui.pingException.visibility = View.VISIBLE
        } else {
            ui.pingException.text = ""
            ui.pingException.visibility = View.INVISIBLE
        }

    }

    private fun pingResponseMessage(wh: WolHost): String {
        val (lastSent, _) = wh.lastPingSentAt.state()
        val (lastAwake, _) = wh.lastPingResponseAt.state()
        return when {
            lastSent == Instant.EPOCH -> {
                "No pings yet..."
            }
            lastAwake == Instant.EPOCH -> {
                "No response to ping at " + timeFormatter.format(
                    LocalDateTime.ofInstant(lastSent, ZoneId.systemDefault())
                )
            }
            else -> {
                val latency = Duration.between(lastSent, lastAwake).toMillis()
                "Ping/Response at " +
                        timeFormatter.format(
                            LocalDateTime.ofInstant(lastAwake, ZoneId.systemDefault())
                        ) + " ($latency mSec)"
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