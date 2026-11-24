package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.util.buildMonthGridDays
import com.example.util.formatEventTimeRange
import com.example.util.formatJapaneseDate
import com.example.util.formatJapaneseMonth
import com.example.util.parseHexColor
import java.time.DayOfWeek
import java.time.LocalDate

val WidgetDateParamKey = ActionParameters.Key<String>("widget_date_key")

class FamilyCalendarWidget : GlanceAppWidget() {

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val snapshot = WidgetCacheStore.loadSnapshot(context)
    provideContent {
      GlanceTheme {
        FamilyCalendarWidgetContent(snapshot = snapshot)
      }
    }
  }
}

@Composable
private fun FamilyCalendarWidgetContent(snapshot: WidgetSnapshot) {
  val creamBg = ColorProvider(Color(0xFFFDF9F3))
  val cardSurface = ColorProvider(Color(0xFFFFFFFF))
  val sandSurface = ColorProvider(Color(0xFFF4EDE2))
  val charcoalText = ColorProvider(Color(0xFF2F2A26))
  val mutedText = ColorProvider(Color(0xFF7B7067))
  val terracotta = ColorProvider(Color(0xFFD97757))
  val dividerColor = ColorProvider(Color(0xFFE5DACB))
  val sundayColor = ColorProvider(Color(0xFFC85A54))
  val saturdayColor = ColorProvider(Color(0xFF4A7BB7))

  val displayedMonth = snapshot.displayedYearMonth()
  val today = LocalDate.now()
  val gridDays = buildMonthGridDays(displayedMonth)
  val eventsByDate = snapshot.events.groupBy { it.dateKey }
  val upcomingEvents = snapshot.upcomingClosestEvents(limit = 2)

  Column(
    modifier = GlanceModifier
      .fillMaxSize()
      .background(creamBg)
      .cornerRadius(18.dp)
      .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    // 1. Top Month & Family Header
    Row(
      modifier = GlanceModifier
        .fillMaxWidth()
        .padding(bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = GlanceModifier
          .defaultWeight()
          .clickable(actionStartActivity<MainActivity>()),
      ) {
        Text(
          text = snapshot.familyName.ifBlank { "家族カレンダー" },
          style = TextStyle(
            color = mutedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
          ),
          maxLines = 1,
        )
        Text(
          text = formatJapaneseMonth(displayedMonth),
          style = TextStyle(
            color = charcoalText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
          ),
          maxLines = 1,
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = GlanceModifier
            .background(sandSurface)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(actionRunCallback<PrevMonthWidgetAction>()),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "◀",
            style = TextStyle(color = charcoalText, fontSize = 11.sp, fontWeight = FontWeight.Bold),
          )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Box(
          modifier = GlanceModifier
            .background(sandSurface)
            .cornerRadius(8.dp)
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .clickable(actionRunCallback<TodayMonthWidgetAction>()),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "今月",
            style = TextStyle(color = charcoalText, fontSize = 10.sp, fontWeight = FontWeight.Medium),
          )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Box(
          modifier = GlanceModifier
            .background(sandSurface)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(actionRunCallback<NextMonthWidgetAction>()),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "▶",
            style = TextStyle(color = charcoalText, fontSize = 11.sp, fontWeight = FontWeight.Bold),
          )
        }
      }
    }

    // 2. Weekday Header Row
    val weekdays = listOf("日", "月", "火", "水", "木", "金", "土")
    Row(
      modifier = GlanceModifier
        .fillMaxWidth()
        .padding(bottom = 2.dp),
    ) {
      weekdays.forEachIndexed { index, label ->
        val dayColor = when (index) {
          0 -> sundayColor
          6 -> saturdayColor
          else -> mutedText
        }
        Text(
          text = label,
          modifier = GlanceModifier.defaultWeight(),
          style = TextStyle(
            color = dayColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
          ),
        )
      }
    }

    // 3. Large Monthly Calendar Grid (Top Dominant Section)
    Column(
      modifier = GlanceModifier
        .fillMaxWidth()
        .defaultWeight()
        .background(cardSurface)
        .cornerRadius(12.dp)
        .padding(3.dp),
    ) {
      gridDays.chunked(7).forEach { weekDays ->
        Row(
          modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight(),
        ) {
          weekDays.forEach { date ->
            if (date == null) {
              Spacer(modifier = GlanceModifier.defaultWeight().fillMaxHeight())
            } else {
              val dateKey = date.toString()
              val isToday = date == today
              val dayEvents = eventsByDate[dateKey].orEmpty()
              val dateNumberColor = when {
                isToday -> ColorProvider(Color.White)
                date.dayOfWeek == DayOfWeek.SUNDAY -> sundayColor
                date.dayOfWeek == DayOfWeek.SATURDAY -> saturdayColor
                else -> charcoalText
              }

              Column(
                modifier = GlanceModifier
                  .defaultWeight()
                  .fillMaxHeight()
                  .padding(1.dp)
                  .clickable(
                    actionStartActivity<MainActivity>(
                      actionParametersOf(WidgetDateParamKey to dateKey)
                    )
                  ),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(
                  modifier = GlanceModifier
                    .size(16.dp)
                    .then(
                      if (isToday) GlanceModifier.background(terracotta).cornerRadius(8.dp)
                      else GlanceModifier
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = date.dayOfMonth.toString(),
                    style = TextStyle(
                      color = dateNumberColor,
                      fontSize = 10.sp,
                      fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    ),
                  )
                }

                dayEvents.take(2).forEach { ev ->
                  val pillBg = ColorProvider(parseHexColor(ev.colorHex))
                  Box(
                    modifier = GlanceModifier
                      .fillMaxWidth()
                      .padding(top = 1.dp)
                      .background(pillBg)
                      .cornerRadius(3.dp)
                      .padding(horizontal = 2.dp),
                  ) {
                    Text(
                      text = ev.title,
                      style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                      ),
                      maxLines = 1,
                    )
                  }
                }
                if (dayEvents.size > 2) {
                  Text(
                    text = "+${dayEvents.size - 2}",
                    style = TextStyle(color = mutedText, fontSize = 7.sp),
                    maxLines = 1,
                  )
                }
              }
            }
          }
        }
      }
    }

    Spacer(modifier = GlanceModifier.height(6.dp))
    Box(
      modifier = GlanceModifier
        .fillMaxWidth()
        .height(1.dp)
        .background(dividerColor),
    ) {}
    Spacer(modifier = GlanceModifier.height(6.dp))

    // 4. Compact Bottom Section: Closest Upcoming Event(s)
    Column(
      modifier = GlanceModifier
        .fillMaxWidth()
        .background(sandSurface)
        .cornerRadius(12.dp)
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .clickable(actionStartActivity<MainActivity>()),
    ) {
      if (!snapshot.isSignedIn) {
        Text(
          text = "タップしてGoogleログイン・家族連携を開始",
          style = TextStyle(color = charcoalText, fontSize = 11.sp, fontWeight = FontWeight.Medium),
          maxLines = 1,
        )
      } else if (upcomingEvents.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = GlanceModifier
              .background(terracotta)
              .cornerRadius(6.dp)
              .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(
              text = "次の予定",
              style = TextStyle(color = ColorProvider(Color.White), fontSize = 9.sp, fontWeight = FontWeight.Bold),
            )
          }
          Spacer(modifier = GlanceModifier.width(6.dp))
          Text(
            text = "今後の予定はありません（タップして追加）",
            style = TextStyle(color = mutedText, fontSize = 11.sp),
            maxLines = 1,
          )
        }
      } else {
        val nextEvent = upcomingEvents.first()
        val parsedDate = runCatching { LocalDate.parse(nextEvent.dateKey) }.getOrNull()
        val dateLabel = when {
          parsedDate == today -> "今日"
          parsedDate == today.plusDays(1) -> "明日"
          parsedDate != null -> formatJapaneseDate(parsedDate)
          else -> nextEvent.dateKey
        }
        val timeLabel = formatEventTimeRange(nextEvent.isAllDay, nextEvent.startMinutes, nextEvent.endMinutes)
        val memberColor = ColorProvider(parseHexColor(nextEvent.colorHex))

        Row(
          modifier = GlanceModifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = GlanceModifier
              .background(terracotta)
              .cornerRadius(6.dp)
              .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(
              text = "次の予定",
              style = TextStyle(color = ColorProvider(Color.White), fontSize = 9.sp, fontWeight = FontWeight.Bold),
            )
          }
          Spacer(modifier = GlanceModifier.width(6.dp))
          Box(
            modifier = GlanceModifier
              .size(10.dp)
              .background(memberColor)
              .cornerRadius(5.dp),
          ) {}
          Spacer(modifier = GlanceModifier.width(5.dp))
          Text(
            text = "${nextEvent.memberName}・$dateLabel $timeLabel",
            style = TextStyle(color = mutedText, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
          )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
          text = "${nextEvent.avatarSymbol} ${nextEvent.title}",
          style = TextStyle(color = charcoalText, fontSize = 12.sp, fontWeight = FontWeight.Bold),
          maxLines = 1,
        )
      }
    }
  }
}

class PrevMonthWidgetAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    WidgetCacheStore.shiftDisplayedMonth(context, -1L)
  }
}

class NextMonthWidgetAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    WidgetCacheStore.shiftDisplayedMonth(context, 1L)
  }
}

class TodayMonthWidgetAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    WidgetCacheStore.resetDisplayedMonthToToday(context)
  }
}

class FamilyCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = FamilyCalendarWidget()
}
