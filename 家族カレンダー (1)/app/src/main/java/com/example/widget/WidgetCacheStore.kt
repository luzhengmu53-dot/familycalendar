package com.example.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import com.example.data.model.CalendarEvent
import com.example.data.model.FamilyMember
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

private val Context.widgetDataStore by preferencesDataStore(name = "family_calendar_widget_cache")

data class WidgetEventItem(
  val id: String,
  val title: String,
  val dateKey: String,
  val startMinutes: Long,
  val endMinutes: Long,
  val isAllDay: Boolean,
  val memberName: String,
  val colorHex: String,
  val avatarSymbol: String,
)

data class WidgetSnapshot(
  val isSignedIn: Boolean = false,
  val familyName: String = "家族カレンダー",
  val displayedYear: Int = LocalDate.now().year,
  val displayedMonth: Int = LocalDate.now().monthValue,
  val events: List<WidgetEventItem> = emptyList(),
) {
  fun displayedYearMonth(): YearMonth = YearMonth.of(displayedYear, displayedMonth)

  fun upcomingClosestEvents(limit: Int = 2): List<WidgetEventItem> {
    val today = LocalDate.now()
    val todayKey = today.toString()
    val nowMinutes = LocalTime.now().let { it.hour * 60L + it.minute }
    return events
      .filter { item ->
        when {
          item.dateKey > todayKey -> true
          item.dateKey == todayKey -> item.isAllDay || item.endMinutes >= nowMinutes
          else -> false
        }
      }
      .sortedWith(compareBy({ it.dateKey }, { !it.isAllDay }, { it.startMinutes }, { it.title }))
      .take(limit)
  }
}

object WidgetCacheStore {
  private val KEY_JSON = stringPreferencesKey("widget_snapshot_json")
  private val KEY_YEAR = intPreferencesKey("widget_displayed_year")
  private val KEY_MONTH = intPreferencesKey("widget_displayed_month")

  suspend fun saveFamilySnapshot(
    context: Context,
    isSignedIn: Boolean,
    familyName: String,
    events: List<CalendarEvent>,
    members: List<FamilyMember>,
  ) {
    val memberMap = members.associateBy { it.uid }
    val root = JSONObject().apply {
      put("isSignedIn", isSignedIn)
      put("familyName", familyName.ifBlank { "家族カレンダー" })
      val arr = JSONArray()
      events.forEach { ev ->
        val member = memberMap[ev.assignedMemberUid]
        val memberName = when {
          ev.assignedMemberUid == CalendarEvent.ASSIGNED_ALL -> "家族みんな"
          member != null -> member.displayName
          else -> "家族"
        }
        val colorHex = when {
          ev.assignedMemberUid == CalendarEvent.ASSIGNED_ALL -> "#D97757"
          member != null -> member.colorHex
          else -> "#6B9080"
        }
        val symbol = when {
          ev.assignedMemberUid == CalendarEvent.ASSIGNED_ALL -> "🏠"
          member != null -> member.avatarSymbol
          else -> "👤"
        }
        arr.put(
          JSONObject().apply {
            put("id", ev.id)
            put("title", ev.title)
            put("dateKey", ev.dateKey)
            put("startMinutes", ev.startMinutes)
            put("endMinutes", ev.endMinutes)
            put("isAllDay", ev.isAllDay)
            put("memberName", memberName)
            put("colorHex", colorHex)
            put("avatarSymbol", symbol)
          }
        )
      }
      put("events", arr)
    }

    val appCtx = context.applicationContext
    appCtx.widgetDataStore.edit { prefs ->
      prefs[KEY_JSON] = root.toString()
    }
    try {
      FamilyCalendarWidget().updateAll(appCtx)
    } catch (_: Exception) {
      // Widget might not be pinned yet or running under Robolectric
    }
  }

  suspend fun shiftDisplayedMonth(context: Context, deltaMonths: Long) {
    val appCtx = context.applicationContext
    val now = LocalDate.now()
    appCtx.widgetDataStore.edit { prefs ->
      val currentYear = prefs[KEY_YEAR] ?: now.year
      val currentMonth = prefs[KEY_MONTH] ?: now.monthValue
      val target = YearMonth.of(currentYear, currentMonth).plusMonths(deltaMonths)
      prefs[KEY_YEAR] = target.year
      prefs[KEY_MONTH] = target.monthValue
    }
    try {
      FamilyCalendarWidget().updateAll(appCtx)
    } catch (_: Exception) {
    }
  }

  suspend fun resetDisplayedMonthToToday(context: Context) {
    val appCtx = context.applicationContext
    val now = LocalDate.now()
    appCtx.widgetDataStore.edit { prefs ->
      prefs[KEY_YEAR] = now.year
      prefs[KEY_MONTH] = now.monthValue
    }
    try {
      FamilyCalendarWidget().updateAll(appCtx)
    } catch (_: Exception) {
    }
  }

  suspend fun loadSnapshot(context: Context): WidgetSnapshot {
    val appCtx = context.applicationContext
    val prefs = appCtx.widgetDataStore.data.first()
    val now = LocalDate.now()
    val year = prefs[KEY_YEAR] ?: now.year
    val month = prefs[KEY_MONTH] ?: now.monthValue
    val raw = prefs[KEY_JSON] ?: return WidgetSnapshot(
      isSignedIn = false,
      displayedYear = year,
      displayedMonth = month,
    )
    return try {
      val root = JSONObject(raw)
      val isSignedIn = root.optBoolean("isSignedIn", false)
      val familyName = root.optString("familyName", "家族カレンダー")
      val arr = root.optJSONArray("events") ?: JSONArray()
      val items = buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          add(
            WidgetEventItem(
              id = obj.optString("id"),
              title = obj.optString("title"),
              dateKey = obj.optString("dateKey"),
              startMinutes = obj.optLong("startMinutes", 540L),
              endMinutes = obj.optLong("endMinutes", 600L),
              isAllDay = obj.optBoolean("isAllDay", false),
              memberName = obj.optString("memberName", "家族"),
              colorHex = obj.optString("colorHex", "#D97757"),
              avatarSymbol = obj.optString("avatarSymbol", "🏠"),
            )
          )
        }
      }
      WidgetSnapshot(
        isSignedIn = isSignedIn,
        familyName = familyName,
        displayedYear = year,
        displayedMonth = month,
        events = items,
      )
    } catch (_: Exception) {
      WidgetSnapshot(displayedYear = year, displayedMonth = month)
    }
  }
}
