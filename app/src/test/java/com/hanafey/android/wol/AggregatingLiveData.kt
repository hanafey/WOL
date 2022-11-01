package com.hanafey.android.wol

import com.hanafey.android.ax.Live
import com.hanafey.android.wol.magic.WolEventLiveData
import com.hanafey.android.wol.magic.WolHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object AggregatingLiveData {

    @JvmStatic
    fun main(args: Array<String>) {
        one()
    }

    /**
     * Does not work. No lifecycle owner etc..
     */
    fun one() {
        val ldA = Live(1)
        val ldB = Live("HELLO")
        val ldC = Live(true)
        val ldList = listOf(ldA, ldB, ldC)

        val wh = WolHost(1, "A", "PING", "a:b:c:d:e:f", "1,2,3,4")
        val mld = WolEventLiveData(wh, ldList)
        val to = System.currentTimeMillis()

        mld.observeForever {
            val td = System.currentTimeMillis() - to
            println("$td: You spoke")
        }

        runBlocking {
            withContext(Dispatchers.Default) {
                repeat(5) {
                    launch {
                        repeat(100) {
                            ldA.value = it
                            ldB.value = "xyz"
                            ldC.value = true
                            delay(10L)
                        }
                    }
                }
            }
        }
    }
}