package com.hanafey.android.wol

enum class PrefNames {
    HOST_TIME_TO_WAKE,
    HOST_ENABLED,
    HOST_SECTION,
    HOST_PKEY,
    HOST_PING_ME,
    HOST_TITLE,
    HOST_PING_NAME,
    HOST_MAC_STRING,
    HOST_BROADCAST_IP,
    PING_DELAY;

    /**
     * String form of preference name. For prefs that are host name dependent provide the host name.
     */
    fun pref(hostName: String = ""): String {
        return if (hostName.isBlank()) toString() else toString() + "_$hostName"
    }

    /**
     * String form of preference name. For prefs that are host name dependent by an integer key provide the key
     * greater than zero.
     */
    fun pref(hostKey: Int = 0): String {
        return if (hostKey <= 0) toString() else "${this}_%02d".format(hostKey)
    }

    companion object {
        /**
         * Returns a pair where first is the [PrefNames] and the second is either -1 (no number at end preceded by
         * a '_') or the trailing number from the [string] that is not part of the [PrefNames] (because it is the host
         * key.
         */
        fun fromString(string: String): Pair<PrefNames, Int> {
            // E.g. with no host index: THIS_IS_A_NAME
            // E.g. with a host index THIS_IS_A_NAME_02
            val li = string.indexOfLast { it == '_' }
            return if (li == -1) {
                PrefNames.valueOf(string) to -1
            } else {
                val hostIndexString = string.substring(li + 1)
                return try {
                    val number = hostIndexString.toInt()
                    PrefNames.valueOf(string.substring(0, li)) to number
                } catch (ex: Exception) {
                    PrefNames.valueOf(string) to -1
                }
            }
        }
    }
}