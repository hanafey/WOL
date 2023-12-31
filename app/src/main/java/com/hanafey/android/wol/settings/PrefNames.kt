package com.hanafey.android.wol.settings

enum class PrefNames {
    HOST_TIME_TO_WAKE,
    HOST_PING_ME,

    /**
     * [com.hanafey.android.wol.magic.WolHost] members
     */
    HOST_ENABLED,
    HOST_SECTION,
    HOST_TITLE,
    HOST_PING_NAME,
    HOST_MAC_STRING,
    HOST_BROADCAST_IP,
    HOST_WOL_BUNDLE_COUNT,
    HOST_WOL_BUNDLE_SPACING,
    HOST_SOUND_TRACK_NOTIFY,
    HOST_DAT_NOTIFY,
    HOST_WOL_NOTIFY,
    HOST_DAT_BUFFER_SIZE,
    HOST_DAT_BUFFER_ALIVE_AT,
    HOST_DAT_BUFFER_DEAD_AT,

    PING_DELAY,
    PING_WAIT,
    PING_SUSPEND_DELAY,
    PING_IGNORE_WIFI_STATE,
    VERSION_ACKNOWLEDGED;


    /**
     * String form of preference name. For prefs that are host name dependent by an integer key provide the key
     * greater than or equal to zero. So the target host at index 0 has pref names that end with "_01".
     * @param hostIndex The host index. The name is string value of index +1 formatted by "_%02d".
     */
    fun pref(hostIndex: Int = -1): String {
        return if (hostIndex < 0) toString() else "${this}_%02d".format(hostIndex + 1)
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
                valueOf(string) to -1
            } else {
                val hostIndexString = string.substring(li + 1)
                return try {
                    val number = hostIndexString.toInt()
                    valueOf(string.substring(0, li)) to number
                } catch (ex: Exception) {
                    valueOf(string) to -1
                }
            }
        }
    }
}