package com.hanafey.android.wol

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.system.measureTimeMillis

object Locking {

    @JvmStatic
    fun main(args: Array<String>) {

        Dispatchers.setMain(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
        // Dispatchers.setMain(newSingleThreadContext("ForCounting"))

        args.forEachIndexed { ix, arg ->
            when (arg) {
                "1-default" -> one(Dispatchers.Default)
                "1-main" -> one(Dispatchers.Main)
                "1-io" -> one(Dispatchers.IO)
                "1-null" -> one(null)
                "2-default" -> two(Dispatchers.Default)
                "2-main" -> two(Dispatchers.Main)
                "2-io" -> two(Dispatchers.IO)
                "2-null" -> two(null)
                "3-default" -> three(Dispatchers.Default)
                "3-main" -> three(Dispatchers.Main)
                "3-io" -> three(Dispatchers.IO)
                "3-null" -> three(null)
                "4-default" -> four(Dispatchers.Default)
                "4-main" -> four(Dispatchers.Main)
                "4-io" -> four(Dispatchers.IO)
                "4-null" -> four(null)
                "5-default" -> five(Dispatchers.Default)
                "5-main" -> five(Dispatchers.Main)
                "5-io" -> five(Dispatchers.IO)
                "5-null" -> five(null)
                else -> println("\n\n>>>>>>> Invalid argument = '$arg'")
            }
        }
    }

    /**
     * @param dispatcher The dispatcher to use. [Dispatchers.Main] is defined to be a single thread dispatcher.
     */
    private fun one(dispatcher: CoroutineDispatcher?) {

        println(
            """
                
Eg One many coroutines mutating a naked int. Dispatcher=$dispatcher
========================================================

"""
        )

        runBlocking {
            val threads = mutableSetOf<String>()
            var counter = 0
            if (dispatcher != null) {
                withContext(dispatcher) {
                    massiveRun {
                        threads.add(Thread.currentThread().name)
                        val savedCounter = counter
                        wasteCpu()
                        counter = savedCounter + 1
                    }
                }
            } else {
                massiveRun {
                    threads.add(Thread.currentThread().name)
                    val savedCounter = counter
                    wasteCpu()
                    counter = savedCounter + 1
                }
            }
            println("Counter = $counter")
            println("Threads used = $threads")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    /**
     * @param dispatcher The dispatcher to use. [Dispatchers.Main] is defined to be a single thread dispatcher.
     */
    private fun two(dispatcher: CoroutineDispatcher?) {
        println(
            """
                
Eg Two many coroutines mutating a naked int. Dispatcher=$dispatcher
========================================================

"""
        )

        runBlocking {
            val threads = mutableSetOf<String>()
            val counter = AtomicInteger(0)
            if (dispatcher != null) {
                withContext(dispatcher) {
                    massiveRun {
                        threads.add(Thread.currentThread().name)
                        wasteCpu()
                        counter.getAndAdd(1)
                    }
                }
            } else {
                massiveRun {
                    threads.add(Thread.currentThread().name)
                    wasteCpu()
                    counter.getAndAdd(1)
                }
            }
            println("Counter = $counter")
            println("Threads used = $threads")
        }
    }

    /**
     * @param dispatcher The dispatcher to use. [Dispatchers.Main] is defined to be a single thread dispatcher.
     */
    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun three(dispatcher: CoroutineDispatcher?) {
        val singleThreadContext: ExecutorCoroutineDispatcher = newSingleThreadContext("ForCounting")
        println(
            """
                
Eg Three many coroutines mutating a naked int. Dispatcher=$dispatcher / $singleThreadContext
========================================================

"""
        )

        runBlocking {
            val threads = mutableSetOf<String>()
            var counter = 0
            if (dispatcher != null) {
                withContext(dispatcher) {
                    massiveRun {
                        withContext(singleThreadContext) {
                            threads.add(Thread.currentThread().name)
                            val savedCounter = counter
                            wasteCpu()
                            counter = savedCounter + 1
                        }
                    }
                }
            } else {
                massiveRun {
                    withContext(singleThreadContext) {
                        threads.add(Thread.currentThread().name)
                        val savedCounter = counter
                        wasteCpu()
                        counter = savedCounter + 1
                    }
                }
            }
            println("Counter = $counter")
            println("Threads used = $threads")
        }
    }

    /**
     * @param dispatcher The dispatcher to use. [Dispatchers.Main] is defined to be a single thread dispatcher.
     */
    private fun four(dispatcher: CoroutineDispatcher?) {

        println(
            """
                
Eg for many coroutines mutating a naked int with mutex. Dispatcher=$dispatcher
==================================================================

"""
        )

        runBlocking {
            val threads = mutableSetOf<String>()
            var counter = 0
            val mutex = Mutex()
            if (dispatcher != null) {
                withContext(dispatcher) {
                    massiveRun {
                        threads.add(Thread.currentThread().name)
                        mutex.withLock {
                            val savedCounter = counter
                            wasteCpu()
                            counter = savedCounter + 1
                        }
                    }
                }
            } else {
                massiveRun {
                    threads.add(Thread.currentThread().name)
                    mutex.withLock {
                        val savedCounter = counter
                        wasteCpu()
                        counter = savedCounter + 1
                    }
                }
            }
            println("Counter = $counter")
            println("Threads used = $threads")
        }
    }


    /**
     * @param dispatcher The dispatcher to use. [Dispatchers.Main] is defined to be a single thread dispatcher.
     */
    private fun five(dispatcher: CoroutineDispatcher?) {

        println(
            """
                
Eg for many coroutines mutating a naked int with reentrant lock. Dispatcher=$dispatcher
==================================================================

"""
        )

        runBlocking {
            val threads = mutableSetOf<String>()
            var counter = 0
            val relock = ReentrantLock()
            if (dispatcher != null) {
                withContext(dispatcher) {
                    massiveRun {
                        threads.add(Thread.currentThread().name)
                        relock.lock()
                        val savedCounter = counter
                        wasteCpu()
                        counter = savedCounter + 1
                        relock.unlock()
                    }
                }
            } else {
                massiveRun {
                    threads.add(Thread.currentThread().name)
                    relock.lock()
                    val savedCounter = counter
                    wasteCpu()
                    counter = savedCounter + 1
                    relock.unlock()
                }
            }
            println("Lock hold count=${relock.holdCount}")
            println("Counter = $counter")
            println("Threads used = $threads")
        }
    }

    private fun wasteCpu() {
        var rubbish = 1000.0
        repeat(3_500) { rubbish /= 1.23456789 }
    }

    private suspend fun massiveRun(action: suspend () -> Unit) {
        val n = 100  // number of coroutines to launch
        val k = 1000 // times an action is repeated by each coroutine
        val time = measureTimeMillis {
            coroutineScope { // scope for coroutines
                repeat(n) {
                    launch {
                        repeat(k) { action() }
                    }
                }
            }
        }
        println("Completed ${n * k} actions in $time ms")
    }
}