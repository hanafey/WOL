package com.hanafey.android.wol

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.hanafey.android.ax.Dog
import com.hanafey.android.wol.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), LifecycleEventObserver {
    private val ltag = "MainActivity"
    private val lon = BuildConfig.LON_MainActivity

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val mvm: MainViewModel = WolApplication.instance.mvm

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.mainToolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_main) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        mvm.networkStateLiveData.observe(this) { ns ->
            val toolBarTheme = ContextThemeWrapper(this, R.style.ThemeOverlay_Toolbar_Special).theme
            if (ns.isAvailable && ns.isWifi) {
                binding.mainToolbar.logo = ResourcesCompat.getDrawable(resources, R.drawable.ic_wifi_on, toolBarTheme)
                // binding.mainToolbar.logo = ContextCompat.getDrawable(this, R.drawable.ic_wifi_on)
            } else {
                binding.mainToolbar.logo = ResourcesCompat.getDrawable(resources, R.drawable.ic_wifi_off, toolBarTheme)
                // binding.mainToolbar.logo = ContextCompat.getDrawable(this, R.drawable.ic_wifi_off)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        val lab = "lifecycle"
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                Dog.bark(ltag, lon, lab) { "ON_CREATE" }
            }
            Lifecycle.Event.ON_START -> {
                Dog.bark(ltag, lon, lab) { "ON_START: cancel kill ping, and re-ping if needed" }
                mvm.cancelKillPingTargetsAfterWaiting(WolApplication.instance.mainScope, true)
            }
            Lifecycle.Event.ON_RESUME -> Unit
            Lifecycle.Event.ON_PAUSE -> Unit
            Lifecycle.Event.ON_STOP -> {
                Dog.bark(ltag, lon, lab) { "ON_STOP: kill ping later" }
                mvm.killPingTargetsAfterWaiting(WolApplication.instance.mainScope)
            }
            Lifecycle.Event.ON_DESTROY -> Unit
            Lifecycle.Event.ON_ANY -> Unit
        }
    }
}