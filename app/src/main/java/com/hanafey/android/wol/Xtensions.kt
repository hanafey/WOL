package com.hanafey.android.wol

import android.content.Intent

internal fun mSecFromSeconds(seconds: Int) = 1000L * seconds
internal fun mSecToSeconds(mSec: Long) = "%1.1f sec".format(mSec / 1000.0)

internal fun mSecFromMinutes(minutes: Int, seconds: Int = 0) = 1000L * (minutes * 60 + seconds)
internal fun mSecToMinutes(mSec: Long) = "%1.1f min".format(mSec / (60.0 * 1000.0))

internal fun mSecFromHours(hours: Int, minutes: Int, seconds: Int = 0) = 1000L * ((hours * 60 + minutes) * 60 + seconds)
internal fun mSecToHours(mSec: Long) = "%1.1f hour".format(mSec / (60.0 * 60.0 * 1000.0))

fun intentToString(intent: Intent): String {
    val sb = StringBuilder(1024)
    sb.append("MainAct/MainFrag:")
    sb.append(intent.toString())
    sb.append("\nExtra Keys:")
    intent.extras?.run {
        keySet().forEach { key ->
            sb.append("  ")
            sb.append(key)
            sb.append(":")
            sb.append(get(key))
            sb.append("\n")
        }
    }
    return sb.toString()
}
