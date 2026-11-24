package com.example.util

import com.example.data.model.CalendarEvent
import com.example.data.model.FamilyGroup
import com.example.data.model.InvitePayload
import com.example.notifications.EventReminderScheduler
import com.example.widget.WidgetEventItem
import com.example.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarAndQrUtilsTest {

  @Test
  fun invitePayload_roundTrip_parsesCorrectly() {
    val payload = InvitePayload(
      familyId = "fam_abc123",
      inviteCode = "KAZO9876",
      familyName = "佐藤家の予定",
      ownerUid = "owner_xyz",
      ownerEmail = "owner@example.com",
    )
    val uri = payload.toQrUri()
    val parsed = InvitePayload.parse(uri)
    assertNotNull(parsed)
    assertEquals("fam_abc123", parsed!!.familyId)
    assertEquals("KAZO9876", parsed.inviteCode)
    assertEquals("佐藤家の予定", parsed.familyName)
    assertEquals("owner_xyz", parsed.ownerUid)
    assertEquals("owner@example.com", parsed.ownerEmail)
  }

  @Test
  fun invitePayload_invalidInput_returnsNull() {
    assertNull(InvitePayload.parse("https://example.com/not-kazocal"))
    assertNull(InvitePayload.parse(""))
  }

  @Test
  fun buildMonthGridDays_returns42CellsAlignedToSunday() {
    val cells = buildMonthGridDays(YearMonth.of(2026, 11))
    assertEquals(42, cells.size)
    // Nov 1, 2026 is Sunday -> index 0 is 2026-11-01
    assertEquals(LocalDate.of(2026, 11, 1), cells[0])
    assertEquals(LocalDate.of(2026, 11, 30), cells[29])
    assertNull(cells[30])
  }

  @Test
  fun widgetSnapshot_upcomingClosestEvents_picksNearestFutureEvents() {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1).toString()
    val tomorrow = today.plusDays(1).toString()
    val nextWeek = today.plusDays(7).toString()

    val snapshot = WidgetSnapshot(
      isSignedIn = true,
      familyName = "佐藤家",
      events = listOf(
        WidgetEventItem("past", "過去の予定", yesterday, 540L, 600L, false, "ママ", "#EA4335", "マ"),
        WidgetEventItem("later", "来週の旅行", nextWeek, 540L, 600L, true, "全員", "#D97757", "🏠"),
        WidgetEventItem("soon", "明日の歯医者", tomorrow, 600L, 660L, false, "パパ", "#4285F4", "パ"),
      ),
    )

    val upcoming = snapshot.upcomingClosestEvents(limit = 2)
    assertEquals(2, upcoming.size)
    assertEquals("soon", upcoming[0].id)
    assertEquals("later", upcoming[1].id)
  }

  @Test
  fun familyGroup_shouldDeleteInsteadOfLeave_matchesCalendarTypeAndMemberCount() {
    val personal = FamilyGroup(
      id = "p1",
      name = "個人用",
      calendarType = FamilyGroup.TYPE_PERSONAL,
      memberUids = listOf("u1"),
    )
    assertFalse(personal.isSharedCalendar())
    assertTrue(personal.shouldDeleteInsteadOfLeave())

    val soloShared = FamilyGroup(
      id = "s1",
      name = "自分だけの共有",
      calendarType = FamilyGroup.TYPE_SHARED,
      memberUids = listOf("u1"),
    )
    assertTrue(soloShared.isSharedCalendar())
    assertTrue(soloShared.shouldDeleteInsteadOfLeave())

    val multiShared = FamilyGroup(
      id = "s2",
      name = "家族の共有",
      calendarType = FamilyGroup.TYPE_SHARED,
      memberUids = listOf("u1", "u2"),
    )
    assertTrue(multiShared.isSharedCalendar())
    assertFalse(multiShared.shouldDeleteInsteadOfLeave())
  }

  @Test
  fun eventReminderScheduler_calculateTriggerMillis_handlesExactOffsetAllDayAndOff() {
    val zone = ZoneId.of("Asia/Tokyo")

    val off = EventReminderScheduler.calculateTriggerMillis(
      dateKey = "2026-11-21",
      startMinutes = 600L,
      isAllDay = false,
      reminderMinutesBefore = CalendarEvent.REMINDER_OFF,
      zoneId = zone,
    )
    assertNull(off)

    val atTime = EventReminderScheduler.calculateTriggerMillis(
      dateKey = "2026-11-21",
      startMinutes = 600L, // 10:00
      isAllDay = false,
      reminderMinutesBefore = 0L,
      zoneId = zone,
    )
    val fifteenBefore = EventReminderScheduler.calculateTriggerMillis(
      dateKey = "2026-11-21",
      startMinutes = 600L,
      isAllDay = false,
      reminderMinutesBefore = 15L,
      zoneId = zone,
    )
    assertNotNull(atTime)
    assertNotNull(fifteenBefore)
    assertEquals(15L * 60_000L, atTime!! - fifteenBefore!!)

    val allDayTrigger = EventReminderScheduler.calculateTriggerMillis(
      dateKey = "2026-11-21",
      startMinutes = 0L,
      isAllDay = true,
      reminderMinutesBefore = 0L,
      zoneId = zone,
    )
    val nineAmTrigger = EventReminderScheduler.calculateTriggerMillis(
      dateKey = "2026-11-21",
      startMinutes = 9L * 60L,
      isAllDay = false,
      reminderMinutesBefore = 0L,
      zoneId = zone,
    )
    assertEquals(nineAmTrigger, allDayTrigger)
    assertEquals("15分前", CalendarEvent.reminderLabel(15L))
    assertEquals("通知しない", CalendarEvent.reminderLabel(CalendarEvent.REMINDER_OFF))
  }
}
