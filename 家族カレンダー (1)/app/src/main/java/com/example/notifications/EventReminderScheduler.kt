package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.model.CalendarEvent
import com.example.util.formatEventTimeRange
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

object EventReminderScheduler {
  const val CHANNEL_ID = "family_calendar_reminders"
  private const val PREFS_NAME = "event_reminders_prefs"
  private const val KEY_STORED_REMINDERS = "stored_reminders_json"
  private const val ALL_DAY_REMINDER_MINUTES = 9 * 60L

  fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      val existing = manager.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          "予定のリマインダー通知",
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "カレンダーに登録された予定の時刻になったときにお知らせします"
          enableVibration(true)
        }
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun calculateTriggerMillis(
    dateKey: String,
    startMinutes: Long,
    isAllDay: Boolean,
    reminderMinutesBefore: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): Long? {
    if (reminderMinutesBefore < 0L) return null
    val parsedDate = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return null
    val baseMinutes = if (isAllDay) ALL_DAY_REMINDER_MINUTES else startMinutes.coerceIn(0L, 1439L)
    val hour = (baseMinutes / 60L).toInt()
    val minute = (baseMinutes % 60L).toInt()
    val eventDateTime = parsedDate.atTime(hour, minute)
    val triggerDateTime = eventDateTime.minusMinutes(reminderMinutesBefore)
    return triggerDateTime.atZone(zoneId).toInstant().toEpochMilli()
  }

  /**
   * Synchronizes AlarmManager alarms for events in the specified calendar.
   * Never fires immediate notifications for newly added events or past times.
   */
  fun syncRemindersForFamily(
    context: Context,
    familyId: String,
    calendarName: String,
    events: List<CalendarEvent>,
    nowMillis: Long = System.currentTimeMillis(),
  ) {
    if (familyId.isBlank()) return
    val appContext = context.applicationContext
    ensureNotificationChannel(appContext)

    val stored = loadStoredMap(appContext).toMutableMap()
    val previousForFamily = stored.filterValues { it.familyId == familyId }.keys.toList()
    val validUpcomingIds = mutableSetOf<String>()

    events.forEach { event ->
      val triggerAt = calculateTriggerMillis(
        dateKey = event.dateKey,
        startMinutes = event.startMinutes,
        isAllDay = event.isAllDay,
        reminderMinutesBefore = event.reminderMinutesBefore,
      )
      if (triggerAt != null && triggerAt > nowMillis) {
        validUpcomingIds.add(event.id)
        val record = StoredReminder(
          eventId = event.id,
          familyId = familyId,
          calendarName = calendarName,
          title = event.title,
          dateKey = event.dateKey,
          timeText = formatEventTimeRange(event.isAllDay, event.startMinutes, event.endMinutes),
          location = event.location,
          reminderMinutesBefore = event.reminderMinutesBefore,
          triggerAtMillis = triggerAt,
        )
        stored[event.id] = record
        scheduleAlarm(appContext, record)
      }
    }

    previousForFamily.forEach { oldEventId ->
      if (oldEventId !in validUpcomingIds) {
        cancelAlarm(appContext, oldEventId)
        stored.remove(oldEventId)
      }
    }

    saveStoredMap(appContext, stored)
  }

  fun cancelFamilyReminders(context: Context, familyId: String) {
    if (familyId.isBlank()) return
    val appContext = context.applicationContext
    val stored = loadStoredMap(appContext).toMutableMap()
    val toRemove = stored.filterValues { it.familyId == familyId }.keys.toList()
    toRemove.forEach { eventId ->
      cancelAlarm(appContext, eventId)
      stored.remove(eventId)
    }
    saveStoredMap(appContext, stored)
  }

  fun cancelEventReminder(context: Context, eventId: String) {
    if (eventId.isBlank()) return
    val appContext = context.applicationContext
    cancelAlarm(appContext, eventId)
    val stored = loadStoredMap(appContext).toMutableMap()
    if (stored.remove(eventId) != null) {
      saveStoredMap(appContext, stored)
    }
  }

  fun rescheduleAllStoredReminders(context: Context, nowMillis: Long = System.currentTimeMillis()) {
    val appContext = context.applicationContext
    ensureNotificationChannel(appContext)
    val stored = loadStoredMap(appContext).toMutableMap()
    val expired = mutableListOf<String>()
    stored.values.forEach { record ->
      if (record.triggerAtMillis > nowMillis) {
        scheduleAlarm(appContext, record)
      } else {
        expired.add(record.eventId)
      }
    }
    if (expired.isNotEmpty()) {
      expired.forEach { stored.remove(it) }
      saveStoredMap(appContext, stored)
    }
  }

  private fun scheduleAlarm(context: Context, record: StoredReminder) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pendingIntent = buildBroadcastPendingIntent(context, record)
    try {
      val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
      } else {
        true
      }
      if (canScheduleExact) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          record.triggerAtMillis,
          pendingIntent,
        )
      } else {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          record.triggerAtMillis,
          pendingIntent,
        )
      }
    } catch (e: SecurityException) {
      Log.w("EventReminderScheduler", "Fallback to inexact alarm", e)
      runCatching {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          record.triggerAtMillis,
          pendingIntent,
        )
      }
    } catch (e: Exception) {
      Log.w("EventReminderScheduler", "Failed to schedule reminder", e)
    }
  }

  private fun cancelAlarm(context: Context, eventId: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, EventNotificationReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      eventId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    runCatching { alarmManager.cancel(pendingIntent) }
  }

  private fun buildBroadcastPendingIntent(context: Context, record: StoredReminder): PendingIntent {
    val intent = Intent(context, EventNotificationReceiver::class.java).apply {
      putExtra(EventNotificationReceiver.EXTRA_EVENT_ID, record.eventId)
      putExtra(EventNotificationReceiver.EXTRA_CALENDAR_NAME, record.calendarName)
      putExtra(EventNotificationReceiver.EXTRA_TITLE, record.title)
      putExtra(EventNotificationReceiver.EXTRA_DATE_KEY, record.dateKey)
      putExtra(EventNotificationReceiver.EXTRA_TIME_TEXT, record.timeText)
      putExtra(EventNotificationReceiver.EXTRA_LOCATION, record.location)
      putExtra(EventNotificationReceiver.EXTRA_REMINDER_MINUTES, record.reminderMinutesBefore)
    }
    return PendingIntent.getBroadcast(
      context,
      record.eventId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private data class StoredReminder(
    val eventId: String,
    val familyId: String,
    val calendarName: String,
    val title: String,
    val dateKey: String,
    val timeText: String,
    val location: String,
    val reminderMinutesBefore: Long,
    val triggerAtMillis: Long,
  )

  private fun loadStoredMap(context: Context): Map<String, StoredReminder> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_STORED_REMINDERS, null)
      ?: return emptyMap()
    return runCatching {
      val arr = JSONArray(raw)
      val map = mutableMapOf<String, StoredReminder>()
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val record = StoredReminder(
          eventId = obj.optString("eventId"),
          familyId = obj.optString("familyId"),
          calendarName = obj.optString("calendarName"),
          title = obj.optString("title"),
          dateKey = obj.optString("dateKey"),
          timeText = obj.optString("timeText"),
          location = obj.optString("location"),
          reminderMinutesBefore = obj.optLong("reminderMinutesBefore", 0L),
          triggerAtMillis = obj.optLong("triggerAtMillis", 0L),
        )
        if (record.eventId.isNotBlank()) {
          map[record.eventId] = record
        }
      }
      map.toMap()
    }.getOrDefault(emptyMap())
  }

  private fun saveStoredMap(context: Context, map: Map<String, StoredReminder>) {
    val arr = JSONArray()
    map.values.forEach { rec ->
      arr.put(
        JSONObject().apply {
          put("eventId", rec.eventId)
          put("familyId", rec.familyId)
          put("calendarName", rec.calendarName)
          put("title", rec.title)
          put("dateKey", rec.dateKey)
          put("timeText", rec.timeText)
          put("location", rec.location)
          put("reminderMinutesBefore", rec.reminderMinutesBefore)
          put("triggerAtMillis", rec.triggerAtMillis)
        }
      )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_STORED_REMINDERS, arr.toString())
      .apply()
  }
}
