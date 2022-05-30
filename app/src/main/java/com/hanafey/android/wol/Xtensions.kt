package com.hanafey.android.wol

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import kotlin.math.roundToInt

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

fun Fragment.requireAppCompatActivity(): AppCompatActivity {
    return (this.requireActivity() as AppCompatActivity)
}


/**
 * This method converts dp unit to equivalent pixels, depending on device density.
 *
 * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
 * @param resources to get resources and device specific display metrics
 * @return A float value to represent px equivalent to dp depending on device density
 */
fun convertDpToPixel(dp: Float, resources: Resources): Int {
    return (dp * (resources.displayMetrics.densityDpi.toDouble() / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
}


/**
 * This method converts device specific pixels to density independent pixels.
 *
 * @param px A value in px (pixels) unit. Which we need to convert into db.
 * @param resources The resources.
 * @return A float value to represent dp equivalent to px value.
 */
fun convertPixelsToDp(px: Float, resources: Resources): Double {
    return px / (resources.displayMetrics.densityDpi.toDouble() / DisplayMetrics.DENSITY_DEFAULT)
}

fun Fragment.hideKeyboardFrom(view: View) {
    val imm = this.requireActivity().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

fun hideKeyboardFrom(context: Context, view: View) {
    val imm: InputMethodManager = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

fun hideKeyboardFrom(view: View) {
    val imm: InputMethodManager = view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Context.resIdByName(resIdName: String, resType: String): Int {
    return resources.getIdentifier(resIdName, resType, packageName)
}

fun Fragment.getToolbarTitle(): String {
    return this.requireAppCompatActivity().supportActionBar?.title?.toString() ?: ""
}
