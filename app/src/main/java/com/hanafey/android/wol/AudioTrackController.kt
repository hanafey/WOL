package com.hanafey.android.wol

import android.content.Context
import android.media.MediaPlayer
import com.hanafey.android.wol.magic.WolHost
import java.util.concurrent.atomic.AtomicLong

/**
 * @param wolSoundResIds A list of resource ids pointing to raw audio files to play in order to
 * announce WOL happened.
 * @param minIntervalBetweenSound Millies after the last audio track ends before another track will be played.
 * The objective is to avoid unnecessary noise if two or more hosts WOL awake at nearly the same time.
 */
class AudioTrackController(
    private val wolSoundResIds: List<Int>,
    private val minIntervalBetweenSound: Long
) {


    /**
     * Zero means no track played. Positive means track is playing, and it started at that time
     * in millies. Negative means track ended, and absolute value is the time it ended.
     */
    private val timeState = AtomicLong(0)

    /**
     * The media player that will play one track and end it.
     */
    private var mediaPlayer: MediaPlayer? = null

    /**
     * @return If Long.MAX_VALUE no track has yet played otherwise the millies since sound was or is playing.
     * If the value is big enough the sound should play, otherwise sound was recent enough that no more should
     * play.
     */
    private fun lastSoundAge(): Long {
        val s = timeState.get()

        return if (s > 0L) {
            // Running
            System.currentTimeMillis() - s
        } else if (s < 0L) {
            // Ended
            System.currentTimeMillis() + s
        } else {
            Long.MAX_VALUE
        }
    }

    /**
     * Play the audio track provided its has been quiet for [minIntervalBetweenSound] millies since the last track
     * ended.
     */
    fun playTrackIfNeeded(context: Context, wh: WolHost) {
        val track = wh.wolSoundTrackIndex
        val resourceId = if (track < 0 || track >= wolSoundResIds.size) 0 else wolSoundResIds[track]
        if (resourceId != 0) {

            if (lastSoundAge() > minIntervalBetweenSound) {
                timeState.set(System.currentTimeMillis())
                mediaPlayer?.stop()
                mediaPlayer?.release()
                val newMediaPlayer = MediaPlayer.create(context, resourceId)
                mediaPlayer = newMediaPlayer
                timeState.set(System.currentTimeMillis())
                newMediaPlayer.setOnCompletionListener {
                    mediaPlayer = null
                    timeState.set(-System.currentTimeMillis())
                    it.release()
                }
                newMediaPlayer.start()
            }
        }
    }

    /**
     * Stops and releases the [mediaPlayer] if it is not already null. Without this the track just plays till it
     * is done.
     */
    fun stopTrackIfPlaying() {
        val currentPlayer = mediaPlayer
        if (currentPlayer != null) {
            mediaPlayer = null
            currentPlayer.stop()
            currentPlayer.release()
        }
    }
}