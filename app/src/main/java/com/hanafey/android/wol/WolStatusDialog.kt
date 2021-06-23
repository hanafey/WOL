package com.hanafey.android.wol

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hanafey.android.wol.databinding.DialogWolStatusBinding
import com.hanafey.android.wol.magic.WolHost
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WolStatusDialog : BottomSheetDialogFragment() {

    private val mvm: MainViewModel by activityViewModels()

    private var _binding: DialogWolStatusBinding? = null
    private val ui: DialogWolStatusBinding
        get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogWolStatusBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val wh = mvm.wolFocussedTarget
        if (wh != null) {
            ui.wolStatusTitle.text = "Name: ${wh.title}"
            ui.wolStatusAddress.text = "Address: ${wh.pingName} Ping Count: ${wh.pingedCountAlive}/${wh.pingedCountDead}"
            ui.pingStatus.text = when (wh.pingState) {
                WolHost.PingStates.INDETERMINATE -> "Not pinging, state unknown"
                WolHost.PingStates.ALIVE -> "Alive, responded to last ping"
                WolHost.PingStates.DEAD -> "Sleeping, no response to last ping"
                WolHost.PingStates.EXCEPTION -> "Error attempting to ping"
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
        mvm.wolFocussedTarget = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun wolStatusMessage(wh: WolHost): String {
        val (lastSent, _) = wh.lastWolSentAt.state()
        return if (lastSent == Instant.EPOCH) {
            "WOL not sent..."
        } else {
            DateTimeFormatter.ofPattern("'WOL at - 'hh:mm:ss a").format(
                LocalDateTime.ofInstant(lastSent, ZoneId.systemDefault())
            )
        }
    }

    private fun wolWaitingMessage(wh: WolHost): String {
        val (lastSent, _) = wh.lastWolSentAt.state()
        return if (lastSent == Instant.EPOCH) {
            "No elapsed time to report..."
        } else {
            String.format("It has been %d since WOL...", (Instant.now().toEpochMilli() - lastSent.toEpochMilli()) / 1000)
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
            if (ix < 0 || ix >= mvm.targets.size || mvm.targets[ix] != mvm.wolFocussedTarget) {
                return@observe // ======================================== >>>
            }

            val wh = mvm.targets[ix]
            ui.pingStatus.text = when (wh.pingState) {
                WolHost.PingStates.INDETERMINATE -> "Not pinging, state unknown"
                WolHost.PingStates.ALIVE -> "Alive, responded to last ping"
                WolHost.PingStates.DEAD -> "Sleeping, no response to last ping"
                WolHost.PingStates.EXCEPTION -> "Error attempting to ping"
            }

            ui.wolSentAt.text = wolStatusMessage(wh)
            ui.wolWaiting.text = wolWaitingMessage(wh)
            ui.wolWakeAt.text = wolAwokeMessage(wh)
        }
    }

}