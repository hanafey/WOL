package com.hanafey.android.wol.magic

/**
 * @param pKey A unique key for each host that also orders a set of hosts.
 * @param title User understandable name for the WOL target.
 * @param pingName Name of ip address of WOL target. This is used to ping to see if host is
 * awake. Examples "192.168.1.250", "nasa"
 * @param macString The MAC address to create magic WOL packet for. Example: "001132F00EC1" or
 * "00:11:32:F0:0E:C1"
 * @param broadcastIp The broadcast address for WOL magic packets. Example: "192.168.1.255"
 */
class WolHost(
    val pKey: Int,
    val title: String,
    val pingName: String,
    macString: String,
    val broadcastIp: String,
) : Comparable<WolHost> {
    /**
     * The MAC address in standard format.
     */
    val macAddress = MagicPacket.standardizeMac(macString)


    /**
     * If this host is being pinged, or should be pinged, this is true.
     */
    var pingMe = false


    /**
     * The number of times host was pinged since last reset
     */
    var pingedCount = 0


    /**
     *  0 means status not known, 1 means responded to last ping, -1 means last ping timed out and
     *  -2 means attempt to ping threw exception.
     */
    var pingState = PingStates.INDETERMINATE


    /**
     * The exception that produced [pingState] of [PingStates.EXCEPTION]
     */
    var pingException: Throwable? = null


    /**
     * The number of wake up magic packets sent to [macAddress]
     */
    var wakeupCount = 0


    /**
     * If the last wake up attempt threw an exception, this is it.
     */
    var wakeupException: Throwable? = null

    fun resetState() {
        pingedCount = 0
        pingState = PingStates.INDETERMINATE
        pingException = null
        wakeupCount = 0
        wakeupException = null
    }


    /**
     * Resets [pingedCount], [pingState], [pingException]
     */
    fun resetPingState() {
        pingedCount = 0
        pingState = PingStates.INDETERMINATE
        pingException = null
    }

    override fun compareTo(other: WolHost): Int {
        return pKey - other.pKey
    }

    enum class PingStates {
        /**
         * Unknown, no ping result yet.
         */
        INDETERMINATE,


        /**
         * Responded within the timeout to a reachability test.
         */
        ALIVE,


        /**
         * No response within timeout to a reachability test.
         */
        DEAD,


        /**
         * Attempt to ping produced an exception.
         */
        EXCEPTION
    }
}