package com.hanafey.android.wol.magic

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object MagicPacket {
    private const val ltag = "MagicPacket"


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
        // This method is hard to understand from the doc. With ttl = 0 it seems we wait for two timeout
        // intervals, but this does not happen if ttl is > 0
        // Source link:https://android.googlesource.com/platform/libcore/+/master/ojluni/src/main/java/java/net/Inet6AddressImpl.java
        // return hostAddress.isReachable(waitForResponseMilli.coerceAtLeast(20))
        return hostAddress.isReachable(null, 0, waitForResponseMilli.coerceAtLeast(20))
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
}