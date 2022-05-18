package com.hanafey.android.wol

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * [dlog] messages contain a time differential from this value. Initializes to when the class is created, but you can
 * set it later to show times relative to some interesting reference point.
 */
var EXT_EPOCH: Instant = Instant.now()

/**
 * Logs an ERROR, but is intended only for the debug phase of development. Normally expunge or comment out.
 * @param tag Normally the simple class name.
 * @param unique Optional random short string to make the log item easy find or filter based on. Default is empty.
 * @param enabled If false the logging is not done. Default is true
 * @param message The message to log
 */
internal inline fun dlog(tag: String, unique: String = "", enabled: Boolean = true, message: () -> String) {
    if (BuildConfig.DEBUG) {
        if (enabled) {
            if (Log.isLoggable(tag, Log.ERROR)) {
                val duration = Duration.between(EXT_EPOCH, Instant.now()).toMillis() / 1000.0
                val durationString = "[%8.3f]".format(duration)
                Log.println(Log.ERROR, tag, durationString + unique + ":" + message())
            }
        }
    }
}

/**
 * Logs an ERROR.
 * @param tag Normally the simple class name.
 * @param enabled If false the logging is not done. Default is true
 * @param message The message to log
 */
internal inline fun elog(tag: String, enabled: Boolean = true, message: () -> String) {
    if (BuildConfig.DEBUG) {
        if (enabled) {
            if (Log.isLoggable(tag, Log.ERROR)) {
                Log.println(Log.ERROR, tag, message())
            }
        }
    }
}

/**
 * Like [dlog] but this just does a "println" instead of a "Log".
 */
internal fun tlog(tag: String, unique: String = "", enabled: Boolean = true, message: () -> String) {
    if (BuildConfig.DEBUG) {
        if (enabled) {
            val duration = Duration.between(EXT_EPOCH, Instant.now()).toMillis() / 1000.0
            val durationString = "[%8.3f]".format(duration)
            println("$tag: $durationString$unique: ${message()}")
        }
    }
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
