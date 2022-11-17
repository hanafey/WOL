package com.hanafey.android.wol

import android.app.Application
import android.media.MediaPlayer
import androidx.annotation.RawRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Currently unused because function because function moved to the `MainViewModel`.
 *
 * This is the template for any view model that takes parameters at construction.
 * ```
 *  private val vm: HostAwokeViewModel by viewModels {
 *      val track = wh.wolSoundTrackIndex
 *      HostAwokeViewModel.Factory(
 *          requireActivity().application,
 *          if (track < 0 || track >= mvm.settingsData.wolSoundTracks.size) 0 else mvm.settingsData.wolSoundTracks[track]
 *      )
 *  }
 *
 * vm.ensureInstantiation()
 * ```
 */
@Deprecated("Replaced, just a template for other view models that need constructor args.")
class HostAwokeViewModel(context: Application, @RawRes audioResourceId: Int) : ViewModel() {
    private var mediaPlayer: MediaPlayer? = null

    init {
        if (audioResourceId != 0) {
            mediaPlayer = MediaPlayer.create(context, audioResourceId)
            mediaPlayer?.start()
        }
    }

    /**
     * Does nothing, but the reference to the view model ensures that the model is instantiated.
     * Audio starts automatically with instantiation, and ends when the view model lifecycle ends.
     */
    fun ensureInstantiation() {}

    override fun onCleared() {
        if (mediaPlayer != null) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    class Factory(private val context: Application, @RawRes private val audioResourceId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HostAwokeViewModel(context, audioResourceId) as T
        }
    }
}