package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.repository.FamilyCalendarRepository
import com.example.notifications.EventReminderScheduler
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalendarHomeScreen
import com.example.ui.screens.FamilyOnboardingScreen
import com.example.ui.theme.FamilyCalendarTheme
import com.example.ui.viewmodel.FamilyCalendarViewModel
import com.example.ui.viewmodel.UiState
import com.example.widget.WidgetCacheStore
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
    )
    EventReminderScheduler.ensureNotificationChannel(this)
    val initialDateKey = intent?.getStringExtra("widget_date_key")
    setContent {
      FamilyCalendarTheme {
        AppNavigation(
          auth = Firebase.auth,
          initialDateKey = initialDateKey,
        )
      }
    }
  }
}

@Composable
fun AppNavigation(
  auth: FirebaseAuth = Firebase.auth,
  initialDateKey: String? = null,
) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(
      onAuthSuccess = {},
    )
  } else {
    key(user.uid) {
      AuthenticatedAppRoot(
        user = user,
        initialDateKey = initialDateKey,
      )
    }
  }
}

@Composable
private fun AuthenticatedAppRoot(
  user: FirebaseUser,
  initialDateKey: String?,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: FamilyCalendarViewModel = viewModel(
    key = user.uid,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val repository = FamilyCalendarRepository(db, Firebase.auth)
        FamilyCalendarViewModel(
          repository = repository,
          currentUserId = user.uid,
          userEmail = user.email.orEmpty(),
          userDisplayName = user.displayName.orEmpty().ifBlank { "家族メンバー" },
          userPhotoUrl = user.photoUrl?.toString(),
          initialDateKey = initialDateKey,
          onWidgetSync = { isSignedIn, familyName, events, members ->
            WidgetCacheStore.saveFamilySnapshot(
              context = app,
              isSignedIn = isSignedIn,
              familyName = familyName,
              events = events,
              members = members,
            )
          },
          onReminderSync = { familyId, familyName, events ->
            EventReminderScheduler.syncRemindersForFamily(
              context = app,
              familyId = familyId,
              calendarName = familyName,
              events = events,
            )
          },
          onCancelFamilyReminders = { familyId ->
            EventReminderScheduler.cancelFamilyReminders(app, familyId)
          },
        )
      }
    },
  )

  val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
  val familyGroup by viewModel.familyGroup.collectAsStateWithLifecycle()
  val members by viewModel.members.collectAsStateWithLifecycle()
  val events by viewModel.events.collectAsStateWithLifecycle()
  val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()
  val myPendingJoinRequest by viewModel.myPendingJoinRequest.collectAsStateWithLifecycle()
  val displayedMonth by viewModel.displayedMonth.collectAsStateWithLifecycle()
  val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
  val selectedMemberFilter by viewModel.selectedMemberFilter.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
  val scannedInvite by viewModel.scannedInvite.collectAsStateWithLifecycle()
  val lastSentInviteForMail by viewModel.lastSentInviteForMail.collectAsStateWithLifecycle()
  val myFamilies by viewModel.myFamilies.collectAsStateWithLifecycle()
  val isLoadingFamilies by viewModel.isLoadingFamilies.collectAsStateWithLifecycle()

  val handleSignOut: () -> Unit = {
    signOutUser(credentialManager = credentialManager, scope = scope)
  }

  when (val state = userProfileState) {
    is UiState.Loading -> {
      Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text("家族カレンダーを読み込み中...", style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
    }

    is UiState.Error -> {
      Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "データの読み込みに失敗しました",
              style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = state.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = handleSignOut) {
              Text("ログアウトしてやり直す")
            }
          }
        }
      }
    }

    is UiState.Success -> {
      val profile = state.data
      val activeGroup = familyGroup
      if (profile?.activeFamilyId.isNullOrBlank()) {
        FamilyOnboardingScreen(
          userProfile = profile,
          userDisplayName = user.displayName.orEmpty().ifBlank { profile?.displayName.orEmpty() },
          userEmail = user.email.orEmpty(),
          currentUserId = user.uid,
          myFamilies = myFamilies,
          isLoadingFamilies = isLoadingFamilies,
          pendingJoinRequest = myPendingJoinRequest,
          lastSentInviteForMail = lastSentInviteForMail,
          scannedInvite = scannedInvite,
          isBusy = isBusy,
          actionMessage = actionMessage,
          onClearMessage = viewModel::clearMessage,
          onSelectFamily = viewModel::selectFamily,
          onCreateFamily = viewModel::createFamily,
          onDeleteFamily = viewModel::deleteFamily,
          onLeaveFamily = viewModel::leaveSharedFamily,
          onUpdateFamilyAppearance = viewModel::updateFamilyAppearance,
          onUpdateGlobalAccount = viewModel::updateGlobalAccount,
          onQrScanned = viewModel::onQrCodeScanned,
          onDismissScannedInvite = viewModel::dismissScannedInvite,
          onConfirmJoinInvite = viewModel::submitJoinRequest,
          onCancelPendingJoin = viewModel::cancelPendingJoin,
          onSignOut = handleSignOut,
        )
      } else if (activeGroup == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.height(12.dp))
              Text("家族カレンダーを同期中...", style = MaterialTheme.typography.bodyLarge)
            }
          }
        }
      } else {
        CalendarHomeScreen(
          familyGroup = activeGroup,
          members = members,
          events = events,
          pendingRequests = pendingRequests,
          currentUserId = user.uid,
          displayedMonth = displayedMonth,
          selectedDate = selectedDate,
          selectedMemberFilter = selectedMemberFilter,
          isBusy = isBusy,
          actionMessage = actionMessage,
          onClearMessage = viewModel::clearMessage,
          onSelectDate = viewModel::selectDate,
          onShiftMonth = viewModel::shiftMonth,
          onJumpToToday = viewModel::jumpToToday,
          onSetMemberFilter = viewModel::setMemberFilter,
          onSaveEvent = viewModel::saveEvent,
          onDeleteEvent = viewModel::deleteEvent,
          onUpdateMyProfile = viewModel::updateMyProfile,
          onUpdateFamilyAppearance = { calName, iconSymbol, iconColorHex, iconImageBase64 ->
            viewModel.updateFamilyAppearance(
              familyId = activeGroup.id,
              familyName = calName,
              iconSymbol = iconSymbol,
              iconColorHex = iconColorHex,
              iconImageBase64 = iconImageBase64,
            )
          },
          onApproveRequest = viewModel::approveJoinRequest,
          onRejectRequest = viewModel::rejectJoinRequest,
          onReturnToHome = viewModel::returnToFamilyHome,
          onDeleteOrLeaveFamily = {
            if (activeGroup.shouldDeleteInsteadOfLeave()) {
              viewModel.deleteFamily(activeGroup.id)
            } else {
              viewModel.leaveSharedFamily(activeGroup.id)
            }
          },
          onSignOut = handleSignOut,
        )
      }
    }
  }
}
