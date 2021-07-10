package com.hanafey.android.wol

import com.hanafey.android.wol.magic.MagicPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.system.exitProcess

object SendMagicPackets {
    private val LTAG = "SendMagicPackets"

    @JvmStatic
    fun main(args: Array<String>) {
        val LTAG = "MagicPacketTest"

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
            tlog(LTAG) { "Launch second ping and wol." }
            launch(Dispatchers.IO) {
                if (pingStr.isNotEmpty()) doPingTest("PING", pingStr)
                if (macStr.length >= 12) doWolTest("WOL", ipStr, macStr)
            }
        }
    }

    private suspend fun doPingTest(name: String, pingStr: String) {
        for (i in 1..5) {
            try {
                tlog(LTAG) { "$name::  send $pingStr" }
                val pingable = MagicPacket.ping(pingStr)
                tlog(LTAG) { "$name::  result $pingable $pingStr" }
            } catch (e: Throwable) {
                tlog(LTAG) { "$name:: FAILED: $e" }
            }
            delay(500L)
        }
    }

    private suspend fun doWolTest(name: String, ipStr: String, macStr: String) {
        try {
            for (i in 1..5) {
                tlog(LTAG) { "$name::  start $ipStr, $macStr" }
                MagicPacket.sendWol(macStr, ipStr, MagicPacket.PORT)
                tlog(LTAG) { "$name::  end $ipStr, $macStr" }
                delay(1000L)
            }
        } catch (e: Throwable) {
            tlog(LTAG) { "$name:: FAILED: $e" }
        }
    }

}
