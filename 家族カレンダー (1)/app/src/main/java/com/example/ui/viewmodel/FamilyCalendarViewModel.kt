package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CalendarEvent
import com.example.data.model.FamilyGroup
import com.example.data.model.FamilyMember
import com.example.data.model.InvitePayload
import com.example.data.model.JoinRequest
import com.example.data.model.UserProfile
import com.example.data.repository.FamilyCalendarRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyCalendarViewModel(
  private val repository: FamilyCalendarRepository,
  val currentUserId: String,
  val userEmail: String,
  val userDisplayName: String,
  val userPhotoUrl: String?,
  initialDateKey: String? = null,
  private val onWidgetSync: suspend (
    isSignedIn: Boolean,
    familyName: String,
    events: List<CalendarEvent>,
    members: List<FamilyMember>,
  ) -> Unit = { _, _, _, _ -> },
  private val onReminderSync: (
    familyId: String,
    familyName: String,
    events: List<CalendarEvent>,
  ) -> Unit = { _, _, _ -> },
  private val onCancelFamilyReminders: (familyId: String) -> Unit = {},
) : ViewModel() {

  private val _displayedMonth = MutableStateFlow(
    initialDateKey?.let { runCatching { YearMonth.from(LocalDate.parse(it)) }.getOrNull() }
      ?: YearMonth.now()
  )
  val displayedMonth: StateFlow<YearMonth> = _displayedMonth.asStateFlow()

  private val _selectedDate = MutableStateFlow(
    initialDateKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
      ?: LocalDate.now()
  )
  val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

  private val _selectedMemberFilter = MutableStateFlow<String?>(null)
  val selectedMemberFilter: StateFlow<String?> = _selectedMemberFilter.asStateFlow()

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _scannedInvite = MutableStateFlow<InvitePayload?>(null)
  val scannedInvite: StateFlow<InvitePayload?> = _scannedInvite.asStateFlow()

  private val _lastSentInviteForMail = MutableStateFlow<InvitePayload?>(null)
  val lastSentInviteForMail: StateFlow<InvitePayload?> = _lastSentInviteForMail.asStateFlow()

  private val _myFamilies = MutableStateFlow<List<FamilyGroup>>(emptyList())
  val myFamilies: StateFlow<List<FamilyGroup>> = _myFamilies.asStateFlow()

  private val _isLoadingFamilies = MutableStateFlow(false)
  val isLoadingFamilies: StateFlow<Boolean> = _isLoadingFamilies.asStateFlow()

  private fun unwrapFirestoreMessage(error: Throwable, fallback: String): String {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    return (firestoreError ?: error).message ?: fallback
  }

  val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
    .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      Log.w(TAG, "Error observing user profile", firestoreError ?: error)
      emit(UiState.Error(unwrapFirestoreMessage(error, "ユーザー情報の取得に失敗しました")))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading,
    )

  private val activeFamilyIdFlow = userProfileState
    .map { state -> (state as? UiState.Success)?.data?.activeFamilyId?.takeIf { it.isNotBlank() } }
    .distinctUntilChanged()

  private val pendingFamilyIdFlow = userProfileState
    .map { state -> (state as? UiState.Success)?.data?.pendingFamilyId?.takeIf { it.isNotBlank() } }
    .distinctUntilChanged()

  val familyGroup: StateFlow<FamilyGroup?> = activeFamilyIdFlow
    .flatMapLatest { familyId ->
      if (familyId == null) flowOf(null)
      else repository.observeFamilyGroup(familyId).catch { e ->
        if (e is CancellationException && generateSequence<Throwable>(e) { it.cause }.none { it is FirebaseFirestoreException }) throw e
        Log.w(TAG, "Error observing family group", e)
        emit(null)
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = null,
    )

  val members: StateFlow<List<FamilyMember>> = activeFamilyIdFlow
    .flatMapLatest { familyId ->
      if (familyId == null) flowOf(emptyList())
      else repository.observeMembers(familyId).catch { e ->
        if (e is CancellationException && generateSequence<Throwable>(e) { it.cause }.none { it is FirebaseFirestoreException }) throw e
        Log.w(TAG, "Error observing members", e)
        emit(emptyList())
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = emptyList(),
    )

  val events: StateFlow<List<CalendarEvent>> = activeFamilyIdFlow
    .flatMapLatest { familyId ->
      if (familyId == null) flowOf(emptyList())
      else repository.observeEvents(familyId).catch { e ->
        if (e is CancellationException && generateSequence<Throwable>(e) { it.cause }.none { it is FirebaseFirestoreException }) throw e
        Log.w(TAG, "Error observing events", e)
        emit(emptyList())
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = emptyList(),
    )

  val pendingRequests: StateFlow<List<JoinRequest>> = combine(activeFamilyIdFlow, familyGroup) { fid, group ->
    if (fid != null && group?.ownerUid == currentUserId) fid else null
  }.distinctUntilChanged()
    .flatMapLatest { ownerFamilyId ->
      if (ownerFamilyId == null) flowOf(emptyList())
      else repository.observePendingJoinRequests(ownerFamilyId).catch { e ->
        if (e is CancellationException && generateSequence<Throwable>(e) { it.cause }.none { it is FirebaseFirestoreException }) throw e
        Log.w(TAG, "Error observing pending join requests", e)
        emit(emptyList())
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = emptyList(),
    )

  val myPendingJoinRequest: StateFlow<JoinRequest?> = pendingFamilyIdFlow
    .flatMapLatest { pendingFid ->
      if (pendingFid == null) flowOf(null)
      else repository.observeMyJoinRequest(pendingFid, currentUserId).catch { e ->
        if (e is CancellationException && generateSequence<Throwable>(e) { it.cause }.none { it is FirebaseFirestoreException }) throw e
        Log.w(TAG, "Error observing my join request", e)
        emit(null)
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = null,
    )

  init {
    viewModelScope.launch {
      repository.ensureUserProfile(currentUserId, userEmail, userDisplayName, userPhotoUrl)
        .onFailure { _actionMessage.value = it.localizedMessage ?: "プロフィールの初期化に失敗しました" }
    }

    // Automatically activate family once the owner approves the pending join request
    viewModelScope.launch {
      myPendingJoinRequest.collect { req ->
        if (req != null && req.status == "approved") {
          repository.activateJoinedFamily(req.familyId)
            .onSuccess {
              _lastSentInviteForMail.value = null
              _actionMessage.value = "家族カレンダーへの参加が承認されました！"
            }
        }
      }
    }

    // Keep Home screen calendar list updated whenever user has no active calendar open
    viewModelScope.launch {
      activeFamilyIdFlow.collect { activeId ->
        if (activeId == null) {
          refreshMyFamilies()
        }
      }
    }

    // Keep Home Screen Glance Widget & scheduled event notifications synchronized
    viewModelScope.launch {
      combine(familyGroup, members, events) { group, memberList, eventList ->
        Triple(group, memberList, eventList)
      }.collect { (group, memberList, eventList) ->
        if (group != null) {
          onWidgetSync(true, group.name, eventList, memberList)
          onReminderSync(group.id, group.name, eventList)
        } else {
          onWidgetSync(false, "家族カレンダー", emptyList(), emptyList())
        }
      }
    }
  }

  fun selectDate(date: LocalDate) {
    _selectedDate.value = date
    val ym = YearMonth.from(date)
    if (_displayedMonth.value != ym) {
      _displayedMonth.value = ym
    }
  }

  fun shiftMonth(deltaMonths: Long) {
    _displayedMonth.value = _displayedMonth.value.plusMonths(deltaMonths)
  }

  fun jumpToToday() {
    val today = LocalDate.now()
    _displayedMonth.value = YearMonth.from(today)
    _selectedDate.value = today
  }

  fun setMemberFilter(uidOrNull: String?) {
    _selectedMemberFilter.value = uidOrNull
  }

  fun clearMessage() {
    _actionMessage.value = null
  }

  fun onQrCodeScanned(rawValue: String) {
    val parsed = InvitePayload.parse(rawValue)
    if (parsed == null) {
      _actionMessage.value = "有効な家族カレンダー招待QRコードではありません"
    } else if (parsed.ownerUid == currentUserId) {
      _actionMessage.value = "ご自身が作成した家族のQRコードです"
    } else {
      _scannedInvite.value = parsed
    }
  }

  fun dismissScannedInvite() {
    _scannedInvite.value = null
  }

  fun createFamily(
    familyName: String,
    iconSymbol: String = "📅",
    iconColorHex: String = "#D97757",
    iconImageBase64: String = "",
    calendarType: String = FamilyGroup.TYPE_SHARED,
  ) {
    val profile = (userProfileState.value as? UiState.Success)?.data
    val ownerName = profile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName
    val accountColor = profile?.colorHex?.ifBlank { "#4285F4" } ?: "#4285F4"
    val accountSymbol = profile?.avatarSymbol?.ifBlank { "🏠" } ?: "🏠"
    val accountImage = profile?.avatarImageBase64.orEmpty()

    viewModelScope.launch {
      _isBusy.value = true
      repository.createFamily(
        familyName = familyName,
        ownerDisplayName = ownerName,
        ownerEmail = userEmail,
        colorHex = accountColor,
        avatarSymbol = accountSymbol,
        avatarImageBase64 = accountImage,
        iconSymbol = iconSymbol,
        iconColorHex = iconColorHex,
        iconImageBase64 = iconImageBase64,
        calendarType = calendarType,
      ).onSuccess {
        refreshMyFamilies()
        _actionMessage.value = "新しいカレンダーを追加しました！"
      }.onFailure { e ->
        _actionMessage.value = e.localizedMessage ?: "カレンダーの作成に失敗しました"
      }
      _isBusy.value = false
    }
  }

  fun updateFamilyAppearance(
    familyId: String,
    familyName: String,
    iconSymbol: String,
    iconColorHex: String,
    iconImageBase64: String = "",
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateFamilyDetails(
        familyId = familyId,
        newName = familyName,
        iconSymbol = iconSymbol,
        colorHex = iconColorHex,
        iconImageBase64 = iconImageBase64,
      ).onSuccess {
        refreshMyFamilies()
        _actionMessage.value = "カレンダーのアイコンと名前を更新しました"
      }.onFailure { e ->
        _actionMessage.value = e.localizedMessage ?: "カレンダー設定の更新に失敗しました"
      }
      _isBusy.value = false
    }
  }

  fun submitJoinRequest(invite: InvitePayload, memberName: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.submitJoinRequest(
        requesterName = memberName,
        requesterEmail = userEmail,
        invite = invite,
      ).onSuccess {
        _scannedInvite.value = null
        _lastSentInviteForMail.value = invite
        _actionMessage.value = "参加リクエストを送信しました。作成者の承認をお待ちください。"
      }.onFailure { e ->
        _actionMessage.value = e.localizedMessage ?: "参加リクエストの送信に失敗しました（招待コードをご確認ください）"
      }
      _isBusy.value = false
    }
  }

  fun approveJoinRequest(request: JoinRequest) {
    val group = familyGroup.value ?: return
    viewModelScope.launch {
      _isBusy.value = true
      repository.approveJoinRequest(
        familyId = group.id,
        request = request,
        currentMembers = members.value,
        existingMemberUids = group.memberUids,
      ).onSuccess {
        _actionMessage.value = "${request.requesterName}さんの参加を承認しました"
      }.onFailure { e ->
        _actionMessage.value = e.localizedMessage ?: "承認処理に失敗しました"
      }
      _isBusy.value = false
    }
  }

  fun rejectJoinRequest(request: JoinRequest) {
    val group = familyGroup.value ?: return
    viewModelScope.launch {
      _isBusy.value = true
      repository.rejectJoinRequest(group.id, request.requesterUid)
        .onSuccess {
          _actionMessage.value = "${request.requesterName}さんの申請を見送りました"
        }.onFailure { e ->
          _actionMessage.value = e.localizedMessage ?: "見送り処理に失敗しました"
        }
      _isBusy.value = false
    }
  }

  fun refreshMyFamilies() {
    viewModelScope.launch {
      _isLoadingFamilies.value = true
      repository.findMyExistingFamilies()
        .onSuccess { list ->
          _myFamilies.value = list.sortedWith(
            compareBy<FamilyGroup>({ it.ownerUid != currentUserId }, { it.name })
          )
        }
        .onFailure { e ->
          Log.w(TAG, "Error loading user's family calendars", e)
        }
      _isLoadingFamilies.value = false
    }
  }

  fun selectFamily(familyId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      _selectedMemberFilter.value = null
      repository.activateJoinedFamily(familyId)
        .onFailure { e ->
          _actionMessage.value = e.localizedMessage ?: "カレンダーを開けませんでした"
        }
      _isBusy.value = false
    }
  }

  fun returnToFamilyHome() {
    viewModelScope.launch {
      _isBusy.value = true
      _selectedMemberFilter.value = null
      repository.leaveActiveFamily()
        .onSuccess {
          refreshMyFamilies()
        }
      _isBusy.value = false
    }
  }

  fun deleteFamily(familyId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.deleteFamily(familyId)
        .onSuccess {
          onCancelFamilyReminders(familyId)
          _selectedMemberFilter.value = null
          refreshMyFamilies()
          _actionMessage.value = "カレンダーを削除しました"
        }.onFailure { e ->
          _actionMessage.value = e.localizedMessage ?: "カレンダーの削除に失敗しました"
        }
      _isBusy.value = false
    }
  }

  fun leaveSharedFamily(familyId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.leaveSharedFamily(familyId)
        .onSuccess {
          onCancelFamilyReminders(familyId)
          _selectedMemberFilter.value = null
          refreshMyFamilies()
          _actionMessage.value = "共有カレンダーから退会しました"
        }.onFailure { e ->
          _actionMessage.value = e.localizedMessage ?: "退会処理に失敗しました"
        }
      _isBusy.value = false
    }
  }

  fun cancelPendingJoin() {
    viewModelScope.launch {
      _isBusy.value = true
      repository.clearPendingJoin()
      _lastSentInviteForMail.value = null
      _isBusy.value = false
    }
  }

  fun updateGlobalAccount(
    displayName: String,
    colorHex: String,
    avatarSymbol: String,
    avatarImageBase64: String = "",
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateGlobalAccountProfile(
        displayName = displayName,
        colorHex = colorHex,
        avatarSymbol = avatarSymbol,
        avatarImageBase64 = avatarImageBase64,
      ).onSuccess {
        _actionMessage.value = "全カレンダー共通のアカウント設定を保存しました"
      }.onFailure { e ->
        _actionMessage.value = e.localizedMessage ?: "アカウント設定の更新に失敗しました"
      }
      _isBusy.value = false
    }
  }

  fun updateMyProfile(
    displayName: String,
    colorHex: String,
    avatarSymbol: String,
    avatarImageBase64: String = "",
  ) {
    updateGlobalAccount(
      displayName = displayName,
      colorHex = colorHex,
      avatarSymbol = avatarSymbol,
      avatarImageBase64 = avatarImageBase64,
    )
  }

  fun saveEvent(
    eventId: String?,
    title: String,
    description: String,
    location: String,
    date: LocalDate,
    startMinutes: Long,
    endMinutes: Long,
    isAllDay: Boolean,
    assignedMemberUid: String,
    reminderMinutesBefore: Long = CalendarEvent.DEFAULT_REMINDER_MINUTES,
    onDone: () -> Unit,
  ) {
    val group = familyGroup.value ?: return
    viewModelScope.launch {
      _isBusy.value = true
      repository.saveEvent(
        familyId = group.id,
        eventId = eventId,
        title = title,
        description = description,
        location = location,
        dateKey = date.toString(),
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        isAllDay = isAllDay,
        assignedMemberUid = assignedMemberUid,
        memberUids = group.memberUids,
        reminderMinutesBefore = reminderMinutesBefore,
      ).onSuccess {
        // 予定の追加・更新時は即時の通知・ポップアップを出さない（指定時刻のみ通知）
        onDone()
      }.onFailure { e ->
        _actionMessage.value = e.localizedMessage ?: "予定の保存に失敗しました"
      }
      _isBusy.value = false
    }
  }

  fun deleteEvent(eventId: String, onDone: () -> Unit) {
    val group = familyGroup.value ?: return
    viewModelScope.launch {
      _isBusy.value = true
      repository.deleteEvent(group.id, eventId)
        .onSuccess {
          _actionMessage.value = "予定を削除しました"
          onDone()
        }.onFailure { e ->
          _actionMessage.value = e.localizedMessage ?: "予定の削除に失敗しました"
        }
      _isBusy.value = false
    }
  }

  private companion object {
    const val TAG = "FamilyCalendarVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
