package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CalendarIconPalette
import com.example.data.model.FamilyGroup
import com.example.data.model.InvitePayload
import com.example.data.model.JoinRequest
import com.example.data.model.MemberColorPalette
import com.example.data.model.UserProfile
import com.example.ui.components.ColorAndSymbolPicker
import com.example.ui.components.MemberAvatarBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyOnboardingScreen(
  userProfile: UserProfile?,
  userDisplayName: String,
  userEmail: String,
  currentUserId: String = "",
  myFamilies: List<FamilyGroup> = emptyList(),
  isLoadingFamilies: Boolean = false,
  pendingJoinRequest: JoinRequest?,
  lastSentInviteForMail: InvitePayload?,
  scannedInvite: InvitePayload?,
  isBusy: Boolean,
  actionMessage: String?,
  onClearMessage: () -> Unit,
  onSelectFamily: (String) -> Unit = {},
  onCreateFamily: (familyName: String, iconSymbol: String, iconColorHex: String, iconImageBase64: String, calendarType: String) -> Unit,
  onDeleteFamily: (String) -> Unit = {},
  onLeaveFamily: (String) -> Unit = {},
  onUpdateFamilyAppearance: (familyId: String, familyName: String, iconSymbol: String, iconColorHex: String, iconImageBase64: String) -> Unit = { _, _, _, _, _ -> },
  onUpdateGlobalAccount: (displayName: String, colorHex: String, symbol: String, avatarImageBase64: String) -> Unit = { _, _, _, _ -> },
  onQrScanned: (String) -> Unit,
  onDismissScannedInvite: () -> Unit,
  onConfirmJoinInvite: (InvitePayload, String) -> Unit,
  onCancelPendingJoin: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scrollState = rememberScrollState()

  var showCreateCalendarSheet by remember { mutableStateOf(false) }
  var editingCalendar by remember { mutableStateOf<FamilyGroup?>(null) }
  var confirmDeleteOrLeaveCalendar by remember { mutableStateOf<FamilyGroup?>(null) }
  var showSettingsSheet by remember { mutableStateOf(false) }

  var manualInviteInput by remember { mutableStateOf("") }
  var showScanner by remember { mutableStateOf(false) }

  val sharedAccountName = userProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName
  val sharedColorHex = userProfile?.colorHex?.ifBlank { "#4285F4" } ?: "#4285F4"
  val sharedSymbol = userProfile?.avatarSymbol?.ifBlank { "🏠" } ?: "🏠"
  val sharedImageBase64 = userProfile?.avatarImageBase64.orEmpty()

  LaunchedEffect(actionMessage) {
    val msg = actionMessage
    if (!msg.isNullOrBlank()) {
      onClearMessage()
      snackbarHostState.showSnackbar(msg)
    }
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
            onClick = onSignOut,
            modifier = Modifier.testTag("onboarding_sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "ログイン画面に戻る",
            )
          }
        },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatarBadge(
              symbol = sharedSymbol,
              colorHex = sharedColorHex,
              avatarImageBase64 = sharedImageBase64,
              size = 34.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "ホーム",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
              )
              Text(
                text = "${sharedAccountName.ifBlank { "ユーザー" }}（共通アカウント）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        },
        actions = {
          IconButton(
            onClick = { showSettingsSheet = true },
            modifier = Modifier.testTag("home_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Settings,
              contentDescription = "設定（共通アカウント変更）",
              tint = MaterialTheme.colorScheme.onBackground,
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxWidth()
          .verticalScroll(scrollState)
          .padding(horizontal = 20.dp, vertical = 12.dp),
      ) {
        // Section 1: Calendar List + "増やす" Button (the ONLY way to create a new calendar)
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_calendars_section"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "カレンダー一覧（${myFamilies.size}件）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              if (isLoadingFamilies) {
                CircularProgressIndicator(
                  modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
                  strokeWidth = 2.dp,
                )
              }
              Button(
                onClick = { showCreateCalendarSheet = true },
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("home_add_calendar_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Add,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("増やす", fontWeight = FontWeight.Bold)
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (myFamilies.isEmpty() && !isLoadingFamilies) {
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("empty_calendars_card"),
            ) {
              Column(modifier = Modifier.padding(18.dp)) {
                Text(
                  text = "まだカレンダーがありません",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "上の「増やす」ボタンから新しいカレンダーを作成できます。アカウント設定は右上の「設定」から全カレンダー共通で変更できます。",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              myFamilies.forEach { group ->
                val isOwner = group.ownerUid == currentUserId || currentUserId.isBlank()
                val isDeleteAction = group.shouldDeleteInsteadOfLeave()
                val isShared = group.isSharedCalendar()
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                  elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = !isBusy) { onSelectFamily(group.id) }
                    .testTag("select_family_${group.id}"),
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 12.dp),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      MemberAvatarBadge(
                        symbol = group.iconSymbol.ifBlank { "📅" },
                        colorHex = group.colorHex.ifBlank { "#D97757" },
                        avatarImageBase64 = group.iconImageBase64,
                        size = 46.dp,
                      )
                      Spacer(modifier = Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                          Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isShared) {
                              MaterialTheme.colorScheme.secondaryContainer
                            } else {
                              MaterialTheme.colorScheme.primaryContainer
                            },
                          ) {
                            Text(
                              text = if (isShared) "共有" else "自分用",
                              style = MaterialTheme.typography.labelSmall,
                              color = if (isShared) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                              } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                              },
                              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                          }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                          text = if (isShared) {
                            "メンバー ${group.memberUids.size}人" + if (isOwner) "・作成者" else ""
                          } else {
                            "自分専用の予定管理"
                          },
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = "開く",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                      )
                      Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "${group.name}を開く",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      if (isOwner) {
                        TextButton(
                          onClick = { editingCalendar = group },
                          enabled = !isBusy,
                          modifier = Modifier.testTag("edit_calendar_${group.id}"),
                        ) {
                          Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                          )
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("アイコン・名前変更")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                      }

                      TextButton(
                        onClick = { confirmDeleteOrLeaveCalendar = group },
                        enabled = !isBusy,
                        colors = ButtonDefaults.textButtonColors(
                          contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.testTag(
                          if (isDeleteAction) "delete_calendar_${group.id}"
                          else "leave_calendar_${group.id}"
                        ),
                      ) {
                        Icon(
                          imageVector = if (isDeleteAction) {
                            Icons.Filled.DeleteOutline
                          } else {
                            Icons.AutoMirrored.Filled.ExitToApp
                          },
                          contentDescription = null,
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                          text = if (isDeleteAction) "消す" else "退会",
                          fontWeight = FontWeight.Bold,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Section 2: Pending Join Request Status Card
        val pendingFamilyId = userProfile?.pendingFamilyId.orEmpty()
        if (pendingFamilyId.isNotBlank()) {
          val isRejected = pendingJoinRequest?.status == "rejected"
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (isRejected) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
              } else {
                MaterialTheme.colorScheme.tertiaryContainer
              }
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.HourglassTop,
                  contentDescription = null,
                  tint = if (isRejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = if (isRejected) {
                    "参加申請が見送られました"
                  } else {
                    "「${userProfile?.pendingFamilyName.orEmpty().ifBlank { "家族カレンダー" }}」の承認待ち"
                  },
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = if (isRejected) {
                  "再度QRコードを読み取るか、「増やす」から新しくカレンダーを作成できます。"
                } else {
                  "QRコードを作成した家族がアプリで「承認」を押すと、自動的に共有カレンダーが開きます。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              val ownerEmail = lastSentInviteForMail?.ownerEmail.orEmpty()
              if (!isRejected && ownerEmail.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                  onClick = {
                    val subject = Uri.encode("【家族カレンダー】参加承認のお願い（${sharedAccountName}）")
                    val body = Uri.encode(
                      "${sharedAccountName}（${userEmail}）が「${lastSentInviteForMail?.familyName ?: "家族カレンダー"}」への参加リクエストを送信しました。\n\n家族カレンダーアプリを開いて承認ボタンを押してください。"
                    )
                    val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                      data = Uri.parse("mailto:$ownerEmail?subject=$subject&body=$body")
                    }
                    runCatching { context.startActivity(mailIntent) }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("send_approval_email_button"),
                ) {
                  Icon(Icons.Filled.Email, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("作成者にメールで承認をお知らせする")
                }
              }

              Spacer(modifier = Modifier.height(8.dp))
              TextButton(
                onClick = onCancelPendingJoin,
                modifier = Modifier.align(Alignment.End),
              ) {
                Text(if (isRejected) "閉じる" else "申請を取り消す")
              }
            }
          }
          Spacer(modifier = Modifier.height(18.dp))
        }

        // Section 3: Join Existing Family via QR Code
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Text(
              text = "QRコードで家族のカレンダーに参加",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "すでに家族の誰かが作成している場合、招待QRコードを読み取って承認リクエストを送ります。",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = { showScanner = true },
              enabled = !isBusy,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("scan_qr_button"),
            ) {
              Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("カメラでQRコードを読み取る", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              OutlinedTextField(
                value = manualInviteInput,
                onValueChange = { manualInviteInput = it },
                label = { Text("招待コード文字列を貼り付け") },
                singleLine = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("manual_invite_input"),
              )
              Spacer(modifier = Modifier.width(8.dp))
              OutlinedButton(
                onClick = { onQrScanned(manualInviteInput) },
                enabled = manualInviteInput.isNotBlank() && !isBusy,
                modifier = Modifier.testTag("manual_invite_submit"),
              ) {
                Text("確認")
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }

  // Modal 1: Create New Calendar (ONLY accessible via Home "増やす" button)
  if (showCreateCalendarSheet) {
    CalendarEditorSheet(
      title = "新しいカレンダーを作成",
      confirmLabel = "作成する",
      initialName = "",
      initialSymbol = "📅",
      initialColorHex = "#D97757",
      initialImageBase64 = "",
      showTypeSelector = true,
      initialCalendarType = FamilyGroup.TYPE_SHARED,
      isBusy = isBusy,
      backButtonTag = "create_calendar_back_button",
      confirmButtonTag = "create_family_button",
      onConfirm = { name, symbol, colorHex, imageBase64, calendarType ->
        showCreateCalendarSheet = false
        val defaultName = if (calendarType == FamilyGroup.TYPE_PERSONAL) "自分用カレンダー" else "共有カレンダー"
        onCreateFamily(name.ifBlank { defaultName }, symbol, colorHex, imageBase64, calendarType)
      },
      onDismiss = { showCreateCalendarSheet = false },
    )
  }

  // Modal 2: Edit Existing Calendar Icon & Name
  val calendarToEdit = editingCalendar
  if (calendarToEdit != null) {
    CalendarEditorSheet(
      title = "カレンダーアイコン・名前を変更",
      confirmLabel = "保存する",
      initialName = calendarToEdit.name,
      initialSymbol = calendarToEdit.iconSymbol.ifBlank { "📅" },
      initialColorHex = calendarToEdit.colorHex.ifBlank { "#D97757" },
      initialImageBase64 = calendarToEdit.iconImageBase64,
      showTypeSelector = false,
      initialCalendarType = calendarToEdit.calendarType,
      isBusy = isBusy,
      backButtonTag = "edit_calendar_back_button",
      confirmButtonTag = "save_calendar_edit_button",
      onConfirm = { name, symbol, colorHex, imageBase64, _ ->
        editingCalendar = null
        onUpdateFamilyAppearance(
          calendarToEdit.id,
          name.ifBlank { calendarToEdit.name },
          symbol,
          colorHex,
          imageBase64,
        )
      },
      onDismiss = { editingCalendar = null },
    )
  }

  // Confirmation Dialog for Delete (消す) or Leave (退会)
  val targetCalendar = confirmDeleteOrLeaveCalendar
  if (targetCalendar != null) {
    val isDelete = targetCalendar.shouldDeleteInsteadOfLeave()
    AlertDialog(
      onDismissRequest = { confirmDeleteOrLeaveCalendar = null },
      title = {
        Text(
          text = if (isDelete) {
            "「${targetCalendar.name}」を消しますか？"
          } else {
            "「${targetCalendar.name}」から退会しますか？"
          },
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Text(
          text = if (isDelete) {
            "このカレンダーと登録されている予定はすべて削除され、元に戻せません。"
          } else {
            "この共有カレンダーから抜けます。他のメンバーのカレンダーと予定はそのまま残ります。"
          },
        )
      },
      confirmButton = {
        Button(
          onClick = {
            confirmDeleteOrLeaveCalendar = null
            if (isDelete) {
              onDeleteFamily(targetCalendar.id)
            } else {
              onLeaveFamily(targetCalendar.id)
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_or_leave_calendar_button"),
        ) {
          Text(if (isDelete) "消す" else "退会する")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteOrLeaveCalendar = null }) {
          Text("キャンセル")
        }
      },
    )
  }

  // Modal 3: Home Settings Sheet (Shared Global Account Configuration)
  if (showSettingsSheet) {
    HomeSettingsSheet(
      initialDisplayName = sharedAccountName,
      initialColorHex = sharedColorHex,
      initialSymbol = sharedSymbol,
      initialAvatarImageBase64 = sharedImageBase64,
      userEmail = userEmail,
      isBusy = isBusy,
      onSaveAccount = { name, colorHex, symbol, imageBase64 ->
        onUpdateGlobalAccount(name, colorHex, symbol, imageBase64)
        showSettingsSheet = false
      },
      onSignOut = {
        showSettingsSheet = false
        onSignOut()
      },
      onDismiss = { showSettingsSheet = false },
    )
  }

  if (showScanner) {
    QrScannerDialog(
      onQrDetected = { raw ->
        showScanner = false
        onQrScanned(raw)
      },
      onDismiss = { showScanner = false },
    )
  }

  if (scannedInvite != null) {
    var joinName by remember(scannedInvite, sharedAccountName) {
      mutableStateOf(sharedAccountName.ifBlank { "家族メンバー" })
    }
    AlertDialog(
      onDismissRequest = onDismissScannedInvite,
      title = { Text("「${scannedInvite.familyName}」に参加しますか？") },
      text = {
        Column {
          Text(
            text = "参加リクエストを送信すると、作成した家族の画面とメールで承認できるようになります。",
            style = MaterialTheme.typography.bodyMedium,
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = joinName,
            onValueChange = { joinName = it },
            label = { Text("カレンダーで表示するお名前") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { onConfirmJoinInvite(scannedInvite, joinName) },
          enabled = joinName.isNotBlank() && !isBusy,
          modifier = Modifier.testTag("confirm_join_request_button"),
        ) {
          Text("承認リクエストを送る")
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissScannedInvite) {
          Text("キャンセル")
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEditorSheet(
  title: String,
  confirmLabel: String,
  initialName: String,
  initialSymbol: String,
  initialColorHex: String,
  initialImageBase64: String,
  showTypeSelector: Boolean = false,
  initialCalendarType: String = FamilyGroup.TYPE_SHARED,
  isBusy: Boolean,
  backButtonTag: String,
  confirmButtonTag: String,
  onConfirm: (name: String, iconSymbol: String, iconColorHex: String, iconImageBase64: String, calendarType: String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var calendarName by remember(initialName) { mutableStateOf(initialName) }
  var selectedSymbol by remember(initialSymbol) { mutableStateOf(initialSymbol) }
  var selectedColorHex by remember(initialColorHex) { mutableStateOf(initialColorHex) }
  var selectedImageBase64 by remember(initialImageBase64) { mutableStateOf(initialImageBase64) }
  var selectedType by remember(initialCalendarType) { mutableStateOf(initialCalendarType) }

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
          modifier = Modifier.testTag(backButtonTag),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "ホーム画面に戻る",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("戻る")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (showTypeSelector) {
        Text(
          text = "カレンダーの種類",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          val isPersonal = selectedType == FamilyGroup.TYPE_PERSONAL
          val isShared = !isPersonal

          Surface(
            onClick = { selectedType = FamilyGroup.TYPE_PERSONAL },
            shape = RoundedCornerShape(16.dp),
            color = if (isPersonal) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
              width = if (isPersonal) 2.dp else 1.dp,
              color = if (isPersonal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("calendar_type_personal"),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = if (isPersonal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "自分用",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "自分の予定のみ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Surface(
            onClick = { selectedType = FamilyGroup.TYPE_SHARED },
            shape = RoundedCornerShape(16.dp),
            color = if (isShared) {
              MaterialTheme.colorScheme.secondaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
              width = if (isShared) 2.dp else 1.dp,
              color = if (isShared) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("calendar_type_shared"),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = null,
                tint = if (isShared) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "共有",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "家族や仲間と共有",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        MemberAvatarBadge(
          symbol = selectedSymbol,
          colorHex = selectedColorHex,
          avatarImageBase64 = selectedImageBase64,
          size = 52.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(
          value = calendarName,
          onValueChange = { calendarName = it },
          label = { Text("カレンダー名（例：佐藤家、個人用）") },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("family_name_input"),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      ColorAndSymbolPicker(
        selectedColorHex = selectedColorHex,
        onColorSelected = { selectedColorHex = it },
        selectedSymbol = selectedSymbol,
        onSymbolSelected = { selectedSymbol = it },
        selectedAvatarImageBase64 = selectedImageBase64,
        onAvatarImageChanged = { selectedImageBase64 = it },
        availableSymbols = CalendarIconPalette.calendarSymbols,
        imageSectionLabel = "カレンダーアイコン画像（任意）",
        colorSectionLabel = "カレンダーカラー",
        symbolSectionLabel = "カレンダーアイコン（画像未設定時に表示）",
      )

      Spacer(modifier = Modifier.height(22.dp))

      Button(
        onClick = {
          onConfirm(
            calendarName,
            selectedSymbol,
            selectedColorHex,
            selectedImageBase64,
            selectedType,
          )
        },
        enabled = !isBusy,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag(confirmButtonTag),
      ) {
        Icon(Icons.Filled.GroupAdd, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(confirmLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSettingsSheet(
  initialDisplayName: String,
  initialColorHex: String,
  initialSymbol: String,
  initialAvatarImageBase64: String,
  userEmail: String,
  isBusy: Boolean,
  onSaveAccount: (displayName: String, colorHex: String, symbol: String, avatarImageBase64: String) -> Unit,
  onSignOut: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var editName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
  var editColorHex by remember(initialColorHex) { mutableStateOf(initialColorHex) }
  var editSymbol by remember(initialSymbol) { mutableStateOf(initialSymbol) }
  var editImageBase64 by remember(initialAvatarImageBase64) { mutableStateOf(initialAvatarImageBase64) }

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
          modifier = Modifier.testTag("settings_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "ホーム画面に戻る",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("戻る")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "設定（全カレンダー共通アカウント）",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "ここで設定した表示名・アイコン・カラーはすべてのカレンダーで共通して使われます。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(14.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        MemberAvatarBadge(
          symbol = editSymbol,
          colorHex = editColorHex,
          avatarImageBase64 = editImageBase64,
          size = 54.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(
          value = editName,
          onValueChange = { editName = it },
          label = { Text("アカウント表示名（例：ママ、パパ）") },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("global_account_name_input"),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      ColorAndSymbolPicker(
        selectedColorHex = editColorHex,
        onColorSelected = { editColorHex = it },
        selectedSymbol = editSymbol,
        onSymbolSelected = { editSymbol = it },
        selectedAvatarImageBase64 = editImageBase64,
        onAvatarImageChanged = { editImageBase64 = it },
        availableSymbols = MemberColorPalette.avatarSymbols,
        imageSectionLabel = "アカウントアイコン画像",
        colorSectionLabel = "アカウントカラー（プリセット＆カスタム調整）",
        symbolSectionLabel = "アカウントマーク（画像未設定時に表示）",
      )

      Spacer(modifier = Modifier.height(20.dp))

      Button(
        onClick = {
          onSaveAccount(
            editName.ifBlank { "メンバー" },
            editColorHex,
            editSymbol,
            editImageBase64,
          )
        },
        enabled = editName.isNotBlank() && !isBusy,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("save_global_account_button"),
      ) {
        Text("共通アカウント設定を保存", fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(18.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "ログインアカウント",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = userEmail.ifBlank { "未設定" },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onSignOut,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("settings_sign_out_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Logout,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("アカウントを切り替える / ログアウト")
      }
    }
  }
}
