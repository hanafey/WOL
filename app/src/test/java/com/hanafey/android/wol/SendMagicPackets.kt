package com.hanafey.android.wol

import com.hanafey.android.wol.magic.MagicPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.system.exitProcess

object SendMagicPackets {
    private const val ltag = "SendMagicPackets"

    @JvmStatic
    fun main(args: Array<String>) {
        val ltag = "MagicPacketTest"

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
            EXT_EPOCH = Instant.now()
            tlog(ltag) { "Launch second ping and wol." }
            launch(Dispatchers.IO) {
                if (pingStr.isNotEmpty()) doPingTest("PING", pingStr)
                if (macStr.length >= 12) doWolTest("WOL", ipStr, macStr)
            }
        }
    }

    private suspend fun doPingTest(name: String, pingStr: String) {
        for (i in 1..5) {
            try {
                tlog(ltag) { "$name::  send $pingStr" }
                @Suppress("BlockingMethodInNonBlockingContext")
                val pingable = MagicPacket.ping(pingStr)
                tlog(ltag) { "$name::  result $pingable $pingStr" }
            } catch (e: Throwable) {
                tlog(ltag) { "$name:: FAILED: $e" }
            }
            delay(500L)
        }
    }

    private suspend fun doWolTest(name: String, ipStr: String, macStr: String) {
        try {
            for (i in 1..5) {
                tlog(ltag) { "$name::  start $ipStr, $macStr" }
                @Suppress("BlockingMethodInNonBlockingContext")
                MagicPacket.sendWol(macStr, ipStr, MagicPacket.PORT)
                tlog(ltag) { "$name::  end $ipStr, $macStr" }
                delay(1000L)
            }
        } catch (e: Throwable) {
            tlog(ltag) { "$name:: FAILED: $e" }
        }
    }

}
