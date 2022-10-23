package com.hanafey.android.wol

import com.hanafey.android.ax.Dog
import com.hanafey.android.wol.magic.MagicPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

object SendMagicPackets {
    private const val ltag = "SendMagicPackets"
    private const val lon = true

    @JvmStatic
    fun main(args: Array<String>) {

        if (args.size < 2 || args.size > 3) {
            println("Usage: java MagicPacket <broadcast-ip> <mac-address> [<ping-ip>]")
            println("Example: java MagicPacket 192.168.0.255 00:0D:61:08:22:4A")
            println("Example: java MagicPacket 192.168.1.255 ff-0D-61-08-22-4A 192.168.1.1")
            exitProcess(1)
        }
        val ipStr = args[0]
        val macStr = args[1]
        val pingStr = if (args.size > 2) args[2] else ""

        runBlocking {
            Dog.resetTimeOrigin()
            Dog.bark(ltag, lon) { "Launch second ping and wol." }
            launch(Dispatchers.IO) {
                if (pingStr.isNotEmpty()) doPingTest("PING", pingStr)
                if (macStr.length >= 12) doWolTest("WOL", ipStr, macStr)
            }
        }
    }

    private suspend fun doPingTest(name: String, pingStr: String) {
        for (i in 1..5) {
            try {
                Dog.bark(ltag, lon) { "$name::  send $pingStr" }
                @Suppress("BlockingMethodInNonBlockingContext")
                val pingable = MagicPacket.ping(pingStr)
                Dog.bark(ltag, lon) { "$name::  result $pingable $pingStr" }
            } catch (e: Throwable) {
                Dog.bark(ltag, lon) { "$name:: FAILED: $e" }
            }
            delay(500L)
        }
    }

    private suspend fun doWolTest(name: String, ipStr: String, macStr: String) {
        try {
            for (i in 1..5) {
                Dog.bark(ltag, lon) { "$name::  start $ipStr, $macStr" }
                @Suppress("BlockingMethodInNonBlockingContext")
                MagicPacket.sendWol(macStr, ipStr, MagicPacket.PORT)
                Dog.bark(ltag, lon) { "$name::  end $ipStr, $macStr" }
                delay(1000L)
            }
        } catch (e: Throwable) {
            Dog.bark(ltag, lon) { "$name:: FAILED: $e" }
        }
    }
}
