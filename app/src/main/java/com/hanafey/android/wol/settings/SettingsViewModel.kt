package com.hanafey.android.wol.settings

import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {
    private var forceInstantiation = false

    /**
     * Set true if anything related to pinging changes. When we navigate back to the main fragment we make sure
     * that the UI and the pinging state reflect the settings changes. Note that it is better to update unnecessarily
     * instead of making the state not reflect settings changes.
     */
    var hostDataChanged = false

    /**
     * Set true if DAT buffer settings changed
     */
    var datBufferChanged = false

    fun forceInstantiation() {
        forceInstantiation = true
    }
}