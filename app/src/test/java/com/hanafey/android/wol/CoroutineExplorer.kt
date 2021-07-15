package com.hanafey.android.wol

import kotlinx.coroutines.*
import java.time.Instant

object CoroutineExplorer {
    const val ltag = "CRE"

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
        tlog(ltag) { "A" }
        runBlocking {
            withContext(Dispatchers.Default) {
                tlog(ltag) { "runBlocking1/withContext1 A" }
                delay(400L)
                tlog(ltag) { "runBlocking1/withContext1 B" }
            }
            withContext(Dispatchers.Default) {
                tlog(ltag) { "runBlocking1/withContext2 A" }
                delay(200L)
                tlog(ltag) { "runBlocking1/withContext2 B" }
            }
            tlog(ltag) { "runBlocking1 A" }
            delay(100L)
            tlog(ltag) { "runBlocking1 B" }
        }
        runBlocking {
            withContext(Dispatchers.Default) {
                tlog(ltag) { "runBlocking2/withContext1 A" }
                delay(400L)
                tlog(ltag) { "runBlocking2/withContext1 B" }
            }
            withContext(Dispatchers.Default) {
                tlog(ltag) { "runBlocking2/withContext2 A" }
                delay(200L)
                tlog(ltag) { "runBlocking2/withContext2 B" }
            }
            tlog(ltag) { "runBlocking2 A" }
            delay(100L)
            tlog(ltag) { "runBlocking2 B" }
        }
        tlog(ltag) { "C" }

    }


    fun two() {
        println("\n\n========== Two expect 1400 msec to end==========")
        EXT_EPOCH = Instant.now()
        tlog(ltag) { "A" }
        runBlocking {
            coroutineScope {
                withContext(Dispatchers.IO) {
                    tlog(ltag) { "runBlocking/crs1/withContext1 A" }
                    delay(400L)
                    tlog(ltag) { "runBlocking/crs1/withContext1 B" }
                }
                withContext(Dispatchers.IO) {
                    tlog(ltag) { "runBlocking/crs1/withContext2 A" }
                    delay(200L)
                    tlog(ltag) { "runBlocking/crs1/withContext2 B" }
                }
                tlog(ltag) { "runBlocking/crs1 A" }
                delay(100L)
                tlog(ltag) { "runBlocking/crs1 B" }
            }
            coroutineScope {
                withContext(Dispatchers.IO) {
                    tlog(ltag) { "runBlocking/crs2/withContext1 A" }
                    delay(400L)
                    tlog(ltag) { "runBlocking/crs2/withContext1 B" }
                }
                withContext(Dispatchers.IO) {
                    tlog(ltag) { "runBlocking/crs2/withContext2 A" }
                    delay(200L)
                    tlog(ltag) { "runBlocking/crs2/withContext2 B" }
                }
                tlog(ltag) { "runBlocking/crs2 A" }
                delay(100L)
                tlog(ltag) { "runBlocking/crs2 B" }
            }
        }
        tlog(ltag) { "C" }

    }

    fun three() {
        println("\n\n========== Three expext 1000 msec to end ==========")
        EXT_EPOCH = Instant.now()
        tlog(ltag) { "A" }
        runBlocking {
            tlog(ltag) { "  runBlocking A" }

            async(Dispatchers.IO) {
                tlog(ltag) { "    runBlocking/async1 A" }
                delay(800L)
                delay(200L)
                tlog(ltag) { "    runBlocking/async1 B" }
            }

            tlog(ltag) { "  runBlocking B" }

            async(Dispatchers.IO) {
                tlog(ltag) { "    runBlocking/async2 A" }
                delay(200L)
                tlog(ltag) { "    runBlocking/async2 B" }
            }

            tlog(ltag) { "  runBlocking C" }
            delay(500L)
            tlog(ltag) { "  runBlocking D" }

            async(Dispatchers.IO) {
                tlog(ltag) { "    runBlocking/async3 A" }
                delay(400L)
                tlog(ltag) { "    runBlocking/async3 B" }
            }

            tlog(ltag) { "  runBlocking E" }

            async(Dispatchers.IO) {
                tlog(ltag) { "    runBlocking/async4 A" }
                delay(200L)
                tlog(ltag) { "    runBlocking/async4 B" }
            }

            tlog(ltag) { "  runBlocking F" }
            delay(100L)
            tlog(ltag) { "  runBlocking G" }
        }
        tlog(ltag) { "B" }
    }

    fun four() {
        println("\n\n========== Four expect 900 msec to end==========")
        EXT_EPOCH = Instant.now()
        tlog(ltag) { "A 0" }
        runBlocking {
            launch {
                withContext(Dispatchers.IO) {
                    tlog(ltag) { "runBlocking/launch1/withContext1 A 0" }
                    delay(400L)
                    tlog(ltag) { "runBlocking/launch1/withContext1 B 400" }
                }
                withContext(Dispatchers.IO) {
                    tlog(ltag) { "runBlocking/launch1/withContext2 A 400" }
                    delay(200L)
                    tlog(ltag) { "runBlocking/launch1/withContext2 B 600" }
                }
            }

            tlog(ltag) { "runBlocking A 0" }
            delay(500L)
            tlog(ltag) { "runBlocking B 500" }

            launch {
                withContext(Dispatchers.IO) {
                    tlog(ltag) { "runBlocking/launch2/withContext1 A 500" }
                    delay(400L)
                    tlog(ltag) { "runBlocking/launch2/withContext1 B 900" }
                }
                withContext(Dispatchers.IO) {
                    tlog(ltag) { "runBlocking/launch2/withContext2 A 900" }
                    delay(200L)
                    tlog(ltag) { "runBlocking/launch2/withContext2 B 1100" }
                }
            }

            tlog(ltag) { "runBlocking C 500" }
            delay(300L)
            tlog(ltag) { "runBlocking D 800" }
        }
        tlog(ltag) { "C 1100" }

    }

    fun five() {
        println("\n\n========== Five expect 900 msec to end==========")
        EXT_EPOCH = Instant.now()
        tlog(ltag) { "A 0" }
        runBlocking {
            launch {
                tlog(ltag) { "runBlocking/launch1/withContext1 A 0" }
                delay(400L)
                tlog(ltag) { "runBlocking/launch1/withContext1 B 400" }
                coroutineScope {
                    tlog(ltag) { "runBlocking/launch1/withContext2 A 400" }
                    delay(200L)
                    tlog(ltag) { "runBlocking/launch1/withContext2 B 600" }
                }
            }

            tlog(ltag) { "runBlocking A 0" }
            delay(500L)
            tlog(ltag) { "runBlocking B 500" }

            launch {
                tlog(ltag) { "runBlocking/launch2/withContext1 A 500" }
                delay(400L)
                tlog(ltag) { "runBlocking/launch2/withContext1 B 900" }
                tlog(ltag) { "runBlocking/launch2/withContext2 A 900" }
                delay(200L)
                tlog(ltag) { "runBlocking/launch2/withContext2 B 1100" }
            }

            tlog(ltag) { "runBlocking C 500" }
            delay(300L)
            tlog(ltag) { "runBlocking D 800" }
        }
        tlog(ltag) { "C 1100" }

    }

}