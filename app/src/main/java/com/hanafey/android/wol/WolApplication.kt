package com.hanafey.android.wol

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.time.Duration
import java.time.Instant

class WolApplication : Application() {

    private var _mainScope: CoroutineScope? = null
    private var _mvm: MainViewModel? = null

    /**
     * Valid only after [onCreate] is called.
     */
    val mainScope: CoroutineScope
        get() = _mainScope ?: throw IllegalStateException("Cannot access 'mainScope' before application 'onCreate'.")

    val mvm: MainViewModel
        get() = _mvm ?: throw IllegalStateException("Cannot access 'mvm' before application 'onCreate'.")

    init {
        dog { "init" }
        singleton = this
    }

    override fun onCreate() {
        dog { "onCreate" }
        super.onCreate()

        _mainScope = MainScope()
        _mvm = MainViewModel(this)

        initializeFromSharedPrefs()
        initializeNotifications()
        mvm.observeAliveDeadTransitions()
    }

    private fun initializeFromSharedPrefs() {
        mvm.settingsData.initializeModel(mvm)
    }

    private fun initializeNotifications() {
        mvm.hostStateNotification.createNotificationChannels()
    }

    companion object {
        private const val tag = "WolApplication"
        private const val debugLoggingEnabled = true
        private const val uniqueIdentifier = "DOGLOG"

        private fun dog(message: () -> String) {
            if (debugLoggingEnabled) {
                if (BuildConfig.DOG_ON && BuildConfig.DEBUG) {
                    if (Log.isLoggable(tag, Log.ERROR)) {
                        val duration = Duration.between(APP_EPOCH, Instant.now()).toMillis() / 1000.0
                        val durationString = "[%8.3f]".format(duration)
                        Log.println(Log.ERROR, tag, durationString + uniqueIdentifier + ":" + message())
                    }
                }
            }
        }

        private var singleton: WolApplication? = null

        /**
         * The instant the application was instantiated,
         */
        var APP_EPOCH: Instant = Instant.now()
            private set

        /**
         * Singleton instance of this class that is created in the [onCreate] method, so prior to this lifecycle event
         * this method is just an [IllegalAccessException]
         */
        val instance: WolApplication
            get() {
                return singleton ?: throw IllegalAccessException("WolApplication has not done 'onCreate', and you are asking for class singleton instance!")
            }

        /**
         * Set [APP_EPOCH] to now.
         */
        fun resetAppEpoch() {
            APP_EPOCH = Instant.now()
        }
    }
}