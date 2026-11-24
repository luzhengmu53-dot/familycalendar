package com.example.dev

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.CalendarEvent
import com.example.data.model.FamilyGroup
import com.example.data.model.FamilyMember
import com.example.data.model.InvitePayload
import com.example.data.model.UserProfile
import com.example.notifications.EventReminderScheduler
import com.example.ui.screens.CalendarHomeScreen
import com.example.ui.screens.FamilyOnboardingScreen
import com.example.widget.WidgetCacheStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * 開発確認用の「Googleアカウント未連携モード」専用モジュール（テスト領域に退避済み・アプリ本体には未収録）。
 *
 * すぐに復活させる手順:
 * 1. 本ファイル (`DevNoGooglePreview.kt`) を `app/src/main/java/com/example/dev/DevNoGooglePreview.kt` へ戻す
 * 2. `AuthScreen.kt` に `onSkipGoogleAuth: (() -> Unit)? = null` 引数とボタンUIを追加する
 * 3. `MainActivity.kt` の `AppNavigation` に `isDevNoGoogleMode` 分岐と `DevPreviewAppRoot` 呼び出しを追加する
 */
object DevPreviewConfig {
  const val ENABLE_NO_GOOGLE_LINK = true
}

@Composable
fun DevPreviewAppRoot(
  initialDateKey: String? = null,
  onExitDevPreview: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val devUid = "dev_local_user"
  val devEmail = "Googleアカウント未連携（開発用モード）"

  var sharedProfile by remember {
    mutableStateOf(
      UserProfile(
        id = devUid,
        uid = devUid,
        email = devEmail,
        displayName = "開発ユーザー",
        colorHex = "#4285F4",
        avatarSymbol = "🏠",
        avatarImageBase64 = "",
        activeFamilyId = "",
      )
    )
  }

  val families = remember { mutableStateListOf<FamilyGroup>() }
  var activeFamilyId by remember { mutableStateOf<String?>(null) }
  val membersByFamily = remember { mutableStateMapOf<String, List<FamilyMember>>() }
  val eventsByFamily = remember { mutableStateMapOf<String, List<CalendarEvent>>() }

  var displayedMonth by remember {
    mutableStateOf(
      initialDateKey?.let { runCatching { YearMonth.from(LocalDate.parse(it)) }.getOrNull() }
        ?: YearMonth.now()
    )
  }
  var selectedDate by remember {
    mutableStateOf(
      initialDateKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()
    )
  }
  var selectedMemberFilter by remember { mutableStateOf<String?>(null) }
  var actionMessage by remember { mutableStateOf<String?>(null) }
  var scannedInvite by remember { mutableStateOf<InvitePayload?>(null) }

  val activeGroup = families.firstOrNull { it.id == activeFamilyId }
  val activeMembers = activeFamilyId?.let { membersByFamily[it] }.orEmpty()
  val activeEvents = activeFamilyId?.let { eventsByFamily[it] }.orEmpty()

  val updateSharedAccountEverywhere: (String, String, String, String) -> Unit = { displayName, colorHex, symbol, avatarImageBase64 ->
    val cleanName = displayName.trim().ifBlank { "開発ユーザー" }.take(40)
    val cleanSymbol = symbol.trim().ifBlank { cleanName.take(1) }.take(8)
    sharedProfile = sharedProfile.copy(
      displayName = cleanName,
      colorHex = colorHex,
      avatarSymbol = cleanSymbol,
      avatarImageBase64 = avatarImageBase64,
    )
    membersByFamily.keys.toList().forEach { fid ->
      membersByFamily[fid] = membersByFamily[fid].orEmpty().map { m ->
        if (m.uid == devUid) {
          m.copy(
            displayName = cleanName,
            colorHex = colorHex,
            avatarSymbol = cleanSymbol,
            avatarImageBase64 = avatarImageBase64,
          )
        } else m
      }
    }
    actionMessage = "全カレンダー共通のアカウント設定を保存しました"
  }

  val updateCalendarAppearance: (String, String, String, String, String) -> Unit = { fid, name, iconSymbol, iconColorHex, iconImageBase64 ->
    val idx = families.indexOfFirst { it.id == fid }
    if (idx >= 0) {
      val cleanName = name.trim().ifBlank { families[idx].name }.take(50)
      val cleanIcon = iconSymbol.trim().ifBlank { "📅" }.take(8)
      families[idx] = families[idx].copy(
        name = cleanName,
        iconSymbol = cleanIcon,
        colorHex = iconColorHex,
        iconImageBase64 = iconImageBase64,
      )
      actionMessage = "カレンダーのアイコンと名前を更新しました"
    }
  }

  LaunchedEffect(activeGroup, activeMembers, activeEvents) {
    val appContext = context.applicationContext
    if (activeGroup != null) {
      WidgetCacheStore.saveFamilySnapshot(
        context = appContext,
        isSignedIn = true,
        familyName = activeGroup.name,
        events = activeEvents,
        members = activeMembers,
      )
      EventReminderScheduler.syncRemindersForFamily(
        context = appContext,
        familyId = activeGroup.id,
        calendarName = activeGroup.name,
        events = activeEvents,
      )
    } else {
      WidgetCacheStore.saveFamilySnapshot(
        context = appContext,
        isSignedIn = false,
        familyName = "家族カレンダー",
        events = emptyList(),
        members = emptyList(),
      )
    }
  }

  if (activeGroup == null) {
    BackHandler {
      onExitDevPreview()
    }
    FamilyOnboardingScreen(
      userProfile = sharedProfile,
      userDisplayName = sharedProfile.displayName,
      userEmail = devEmail,
      currentUserId = devUid,
      myFamilies = families.toList(),
      isLoadingFamilies = false,
      pendingJoinRequest = null,
      lastSentInviteForMail = null,
      scannedInvite = scannedInvite,
      isBusy = false,
      actionMessage = actionMessage,
      onClearMessage = { actionMessage = null },
      onSelectFamily = { familyId ->
        selectedMemberFilter = null
        activeFamilyId = familyId
      },
      onCreateFamily = { familyName, iconSymbol, iconColorHex, iconImageBase64, calendarType ->
        val newId = "dev_family_${UUID.randomUUID().toString().take(8)}"
        val cleanFamilyName = familyName.trim().ifBlank { "マイ家族カレンダー" }.take(50)
        val cleanIcon = iconSymbol.trim().ifBlank { "📅" }.take(8)
        val cleanType = if (calendarType == FamilyGroup.TYPE_PERSONAL) {
          FamilyGroup.TYPE_PERSONAL
        } else {
          FamilyGroup.TYPE_SHARED
        }
        val newGroup = FamilyGroup(
          id = newId,
          name = cleanFamilyName,
          iconSymbol = cleanIcon,
          colorHex = iconColorHex,
          iconImageBase64 = iconImageBase64,
          calendarType = cleanType,
          ownerUid = devUid,
          ownerEmail = devEmail,
          inviteCode = "DEVPREVIEW",
          memberUids = listOf(devUid),
        )
        val ownerMember = FamilyMember(
          id = devUid,
          uid = devUid,
          familyId = newId,
          displayName = sharedProfile.displayName,
          email = devEmail,
          colorHex = sharedProfile.colorHex,
          avatarSymbol = sharedProfile.avatarSymbol,
          avatarImageBase64 = sharedProfile.avatarImageBase64,
          role = "owner",
          memberUids = listOf(devUid),
        )
        families.add(0, newGroup)
        membersByFamily[newId] = listOf(ownerMember)
        eventsByFamily[newId] = emptyList()
        selectedMemberFilter = null
        activeFamilyId = newId
        actionMessage = "新しいカレンダーを追加しました！"
      },
      onDeleteFamily = { fid ->
        families.removeAll { it.id == fid }
        membersByFamily.remove(fid)
        eventsByFamily.remove(fid)
        EventReminderScheduler.cancelFamilyReminders(context.applicationContext, fid)
        if (activeFamilyId == fid) {
          activeFamilyId = null
        }
        actionMessage = "カレンダーを削除しました"
      },
      onLeaveFamily = { fid ->
        families.removeAll { it.id == fid }
        membersByFamily.remove(fid)
        eventsByFamily.remove(fid)
        EventReminderScheduler.cancelFamilyReminders(context.applicationContext, fid)
        if (activeFamilyId == fid) {
          activeFamilyId = null
        }
        actionMessage = "共有カレンダーから退会しました"
      },
      onUpdateFamilyAppearance = updateCalendarAppearance,
      onUpdateGlobalAccount = updateSharedAccountEverywhere,
      onQrScanned = { rawValue ->
        val parsed = InvitePayload.parse(rawValue)
        if (parsed == null) {
          actionMessage = "有効な家族カレンダー招待QRコードではありません"
        } else {
          scannedInvite = parsed
        }
      },
      onDismissScannedInvite = { scannedInvite = null },
      onConfirmJoinInvite = { _, _ ->
        scannedInvite = null
        actionMessage = "他の家族との共有・承認リクエストはGoogleアカウントログイン後に利用できます"
      },
      onCancelPendingJoin = {},
      onSignOut = onExitDevPreview,
    )
  } else {
    CalendarHomeScreen(
      familyGroup = activeGroup,
      members = activeMembers,
      events = activeEvents,
      pendingRequests = emptyList(),
      currentUserId = devUid,
      displayedMonth = displayedMonth,
      selectedDate = selectedDate,
      selectedMemberFilter = selectedMemberFilter,
      isBusy = false,
      actionMessage = actionMessage,
      onClearMessage = { actionMessage = null },
      onSelectDate = { date ->
        selectedDate = date
        val ym = YearMonth.from(date)
        if (displayedMonth != ym) {
          displayedMonth = ym
        }
      },
      onShiftMonth = { deltaMonths ->
        displayedMonth = displayedMonth.plusMonths(deltaMonths)
      },
      onJumpToToday = {
        val today = LocalDate.now()
        displayedMonth = YearMonth.from(today)
        selectedDate = today
      },
      onSetMemberFilter = { uidOrNull ->
        selectedMemberFilter = uidOrNull
      },
      onSaveEvent = { eventId, title, description, location, date, startMinutes, endMinutes, isAllDay, assignedMemberUid, reminderMinutesBefore, onDone ->
        val cleanTitle = title.trim().take(100)
        if (cleanTitle.isEmpty()) {
          actionMessage = "タイトルを入力してください"
        } else {
          val safeStart = if (isAllDay) 0L else startMinutes.coerceIn(0L, 1439L)
          val safeEnd = if (isAllDay) 1439L else endMinutes.coerceIn(safeStart, 1439L)
          val safeReminder = if (reminderMinutesBefore < 0L) {
            CalendarEvent.REMINDER_OFF
          } else {
            reminderMinutesBefore.coerceIn(0L, 1440L)
          }
          val currentList = eventsByFamily[activeGroup.id].orEmpty().toMutableList()
          if (eventId.isNullOrBlank()) {
            val newEvent = CalendarEvent(
              id = "dev_event_${UUID.randomUUID().toString().take(8)}",
              familyId = activeGroup.id,
              title = cleanTitle,
              description = description.trim().take(500),
              location = location.trim().take(120),
              dateKey = date.toString(),
              startMinutes = safeStart,
              endMinutes = safeEnd,
              isAllDay = isAllDay,
              assignedMemberUid = assignedMemberUid.ifBlank { CalendarEvent.ASSIGNED_ALL },
              reminderMinutesBefore = safeReminder,
              createdByUid = devUid,
              memberUids = activeGroup.memberUids,
            )
            currentList.add(newEvent)
          } else {
            val idx = currentList.indexOfFirst { it.id == eventId }
            if (idx >= 0) {
              val old = currentList[idx]
              currentList[idx] = old.copy(
                title = cleanTitle,
                description = description.trim().take(500),
                location = location.trim().take(120),
                dateKey = date.toString(),
                startMinutes = safeStart,
                endMinutes = safeEnd,
                isAllDay = isAllDay,
                assignedMemberUid = assignedMemberUid.ifBlank { CalendarEvent.ASSIGNED_ALL },
                reminderMinutesBefore = safeReminder,
              )
            }
          }
          eventsByFamily[activeGroup.id] = currentList.sortedWith(
            compareBy({ it.dateKey }, { !it.isAllDay }, { it.startMinutes }, { it.title })
          )
          // 予定の追加・更新時は即時の通知・ポップアップを出さない（指定時刻のみ通知）
          onDone()
        }
      },
      onDeleteEvent = { eventId, onDone ->
        val currentList = eventsByFamily[activeGroup.id].orEmpty()
        eventsByFamily[activeGroup.id] = currentList.filterNot { it.id == eventId }
        EventReminderScheduler.cancelEventReminder(context.applicationContext, eventId)
        actionMessage = "予定を削除しました"
        onDone()
      },
      onUpdateMyProfile = updateSharedAccountEverywhere,
      onUpdateFamilyAppearance = { name, iconSymbol, iconColorHex, iconImageBase64 ->
        updateCalendarAppearance(activeGroup.id, name, iconSymbol, iconColorHex, iconImageBase64)
      },
      onApproveRequest = {},
      onRejectRequest = {},
      onReturnToHome = {
        selectedMemberFilter = null
        activeFamilyId = null
      },
      onDeleteOrLeaveFamily = {
        val fid = activeGroup.id
        val wasDelete = activeGroup.shouldDeleteInsteadOfLeave()
        families.removeAll { it.id == fid }
        membersByFamily.remove(fid)
        eventsByFamily.remove(fid)
        EventReminderScheduler.cancelFamilyReminders(context.applicationContext, fid)
        selectedMemberFilter = null
        activeFamilyId = null
        actionMessage = if (wasDelete) "カレンダーを削除しました" else "共有カレンダーから退会しました"
      },
      onSignOut = {
        scope.launch {
          WidgetCacheStore.saveFamilySnapshot(
            context = context.applicationContext,
            isSignedIn = false,
            familyName = "家族カレンダー",
            events = emptyList(),
            members = emptyList(),
          )
        }
        onExitDevPreview()
      },
    )
  }
}
