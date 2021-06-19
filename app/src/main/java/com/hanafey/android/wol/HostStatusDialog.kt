package com.hanafey.android.wol

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hanafey.android.wol.databinding.DialogHostStatusBinding
import com.hanafey.android.wol.magic.WolHost

class HostStatusDialog : BottomSheetDialogFragment() {

    private val mvm: MainViewModel by activityViewModels()

    private var _binding: DialogHostStatusBinding? = null
    private val ui: DialogHostStatusBinding
        get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogHostStatusBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val wh = mvm.pingFocussedTarget
        if (wh != null) {
            ui.hostStatusTitle.text = "Name: ${wh.title}"
            ui.hostStatusAddress.text = "Address: ${wh.pingName} Ping Count: ${wh.pingedCountAlive}/${wh.pingedCountDead}"
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
            ui.upButton.setOnClickListener {
                findNavController().navigateUp()
            }
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
}