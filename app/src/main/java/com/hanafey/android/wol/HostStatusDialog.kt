package com.hanafey.android.wol

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hanafey.android.wol.databinding.DialogHostStatusBinding

class HostStatusDialog : BottomSheetDialogFragment() {

    private var _binding: DialogHostStatusBinding? = null
    private val ui: DialogHostStatusBinding
        get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogHostStatusBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}