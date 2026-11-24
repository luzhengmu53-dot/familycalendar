package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.CalendarEvent
import com.example.data.model.FamilyGroup
import com.example.data.model.FamilyMember
import com.example.data.model.JoinRequest
import com.example.ui.components.MemberAvatarBadge
import com.example.util.buildMonthGridDays
import com.example.util.formatEventTimeRange
import com.example.util.formatJapaneseDate
import com.example.util.formatJapaneseMonth
import com.example.util.parseHexColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHomeScreen(
  familyGroup: FamilyGroup,
  members: List<FamilyMember>,
  events: List<CalendarEvent>,
  pendingRequests: List<JoinRequest>,
  currentUserId: String,
  displayedMonth: YearMonth,
  selectedDate: LocalDate,
  selectedMemberFilter: String?,
  isBusy: Boolean,
  actionMessage: String?,
  onClearMessage: () -> Unit,
  onSelectDate: (LocalDate) -> Unit,
  onShiftMonth: (Long) -> Unit,
  onJumpToToday: () -> Unit,
  onSetMemberFilter: (String?) -> Unit,
  onSaveEvent: (
    eventId: String?,
    title: String,
    description: String,
    location: String,
    date: LocalDate,
    startMinutes: Long,
    endMinutes: Long,
    isAllDay: Boolean,
    assignedMemberUid: String,
    reminderMinutesBefore: Long,
    onDone: () -> Unit,
  ) -> Unit,
  onDeleteEvent: (eventId: String, onDone: () -> Unit) -> Unit,
  onUpdateMyProfile: (displayName: String, colorHex: String, symbol: String, avatarImageBase64: String) -> Unit,
  onUpdateFamilyAppearance: (familyName: String, iconSymbol: String, iconColorHex: String, iconImageBase64: String) -> Unit = { _, _, _, _ -> },
  onApproveRequest: (JoinRequest) -> Unit,
  onRejectRequest: (JoinRequest) -> Unit,
  onReturnToHome: () -> Unit,
  onDeleteOrLeaveFamily: () -> Unit = {},
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  var showEventSheet by remember { mutableStateOf(false) }
  var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
  var showMembersSheet by remember { mutableStateOf(false) }
  var membersSheetInitialTab by remember { mutableIntStateOf(0) }
  var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = {},
  )

  LaunchedEffect(Unit) {
    if (!askedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        askedNotificationPermission = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  BackHandler(enabled = !showEventSheet && !showMembersSheet) {
    onReturnToHome()
  }

  LaunchedEffect(actionMessage) {
    val msg = actionMessage
    if (!msg.isNullOrBlank()) {
      onClearMessage()
      snackbarHostState.showSnackbar(msg)
    }
  }

  val memberMap = remember(members) { members.associateBy { it.uid } }
  val myMember = memberMap[currentUserId]

  val filteredEvents = remember(events, selectedMemberFilter) {
    if (selectedMemberFilter == null) {
      events
    } else {
      events.filter { it.assignedMemberUid == selectedMemberFilter }
    }
  }

  val eventsByDate = remember(filteredEvents) {
    filteredEvents.groupBy { it.dateKey }
  }

  val selectedDateKey = selectedDate.toString()
  val selectedDayEvents = eventsByDate[selectedDateKey].orEmpty()

  val upcomingEvents = remember(filteredEvents) {
    val today = LocalDate.now()
    val todayKey = today.toString()
    val nowMinutes = LocalTime.now().let { it.hour * 60L + it.minute }
    filteredEvents.filter { ev ->
      when {
        ev.dateKey > todayKey -> true
        ev.dateKey == todayKey -> ev.isAllDay || ev.endMinutes >= nowMinutes
        else -> false
      }
    }.take(3)
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
        navigationIcon = {
          IconButton(
            onClick = onReturnToHome,
            modifier = Modifier.testTag("back_to_home_selector_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "ホーム画面に戻る",
            )
          }
        },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatarBadge(
              symbol = familyGroup.iconSymbol.ifBlank { "📅" },
              colorHex = familyGroup.colorHex.ifBlank { "#D97757" },
              avatarImageBase64 = familyGroup.iconImageBase64,
              size = 34.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = familyGroup.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = if (familyGroup.isSharedCalendar()) {
                  "共有カレンダー・${members.size}人で共有中"
                } else {
                  "自分用カレンダー"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        actions = {
          if (familyGroup.isSharedCalendar()) {
            IconButton(
              onClick = {
                membersSheetInitialTab = 1
                showMembersSheet = true
              },
              modifier = Modifier.testTag("open_qr_invite_button"),
            ) {
              BadgedBox(
                badge = {
                  if (pendingRequests.isNotEmpty()) {
                    Badge { Text(pendingRequests.size.toString()) }
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.Filled.QrCode2,
                  contentDescription = "招待QRコードと承認管理",
                )
              }
            }
          }

          IconButton(
            onClick = {
              membersSheetInitialTab = 0
              showMembersSheet = true
            },
            modifier = Modifier.testTag("open_member_customization_button"),
          ) {
            if (myMember != null) {
              MemberAvatarBadge(
                symbol = myMember.avatarSymbol,
                colorHex = myMember.colorHex,
                avatarImageBase64 = myMember.avatarImageBase64,
                size = 32.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Filled.Palette,
                contentDescription = "アイコン色設定",
              )
            }
          }

          IconButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Logout,
              contentDescription = "ログアウト",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingEvent = null
          showEventSheet = true
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("add_event_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "予定を追加")
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 840.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // 1. Real-time Owner Pending Approval Banner
        if (pendingRequests.isNotEmpty()) {
          item(key = "pending_banner") {
            AnimatedVisibility(visible = pendingRequests.isNotEmpty()) {
              Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  val req = pendingRequests.first()
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.NotificationsActive,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "${req.requesterName}（${req.requesterEmail}）が参加承認を待っています",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.weight(1f),
                    )
                  }
                  Spacer(modifier = Modifier.height(10.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                      onClick = { onApproveRequest(req) },
                      enabled = !isBusy,
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("banner_approve_button"),
                    ) {
                      Text("承認する")
                    }
                    OutlinedButton(
                      onClick = { onRejectRequest(req) },
                      enabled = !isBusy,
                      modifier = Modifier.weight(1f),
                    ) {
                      Text("見送る")
                    }
                  }
                }
              }
            }
          }
        }

        // 2. Month Header + Member Filter Chips
        item(key = "month_controls") {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = { onShiftMonth(-1L) },
                  modifier = Modifier.testTag("prev_month_button"),
                ) {
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の月")
                }
                Text(
                  text = formatJapaneseMonth(displayedMonth),
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                IconButton(
                  onClick = { onShiftMonth(1L) },
                  modifier = Modifier.testTag("next_month_button"),
                ) {
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の月")
                }
              }

              OutlinedButton(
                onClick = {
                  onSetMemberFilter(null)
                  onJumpToToday()
                },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("today_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Today,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("今日")
              }
            }

            // Member Filter Chips (All / Family / Individual members)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              MemberFilterPill(
                label = "すべて",
                colorHex = null,
                symbol = null,
                selected = selectedMemberFilter == null,
                onClick = { onSetMemberFilter(null) },
              )
              MemberFilterPill(
                label = "家族みんな",
                colorHex = "#D97757",
                symbol = "🏠",
                selected = selectedMemberFilter == CalendarEvent.ASSIGNED_ALL,
                onClick = {
                  onSetMemberFilter(
                    if (selectedMemberFilter == CalendarEvent.ASSIGNED_ALL) null
                    else CalendarEvent.ASSIGNED_ALL
                  )
                },
              )
              members.forEach { member ->
                MemberFilterPill(
                  label = member.displayName,
                  colorHex = member.colorHex,
                  symbol = member.avatarSymbol,
                  avatarImageBase64 = member.avatarImageBase64,
                  selected = selectedMemberFilter == member.uid,
                  onClick = {
                    onSetMemberFilter(
                      if (selectedMemberFilter == member.uid) null else member.uid
                    )
                  },
                )
              }
            }
          }
        }

        // 3. Large Monthly Calendar Grid Card
        item(key = "calendar_grid") {
          MonthlyCalendarCard(
            displayedMonth = displayedMonth,
            selectedDate = selectedDate,
            eventsByDate = eventsByDate,
            memberMap = memberMap,
            onSelectDate = onSelectDate,
          )
        }

        // 4. Selected Date Schedule Header & Events
        item(key = "selected_day_header") {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${formatJapaneseDate(selectedDate)}の予定",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(
              onClick = {
                editingEvent = null
                showEventSheet = true
              },
              modifier = Modifier.testTag("add_day_event_button"),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("予定を追加")
            }
          }
        }

        if (selectedDayEvents.isEmpty()) {
          item(key = "selected_day_empty") {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    editingEvent = null
                    showEventSheet = true
                  }
                  .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Filled.EventAvailable,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "この日の予定はまだありません",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text = "タップして家族の予定を登録できます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        } else {
          items(selectedDayEvents, key = { it.id }) { event ->
            EventItemCard(
              event = event,
              member = memberMap[event.assignedMemberUid],
              onClick = {
                editingEvent = event
                showEventSheet = true
              },
            )
          }
        }

        // 5. Closest Upcoming Schedule Preview (mirrors the Home Widget bottom strip)
        item(key = "upcoming_section") {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "これからの一番近い予定",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                TextButton(
                  onClick = {
                    membersSheetInitialTab = 2
                    showMembersSheet = true
                  },
                ) {
                  Text("ウィジェット設定")
                }
              }

              if (upcomingEvents.isEmpty()) {
                Text(
                  text = "今後の予定は登録されていません。",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                Spacer(modifier = Modifier.height(6.dp))
                upcomingEvents.forEach { ev ->
                  val member = memberMap[ev.assignedMemberUid]
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
                  val parsedDate = runCatching { LocalDate.parse(ev.dateKey) }.getOrNull()
                  val dateText = parsedDate?.let { formatJapaneseDate(it) } ?: ev.dateKey
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .clickable {
                        parsedDate?.let(onSelectDate)
                        editingEvent = ev
                        showEventSheet = true
                      }
                      .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    MemberAvatarBadge(
                      symbol = symbol,
                      colorHex = colorHex,
                      avatarImageBase64 = member?.avatarImageBase64.orEmpty(),
                      size = 30.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = ev.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "$dateText ${formatEventTimeRange(ev.isAllDay, ev.startMinutes, ev.endMinutes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showEventSheet) {
    EventEditorSheet(
      initialDate = selectedDate,
      editingEvent = editingEvent,
      members = members,
      currentUserId = currentUserId,
      isBusy = isBusy,
      onSave = { eventId, title, desc, loc, date, start, end, allDay, assignedUid, reminderMinutesBefore ->
        onSaveEvent(eventId, title, desc, loc, date, start, end, allDay, assignedUid, reminderMinutesBefore) {
          showEventSheet = false
          editingEvent = null
        }
      },
      onDelete = { id ->
        onDeleteEvent(id) {
          showEventSheet = false
          editingEvent = null
        }
      },
      onDismiss = {
        showEventSheet = false
        editingEvent = null
      },
    )
  }

  if (showMembersSheet) {
    FamilyMembersAndQrSheet(
      initialTab = membersSheetInitialTab,
      familyGroup = familyGroup,
      members = members,
      pendingRequests = pendingRequests,
      currentUserId = currentUserId,
      isBusy = isBusy,
      onUpdateMyProfile = onUpdateMyProfile,
      onUpdateFamilyAppearance = onUpdateFamilyAppearance,
      onApproveRequest = onApproveRequest,
      onRejectRequest = onRejectRequest,
      onDeleteOrLeaveFamily = onDeleteOrLeaveFamily,
      onDismiss = { showMembersSheet = false },
    )
  }
}

@Composable
private fun MemberFilterPill(
  label: String,
  colorHex: String?,
  symbol: String?,
  avatarImageBase64: String = "",
  selected: Boolean,
  onClick: () -> Unit,
) {
  val accent = colorHex?.let { parseHexColor(it) } ?: MaterialTheme.colorScheme.primary
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(20.dp))
      .background(if (selected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
      .border(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.dp),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (colorHex != null && symbol != null) {
      MemberAvatarBadge(
        symbol = symbol,
        colorHex = colorHex,
        avatarImageBase64 = avatarImageBase64,
        size = 20.dp,
      )
      Spacer(modifier = Modifier.width(6.dp))
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
    )
  }
}

@Composable
private fun MonthlyCalendarCard(
  displayedMonth: YearMonth,
  selectedDate: LocalDate,
  eventsByDate: Map<String, List<CalendarEvent>>,
  memberMap: Map<String, FamilyMember>,
  onSelectDate: (LocalDate) -> Unit,
) {
  val today = LocalDate.now()
  val gridDays = remember(displayedMonth) { buildMonthGridDays(displayedMonth) }
  val weekdays = listOf("日", "月", "火", "水", "木", "金", "土")

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
      Row(modifier = Modifier.fillMaxWidth()) {
        weekdays.forEachIndexed { idx, label ->
          val textColor = when (idx) {
            0 -> Color(0xFFC85A54)
            6 -> Color(0xFF4A7BB7)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          }
          Text(
            text = label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      gridDays.chunked(7).forEach { weekDays ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        ) {
          weekDays.forEach { date ->
            if (date == null) {
              Spacer(
                modifier = Modifier
                  .weight(1f)
                  .height(62.dp),
              )
            } else {
              val isToday = date == today
              val isSelected = date == selectedDate
              val dayEvents = eventsByDate[date.toString()].orEmpty()

              val numberColor = when {
                isToday -> Color.White
                date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFC85A54)
                date.dayOfWeek == DayOfWeek.SATURDAY -> Color(0xFF4A7BB7)
                else -> MaterialTheme.colorScheme.onSurface
              }

              Column(
                modifier = Modifier
                  .weight(1f)
                  .height(64.dp)
                  .padding(1.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else Color.Transparent
                  )
                  .then(
                    if (isSelected) {
                      Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                      )
                    } else Modifier
                  )
                  .clickable { onSelectDate(date) }
                  .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(
                  modifier = Modifier
                    .size(20.dp)
                    .then(
                      if (isToday) {
                        Modifier
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.primary)
                      } else Modifier
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = 11.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = numberColor,
                  )
                }

                Spacer(modifier = Modifier.height(2.dp))

                dayEvents.take(2).forEach { ev ->
                  val colorHex = when {
                    ev.assignedMemberUid == CalendarEvent.ASSIGNED_ALL -> "#D97757"
                    memberMap[ev.assignedMemberUid] != null -> memberMap[ev.assignedMemberUid]!!.colorHex
                    else -> "#6B9080"
                  }
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(bottom = 1.5.dp)
                      .clip(RoundedCornerShape(4.dp))
                      .background(parseHexColor(colorHex))
                      .padding(horizontal = 3.dp, vertical = 1.dp),
                  ) {
                    Text(
                      text = ev.title,
                      color = Color.White,
                      fontSize = 8.sp,
                      lineHeight = 10.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Clip,
                    )
                  }
                }
                if (dayEvents.size > 2) {
                  Text(
                    text = "+${dayEvents.size - 2}",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 9.sp,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EventItemCard(
  event: CalendarEvent,
  member: FamilyMember?,
  onClick: () -> Unit,
) {
  val colorHex = when {
    event.assignedMemberUid == CalendarEvent.ASSIGNED_ALL -> "#D97757"
    member != null -> member.colorHex
    else -> "#6B9080"
  }
  val symbol = when {
    event.assignedMemberUid == CalendarEvent.ASSIGNED_ALL -> "🏠"
    member != null -> member.avatarSymbol
    else -> "👤"
  }
  val memberLabel = when {
    event.assignedMemberUid == CalendarEvent.ASSIGNED_ALL -> "家族みんな"
    member != null -> member.displayName
    else -> "家族"
  }
  val accentColor = parseHexColor(colorHex)

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .width(5.dp)
          .height(44.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(accentColor),
      )
      Spacer(modifier = Modifier.width(12.dp))
      MemberAvatarBadge(
        symbol = symbol,
        colorHex = colorHex,
        avatarImageBase64 = member?.avatarImageBase64.orEmpty(),
        size = 36.dp,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = formatEventTimeRange(event.isAllDay, event.startMinutes, event.endMinutes),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "・$memberLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (event.reminderMinutesBefore >= 0L) {
            Text(
              text = "・通知:${CalendarEvent.reminderLabel(event.reminderMinutesBefore)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Text(
          text = event.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (event.location.isNotBlank()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = event.location,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (event.description.isNotBlank()) {
          Text(
            text = event.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
