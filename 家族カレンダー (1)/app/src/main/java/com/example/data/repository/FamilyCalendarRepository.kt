package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CalendarEvent
import com.example.data.model.FamilyGroup
import com.example.data.model.FamilyMember
import com.example.data.model.InvitePayload
import com.example.data.model.JoinRequest
import com.example.data.model.MemberColorPalette
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class FamilyCalendarRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    auth = Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("Googleアカウントでのログインが必要です。")
  }

  private fun <T> Flow<T>.logFirestoreErrors(
    uid: String,
    operationType: OperationType,
    path: String,
  ): Flow<T> = catch { error ->
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path, auth)
    }
    throw error
  }

  suspend fun ensureUserProfile(
    uid: String,
    email: String,
    displayName: String,
    photoUrl: String?,
  ): Result<UserProfile> = runCatching {
    val activeUid = requireUserId()
    require(activeUid == uid) { "Authenticated user mismatch" }
    val docRef = db.collection("users").document(uid)
    val existing = docRef.get().await()
    if (!existing.exists()) {
      val payload = mutableMapOf<String, Any>(
        "uid" to uid,
        "email" to email.take(120),
        "displayName" to displayName.ifBlank { "ユーザー" }.take(60),
        "colorHex" to "#4285F4",
        "avatarSymbol" to "🏠",
        "avatarImageBase64" to "",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (!photoUrl.isNullOrBlank()) {
        payload["photoUrl"] = photoUrl.take(500)
      }
      docRef.set(payload).await()
    } else {
      val existingName = existing.getString("displayName").orEmpty()
      val updates = mutableMapOf<String, Any>(
        "email" to email.take(120),
        "displayName" to existingName.ifBlank { displayName.ifBlank { "ユーザー" } }.take(60),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (existing.getString("colorHex").isNullOrBlank()) {
        updates["colorHex"] = "#4285F4"
      }
      if (existing.getString("avatarSymbol").isNullOrBlank()) {
        updates["avatarSymbol"] = "🏠"
      }
      if (!photoUrl.isNullOrBlank()) {
        updates["photoUrl"] = photoUrl.take(500)
      }
      docRef.update(updates).await()
    }
    val snap = docRef.get().await()
    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      ?: UserProfile(id = uid, uid = uid, email = email, displayName = displayName, photoUrl = photoUrl)
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.WRITE, "users/$uid", auth)
  }

  fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
    val activeUid = requireUserId()
    val path = "users/$uid"
    emitAll(
      db.collection("users").document(uid)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .logFirestoreErrors(activeUid, OperationType.GET, path)
    )
  }

  fun observeFamilyGroup(familyId: String): Flow<FamilyGroup?> = flow {
    val uid = requireUserId()
    val path = "families/$familyId"
    emitAll(
      db.collection("families").document(familyId)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(FamilyGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .logFirestoreErrors(uid, OperationType.GET, path)
    )
  }

  fun observeMembers(familyId: String): Flow<List<FamilyMember>> = flow {
    val uid = requireUserId()
    val path = "families/$familyId/members"
    emitAll(
      db.collection("families").document(familyId).collection("members")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(FamilyMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy({ it.role != "owner" }, { it.displayName }))
        }
        .logFirestoreErrors(uid, OperationType.LIST, path)
    )
  }

  fun observeEvents(familyId: String): Flow<List<CalendarEvent>> = flow {
    val uid = requireUserId()
    val path = "families/$familyId/events"
    emitAll(
      db.collection("families").document(familyId).collection("events")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(CalendarEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy({ it.dateKey }, { !it.isAllDay }, { it.startMinutes }, { it.title }))
        }
        .logFirestoreErrors(uid, OperationType.LIST, path)
    )
  }

  fun observePendingJoinRequests(familyId: String): Flow<List<JoinRequest>> = flow {
    val uid = requireUserId()
    val path = "families/$familyId/joinRequests"
    emitAll(
      db.collection("families").document(familyId).collection("joinRequests")
        .whereEqualTo("familyOwnerUid", uid)
        .snapshots()
        .map { snap ->
          snap.toObjects(JoinRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .filter { it.status == "pending" }
        }
        .logFirestoreErrors(uid, OperationType.LIST, path)
    )
  }

  fun observeMyJoinRequest(familyId: String, requesterUid: String): Flow<JoinRequest?> = flow {
    val uid = requireUserId()
    val path = "families/$familyId/joinRequests/$requesterUid"
    emitAll(
      db.collection("families").document(familyId).collection("joinRequests").document(requesterUid)
        .snapshots()
        .map { snap ->
          if (snap.exists()) {
            snap.toObject(JoinRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .logFirestoreErrors(uid, OperationType.GET, path)
    )
  }

  suspend fun findMyExistingFamilies(): Result<List<FamilyGroup>> = runCatching {
    val uid = requireUserId()
    val snap = db.collection("families")
      .whereArrayContains("memberUids", uid)
      .get()
      .await()
    snap.toObjects(FamilyGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.LIST, "families", auth)
  }

  suspend fun createFamily(
    familyName: String,
    ownerDisplayName: String,
    ownerEmail: String,
    colorHex: String,
    avatarSymbol: String,
    avatarImageBase64: String = "",
    iconSymbol: String = "📅",
    iconColorHex: String = "#D97757",
    iconImageBase64: String = "",
    calendarType: String = FamilyGroup.TYPE_SHARED,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val familyRef = db.collection("families").document()
    val familyId = familyRef.id
    val inviteCode = generateInviteCode()
    val cleanFamilyName = familyName.trim().ifBlank { "マイ家族カレンダー" }.take(50)
    val cleanMemberName = ownerDisplayName.trim().ifBlank { "パパ/ママ" }.take(40)
    val cleanSymbol = avatarSymbol.trim().ifBlank { cleanMemberName.take(1) }.take(8)
    val cleanImage = avatarImageBase64.take(65_000)
    val cleanIconSymbol = iconSymbol.trim().ifBlank { "📅" }.take(8)
    val cleanIconImage = iconImageBase64.take(65_000)
    val cleanCalendarType = if (calendarType == FamilyGroup.TYPE_PERSONAL) {
      FamilyGroup.TYPE_PERSONAL
    } else {
      FamilyGroup.TYPE_SHARED
    }

    val batch = db.batch()
    batch.set(
      familyRef,
      mapOf(
        "name" to cleanFamilyName,
        "iconSymbol" to cleanIconSymbol,
        "colorHex" to iconColorHex,
        "iconImageBase64" to cleanIconImage,
        "calendarType" to cleanCalendarType,
        "ownerUid" to uid,
        "ownerEmail" to ownerEmail.take(120),
        "inviteCode" to inviteCode,
        "memberUids" to listOf(uid),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    batch.set(
      familyRef.collection("members").document(uid),
      mapOf(
        "uid" to uid,
        "familyId" to familyId,
        "displayName" to cleanMemberName,
        "email" to ownerEmail.take(120),
        "colorHex" to colorHex,
        "avatarSymbol" to cleanSymbol,
        "avatarImageBase64" to cleanImage,
        "role" to "owner",
        "memberUids" to listOf(uid),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    batch.update(
      db.collection("users").document(uid),
      mapOf(
        "activeFamilyId" to familyId,
        "pendingFamilyId" to "",
        "pendingFamilyName" to "",
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    batch.commit().await()
    familyId
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.CREATE, "families", auth)
  }

  suspend fun submitJoinRequest(
    requesterName: String,
    requesterEmail: String,
    invite: InvitePayload,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val reqRef = db.collection("families").document(invite.familyId)
      .collection("joinRequests").document(uid)
    val existing = reqRef.get().await()
    if (existing.exists() && existing.getString("status") == "rejected") {
      reqRef.update(
        mapOf(
          "requesterName" to requesterName.trim().ifBlank { "家族メンバー" }.take(50),
          "requesterEmail" to requesterEmail.take(120),
          "inviteCode" to invite.inviteCode,
          "status" to "pending",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    } else {
      reqRef.set(
        mapOf(
          "familyId" to invite.familyId,
          "familyOwnerUid" to invite.ownerUid,
          "requesterUid" to uid,
          "requesterName" to requesterName.trim().ifBlank { "家族メンバー" }.take(50),
          "requesterEmail" to requesterEmail.take(120),
          "inviteCode" to invite.inviteCode,
          "status" to "pending",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    }
    db.collection("users").document(uid).update(
      mapOf(
        "pendingFamilyId" to invite.familyId,
        "pendingFamilyName" to invite.familyName.take(60),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.WRITE, "families/${invite.familyId}/joinRequests", auth)
  }

  suspend fun approveJoinRequest(
    familyId: String,
    request: JoinRequest,
    currentMembers: List<FamilyMember>,
    existingMemberUids: List<String>,
  ): Result<Unit> = runCatching {
    requireUserId()
    val familyRef = db.collection("families").document(familyId)
    val updatedMemberUids = (existingMemberUids + request.requesterUid).distinct()
    val usedColors = currentMembers.map { it.colorHex }.toSet()
    val assignedColor = MemberColorPalette.pickNextColor(usedColors)
    val initialSymbol = request.requesterName.trim().take(1).ifBlank { "👤" }

    val existingMembersSnap = familyRef.collection("members")
      .whereArrayContains("memberUids", requireUserId())
      .get()
      .await()
    val existingEventsSnap = familyRef.collection("events")
      .whereArrayContains("memberUids", requireUserId())
      .get()
      .await()

    val batch = db.batch()
    batch.update(
      familyRef.collection("joinRequests").document(request.requesterUid),
      mapOf(
        "status" to "approved",
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    batch.update(
      familyRef,
      mapOf(
        "memberUids" to updatedMemberUids,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    batch.set(
      familyRef.collection("members").document(request.requesterUid),
      mapOf(
        "uid" to request.requesterUid,
        "familyId" to familyId,
        "displayName" to request.requesterName.take(40),
        "email" to request.requesterEmail.take(120),
        "colorHex" to assignedColor,
        "avatarSymbol" to initialSymbol,
        "avatarImageBase64" to "",
        "role" to "member",
        "memberUids" to updatedMemberUids,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    for (doc in existingMembersSnap.documents) {
      batch.update(
        doc.reference,
        mapOf(
          "memberUids" to updatedMemberUids,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
    }
    for (doc in existingEventsSnap.documents) {
      batch.update(
        doc.reference,
        mapOf(
          "memberUids" to updatedMemberUids,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
    }
    batch.commit().await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/joinRequests/${request.requesterUid}", auth)
  }

  suspend fun rejectJoinRequest(familyId: String, requesterUid: String): Result<Unit> = runCatching {
    requireUserId()
    db.collection("families").document(familyId)
      .collection("joinRequests").document(requesterUid)
      .update(
        mapOf(
          "status" to "rejected",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/joinRequests/$requesterUid", auth)
  }

  suspend fun activateJoinedFamily(familyId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val userRef = db.collection("users").document(uid)
    val userSnap = userRef.get().await()
    userRef.update(
      mapOf(
        "activeFamilyId" to familyId,
        "pendingFamilyId" to "",
        "pendingFamilyName" to "",
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    ).await()

    val memberRef = db.collection("families").document(familyId)
      .collection("members").document(uid)
    val memberSnap = memberRef.get().await()
    if (memberSnap.exists() && userSnap.exists()) {
      val cleanName = userSnap.getString("displayName")?.takeIf { it.isNotBlank() }?.take(40)
      val cleanColor = userSnap.getString("colorHex")?.takeIf { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) }
      val cleanSymbol = userSnap.getString("avatarSymbol")?.takeIf { it.isNotBlank() }?.take(8)
      val cleanImage = userSnap.getString("avatarImageBase64")?.take(65_000)
      val syncMap = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
      if (cleanName != null) syncMap["displayName"] = cleanName
      if (cleanColor != null) syncMap["colorHex"] = cleanColor
      if (cleanSymbol != null) syncMap["avatarSymbol"] = cleanSymbol
      if (cleanImage != null) syncMap["avatarImageBase64"] = cleanImage
      if (syncMap.size > 1) {
        memberRef.update(syncMap).await()
      }
    }
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}", auth)
  }

  suspend fun leaveActiveFamily(): Result<Unit> = runCatching {
    val uid = requireUserId()
    db.collection("users").document(uid).update(
      mapOf(
        "activeFamilyId" to "",
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}", auth)
  }

  suspend fun clearPendingJoin(): Result<Unit> = runCatching {
    val uid = requireUserId()
    db.collection("users").document(uid).update(
      mapOf(
        "pendingFamilyId" to "",
        "pendingFamilyName" to "",
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    ).await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}", auth)
  }

  suspend fun updateGlobalAccountProfile(
    displayName: String,
    colorHex: String,
    avatarSymbol: String,
    avatarImageBase64: String = "",
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val cleanName = displayName.trim().ifBlank { "メンバー" }.take(40)
    val cleanSymbol = avatarSymbol.trim().ifBlank { cleanName.take(1) }.take(8)
    val cleanImage = avatarImageBase64.take(65_000)

    val familiesSnap = db.collection("families")
      .whereArrayContains("memberUids", uid)
      .get()
      .await()

    val batch = db.batch()
    batch.update(
      db.collection("users").document(uid),
      mapOf(
        "displayName" to cleanName,
        "colorHex" to colorHex,
        "avatarSymbol" to cleanSymbol,
        "avatarImageBase64" to cleanImage,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    )
    for (familyDoc in familiesSnap.documents) {
      val memberRef = familyDoc.reference.collection("members").document(uid)
      val memberSnap = memberRef.get().await()
      if (memberSnap.exists()) {
        batch.update(
          memberRef,
          mapOf(
            "displayName" to cleanName,
            "colorHex" to colorHex,
            "avatarSymbol" to cleanSymbol,
            "avatarImageBase64" to cleanImage,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }
    }
    batch.commit().await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}", auth)
  }

  suspend fun updateMyMemberProfile(
    familyId: String,
    displayName: String,
    colorHex: String,
    avatarSymbol: String,
    avatarImageBase64: String = "",
  ): Result<Unit> {
    return updateGlobalAccountProfile(
      displayName = displayName,
      colorHex = colorHex,
      avatarSymbol = avatarSymbol,
      avatarImageBase64 = avatarImageBase64,
    )
  }

  suspend fun saveEvent(
    familyId: String,
    eventId: String?,
    title: String,
    description: String,
    location: String,
    dateKey: String,
    startMinutes: Long,
    endMinutes: Long,
    isAllDay: Boolean,
    assignedMemberUid: String,
    memberUids: List<String>,
    reminderMinutesBefore: Long = CalendarEvent.DEFAULT_REMINDER_MINUTES,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val eventsRef = db.collection("families").document(familyId).collection("events")
    val safeStart = if (isAllDay) 0L else startMinutes.coerceIn(0L, 1439L)
    val safeEnd = if (isAllDay) 1439L else endMinutes.coerceIn(safeStart, 1439L)
    val safeReminder = if (reminderMinutesBefore < 0L) {
      CalendarEvent.REMINDER_OFF
    } else {
      reminderMinutesBefore.coerceIn(0L, 1440L)
    }
    val cleanTitle = title.trim().take(100)
    require(cleanTitle.isNotEmpty()) { "タイトルを入力してください" }

    if (eventId.isNullOrBlank()) {
      val docRef = eventsRef.document()
      val ensuredMembers = (memberUids + uid).distinct()
      docRef.set(
        mapOf(
          "familyId" to familyId,
          "title" to cleanTitle,
          "description" to description.trim().take(500),
          "location" to location.trim().take(120),
          "dateKey" to dateKey,
          "startMinutes" to safeStart,
          "endMinutes" to safeEnd,
          "isAllDay" to isAllDay,
          "assignedMemberUid" to assignedMemberUid.ifBlank { CalendarEvent.ASSIGNED_ALL },
          "reminderMinutesBefore" to safeReminder,
          "createdByUid" to uid,
          "memberUids" to ensuredMembers,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      docRef.id
    } else {
      val docRef = eventsRef.document(eventId)
      docRef.update(
        mapOf(
          "title" to cleanTitle,
          "description" to description.trim().take(500),
          "location" to location.trim().take(120),
          "dateKey" to dateKey,
          "startMinutes" to safeStart,
          "endMinutes" to safeEnd,
          "isAllDay" to isAllDay,
          "assignedMemberUid" to assignedMemberUid.ifBlank { CalendarEvent.ASSIGNED_ALL },
          "reminderMinutesBefore" to safeReminder,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      eventId
    }
  }.onFailure { e ->
    if (e is CancellationException) throw e
    val op = if (eventId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE
    handleFirestoreError(e, op, "families/$familyId/events", auth)
  }

  suspend fun deleteEvent(familyId: String, eventId: String): Result<Unit> = runCatching {
    requireUserId()
    db.collection("families").document(familyId)
      .collection("events").document(eventId)
      .delete()
      .await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.DELETE, "families/$familyId/events/$eventId", auth)
  }

  suspend fun updateFamilyDetails(
    familyId: String,
    newName: String,
    iconSymbol: String,
    colorHex: String,
    iconImageBase64: String = "",
  ): Result<Unit> = runCatching {
    requireUserId()
    val cleanName = newName.trim().take(50)
    require(cleanName.isNotEmpty()) { "カレンダー名を入力してください" }
    val cleanSymbol = iconSymbol.trim().ifBlank { "📅" }.take(8)
    val cleanImage = iconImageBase64.take(65_000)
    db.collection("families").document(familyId)
      .update(
        mapOf(
          "name" to cleanName,
          "iconSymbol" to cleanSymbol,
          "colorHex" to colorHex,
          "iconImageBase64" to cleanImage,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "families/$familyId", auth)
  }

  suspend fun updateFamilyName(familyId: String, newName: String): Result<Unit> = runCatching {
    requireUserId()
    val clean = newName.trim().take(50)
    require(clean.isNotEmpty()) { "家族名を入力してください" }
    db.collection("families").document(familyId)
      .update(
        mapOf(
          "name" to clean,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "families/$familyId", auth)
  }

  /**
   * 自分用カレンダー、またはメンバーが自分1人以下の共有カレンダーを完全に削除する。
   */
  suspend fun deleteFamily(familyId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val familyRef = db.collection("families").document(familyId)
    val familySnap = familyRef.get().await()
    require(familySnap.exists()) { "カレンダーが見つかりません" }
    val group = familySnap.toObject(FamilyGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      ?: throw IllegalStateException("カレンダー情報を読み取れませんでした")
    require(group.ownerUid == uid) { "このカレンダーを削除する権限がありません" }
    require(group.memberUids.size <= 1) { "他のメンバーがいる共有カレンダーは「退会」してください" }

    val eventsSnap = familyRef.collection("events")
      .whereArrayContains("memberUids", uid)
      .get()
      .await()
    val joinRequestsSnap = familyRef.collection("joinRequests")
      .whereEqualTo("familyOwnerUid", uid)
      .get()
      .await()
    val membersSnap = familyRef.collection("members")
      .whereArrayContains("memberUids", uid)
      .get()
      .await()

    val subDocRefs = (eventsSnap.documents + joinRequestsSnap.documents + membersSnap.documents).map { it.reference }
    for (chunk in subDocRefs.chunked(400)) {
      val batch = db.batch()
      chunk.forEach { batch.delete(it) }
      batch.commit().await()
    }

    val userRef = db.collection("users").document(uid)
    val userSnap = userRef.get().await()
    val finalBatch = db.batch()
    finalBatch.delete(familyRef)
    if (userSnap.exists() && userSnap.getString("activeFamilyId") == familyId) {
      finalBatch.update(
        userRef,
        mapOf(
          "activeFamilyId" to "",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
    }
    finalBatch.commit().await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.DELETE, "families/$familyId", auth)
  }

  /**
   * 他のメンバーがいる共有カレンダーから自分が退会する。
   * 作成者が退会する場合は残りのメンバーへ作成者権限を引き継ぐ。
   */
  suspend fun leaveSharedFamily(familyId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val familyRef = db.collection("families").document(familyId)
    val familySnap = familyRef.get().await()
    require(familySnap.exists()) { "カレンダーが見つかりません" }
    val group = familySnap.toObject(FamilyGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      ?: throw IllegalStateException("カレンダー情報を読み取れませんでした")
    require(uid in group.memberUids) { "このカレンダーのメンバーではありません" }
    require(group.memberUids.size > 1) { "メンバーが自分のみの場合は「消す」を実行してください" }

    val remainingUids = group.memberUids.filter { it != uid }
    val isLeavingOwner = group.ownerUid == uid
    val nextOwnerUid = if (isLeavingOwner) remainingUids.first() else group.ownerUid

    val membersSnap = familyRef.collection("members")
      .whereArrayContains("memberUids", uid)
      .get()
      .await()
    val eventsSnap = familyRef.collection("events")
      .whereArrayContains("memberUids", uid)
      .get()
      .await()

    val nextOwnerEmail = membersSnap.documents
      .firstOrNull { it.id == nextOwnerUid }
      ?.getString("email")
      .orEmpty()
      .ifBlank { group.ownerEmail }

    // Update events first while uid is still a family member
    for (chunk in eventsSnap.documents.chunked(400)) {
      val eventBatch = db.batch()
      for (doc in chunk) {
        eventBatch.update(
          doc.reference,
          mapOf(
            "memberUids" to remainingUids,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
      }
      eventBatch.commit().await()
    }

    val userRef = db.collection("users").document(uid)
    val userSnap = userRef.get().await()

    val batch = db.batch()
    val familyUpdates = mutableMapOf<String, Any>(
      "memberUids" to remainingUids,
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    if (isLeavingOwner) {
      familyUpdates["ownerUid"] = nextOwnerUid
      familyUpdates["ownerEmail"] = nextOwnerEmail
    }
    batch.update(familyRef, familyUpdates)

    for (memberDoc in membersSnap.documents) {
      if (memberDoc.id == uid) {
        batch.delete(memberDoc.reference)
      } else {
        val memberUpdates = mutableMapOf<String, Any>(
          "memberUids" to remainingUids,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isLeavingOwner && memberDoc.id == nextOwnerUid) {
          memberUpdates["role"] = "owner"
        }
        batch.update(memberDoc.reference, memberUpdates)
      }
    }

    if (userSnap.exists() && userSnap.getString("activeFamilyId") == familyId) {
      batch.update(
        userRef,
        mapOf(
          "activeFamilyId" to "",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
    }
    batch.commit().await()
    Unit
  }.onFailure { e ->
    if (e is CancellationException) throw e
    handleFirestoreError(e, OperationType.UPDATE, "families/$familyId", auth)
  }

  companion object {
    private const val INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generateInviteCode(length: Int = 8): String {
      return buildString(length) {
        repeat(length) {
          append(INVITE_CHARS[random.nextInt(INVITE_CHARS.length)])
        }
      }
    }
  }
}
