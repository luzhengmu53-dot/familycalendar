package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CalendarIconPalette
import com.example.data.model.FamilyGroup
import com.example.data.model.FamilyMember
import com.example.data.model.InvitePayload
import com.example.data.model.JoinRequest
import com.example.ui.components.ColorAndSymbolPicker
import com.example.ui.components.MemberAvatarBadge
import com.example.util.QrGenerator
import com.example.widget.FamilyCalendarWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMembersAndQrSheet(
  initialTab: Int = 0,
  familyGroup: FamilyGroup,
  members: List<FamilyMember>,
  pendingRequests: List<JoinRequest>,
  currentUserId: String,
  isBusy: Boolean,
  onUpdateMyProfile: (displayName: String, colorHex: String, symbol: String, avatarImageBase64: String) -> Unit,
  onUpdateFamilyAppearance: (familyName: String, iconSymbol: String, iconColorHex: String, iconImageBase64: String) -> Unit = { _, _, _, _ -> },
  onApproveRequest: (JoinRequest) -> Unit,
  onRejectRequest: (JoinRequest) -> Unit,
  onDeleteOrLeaveFamily: () -> Unit = {},
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val isShared = familyGroup.isSharedCalendar()
  val safeInitialTab = if (isShared) initialTab.coerceIn(0, 2) else if (initialTab == 2) 1 else 0
  var selectedTab by remember(safeInitialTab) { mutableIntStateOf(safeInitialTab) }
  var showDeleteOrLeaveConfirm by remember { mutableStateOf(false) }

  val myMember = members.firstOrNull { it.uid == currentUserId }
  var editName by remember(myMember) { mutableStateOf(myMember?.displayName.orEmpty()) }
  var editColorHex by remember(myMember) { mutableStateOf(myMember?.colorHex ?: "#4285F4") }
  var editSymbol by remember(myMember) { mutableStateOf(myMember?.avatarSymbol ?: "🏠") }
  var editAvatarImageBase64 by remember(myMember) { mutableStateOf(myMember?.avatarImageBase64.orEmpty()) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("members_sheet_back_home_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "カレンダーに戻る",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("戻る", fontWeight = FontWeight.Bold)
        }
        Text(
          text = "家族・設定",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(end = 8.dp),
        )
      }

      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface,
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text(if (isShared) "メンバー・設定" else "設定") },
        )
        if (isShared) {
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = {
              val badge = if (pendingRequests.isNotEmpty()) " (${pendingRequests.size})" else ""
              Text("QR招待・承認$badge")
            },
          )
          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = { Text("ウィジェット") },
          )
        } else {
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("ウィジェット") },
          )
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      ) {
        val effectiveSection = when {
          isShared -> selectedTab
          selectedTab == 0 -> 0
          else -> 2
        }
        when (effectiveSection) {
          0 -> {
            Text(
              text = "参加中の家族メンバー（${members.size}人）",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))

            members.forEach { member ->
              Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  MemberAvatarBadge(
                    symbol = member.avatarSymbol,
                    colorHex = member.colorHex,
                    avatarImageBase64 = member.avatarImageBase64,
                    size = 38.dp,
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = member.displayName,
                        style = MaterialTheme.typography.titleMedium,
                      )
                      if (member.uid == currentUserId) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                          shape = RoundedCornerShape(8.dp),
                          color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                          Text(
                            text = "あなた",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                          )
                        }
                      }
                    }
                    Text(
                      text = member.email,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Text(
                    text = if (member.role == "owner") "作成者" else "メンバー",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
              text = "自分のアカウント設定（全カレンダー共通）",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "※ ここでの変更はホームの「設定」と同じく全カレンダー共通で反映されます",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              MemberAvatarBadge(
                symbol = editSymbol,
                colorHex = editColorHex,
                avatarImageBase64 = editAvatarImageBase64,
                size = 50.dp,
              )
              Spacer(modifier = Modifier.width(12.dp))
              OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("表示名") },
                singleLine = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("edit_my_name_input"),
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            ColorAndSymbolPicker(
              selectedColorHex = editColorHex,
              onColorSelected = { editColorHex = it },
              selectedSymbol = editSymbol,
              onSymbolSelected = { editSymbol = it },
              selectedAvatarImageBase64 = editAvatarImageBase64,
              onAvatarImageChanged = { editAvatarImageBase64 = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
              onClick = {
                onUpdateMyProfile(editName, editColorHex, editSymbol, editAvatarImageBase64)
              },
              enabled = editName.isNotBlank() && !isBusy,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_my_profile_button"),
            ) {
              Text("共通アカウント設定を保存", fontWeight = FontWeight.Bold)
            }

            if (familyGroup.ownerUid == currentUserId) {
              Spacer(modifier = Modifier.height(22.dp))
              var calName by remember(familyGroup.id, familyGroup.name) { mutableStateOf(familyGroup.name) }
              var calIconSymbol by remember(familyGroup.id, familyGroup.iconSymbol) {
                mutableStateOf(familyGroup.iconSymbol.ifBlank { "📅" })
              }
              var calColorHex by remember(familyGroup.id, familyGroup.colorHex) {
                mutableStateOf(familyGroup.colorHex.ifBlank { "#D97757" })
              }
              var calIconImage by remember(familyGroup.id, familyGroup.iconImageBase64) {
                mutableStateOf(familyGroup.iconImageBase64)
              }

              Text(
                text = "このカレンダーのアイコン・名前",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(10.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatarBadge(
                  symbol = calIconSymbol,
                  colorHex = calColorHex,
                  avatarImageBase64 = calIconImage,
                  size = 48.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                  value = calName,
                  onValueChange = { calName = it },
                  label = { Text("カレンダー名") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
              }
              Spacer(modifier = Modifier.height(12.dp))
              ColorAndSymbolPicker(
                selectedColorHex = calColorHex,
                onColorSelected = { calColorHex = it },
                selectedSymbol = calIconSymbol,
                onSymbolSelected = { calIconSymbol = it },
                selectedAvatarImageBase64 = calIconImage,
                onAvatarImageChanged = { calIconImage = it },
                availableSymbols = CalendarIconPalette.calendarSymbols,
                imageSectionLabel = "カレンダーアイコン画像（任意）",
                colorSectionLabel = "カレンダーカラー",
                symbolSectionLabel = "カレンダーアイコン（画像未設定時に表示）",
              )
              Spacer(modifier = Modifier.height(14.dp))
              OutlinedButton(
                onClick = {
                  onUpdateFamilyAppearance(calName, calIconSymbol, calColorHex, calIconImage)
                },
                enabled = calName.isNotBlank() && !isBusy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp),
              ) {
                Text("カレンダーのアイコン・名前を保存", fontWeight = FontWeight.Bold)
              }
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(14.dp))

            val isDeleteAction = familyGroup.shouldDeleteInsteadOfLeave()
            OutlinedButton(
              onClick = { showDeleteOrLeaveConfirm = true },
              enabled = !isBusy,
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag(
                  if (isDeleteAction) "sheet_delete_calendar_button"
                  else "sheet_leave_calendar_button"
                ),
            ) {
              Icon(
                imageVector = if (isDeleteAction) {
                  Icons.Filled.DeleteOutline
                } else {
                  Icons.AutoMirrored.Filled.ExitToApp
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isDeleteAction) "このカレンダーを消す" else "この共有カレンダーから退会する",
                fontWeight = FontWeight.Bold,
              )
            }
          }

          1 -> {
            val invitePayload = remember(familyGroup) {
              InvitePayload(
                familyId = familyGroup.id,
                inviteCode = familyGroup.inviteCode,
                familyName = familyGroup.name,
                ownerUid = familyGroup.ownerUid,
                ownerEmail = familyGroup.ownerEmail,
              )
            }
            val qrUri = remember(invitePayload) { invitePayload.toQrUri() }
            val qrBitmap = remember(qrUri) { QrGenerator.generateQrBitmap(qrUri, 520) }

            Text(
              text = "招待QRコード（${familyGroup.name}）",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "家族のスマホでこのQRコードを読み取ってもらってください。申請が届いたら下の欄またはホーム画面で承認できます。",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (qrBitmap != null) {
              Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
              ) {
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                  Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "家族招待QRコード",
                    modifier = Modifier
                      .padding(16.dp)
                      .size(210.dp),
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("家族カレンダー招待", qrUri))
                  Toast.makeText(context, "招待コードをコピーしました", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("copy_invite_button"),
              ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("コードをコピー")
              }

              OutlinedButton(
                onClick = {
                  val subject = Uri.encode("【家族カレンダー】「${familyGroup.name}」への招待")
                  val body = Uri.encode(
                    "家族カレンダーアプリをインストール後、QRスキャン画面の招待コード欄に以下を貼り付けて参加申請を送ってください。\n\n$qrUri"
                  )
                  val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:?subject=$subject&body=$body")
                  }
                  runCatching { context.startActivity(intent) }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("メールで送る")
              }
            }

            if (familyGroup.ownerUid == currentUserId) {
              Spacer(modifier = Modifier.height(22.dp))
              Text(
                text = "参加承認待ちのリクエスト（${pendingRequests.size}件）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(8.dp))

              if (pendingRequests.isEmpty()) {
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = "現在、承認待ちの家族リクエストはありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                  )
                }
              } else {
                pendingRequests.forEach { req ->
                  Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                  ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                      Text(
                        text = req.requesterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = req.requesterEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Spacer(modifier = Modifier.height(10.dp))
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                          onClick = { onApproveRequest(req) },
                          enabled = !isBusy,
                          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                          modifier = Modifier
                            .weight(1f)
                            .testTag("approve_request_${req.requesterUid}"),
                        ) {
                          Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("承認する")
                        }
                        OutlinedButton(
                          onClick = { onRejectRequest(req) },
                          enabled = !isBusy,
                          modifier = Modifier.weight(1f),
                        ) {
                          Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("見送る")
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          2 -> {
            Text(
              text = "ホーム画面予定ウィジェット",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "ホーム画面に「家族カレンダー」ウィジェットを追加すると、上部に大きな月間カレンダー、下部に一番近い直近の予定をいつでも確認できます。",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Visual layout preview card
            Card(
              shape = RoundedCornerShape(20.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                ) {
                  Column {
                    Text(
                      text = "上部エリア（大きく見やすい月間カレンダー）",
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = "日〜土のカレンダーにメンバー色の予定ラベルが並びます。",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                ) {
                  Text(
                    text = "下部エリア：次の予定（直近の一番近い予定をコンパクト表示）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
              onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  val appWidgetManager = AppWidgetManager.getInstance(context)
                  val provider = ComponentName(context, FamilyCalendarWidgetReceiver::class.java)
                  if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(provider, null, null)
                  } else {
                    Toast.makeText(
                      context,
                      "ホーム画面を長押し →「ウィジェット」から「家族カレンダー」を追加してください",
                      Toast.LENGTH_LONG,
                    ).show()
                  }
                } else {
                  Toast.makeText(
                    context,
                    "ホーム画面を長押し →「ウィジェット」から「家族カレンダー」を追加してください",
                    Toast.LENGTH_LONG,
                  ).show()
                }
              },
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pin_widget_button"),
            ) {
              Icon(Icons.Filled.Widgets, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("ホーム画面にウィジェットを追加", fontWeight = FontWeight.Bold)
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("members_sheet_bottom_back_home_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("カレンダーに戻る", fontWeight = FontWeight.Bold)
        }
      }
    }
  }

  if (showDeleteOrLeaveConfirm) {
    val isDelete = familyGroup.shouldDeleteInsteadOfLeave()
    AlertDialog(
      onDismissRequest = { showDeleteOrLeaveConfirm = false },
      title = {
        Text(
          text = if (isDelete) {
            "「${familyGroup.name}」を消しますか？"
          } else {
            "「${familyGroup.name}」から退会しますか？"
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
            showDeleteOrLeaveConfirm = false
            onDismiss()
            onDeleteOrLeaveFamily()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("sheet_confirm_delete_or_leave_button"),
        ) {
          Text(if (isDelete) "消す" else "退会する")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteOrLeaveConfirm = false }) {
          Text("キャンセル")
        }
      },
    )
  }
}
