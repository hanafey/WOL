package com.hanafey.android.wol

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WolApplication : Application() {
    private val ltag = this.javaClass.simpleName

    private var _mainScope: CoroutineScope? = null
    private var _mvm: MainViewModel? = null

    val mainScope: CoroutineScope
        get() = _mainScope ?: throw IllegalStateException("Cannot access 'mainScope' before application 'onCreate'.")

    val mvm: MainViewModel
        get() = _mvm ?: throw IllegalStateException("Cannot access 'mvm' before application 'onCreate'.")

    init {
        singleton = this
    }

    override fun onCreate() {
        dlog(ltag) { "I am onCreate." }
        super.onCreate()
        _mainScope = MainScope()
        _mvm = MainViewModel(this)
        mainScope.launch {
            repeat(10) {
                dlog(ltag) { "I was launched." }
                delay(2000L)
            }
            dlog(ltag) { "Funky shit is over!!" }
        }
    }

    companion object {
        private var singleton: WolApplication? = null
        val instance: WolApplication
            get() {
                return singleton ?: throw IllegalAccessException("WolApplication has not done 'onCreate', and you are asking for class singleton instance!")
            }
    }

}