package com.hanafey.android.wol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hanafey.android.wol.magic.WolHost

/**
 * Defines two notification channels, [channelIdAwoke] and [channelIdAsleep]
 */
class HostStateNotification(val context: Context) {
    private val requestCodeBase = 10000
    private var notificationId = 0

    private val channelIdAwoke = "HOST_AWOKE"
    private val channelNameAwoke = context.getString(R.string.notification_awoke_name)
    private val channelDescriptionAwoke = context.getString(R.string.notification_awoke_description)

    private val channelIdAsleep = "HOST_ASLEEP"
    private val channelNameAsleep = context.getString(R.string.notification_asleep_name)
    private val channelDescriptionAsleep = context.getString(R.string.notification_asleep_description)

    private fun make(host: WolHost, channelId: String, @DrawableRes icon: Int, contentTitle: String, contentText: String): Int {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            // CLEAR_TASK needed or else the UP button will navigate back to old tasks
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("HOST_IX", host.pKey)
        }
        // Because the intent is different for different hosts we need a different pending intent for each host.
        // This was observed experimentally. With the same request code the last host to notify affects all notifications.
        val pendingIntent = PendingIntent.getActivity(
            context,
            host.pKey + requestCodeBase,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId).apply {
            setSmallIcon(icon)
            if (contentTitle.isNotBlank()) setContentTitle(contentTitle)
            if (contentText.isNotBlank()) setContentText(contentText)
            priority = NotificationCompat.PRIORITY_HIGH
            // setSound(alarmSound)
            setContentIntent(pendingIntent)
            setAutoCancel(true)
        }

        val nm = NotificationManagerCompat.from(context)
        nm.notify(++notificationId, builder.build())

        return notificationId
    }

    /**
     * Send a notification of host changed to awake.
     * @return the notification id for [dismiss]
     */
    fun makeAwokeNotification(host: WolHost, title: String, content: String): Int {
        return make(host, channelIdAwoke, R.drawable.ic_host_awoke, title, content)
    }

    /**
     * Send a notification of host changed to asleep.
     * @return the notification id for [dismiss]
     */
    fun makeAsleepNotification(host: WolHost, title: String, content: String): Int {
        return make(host, channelIdAsleep, R.drawable.ic_host_asleep, title, content)
    }

    /**
     * Dismiss a notification by it's ID.
     */
    private fun dismiss(notificationId: Int) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(notificationId)
    }

    /**
     * Creates the "HOST_AWOKE" and "HOST_ASLEEP" channels if they do not already exist,
     */
    fun createNotificationChannels() {
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager, channelIdAwoke, channelNameAwoke, channelDescriptionAwoke)
        createChannel(notificationManager, channelIdAsleep, channelNameAsleep, channelDescriptionAsleep)
    }

    private fun createChannel(notificationManager: NotificationManager, channelId: String, channelName: String, channelDescription: String) {
        val channel: NotificationChannel? = notificationManager.getNotificationChannel(channelId)
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (channel == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channelToCreate = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            // Register the channel with the system

            notificationManager.createNotificationChannel(channelToCreate)
        }
    }
}