package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CalendarEvent
import com.example.data.model.FamilyGroup
import com.example.data.model.InvitePayload
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FamilyCalendarRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createFamily_joinViaQr_approve_shareEvents_and_leaveWithOwnershipTransfer_succeeds() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val ownerEmail = "owner_$suffix@test.com"
    val memberEmail = "member_$suffix@test.com"

    // 1. Owner signs in and creates a shared calendar
    val ownerUid = signInTestUser(ownerEmail)
    val ownerRepo = FamilyCalendarRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) {
      ownerRepo.ensureUserProfile(ownerUid, ownerEmail, "ママ", null).getOrThrow()
    }
    val familyId = withTimeout(TIMEOUT_MS) {
      ownerRepo.createFamily(
        familyName = "テスト家",
        ownerDisplayName = "ママ",
        ownerEmail = ownerEmail,
        colorHex = "#EA4335",
        avatarSymbol = "マ",
        calendarType = FamilyGroup.TYPE_SHARED,
      ).getOrThrow()
    }

    val family = withTimeout(TIMEOUT_MS) {
      ownerRepo.observeFamilyGroup(familyId).first { it != null }!!
    }
    assertEquals("テスト家", family.name)
    assertEquals(FamilyGroup.TYPE_SHARED, family.calendarType)

    // 2. Owner creates a family event with 15m reminder
    val eventId = withTimeout(TIMEOUT_MS) {
      ownerRepo.saveEvent(
        familyId = familyId,
        eventId = null,
        title = "運動会",
        description = "お弁当持参",
        location = "小学校",
        dateKey = "2026-11-21",
        startMinutes = 540L,
        endMinutes = 840L,
        isAllDay = false,
        assignedMemberUid = CalendarEvent.ASSIGNED_ALL,
        memberUids = family.memberUids,
        reminderMinutesBefore = 15L,
      ).getOrThrow()
    }

    // 3. Second member signs in and submits a QR join request
    val memberUid = signInTestUser(memberEmail)
    val memberRepo = FamilyCalendarRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) {
      memberRepo.ensureUserProfile(memberUid, memberEmail, "パパ", null).getOrThrow()
    }
    val invite = InvitePayload(
      familyId = familyId,
      inviteCode = family.inviteCode,
      familyName = family.name,
      ownerUid = ownerUid,
      ownerEmail = ownerEmail,
    )
    withTimeout(TIMEOUT_MS) {
      memberRepo.submitJoinRequest("パパ", memberEmail, invite).getOrThrow()
    }

    // 4. Owner sees pending request and approves
    signInTestUser(ownerEmail)
    val pending = withTimeout(TIMEOUT_MS) {
      ownerRepo.observePendingJoinRequests(familyId).first { list ->
        list.any { it.requesterUid == memberUid }
      }
    }
    val currentMembers = withTimeout(TIMEOUT_MS) {
      ownerRepo.observeMembers(familyId).first { it.isNotEmpty() }
    }
    withTimeout(TIMEOUT_MS) {
      ownerRepo.approveJoinRequest(
        familyId = familyId,
        request = pending.first { it.requesterUid == memberUid },
        currentMembers = currentMembers,
        existingMemberUids = family.memberUids,
      ).getOrThrow()
    }

    // 5. Second member now reads shared events and reminder setting
    signInTestUser(memberEmail)
    val memberEvents = withTimeout(TIMEOUT_MS) {
      memberRepo.observeEvents(familyId).first { list -> list.any { it.id == eventId } }
    }
    val savedEvent = memberEvents.first { it.id == eventId }
    assertEquals("運動会", savedEvent.title)
    assertEquals(15L, savedEvent.reminderMinutesBefore)

    // 6. Multi-member shared calendar cannot be deleted directly, owner must leave (退会) and hand off ownership
    signInTestUser(ownerEmail)
    val multiDeleteAttempt = withTimeout(TIMEOUT_MS) {
      ownerRepo.deleteFamily(familyId)
    }
    assertTrue(multiDeleteAttempt.isFailure)

    withTimeout(TIMEOUT_MS) {
      ownerRepo.leaveSharedFamily(familyId).getOrThrow()
    }
    val postLeaveFamilies = withTimeout(TIMEOUT_MS) {
      ownerRepo.findMyExistingFamilies().getOrThrow()
    }
    assertTrue(postLeaveFamilies.none { it.id == familyId })

    // 7. Remaining sole member becomes owner and can now delete (消す) the calendar
    signInTestUser(memberEmail)
    val updatedFamily = withTimeout(TIMEOUT_MS) {
      memberRepo.observeFamilyGroup(familyId).first { it != null }!!
    }
    assertEquals(memberUid, updatedFamily.ownerUid)
    assertEquals(listOf(memberUid), updatedFamily.memberUids)

    withTimeout(TIMEOUT_MS) {
      memberRepo.deleteFamily(familyId).getOrThrow()
    }
    val afterDelete = withTimeout(TIMEOUT_MS) {
      memberRepo.findMyExistingFamilies().getOrThrow()
    }
    assertTrue(afterDelete.none { it.id == familyId })
  }

  @Test
  fun personalCalendar_blocksJoinRequest_and_canBeDeleted() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val ownerEmail = "personal_owner_$suffix@test.com"
    val strangerEmail = "personal_stranger_$suffix@test.com"

    val ownerUid = signInTestUser(ownerEmail)
    val ownerRepo = FamilyCalendarRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) {
      ownerRepo.ensureUserProfile(ownerUid, ownerEmail, "自分", null).getOrThrow()
    }
    val personalId = withTimeout(TIMEOUT_MS) {
      ownerRepo.createFamily(
        familyName = "自分用メモ",
        ownerDisplayName = "自分",
        ownerEmail = ownerEmail,
        colorHex = "#34A853",
        avatarSymbol = "自",
        calendarType = FamilyGroup.TYPE_PERSONAL,
      ).getOrThrow()
    }
    val created = withTimeout(TIMEOUT_MS) {
      ownerRepo.observeFamilyGroup(personalId).first { it != null }!!
    }
    assertEquals(FamilyGroup.TYPE_PERSONAL, created.calendarType)

    // Stranger cannot send joinRequest to personal calendar
    val strangerUid = signInTestUser(strangerEmail)
    val strangerRepo = FamilyCalendarRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) {
      strangerRepo.ensureUserProfile(strangerUid, strangerEmail, "他人", null).getOrThrow()
    }
    val joinAttempt = withTimeout(TIMEOUT_MS) {
      strangerRepo.submitJoinRequest(
        "他人",
        strangerEmail,
        InvitePayload(personalId, created.inviteCode, created.name, ownerUid, ownerEmail),
      )
    }
    assertTrue(joinAttempt.isFailure)
    val joinEx = joinAttempt.exceptionOrNull()
    assertTrue(joinEx is FirebaseFirestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (joinEx as FirebaseFirestoreException).code)

    // Owner deletes personal calendar
    signInTestUser(ownerEmail)
    withTimeout(TIMEOUT_MS) {
      ownerRepo.deleteFamily(personalId).getOrThrow()
    }
    val remaining = withTimeout(TIMEOUT_MS) {
      ownerRepo.findMyExistingFamilies().getOrThrow()
    }
    assertTrue(remaining.none { it.id == personalId })
  }

  @Test
  fun unauthenticatedOrOutsider_access_failsWithPermissionDenied() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val ownerEmail = "owner_sec_$suffix@test.com"
    val outsiderEmail = "outsider_sec_$suffix@test.com"

    val ownerUid = signInTestUser(ownerEmail)
    val ownerRepo = FamilyCalendarRepository(firestore, auth)
    withTimeout(TIMEOUT_MS) {
      ownerRepo.ensureUserProfile(ownerUid, ownerEmail, "Owner", null).getOrThrow()
    }
    val familyId = withTimeout(TIMEOUT_MS) {
      ownerRepo.createFamily("秘密の家", "Owner", ownerEmail, "#4285F4", "🏠").getOrThrow()
    }

    // Outsider cannot observe events
    signInTestUser(outsiderEmail)
    val outsiderRepo = FamilyCalendarRepository(firestore, auth)
    val outsiderEvents = withTimeout(TIMEOUT_MS) {
      outsiderRepo.observeEvents(familyId).first()
    }
    // Filtered query with array-contains returns empty list for outsider; direct doc write is denied
    assertTrue(outsiderEvents.isEmpty())
    val writeResult = withTimeout(TIMEOUT_MS) {
      outsiderRepo.saveEvent(
        familyId = familyId,
        eventId = null,
        title = "Unauthorized",
        description = "",
        location = "",
        dateKey = "2026-11-22",
        startMinutes = 540L,
        endMinutes = 600L,
        isAllDay = false,
        assignedMemberUid = CalendarEvent.ASSIGNED_ALL,
        memberUids = listOf(ownerUid),
      )
    }
    assertTrue(writeResult.isFailure)
    val ex = writeResult.exceptionOrNull()
    assertTrue(ex is FirebaseFirestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (ex as FirebaseFirestoreException).code)

    // Unauthenticated caller fails
    auth.signOut()
    var threwUnauth = false
    try {
      withTimeout(TIMEOUT_MS) {
        outsiderRepo.observeFamilyGroup(familyId).first()
      }
    } catch (_: IllegalStateException) {
      threwUnauth = true
    }
    assertTrue(threwUnauth)
  }

  private companion object {
    const val TIMEOUT_MS = 8000L
  }
}
