package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.CalendarEvent
import com.example.data.model.FamilyMember
import com.example.ui.components.MemberAvatarBadge
import com.example.util.formatJapaneseDate
import com.example.util.formatMinutesToTime
import com.example.util.parseHexColor
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(
  initialDate: LocalDate,
  editingEvent: CalendarEvent?,
  members: List<FamilyMember>,
  currentUserId: String,
  isBusy: Boolean,
  onSave: (
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
  ) -> Unit,
  onDelete: (eventId: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var title by remember(editingEvent) { mutableStateOf(editingEvent?.title.orEmpty()) }
  var description by remember(editingEvent) { mutableStateOf(editingEvent?.description.orEmpty()) }
  var location by remember(editingEvent) { mutableStateOf(editingEvent?.location.orEmpty()) }
  var date by remember(editingEvent, initialDate) {
    mutableStateOf(
      editingEvent?.dateKey?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: initialDate
    )
  }
  var isAllDay by remember(editingEvent) { mutableStateOf(editingEvent?.isAllDay ?: false) }
  var startMinutes by remember(editingEvent) { mutableLongStateOf(editingEvent?.startMinutes ?: 540L) }
  var endMinutes by remember(editingEvent) { mutableLongStateOf(editingEvent?.endMinutes ?: 600L) }
  var assignedMemberUid by remember(editingEvent) {
    mutableStateOf(editingEvent?.assignedMemberUid ?: CalendarEvent.ASSIGNED_ALL)
  }
  var reminderMinutesBefore by remember(editingEvent) {
    mutableLongStateOf(editingEvent?.reminderMinutesBefore ?: CalendarEvent.DEFAULT_REMINDER_MINUTES)
  }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  var hasNotificationPermission by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted -> hasNotificationPermission = granted },
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("event_sheet_back_home_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "カレンダーに戻る",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("戻る")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (editingEvent == null) "予定を追加" else "予定を編集",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
        if (editingEvent != null) {
          IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.testTag("delete_event_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteOutline,
              contentDescription = "予定を削除",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("予定のタイトル *") },
        placeholder = { Text("例：小児科、授業参観、買い物") },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("event_title_input"),
      )

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "誰の予定？（カラー分け）",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AssigneeOptionChip(
          label = "家族みんな",
          symbol = "🏠",
          colorHex = "#D97757",
          selected = assignedMemberUid == CalendarEvent.ASSIGNED_ALL,
          onClick = { assignedMemberUid = CalendarEvent.ASSIGNED_ALL },
        )
        members.forEach { member ->
          val suffix = if (member.uid == currentUserId) " (自分)" else ""
          AssigneeOptionChip(
            label = member.displayName + suffix,
            symbol = member.avatarSymbol,
            colorHex = member.colorHex,
            avatarImageBase64 = member.avatarImageBase64,
            selected = assignedMemberUid == member.uid,
            onClick = { assignedMemberUid = member.uid },
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Date Picker Row
      OutlinedButton(
        onClick = {
          DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
              date = LocalDate.of(year, month + 1, dayOfMonth)
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
          ).show()
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("event_date_picker_button"),
      ) {
        Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "${date.year}年${formatJapaneseDate(date)}",
          style = MaterialTheme.typography.titleMedium,
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "終日の予定",
          style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
          checked = isAllDay,
          onCheckedChange = { isAllDay = it },
          modifier = Modifier.testTag("event_all_day_switch"),
        )
      }

      if (!isAllDay) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = {
              TimePickerDialog(
                context,
                { _, hour, minute ->
                  val newStart = hour * 60L + minute
                  startMinutes = newStart
                  if (endMinutes < newStart) {
                    endMinutes = (newStart + 60L).coerceAtMost(1439L)
                  }
                },
                (startMinutes / 60).toInt(),
                (startMinutes % 60).toInt(),
                true,
              ).show()
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("event_start_time_button"),
          ) {
            Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("開始 ${formatMinutesToTime(startMinutes)}")
          }

          OutlinedButton(
            onClick = {
              TimePickerDialog(
                context,
                { _, hour, minute ->
                  val newEnd = hour * 60L + minute
                  endMinutes = newEnd.coerceAtLeast(startMinutes)
                },
                (endMinutes / 60).toInt(),
                (endMinutes % 60).toInt(),
                true,
              ).show()
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("event_end_time_button"),
          ) {
            Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("終了 ${formatMinutesToTime(endMinutes)}")
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = location,
        onValueChange = { location = it },
        label = { Text("場所（任意）") },
        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("event_location_input"),
      )

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("メモ・持ち物（任意）") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("event_description_input"),
      )

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = if (reminderMinutesBefore >= 0L) {
            Icons.Filled.NotificationsActive
          } else {
            Icons.Filled.NotificationsOff
          },
          contentDescription = null,
          tint = if (reminderMinutesBefore >= 0L) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "予定の通知設定",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CalendarEvent.REMINDER_OPTIONS.forEach { (minutes, label) ->
          FilterChip(
            selected = reminderMinutesBefore == minutes,
            onClick = {
              reminderMinutesBefore = minutes
              if (minutes >= 0L &&
                !hasNotificationPermission &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
              ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            label = { Text(label) },
            modifier = Modifier.testTag("reminder_option_$minutes"),
          )
        }
      }

      if (reminderMinutesBefore >= 0L &&
        !hasNotificationPermission &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
      ) {
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(
          onClick = {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          },
          modifier = Modifier.testTag("grant_notification_permission_button"),
        ) {
          Text("※ 通知を受け取るには権限を許可してください（タップして許可）")
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "※ 予定の追加・更新時には通知されません。設定した時刻にお知らせします。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .weight(1f)
            .height(50.dp),
        ) {
          Text("戻る")
        }
        Button(
          onClick = {
            onSave(
              editingEvent?.id,
              title,
              description,
              location,
              date,
              startMinutes,
              endMinutes,
              isAllDay,
              assignedMemberUid,
              reminderMinutesBefore,
            )
          },
          enabled = title.isNotBlank() && !isBusy,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .weight(1.4f)
            .height(50.dp)
            .testTag("save_event_button"),
        ) {
          Text(if (editingEvent == null) "追加する" else "更新する", fontWeight = FontWeight.Bold)
        }
      }
    }
  }

  if (showDeleteConfirm && editingEvent != null) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("予定を削除しますか？") },
      text = { Text("「${editingEvent.title}」を家族カレンダーから削除します。") },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDelete(editingEvent.id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("削除する")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) {
          Text("キャンセル")
        }
      },
    )
  }
}

@Composable
private fun AssigneeOptionChip(
  label: String,
  symbol: String,
  colorHex: String,
  avatarImageBase64: String = "",
  selected: Boolean,
  onClick: () -> Unit,
) {
  val accent = parseHexColor(colorHex)
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(20.dp))
      .background(if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
      .border(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        shape = RoundedCornerShape(20.dp),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MemberAvatarBadge(
      symbol = symbol,
      colorHex = colorHex,
      avatarImageBase64 = avatarImageBase64,
      size = 24.dp,
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
  }
}
