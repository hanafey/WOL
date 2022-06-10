package com.hanafey.android.wol.settings

import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {
    var hostDataChanged = false
    var datBufferChanged = false

    // FIX: Not needed
    private fun resetHostDataChanged() {
        hostDataChanged = false
        datBufferChanged = false
    }
}