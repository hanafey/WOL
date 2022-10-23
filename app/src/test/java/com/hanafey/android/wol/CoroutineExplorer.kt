@file:Suppress("DeferredResultUnused")

package com.hanafey.android.wol

import com.hanafey.android.ax.Dog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object CoroutineExplorer {
    private const val ltag = "COE"
    private const val lon = true

    @JvmStatic
    fun main(args: Array<String>) {
        one()
        two()
        three()
        four()
        five()
    }

    private fun one() {
        println("\n\n========== One expect 1400 msec to end==========")
        Dog.resetTimeOrigin()
        Dog.bark(ltag, lon) { "A" }
        runBlocking {
            withContext(Dispatchers.Default) {
                Dog.bark(ltag, lon) { "runBlocking1/withContext1 A" }
                delay(400L)
                Dog.bark(ltag, lon) { "runBlocking1/withContext1 B" }
            }
            withContext(Dispatchers.Default) {
                Dog.bark(ltag, lon) { "runBlocking1/withContext2 A" }
                delay(200L)
                Dog.bark(ltag, lon) { "runBlocking1/withContext2 B" }
            }
            Dog.bark(ltag, lon) { "runBlocking1 A" }
            delay(100L)
            Dog.bark(ltag, lon) { "runBlocking1 B" }
        }
        runBlocking {
            withContext(Dispatchers.Default) {
                Dog.bark(ltag, lon) { "runBlocking2/withContext1 A" }
                delay(400L)
                Dog.bark(ltag, lon) { "runBlocking2/withContext1 B" }
            }
            withContext(Dispatchers.Default) {
                Dog.bark(ltag, lon) { "runBlocking2/withContext2 A" }
                delay(200L)
                Dog.bark(ltag, lon) { "runBlocking2/withContext2 B" }
            }
            Dog.bark(ltag, lon) { "runBlocking2 A" }
            delay(100L)
            Dog.bark(ltag, lon) { "runBlocking2 B" }
        }
        Dog.bark(ltag, lon) { "C" }

    }


    private fun two() {
        println("\n\n========== Two expect 1400 msec to end==========")
        Dog.resetTimeOrigin()
        Dog.bark(ltag, lon) { "A" }
        runBlocking {
            coroutineScope {
                withContext(Dispatchers.IO) {
                    Dog.bark(ltag, lon) { "runBlocking/crs1/withContext1 A" }
                    delay(400L)
                    Dog.bark(ltag, lon) { "runBlocking/crs1/withContext1 B" }
                }
                withContext(Dispatchers.IO) {
                    Dog.bark(ltag, lon) { "runBlocking/crs1/withContext2 A" }
                    delay(200L)
                    Dog.bark(ltag, lon) { "runBlocking/crs1/withContext2 B" }
                }
                Dog.bark(ltag, lon) { "runBlocking/crs1 A" }
                delay(100L)
                Dog.bark(ltag, lon) { "runBlocking/crs1 B" }
            }
            coroutineScope {
                withContext(Dispatchers.IO) {
                    Dog.bark(ltag, lon) { "runBlocking/crs2/withContext1 A" }
                    delay(400L)
                    Dog.bark(ltag, lon) { "runBlocking/crs2/withContext1 B" }
                }
                withContext(Dispatchers.IO) {
                    Dog.bark(ltag, lon) { "runBlocking/crs2/withContext2 A" }
                    delay(200L)
                    Dog.bark(ltag, lon) { "runBlocking/crs2/withContext2 B" }
                }
                Dog.bark(ltag, lon) { "runBlocking/crs2 A" }
                delay(100L)
                Dog.bark(ltag, lon) { "runBlocking/crs2 B" }
            }
        }
        Dog.bark(ltag, lon) { "C" }

    }

    private fun three() {
        println("\n\n========== Three expect 1000 msec to end ==========")
        Dog.resetTimeOrigin()
        Dog.bark(ltag, lon) { "A" }
        runBlocking {
            Dog.bark(ltag, lon) { "  runBlocking A" }

            async(Dispatchers.IO) {
                Dog.bark(ltag, lon) { "    runBlocking/async1 A" }
                delay(800L)
                delay(200L)
                Dog.bark(ltag, lon) { "    runBlocking/async1 B" }
            }

            Dog.bark(ltag, lon) { "  runBlocking B" }

            async(Dispatchers.IO) {
                Dog.bark(ltag, lon) { "    runBlocking/async2 A" }
                delay(200L)
                Dog.bark(ltag, lon) { "    runBlocking/async2 B" }
            }

            Dog.bark(ltag, lon) { "  runBlocking C" }
            delay(500L)
            Dog.bark(ltag, lon) { "  runBlocking D" }

            async(Dispatchers.IO) {
                Dog.bark(ltag, lon) { "    runBlocking/async3 A" }
                delay(400L)
                Dog.bark(ltag, lon) { "    runBlocking/async3 B" }
            }

            Dog.bark(ltag, lon) { "  runBlocking E" }

            async(Dispatchers.IO) {
                Dog.bark(ltag, lon) { "    runBlocking/async4 A" }
                delay(200L)
                Dog.bark(ltag, lon) { "    runBlocking/async4 B" }
            }

            Dog.bark(ltag, lon) { "  runBlocking F" }
            delay(100L)
            Dog.bark(ltag, lon) { "  runBlocking G" }
        }
        Dog.bark(ltag, lon) { "B" }
    }

    private fun four() {
        println("\n\n========== Four expect 900 msec to end==========")
        Dog.resetTimeOrigin()
        Dog.bark(ltag, lon) { "A 0" }
        runBlocking {
            launch {
                withContext(Dispatchers.IO) {
                    Dog.bark(ltag, lon) { "runBlocking/launch1/withContext1 A 0" }
                    delay(400L)
                    Dog.bark(ltag, lon) { "runBlocking/launch1/withContext1 B 400" }
                }
                withContext(Dispatchers.IO) {
                    Dog.bark(ltag, lon) { "runBlocking/launch1/withContext2 A 400" }
                    delay(200L)
                    Dog.bark(ltag, lon) { "runBlocking/launch1/withContext2 B 600" }
                }
            }

            Dog.bark(ltag, lon) { "runBlocking A 0" }
            delay(500L)
            Dog.bark(ltag, lon) { "runBlocking B 500" }

            launch {
                withContext(Dispatchers.IO) {
                    Dog.bark(ltag, lon) { "runBlocking/launch2/withContext1 A 500" }
                    delay(400L)
                    Dog.bark(ltag, lon) { "runBlocking/launch2/withContext1 B 900" }
                }
                withContext(Dispatchers.IO) {
                    Dog.bark(ltag, lon) { "runBlocking/launch2/withContext2 A 900" }
                    delay(200L)
                    Dog.bark(ltag, lon) { "runBlocking/launch2/withContext2 B 1100" }
                }
            }

            Dog.bark(ltag, lon) { "runBlocking C 500" }
            delay(300L)
            Dog.bark(ltag, lon) { "runBlocking D 800" }
        }
        Dog.bark(ltag, lon) { "C 1100" }

    }

    private fun five() {
        println("\n\n========== Five expect 900 msec to end==========")
        Dog.resetTimeOrigin()
        Dog.bark(ltag, lon) { "A 0" }
        runBlocking {
            launch {
                Dog.bark(ltag, lon) { "runBlocking/launch1/withContext1 A 0" }
                delay(400L)
                Dog.bark(ltag, lon) { "runBlocking/launch1/withContext1 B 400" }
                coroutineScope {
                    Dog.bark(ltag, lon) { "runBlocking/launch1/withContext2 A 400" }
                    delay(200L)
                    Dog.bark(ltag, lon) { "runBlocking/launch1/withContext2 B 600" }
                }
            }

            Dog.bark(ltag, lon) { "runBlocking A 0" }
            delay(500L)
            Dog.bark(ltag, lon) { "runBlocking B 500" }

            launch {
                Dog.bark(ltag, lon) { "runBlocking/launch2/withContext1 A 500" }
                delay(400L)
                Dog.bark(ltag, lon) { "runBlocking/launch2/withContext1 B 900" }
                Dog.bark(ltag, lon) { "runBlocking/launch2/withContext2 A 900" }
                delay(200L)
                Dog.bark(ltag, lon) { "runBlocking/launch2/withContext2 B 1100" }
            }

            Dog.bark(ltag, lon) { "runBlocking C 500" }
            delay(300L)
            Dog.bark(ltag, lon) { "runBlocking D 800" }
        }
        Dog.bark(ltag, lon) { "C 1100" }
    }
}