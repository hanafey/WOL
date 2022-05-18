package com.hanafey.android.wol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class HostStateNotification(val context: Context) {
    private var notificationId = 0

    val channelIdAwoke = "HOST_AWOKE"
    val channelNameAwoke = context.getString(R.string.notification_awoke_name)
    private val channelDescriptionAwoke = context.getString(R.string.notification_awoke_description)

    val channelIdAsleep = "HOST_ASLEEP"
    val channelNameAsleep = context.getString(R.string.notification_asleep_name)
    private val channelDescriptionAsleep = context.getString(R.string.notification_asleep_description)

    private fun make(channelId: String, @DrawableRes icon: Int, contentTitle: String, contentText: String): Int {
        val builder = NotificationCompat.Builder(context, channelId).apply {
            setSmallIcon(icon)
            setContentTitle(contentTitle)
            setContentText(contentText)
            priority = NotificationCompat.PRIORITY_HIGH
            // setSound(alarmSound)
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
    fun makeAwokeNotification(title: String, content: String): Int {
        return make(channelIdAwoke, R.drawable.ic_host_awoke, title, content)
    }

    /**
     * Send a notification of host changed to asleep.
     * @return the notification id for [dismiss]
     */
    fun makeAsleepNotification(title: String, content: String): Int {
        return make(channelIdAsleep, R.drawable.ic_host_asleep, title, content)
    }

    /**
     * Dismiss a notification by it's ID.
     */
    fun dismiss(notificationId: Int) {
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