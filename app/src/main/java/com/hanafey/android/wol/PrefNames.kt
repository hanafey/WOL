package com.hanafey.android.wol

enum class PrefNames {
    HOST_TIME_TO_WAKE,
    TWO;

    /**
     * String form of preference name. For prefs that are host name dependent provide the host name.
     */
    fun pref(hostName: String = ""): String {
        return if (hostName.isBlank()) toString() else toString() + "_$hostName"
    }
}