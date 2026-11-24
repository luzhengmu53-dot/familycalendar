package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.example.data.model.FamilyGroup
import com.example.data.model.UserProfile
import com.example.dev.DevPreviewAppRoot
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FamilyOnboardingScreen
import com.example.ui.theme.FamilyCalendarTheme
import com.example.ui.theme.WarmCream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeAndBackNavigationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `authScreen excludes dev skip button from app`() {
    assertEquals(Color.White, WarmCream)

    composeTestRule.setContent {
      FamilyCalendarTheme {
        AuthScreen(
          onAuthSuccess = {},
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    assertEquals(
      0,
      composeTestRule.onAllNodesWithTag("dev_skip_google_auth_button").fetchSemanticsNodes().size,
    )
  }

  @Test
  fun `home supports personal vs shared creation, reminder scheduling without creation alert, and calendar deletion`() {
    var exited = false

    composeTestRule.setContent {
      FamilyCalendarTheme {
        DevPreviewAppRoot(
          onExitDevPreview = { exited = true },
        )
      }
    }
    composeTestRule.waitForIdle()

    // 1. Top-left back & top-right Settings on Home screen
    val homeBackBounds = composeTestRule
      .onNodeWithTag("onboarding_sign_out_button")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()
    assertTrue("Home back button should be on the left", homeBackBounds.left < 80.dp)
    assertTrue("Home back button should be at the top", homeBackBounds.top < 80.dp)

    val settingsBounds = composeTestRule
      .onNodeWithTag("home_settings_button")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()
    assertTrue("Settings button should be on the right", settingsBounds.left > 200.dp)
    assertTrue("Settings button should be at the top", settingsBounds.top < 80.dp)

    // Inline calendar creation form must NOT exist on Home directly
    assertEquals(
      "Create calendar input must not exist before tapping 増やす",
      0,
      composeTestRule.onAllNodesWithTag("family_name_input").fetchSemanticsNodes().size,
    )
    composeTestRule.onNodeWithTag("empty_calendars_card").assertIsDisplayed()

    // 2. Open top-right Settings -> change global shared account name -> Save
    composeTestRule.onNodeWithTag("home_settings_button").performClick()
    composeTestRule.waitForIdle()
    val settingsBackBounds = composeTestRule
      .onNodeWithTag("settings_back_button")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()
    assertTrue("Settings back button should be on the left", settingsBackBounds.left < 80.dp)

    composeTestRule.onNodeWithTag("global_account_name_input").performScrollTo().performTextReplacement("ママ共通")
    composeTestRule.onNodeWithTag("save_global_account_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("ママ共通（共通アカウント）").assertIsDisplayed()

    // 3. Tap "増やす" to create personal calendar "自分ノート"
    composeTestRule.onNodeWithTag("home_add_calendar_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("calendar_type_personal").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("family_name_input").performScrollTo().performTextInput("自分ノート")
    composeTestRule.onNodeWithText("⚽").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("create_family_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Personal calendar opens, displays "自分用カレンダー", and hides QR invite button
    composeTestRule.onNodeWithText("自分ノート").assertIsDisplayed()
    composeTestRule.onNodeWithText("自分用カレンダー").assertIsDisplayed()
    composeTestRule.onNodeWithText("ママ共通").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithTag("open_qr_invite_button").fetchSemanticsNodes().size)

    // Top-left back button returns to Home
    val calendarBackBounds = composeTestRule
      .onNodeWithTag("back_to_home_selector_button")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()
    assertTrue("Calendar back button should be on the left", calendarBackBounds.left < 80.dp)
    composeTestRule.onNodeWithTag("back_to_home_selector_button").performClick()
    composeTestRule.waitForIdle()

    // Home lists "自分ノート" with "自分用" badge and "消す" button
    composeTestRule.onNodeWithText("自分ノート").assertIsDisplayed()
    composeTestRule.onNodeWithText("自分用").assertIsDisplayed()

    // 4. Tap "増やす" again to create shared calendar "佐藤家"
    composeTestRule.onNodeWithTag("home_add_calendar_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("calendar_type_shared").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("family_name_input").performScrollTo().performTextInput("佐藤家")
    composeTestRule.onNodeWithTag("create_family_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Shared calendar shows QR invite button and inherits shared account "ママ共通"
    composeTestRule.onNodeWithText("佐藤家").assertIsDisplayed()
    composeTestRule.onNodeWithTag("open_qr_invite_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("ママ共通").assertIsDisplayed()

    // Verify EventEditorSheet has top-left back button, scheduled reminder chips, and silent creation
    composeTestRule.onNodeWithTag("add_event_fab").performClick()
    composeTestRule.waitForIdle()
    val eventBackBounds = composeTestRule
      .onNodeWithTag("event_sheet_back_home_button")
      .assertIsDisplayed()
      .getUnclippedBoundsInRoot()
    assertTrue("Event sheet back button should be on the left", eventBackBounds.left < 80.dp)
    composeTestRule.onNodeWithText("※ 予定の追加・更新時には通知されません。設定した時刻にお知らせします。")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("reminder_option_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("reminder_option_15").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("reminder_option_-1").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("event_title_input").performScrollTo().performTextInput("歯医者の予約")
    composeTestRule.onNodeWithTag("save_event_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("歯医者の予約").performScrollTo().assertIsDisplayed()

    // Return to Home, verify both calendars exist, delete "自分ノート", then exit via top-left back
    composeTestRule.onNodeWithTag("back_to_home_selector_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("自分ノート").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("佐藤家").performScrollTo().assertIsDisplayed()

    // Tap second "消す" button (自分ノート, since 佐藤家 was prepended at index 0) -> confirm -> verify 自分ノート is removed and 佐藤家 remains
    composeTestRule.onAllNodesWithText("消す", useUnmergedTree = true)[1].performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_delete_or_leave_calendar_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithText("自分ノート").fetchSemanticsNodes().isEmpty())
    composeTestRule.onNodeWithText("佐藤家").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("onboarding_sign_out_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(exited)
  }

  @Test
  fun `home distinguishes delete vs leave based on calendarType and member count`() {
    var deletedId: String? = null
    var leftId: String? = null

    val personalCal = FamilyGroup(
      id = "cal_personal",
      name = "マイ予定",
      calendarType = FamilyGroup.TYPE_PERSONAL,
      ownerUid = "u1",
      memberUids = listOf("u1"),
    )
    val soloSharedCal = FamilyGroup(
      id = "cal_solo_shared",
      name = "ひとり共有",
      calendarType = FamilyGroup.TYPE_SHARED,
      ownerUid = "u1",
      memberUids = listOf("u1"),
    )
    val multiSharedCal = FamilyGroup(
      id = "cal_multi_shared",
      name = "家族みんなで",
      calendarType = FamilyGroup.TYPE_SHARED,
      ownerUid = "u1",
      memberUids = listOf("u1", "u2"),
    )

    composeTestRule.setContent {
      FamilyCalendarTheme {
        FamilyOnboardingScreen(
          userProfile = UserProfile(id = "u1", uid = "u1", displayName = "ママ"),
          userDisplayName = "ママ",
          userEmail = "mama@example.com",
          currentUserId = "u1",
          myFamilies = listOf(personalCal, soloSharedCal, multiSharedCal),
          isLoadingFamilies = false,
          pendingJoinRequest = null,
          lastSentInviteForMail = null,
          scannedInvite = null,
          isBusy = false,
          actionMessage = null,
          onClearMessage = {},
          onCreateFamily = { _, _, _, _, _ -> },
          onDeleteFamily = { deletedId = it },
          onLeaveFamily = { leftId = it },
          onQrScanned = {},
          onDismissScannedInvite = {},
          onConfirmJoinInvite = { _, _ -> },
          onCancelPendingJoin = {},
          onSignOut = {},
        )
      }
    }
    composeTestRule.waitForIdle()

    // Personal -> 消す, Solo shared -> 消す, Multi-member shared -> 退会
    composeTestRule.onNodeWithTag("delete_calendar_cal_personal").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("delete_calendar_cal_solo_shared").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("leave_calendar_cal_multi_shared").performScrollTo().assertIsDisplayed()

    // Confirm leave on multi-member shared calendar (even for owner!)
    composeTestRule.onNodeWithTag("leave_calendar_cal_multi_shared").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_delete_or_leave_calendar_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("cal_multi_shared", leftId)

    // Confirm delete on personal calendar
    composeTestRule.onNodeWithTag("delete_calendar_cal_personal").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_delete_or_leave_calendar_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("cal_personal", deletedId)
  }
}
