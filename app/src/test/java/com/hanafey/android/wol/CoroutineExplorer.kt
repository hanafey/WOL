package com.hanafey.android.wol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

object CoroutineExplorer {

    @JvmStatic
    fun main(args: Array<String>) {
        one()
        two()
        three()
        four()
        five()
    }

    fun one() {
        println("\n\n========== One expect 1400 msec to end==========")
        EXT_EPOCH = Instant.now()
        dog { "A" }
        runBlocking {
            withContext(Dispatchers.Default) {
                dog { "runBlocking1/withContext1 A" }
                delay(400L)
                dog { "runBlocking1/withContext1 B" }
            }
            withContext(Dispatchers.Default) {
                dog { "runBlocking1/withContext2 A" }
                delay(200L)
                dog { "runBlocking1/withContext2 B" }
            }
            dog { "runBlocking1 A" }
            delay(100L)
            dog { "runBlocking1 B" }
        }
        runBlocking {
            withContext(Dispatchers.Default) {
                dog { "runBlocking2/withContext1 A" }
                delay(400L)
                dog { "runBlocking2/withContext1 B" }
            }
            withContext(Dispatchers.Default) {
                dog { "runBlocking2/withContext2 A" }
                delay(200L)
                dog { "runBlocking2/withContext2 B" }
            }
            dog { "runBlocking2 A" }
            delay(100L)
            dog { "runBlocking2 B" }
        }
        dog { "C" }

    }


    fun two() {
        println("\n\n========== Two expect 1400 msec to end==========")
        EXT_EPOCH = Instant.now()
        dog { "A" }
        runBlocking {
            coroutineScope {
                withContext(Dispatchers.IO) {
                    dog { "runBlocking/crs1/withContext1 A" }
                    delay(400L)
                    dog { "runBlocking/crs1/withContext1 B" }
                }
                withContext(Dispatchers.IO) {
                    dog { "runBlocking/crs1/withContext2 A" }
                    delay(200L)
                    dog { "runBlocking/crs1/withContext2 B" }
                }
                dog { "runBlocking/crs1 A" }
                delay(100L)
                dog { "runBlocking/crs1 B" }
            }
            coroutineScope {
                withContext(Dispatchers.IO) {
                    dog { "runBlocking/crs2/withContext1 A" }
                    delay(400L)
                    dog { "runBlocking/crs2/withContext1 B" }
                }
                withContext(Dispatchers.IO) {
                    dog { "runBlocking/crs2/withContext2 A" }
                    delay(200L)
                    dog { "runBlocking/crs2/withContext2 B" }
                }
                dog { "runBlocking/crs2 A" }
                delay(100L)
                dog { "runBlocking/crs2 B" }
            }
        }
        dog { "C" }

    }

    fun three() {
        println("\n\n========== Three expext 1000 msec to end ==========")
        EXT_EPOCH = Instant.now()
        dog { "A" }
        runBlocking {
            dog { "  runBlocking A" }

            async(Dispatchers.IO) {
                dog { "    runBlocking/async1 A" }
                delay(800L)
                delay(200L)
                dog { "    runBlocking/async1 B" }
            }

            dog { "  runBlocking B" }

            async(Dispatchers.IO) {
                dog { "    runBlocking/async2 A" }
                delay(200L)
                dog { "    runBlocking/async2 B" }
            }

            dog { "  runBlocking C" }
            delay(500L)
            dog { "  runBlocking D" }

            async(Dispatchers.IO) {
                dog { "    runBlocking/async3 A" }
                delay(400L)
                dog { "    runBlocking/async3 B" }
            }

            dog { "  runBlocking E" }

            async(Dispatchers.IO) {
                dog { "    runBlocking/async4 A" }
                delay(200L)
                dog { "    runBlocking/async4 B" }
            }

            dog { "  runBlocking F" }
            delay(100L)
            dog { "  runBlocking G" }
        }
        dog { "B" }
    }

    fun four() {
        println("\n\n========== Four expect 900 msec to end==========")
        EXT_EPOCH = Instant.now()
        dog { "A 0" }
        runBlocking {
            launch {
                withContext(Dispatchers.IO) {
                    dog { "runBlocking/launch1/withContext1 A 0" }
                    delay(400L)
                    dog { "runBlocking/launch1/withContext1 B 400" }
                }
                withContext(Dispatchers.IO) {
                    dog { "runBlocking/launch1/withContext2 A 400" }
                    delay(200L)
                    dog { "runBlocking/launch1/withContext2 B 600" }
                }
            }

            dog { "runBlocking A 0" }
            delay(500L)
            dog { "runBlocking B 500" }

            launch {
                withContext(Dispatchers.IO) {
                    dog { "runBlocking/launch2/withContext1 A 500" }
                    delay(400L)
                    dog { "runBlocking/launch2/withContext1 B 900" }
                }
                withContext(Dispatchers.IO) {
                    dog { "runBlocking/launch2/withContext2 A 900" }
                    delay(200L)
                    dog { "runBlocking/launch2/withContext2 B 1100" }
                }
            }

            dog { "runBlocking C 500" }
            delay(300L)
            dog { "runBlocking D 800" }
        }
        dog { "C 1100" }

    }

    fun five() {
        println("\n\n========== Five expect 900 msec to end==========")
        EXT_EPOCH = Instant.now()
        dog { "A 0" }
        runBlocking {
            launch {
                dog { "runBlocking/launch1/withContext1 A 0" }
                delay(400L)
                dog { "runBlocking/launch1/withContext1 B 400" }
                coroutineScope {
                    dog { "runBlocking/launch1/withContext2 A 400" }
                    delay(200L)
                    dog { "runBlocking/launch1/withContext2 B 600" }
                }
            }

            dog { "runBlocking A 0" }
            delay(500L)
            dog { "runBlocking B 500" }

            launch {
                dog { "runBlocking/launch2/withContext1 A 500" }
                delay(400L)
                dog { "runBlocking/launch2/withContext1 B 900" }
                dog { "runBlocking/launch2/withContext2 A 900" }
                delay(200L)
                dog { "runBlocking/launch2/withContext2 B 1100" }
            }

            dog { "runBlocking C 500" }
            delay(300L)
            dog { "runBlocking D 800" }
        }
        dog { "C 1100" }

    }

    // --------------------------------------------------------------------------------
    // Logging
    // --------------------------------------------------------------------------------

    private const val tag = "COE"
    private const val debugLoggingEnabled = true
    var EXT_EPOCH: Instant = Instant.now()

    private fun dog(message: () -> String) {
        if (debugLoggingEnabled) {
            val duration = Duration.between(EXT_EPOCH, Instant.now()).toMillis() / 1000.0
            val prefix = "%s [%8.3f]: ".format(tag, duration)
            println(prefix + message())
        }
    }


}