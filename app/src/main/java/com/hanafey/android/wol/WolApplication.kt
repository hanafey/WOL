package com.hanafey.android.wol

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

class WolApplication : Application() {

    private var _mainScope: CoroutineScope? = null
    private var _mvm: MainViewModel? = null
    private lateinit var networkStateTracker: NetworkStateTracker
    private val netStateMLD = Live(
        NetworkStateTracker.NetState(System.currentTimeMillis(), isAvailable = false, isWifi = false)
    )
    private val netStateDMLD: LiveData<NetworkStateTracker.NetState> = Transformations.distinctUntilChanged(netStateMLD)

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
        networkStateTracker = NetworkStateTracker(1_000L, mainScope, netStateMLD)

        _mvm = MainViewModel(this, netStateDMLD)
        initializeFromSharedPrefs()

        initializeNotifications()
        initializeNetworkObserver()
        mvm.observeAliveDeadTransitions()
        observerWiFiState()
    }

    private fun observerWiFiState() {
        netStateDMLD.observeForever { ns ->
            mvm.targets.forEach { wh ->
                wh.deadAliveTransition.resetBuffer()
            }
        }
    }

    private fun initializeFromSharedPrefs() {
        mvm.settingsData.initializeModel(mvm)
        mvm.initializeFromSettings()
    }

    private fun initializeNotifications() {
        mvm.hostStateNotification.createNotificationChannels()
    }

    private fun initializeNetworkObserver() {

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkStateTracker.isAvailable = true
            }

            override fun onLost(network: Network) {
                networkStateTracker.isAvailable = false
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val wifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                networkStateTracker.isWifi = wifi
            }

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {}
        })
    }

    private fun lookupHosts(network: Network, hosts: List<WolHost>): List<Pair<String, InetAddress?>> {
        return hosts.map { wh ->
            val address = try {
                network.getByName(wh.pingName)
            } catch (ex: Exception) {
                null
            }
            wh.title to address
        }
    }

    companion object {
        private const val tag = "WolApplication"
        private const val debugLoggingEnabled = false
        private const val uniqueIdentifier = "DOGLOG"

        private fun dog(forceOn: Boolean = false, message: () -> String) {
            if (forceOn || debugLoggingEnabled) {
                if (BuildConfig.DOG_ON && BuildConfig.DEBUG) {
                    if (Log.isLoggable(tag, Log.ERROR)) {
                        val duration = Duration.between(APP_EPOCH, Instant.now()).toMillis() / 1000.0
                        val durationString = "[%8.3f]".format(duration)
                        Log.println(Log.ERROR, tag, durationString + uniqueIdentifier + ":" + message())
                    }
                }
            }
        }

        private inline fun die(errorIfTrue: Boolean, message: () -> String) {
            if (BuildConfig.DEBUG) {
                require(errorIfTrue, message)
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