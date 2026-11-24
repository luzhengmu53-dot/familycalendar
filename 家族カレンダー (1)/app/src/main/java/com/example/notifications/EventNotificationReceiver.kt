package com.example.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class EventNotificationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "予定の時間です" }
    val calendarName = intent.getStringExtra(EXTRA_CALENDAR_NAME).orEmpty().ifBlank { "家族カレンダー" }
    val dateKey = intent.getStringExtra(EXTRA_DATE_KEY).orEmpty()
    val timeText = intent.getStringExtra(EXTRA_TIME_TEXT).orEmpty()
    val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
    val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
    val reminderMinutes = intent.getLongExtra(EXTRA_REMINDER_MINUTES, 0L)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    EventReminderScheduler.ensureNotificationChannel(context)

    val launchIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      if (dateKey.isNotBlank()) {
        putExtra("widget_date_key", dateKey)
      }
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context,
      eventId.hashCode(),
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val prefix = when {
      reminderMinutes > 0L -> "あと${reminderMinutes}分："
      else -> ""
    }
    val bodyParts = buildList {
      add(calendarName)
      if (timeText.isNotBlank()) add(timeText)
      if (location.isNotBlank()) add("📍$location")
    }
    val bodyText = bodyParts.joinToString("・")

    val notification = NotificationCompat.Builder(context, EventReminderScheduler.CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("$prefix$title")
      .setContentText(bodyText)
      .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_EVENT)
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)
      .build()

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    manager.notify(eventId.hashCode(), notification)
  }

  companion object {
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_CALENDAR_NAME = "extra_calendar_name"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_DATE_KEY = "extra_date_key"
    const val EXTRA_TIME_TEXT = "extra_time_text"
    const val EXTRA_LOCATION = "extra_location"
    const val EXTRA_REMINDER_MINUTES = "extra_reminder_minutes"
  }
}

class BootReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
      intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      EventReminderScheduler.rescheduleAllStoredReminders(context.applicationContext)
    }
  }
}
