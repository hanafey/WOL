package com.hanafey.android.wol.magic

import com.hanafey.android.wol.EXT_EPOCH
import com.hanafey.android.wol.tlog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.Instant
import kotlin.system.exitProcess

object MagicPacket {
    private const val LTAG = "MagicPacket"


    /**
     * The default broadcast name for WOL magic packet.
     */
    const val BROADCAST = "192.168.1.255"


    /**
     * The default port destination for the WOL magic packet.
     */
    const val PORT = 9

    private const val SEPARATOR = ":"
    private val reMacAddressNoPunctuation = Regex("([a-zA-Z0-9]){12}")
    private val reMacAddress = Regex("((([0-9a-fA-F]){2}[-:]){5}([0-9a-fA-F]){2})")
    private val magicSignal = byteArrayOf(-1, -1, -1, -1, -1, -1)


    /**
     * Given host name converts it to [InetAddress], tests if address is "reachable", and returns true if
     * it is, else false. The time to wait for a response is in [waitForResponseMilli].
     * @param hostName The name to ping.
     * @param waitForResponseMilli Time to wait for a response before returning false. Default is 1000 mSec.
     * @return True if ping succeeded, false if ping timed out.
     */
    @Throws(
        java.net.UnknownHostException::class,
        SecurityException::class,
    )
    fun ping(hostName: String, waitForResponseMilli: Int = 1000): Boolean {
        return ping(InetAddress.getByName(hostName), waitForResponseMilli)
    }


    /**
     * Given address tests if address is "reachable", and returns true if it is, else false. The time
     * to wait for a response is in [waitForResponseMilli]
     * @param hostAddress The `InetAddress` to ping.
     * @param waitForResponseMilli Time to wait for a response before returning false. Default is 250 mSec.
     * @return True if ping succeeded, false if ping timed out.
     */
    @Throws(java.io.IOException::class)
    fun ping(hostAddress: InetAddress, waitForResponseMilli: Int = 250): Boolean {
        return hostAddress.isReachable(waitForResponseMilli)
    }


    /**
     * Sends a magic WOL packet to the [mac] address. The IP address for the broadcast by default is [BROADCAST], and
     * the port is [PORT].
     * @param mac The MAC address in a form suitable for [validateMac].
     * @param broadCastName The broadcast address, by default [BROADCAST].
     * @param port The broadcast port, by default [PORT].
     */
    @Throws(
        java.net.UnknownHostException::class,
        java.net.SocketException::class,
        java.net.PortUnreachableException::class,
        java.nio.channels.IllegalBlockingModeException::class,
        SecurityException::class,
    )
    fun sendWol(mac: String, broadCastName: String = BROADCAST, port: Int = PORT) {
        // validate MAC and chop into array
        val hex: List<String> = validateMac(mac)

        // convert base 16 strings to bytes
        val macBytes = ByteArray(6)
        for (i in 0..5) {
            macBytes[i] = hex[i].toInt(16).toByte()
        }
        val bytes = ByteArray(102)

        // fill first 6 bytes
        magicSignal.copyInto(bytes)

        // fill remaining bytes with target MAC
        var dix = 6
        for (i in 0 until 16) {
            macBytes.copyInto(bytes, dix)
            dix += 6
        }

        // create socket to IP
        val address = InetAddress.getByName(broadCastName)
        val packet = DatagramPacket(bytes, bytes.size, address, port)
        DatagramSocket().use { socket ->
            socket.send(packet)
        }
    }


    /**
     * Returns [mac] in the standard format "a1:b2:c3:d4:e5:f6"
     */
    fun standardizeMac(mac: String): String {
        return validateMac(mac).joinToString(SEPARATOR)
    }

    private fun validateMac(rawMac: String): List<String> {
        val mac = rawMac.lowercase()
        val newMac = if (mac.matches(reMacAddressNoPunctuation)) {
            // expand 12 chars into a valid mac address
            val buffer = StringBuilder(32)
            for (i in 0..10 step 2) {
                buffer.append(mac[i])
                buffer.append(mac[i + 1])
                if (i != 10) buffer.append(':')
            }
            buffer.toString()
        } else {
            mac
        }

        return if (newMac.matches(reMacAddress)) {
            newMac.split(':', '-')
        } else {
            throw IllegalArgumentException("Invalid MAC address")
        }
    }


    @JvmStatic
    fun main(args: Array<String>) {
        val LTAG = "MagicPacketTest"

        if (args.size < 2 || args.size > 3) {
            println("Usage: java MagicPacket <broadcast-ip> <mac-address> [<ping-ip>]")
            println("Example: java MagicPacket 192.168.0.255 00:0D:61:08:22:4A")
            println("Example: java MagicPacket 192.168.0.255 00-0D-61-08-22-4A 192.168.1.1")
            exitProcess(1)
        }
        val ipStr = args[0]
        val macStr = args[1]
        val pingStr = if (args.size > 2) args[2] else ""

        runBlocking {
            EXT_EPOCH = Instant.now()
            /*
            tlog(LTAG) {"Launch first ping."}
            launch(Dispatchers.IO) {
                if (pingStr.isNotEmpty()) doPingPlayTest("FIRST", pingStr)
            }
            tlog(LTAG) {"Launch first wol."}
            launch(Dispatchers.IO) {
                if (macStr.length >= 12) doWolPlayTest("SECOND", ipStr, macStr)
            }
            */
            tlog(LTAG) { "Launch second ping and wol." }
            launch(Dispatchers.IO) {
                if (pingStr.isNotEmpty()) doPingTest("PING", pingStr)
                if (macStr.length >= 12) doWolTest("WOL", ipStr, macStr)
            }
        }
        /*
        var state = ""
        try {
            if (pingStr.isNotEmpty()) {
                state = "Ping"
                println("Sending ping to $pingStr")
                val now = Instant.now()
                val pingable = ping(pingStr)
                val after = Instant.now()
                val duration = Duration.between(now, after).toMillis()
                println("Ping $pingStr is pingable? $pingable ($duration mSec)")
            }
            state = "WOL"
            print("Wake $macStr on $ipStr:$PORT ...")
            sendWol(macStr, ipStr, PORT)
            println(" sent.")
        } catch (e: IllegalArgumentException) {
            println(e.message)
        } catch (e: Throwable) {
            println("Failed $state:" + e.message)
        }
        */
    }

    private suspend fun doWolPlayTest(name: String, ipStr: String, macStr: String) {
        tlog(LTAG) { "$name:: doWolTest wait..." }
        delay(1000L)
        tlog(LTAG) { "$name:: doWolTest done..." }
    }

    private suspend fun doPingPlayTest(name: String, pingStr: String) {
        tlog(LTAG) { "$name:: doPingTest wait..." }
        delay(1500L)
        tlog(LTAG) { "$name:: doPingTest done..." }
    }

    private suspend fun doPingTest(name: String, pingStr: String) {
        for (i in 1..5) {
            try {
                tlog(LTAG) { "$name::  send $pingStr" }
                val pingable = ping(pingStr)
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
                sendWol(macStr, ipStr, PORT)
                tlog(LTAG) { "$name::  end $ipStr, $macStr" }
                delay(1000L)
            }
        } catch (e: Throwable) {
            tlog(LTAG) { "$name:: FAILED: $e" }
        }
    }
}